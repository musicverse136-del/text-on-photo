package com.textswag.studio

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import com.textswag.studio.EditorCanvas.Layer
import com.textswag.studio.databinding.PanelNormalDetailsBinding
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.function.IntConsumer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class NormalTextPanels(
    activity: EditorActivity,
    canvas: EditorCanvas,
    assets: DesignAssets,
    binding: PanelNormalDetailsBinding
) {
    internal enum class Page {
        FORMAT, GRADIENT, BACKGROUND, POSITION, TEXTURE, SHADOW, NEON, COLOR, RESIZE, WORD, PALETTE,
        BG_COLOR, BG_GRADIENT, BG_OPACITY, BG_BORDER, BG_PADDING, BG_RADIUS, BG_SHADOW
    }

    private val activity: EditorActivity
    private val canvas: EditorCanvas
    private val assets: DesignAssets
    private val scroll: ScrollView
    private val rows: LinearLayout
    private val gradients: MutableList<String>
    private lateinit var layer: Layer
    private lateinit var effects: NormalTextEffects
    private var manual: Boolean = false
    private var step: Int = 5

    init {
        this.activity = activity
        this.canvas = canvas
        this.assets = assets
        scroll = binding.root
        rows = binding.normalDetailRows
        gradients = assets.files("text_gradient", ".jpg", ".jpeg", ".png", ".webp")
    }

    fun show(selected: Layer, page: NormalTextPanels.Page) {
        layer = selected
        effects = selected.effects
        rows.removeAllViews()
        scroll.scrollTo(0, 0)
        when (page) {
            NormalTextPanels.Page.FORMAT -> format()
            NormalTextPanels.Page.GRADIENT -> gradients(false)
            NormalTextPanels.Page.BACKGROUND -> background()
            NormalTextPanels.Page.POSITION -> position()
            NormalTextPanels.Page.TEXTURE -> texture()
            NormalTextPanels.Page.SHADOW -> shadow()
            NormalTextPanels.Page.NEON -> {
                slider(
                    "Radius",
                    effects.neon,
                    0,
                    40,
                    IntConsumer { value: Int -> effects.neon = value })
                palette(effects.neonColor, true, IntConsumer { value: Int ->
                    effects.neonColor = value
                    if (value == Color.TRANSPARENT) effects.neon = 0
                    else if (effects.neon == 0) effects.neon = 18

                }, Runnable { show(layer, NormalTextPanels.Page.NEON) })
            }

            NormalTextPanels.Page.COLOR -> palette(layer.color, false, IntConsumer { value: Int ->
                layer.color = value
                effects.clearWordColors()
                effects.clearGradient()
                effects.texture = null
                effects.texturePath = null

            }, null)

            NormalTextPanels.Page.RESIZE -> resize()
            NormalTextPanels.Page.WORD -> if (effects.curve == 0) wordColors()
            else curveNotice()

            NormalTextPanels.Page.PALETTE -> if (effects.curve == 0) paletteGroups()
            else curveNotice()

            NormalTextPanels.Page.BG_COLOR -> palette(
                effects.background,
                true,
                IntConsumer { value: Int ->
                    effects.background = value
                    effects.backgroundGradientPath = null
                    effects.backgroundGradient = null
                    effects.backgroundGradientEnd = 0

                },
                null
            )

            NormalTextPanels.Page.BG_GRADIENT -> gradients(true)
            NormalTextPanels.Page.BG_OPACITY -> slider(
                "Opacity",
                effects.backgroundOpacity,
                0,
                255,
                IntConsumer { value: Int -> effects.backgroundOpacity = value })

            NormalTextPanels.Page.BG_BORDER -> slider(
                "Border",
                effects.backgroundBorder,
                0,
                30,
                IntConsumer { value: Int -> effects.backgroundBorder = value })

            NormalTextPanels.Page.BG_PADDING -> slider(
                "Padding",
                effects.backgroundPadding,
                0,
                100,
                IntConsumer { value: Int -> effects.backgroundPadding = value })

            NormalTextPanels.Page.BG_RADIUS -> slider(
                "Round Corners",
                effects.backgroundRadius,
                0,
                100,
                IntConsumer { value: Int -> effects.backgroundRadius = value })

            NormalTextPanels.Page.BG_SHADOW -> slider(
                "Shadow",
                effects.backgroundShadow,
                0,
                40,
                IntConsumer { value: Int -> effects.backgroundShadow = value })
        }
    }

    private fun format() {
        val top: LinearLayout = row(78)
        toggle(
            top,
            "Bold",
            R.drawable.ic_format_bold,
            (effects.format and 1) != 0,
            Runnable { effects.format = effects.format xor 1 })
        toggle(
            top,
            "Italic",
            R.drawable.ic_format_italic,
            (effects.format and 2) != 0,
            Runnable { effects.format = effects.format xor 2 })
        toggle(
            top,
            "UnderLine",
            R.drawable.ic_format_underline,
            effects.underline,
            Runnable { effects.underline = !effects.underline })
        toggle(
            top,
            "Strike",
            R.drawable.ic_format_strikethrough,
            effects.strike,
            Runnable { effects.strike = !effects.strike })
        val bottom: LinearLayout = row(78)
        val names: Array<String> = arrayOf("Left", "Center", "Right")
        val icons: IntArray = kotlin.intArrayOf(
            R.drawable.ic_align_left,
            R.drawable.ic_align_center,
            R.drawable.ic_align_right
        )
        for (index in names.indices) {
            val alignment: Int = index
            toggle(
                bottom,
                names[index],
                icons[index],
                effects.alignment == index,
                Runnable { effects.alignment = alignment })
        }
    }

    private fun toggle(
        row: LinearLayout,
        label: String?,
        icon: Int,
        active: Boolean,
        action: Runnable
    ) {
        val item: TextView = tool(row, label, icon, Runnable {
            change(action)
            show(layer, NormalTextPanels.Page.FORMAT)

        })
        item.setSelected(active)
        if (active) item.setBackground(shape(-0x1b1b1c, 12, false))
    }

    private fun background() {
        val first: LinearLayout = row(78)
        backgroundTool(first, "Color", R.drawable.ic_pick_color, NormalTextPanels.Page.BG_COLOR)
        backgroundTool(
            first,
            "Gradient",
            R.drawable.ic_color_gradient,
            NormalTextPanels.Page.BG_GRADIENT
        )
        backgroundTool(first, "Opacity", R.drawable.ic_opacity, NormalTextPanels.Page.BG_OPACITY)
        backgroundTool(first, "Border", R.drawable.ic_text_border, NormalTextPanels.Page.BG_BORDER)
        val second: LinearLayout = row(78)
        backgroundTool(
            second,
            "Padding",
            R.drawable.ic_resize_text,
            NormalTextPanels.Page.BG_PADDING
        )
        backgroundTool(
            second,
            "Round C...",
            R.drawable.ic_round_corners,
            NormalTextPanels.Page.BG_RADIUS
        )
        backgroundTool(second, "Shadow", R.drawable.ic_shadow, NormalTextPanels.Page.BG_SHADOW)
        second.addView(View(activity), LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun backgroundTool(
        row: LinearLayout,
        name: String?,
        icon: Int,
        page: NormalTextPanels.Page
    ) {
        tool(row, name, icon, Runnable { activity.showNormalDetail(layer, page) })
    }

    private fun gradients(background: Boolean) {
        val tiles: MutableList<View?> = ArrayList<View?>()
        tiles.add(
            chip(
                "Clear gradient", R.drawable.ic_none, NormalTextPanels.TILE, false,
                Runnable { applyGradient(background, null, null, null) })
        )
        for (i in NormalTextPanels.GRADIENTS.indices) {
            val colors: IntArray = NormalTextPanels.GRADIENTS[i]
            val tile: ImageView = chip(
                "Gradient " + (i + 1), 0, NormalTextPanels.TILE, false,
                Runnable { applyGradient(background, colors, null, null) })
            val fill: GradientDrawable =
                GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
            fill.setCornerRadius(dp(10).toFloat())
            tile.setBackground(fill)
            tiles.add(tile)
        }
        for (path in gradients) {
            val tile: ImageView =
                chip("Gradient " + (tiles.size), 0, NormalTextPanels.TILE, false, null)
            tile.setScaleType(ImageView.ScaleType.CENTER_CROP)
            tile.setClipToOutline(true)
            try {
                tile.setImageBitmap(assets.image(path, 96))
                tile.setOnClickListener(View.OnClickListener { view: View ->
                    try {
                        applyGradient(background, null, path, assets.image(path, 500))
                    } catch (error: IOException) {
                        activity.message("Could not load this gradient.")
                    }
                })
                tiles.add(tile)
            } catch (ignored: IOException) {
            }
        }
        grid(tiles)
    }

    private fun applyGradient(
        background: Boolean,
        colors: IntArray?,
        path: String?,
        image: Bitmap?
    ) {
        change(Runnable {
            val start: Int = if (colors == null) 0 else colors[0]
            val end: Int = if (colors == null) 0 else colors[1]
            if (background) {
                effects.backgroundGradientStart = start
                effects.backgroundGradientEnd = end
                effects.backgroundGradientPath = path
                effects.backgroundGradient = image
            } else {
                effects.gradientStart = start
                effects.gradient = end
                effects.gradientPath = path
                effects.gradientImage = image
                if (colors != null || image != null) {
                    effects.texturePath = null
                    effects.texture = null
                }
            }

        })
    }

    private fun shadow() {
        val row: LinearLayout = row(49)
        row.setPadding(dp(20), 0, 0, 0)
        row.addView(label("Color", 14), LinearLayout.LayoutParams(dp(65), -1))
        val scroll: HorizontalScrollView = HorizontalScrollView(activity)
        scroll.setHorizontalScrollBarEnabled(false)
        row.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        val strip: LinearLayout = LinearLayout(activity)
        strip.setGravity(Gravity.CENTER_VERTICAL)
        scroll.addView(strip)
        val assign: IntConsumer = IntConsumer { value: Int ->
            effects.shadowColor = value
            if (value == Color.TRANSPARENT) {
                effects.shadow = 0
                effects.shadowX = 0
                effects.shadowY = 0
            }
            if (value != Color.TRANSPARENT && effects.shadow == 0 && effects.shadowX == 0 && effects.shadowY == 0) {
                effects.shadow = 8
            }
            show(layer, NormalTextPanels.Page.SHADOW)

        }
        circle(
            strip, chip(
                "Clear shadow", R.drawable.ic_none, NormalTextPanels.TILE, true,
                Runnable { change(Runnable { assign.accept(Color.TRANSPARENT) }) })
        )
        circle(strip, customColor(effects.shadowColor, true, assign))
        for (color in NormalTextPanels.COLORS) circle(strip, colorChip(color, true, assign))
        slider(
            "Blur",
            effects.shadow,
            0,
            40,
            IntConsumer { value: Int -> updateShadow(Runnable { effects.shadow = value }) })
        slider(
            "Offset X",
            effects.shadowX,
            0,
            80,
            IntConsumer { value: Int -> updateShadow(Runnable { effects.shadowX = value }) })
        slider(
            "Offset Y",
            effects.shadowY,
            0,
            80,
            IntConsumer { value: Int -> updateShadow(Runnable { effects.shadowY = value }) })
    }

    private fun updateShadow(change: Runnable) {
        if (effects.shadowColor == Color.TRANSPARENT) effects.shadowColor = -0x67000000
        change.run()
    }

    private fun palette(
        current: Int,
        allowsClear: Boolean,
        assign: IntConsumer,
        refresh: Runnable?
    ) {
        val apply: IntConsumer = IntConsumer { color: Int ->
            assign.accept(color)
            if (refresh != null) refresh.run()

        }
        val heading: TextView = label("Recommended:", 18)
        heading.setPadding(dp(13), 0, 0, 0)
        rows.addView(heading, LinearLayout.LayoutParams(-1, dp(48)))
        val tiles: MutableList<View?> = ArrayList<View?>()
        if (allowsClear) tiles.add(
            chip(
                "Clear color", R.drawable.ic_none, NormalTextPanels.TILE, false,
                Runnable { change(Runnable { apply.accept(Color.TRANSPARENT) }) })
        )
        tiles.add(customColor(current, false, apply))
        for (color in NormalTextPanels.COLORS) tiles.add(colorChip(color, false, apply))
        grid(tiles)
    }

    private fun customColor(current: Int, round: Boolean, assign: IntConsumer): ImageView {
        return chip(
            "Custom color",
            R.drawable.ic_color_picker,
            NormalTextPanels.TILE,
            round,
            Runnable {
                val target: Layer? = layer
                ColorWheelDialog.show(activity, current, IntConsumer { color: Int ->
                    if (layer == target) change(Runnable { assign.accept(color) })
                })

            })
    }

    private fun curveNotice() {
        val notice: TextView = label("Turn off Curve to use Color Word and Palette.", 16)
        notice.setPadding(dp(18), 0, dp(18), 0)
        row(68).addView(notice)
    }

    private fun wordColors() {
        val preview: TextView = label("", 24)
        preview.setGravity(Gravity.CENTER)
        preview.setTextIsSelectable(true)
        preview.setHighlightColor(-0x783814)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            for (handle in kotlin.arrayOf<Drawable?>(
                preview.getTextSelectHandleLeft(),
                preview.getTextSelectHandleRight()
            )) {
                if (handle != null) handle.mutate().setTint(-0x39db)
            }
        }
        preview.setPadding(dp(15), dp(8), dp(15), dp(8))
        preview.setBackground(shape(-0x19191a, 11, false))
        val box: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        box.setMargins(dp(8), dp(8), dp(8), dp(7))
        preview.setMinHeight(dp(96))
        rows.addView(preview, box)
        updateWordPreview(preview, 0, layer.text.length)

        val recommend: LinearLayout = row(51)
        recommend.setPadding(dp(23), 0, 0, 0)
        recommend.addView(label("Recommended:", 17), LinearLayout.LayoutParams(dp(128), -1))
        val scroll: HorizontalScrollView = HorizontalScrollView(activity)
        scroll.setHorizontalScrollBarEnabled(false)
        recommend.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        val strip: LinearLayout = LinearLayout(activity)
        strip.setGravity(Gravity.CENTER_VERTICAL)
        scroll.addView(strip)
        val apply: IntConsumer = IntConsumer { color: Int ->
            applyWordColor(
                preview,
                preview.getSelectionStart(),
                preview.getSelectionEnd(),
                color
            )
        }
        circle(
            strip,
            chip("Custom color", R.drawable.ic_color_picker, NormalTextPanels.TILE, true, Runnable {
                val start: Int = preview.getSelectionStart()
                val end: Int = preview.getSelectionEnd()
                val target: Layer? = layer
                ColorWheelDialog.show(activity, layer.color, IntConsumer { color: Int ->
                    if (layer == target) change(Runnable {
                        applyWordColor(
                            preview,
                            start,
                            end,
                            color
                        )
                    })
                })

            })
        )
        for (color in NormalTextPanels.COLORS) circle(strip, colorChip(color, true, apply))
    }

    private fun applyWordColor(
        preview: TextView,
        selectionStart: Int,
        selectionEnd: Int,
        color: Int
    ) {
        var start: Int = kotlin.math.min(selectionStart, selectionEnd)
        var end: Int = kotlin.math.max(selectionStart, selectionEnd)
        if (start < 0 || start == end) {
            start = 0
            end = layer.text.length
        }
        clearFillShaders()
        if (effects.selectionColors == null || effects.selectionColors?.size != layer.text.length) {
            effects.selectionColors = IntArray(layer.text.length)
        }
        java.util.Arrays.fill(effects.selectionColors, start, end, color)
        updateWordPreview(preview, start, end)
    }

    private fun updateWordPreview(preview: TextView, start: Int, end: Int) {
        preview.setTextColor(layer.color)
        preview.setText(
            SpannableString(effects.colorWord(layer.text)),
            TextView.BufferType.SPANNABLE
        )
        preview.requestFocus()
        Selection.setSelection(preview.getText() as Spannable?, start, end)
    }

    private fun paletteGroups() {
        val create: MutableList<View?> = ArrayList<View?>()
        create.add(
            chip(
                "Create palette",
                android.R.drawable.ic_input_add,
                Color.WHITE,
                false,
                Runnable { this.createPalette() })
        )
        grid(create)
        try {
            val groups: JSONArray = JSONArray(assets.text("json/colorPalette.json"))
            for (index in 0..<groups.length()) {
                val group: JSONObject = groups.getJSONObject(index)
                val title: TextView = label(group.getString("name"), 18)
                title.setPadding(dp(13), 0, 0, 0)
                rows.addView(title, LinearLayout.LayoutParams(-1, dp(43)))
                val entries: JSONArray = group.getJSONArray("items")
                val tiles: MutableList<View?> = ArrayList<View?>()
                for (item in 0..<entries.length()) {
                    val entry: JSONArray = entries.getJSONArray(item)
                    if (entry.length() == 0) continue
                    val colors: IntArray = IntArray(entry.length())
                    for (i in colors.indices) colors[i] = Color.parseColor(entry.getString(i))
                    tiles.add(
                        paletteTile(
                            group.getString("name") + " palette " + (item + 1),
                            colors
                        )
                    )
                }
                grid(tiles)
            }
        } catch (error: IOException) {
            activity.message("Could not load the bundled palettes.")
        } catch (error: JSONException) {
            activity.message("Could not load the bundled palettes.")
        } catch (error: java.lang.IllegalArgumentException) {
            activity.message("Could not load the bundled palettes.")
        }
    }

    private fun paletteTile(name: String?, colors: IntArray): View {
        val tile: LinearLayout = LinearLayout(activity)
        tile.setOrientation(LinearLayout.VERTICAL)
        tile.setBackground(shape(Color.WHITE, 10, false))
        tile.setClipToOutline(true)
        tile.setElevation(dp(2).toFloat())
        tile.setContentDescription(name)
        tile.setPadding(dp(2), dp(2), dp(2), dp(2))
        for (color in colors) {
            val stripe: View = View(activity)
            stripe.setBackgroundColor(color)
            tile.addView(stripe, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        tile.setOnClickListener(View.OnClickListener { view: View -> applyPalette(colors) })
        return tile
    }

    private fun createPalette() {
        val colors: IntArray = if (effects.paletteColors == null)
            kotlin.intArrayOf(
                NormalTextPanels.COLORS[2],
                NormalTextPanels.COLORS[3],
                NormalTextPanels.COLORS[4],
                NormalTextPanels.COLORS[5],
                NormalTextPanels.COLORS[6]
            )
        else
            requireNotNull(effects.paletteColors).clone()
        val swatches: LinearLayout = LinearLayout(activity)
        swatches.setPadding(dp(12), dp(12), dp(12), dp(12))
        for (i in colors.indices) {
            val index: Int = i
            val pick: ImageView = chip(
                "Pick palette color " + (i + 1), R.drawable.ic_color_picker,
                colors[i], false, null
            )
            val size: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            size.setMargins(dp(3), 0, dp(3), 0)
            swatches.addView(pick, size)
            pick.setOnClickListener(View.OnClickListener { view: View ->
                ColorWheelDialog.show(activity, colors[index], IntConsumer { color: Int ->
                    colors[index] = color
                    pick.setBackground(shape(color, 10, false))

                })
            })
        }
        AlertDialog.Builder(activity).setTitle("Create palette").setView(swatches)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Apply",
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    applyPalette(colors)
                }).show()
    }

    private fun applyPalette(colors: IntArray) {
        change(Runnable {
            clearFillShaders()
            effects.clearWordColors()
            effects.paletteColors = colors.clone()

        })
    }

    private fun clearFillShaders() {
        effects.clearGradient()
        effects.texture = null
        effects.texturePath = null
    }

    private fun colorChip(color: Int, round: Boolean, assign: IntConsumer): ImageView {
        val tile: ImageView = chip(
            String.format(java.util.Locale.ROOT, "#%06X", color and 0xffffff),
            0, color, round, Runnable { change(Runnable { assign.accept(color) }) })
        if (!round) tile.setBackground(shape(color, 10, true))
        return tile
    }

    private fun position() {
        slider(
            "Rotate",
            java.lang.Math.round(layer.rotation),
            -180,
            180,
            IntConsumer { value: Int -> layer.rotation = value.toFloat() },
            true
        )
        val tabs: LinearLayout = row(53)
        tabs.setGravity(Gravity.CENTER)
        mode(tabs, "Relative", false)
        mode(tabs, "Manual", true)
        if (manual) {
            slider("Step", step, 1, 40, IntConsumer { value: Int -> step = value })
            val directions: LinearLayout = row(46)
            tool(directions, "←", 0, Runnable { nudge(-step, 0) })
            tool(directions, "↑", 0, Runnable { nudge(0, -step) })
            tool(directions, "↓", 0, Runnable { nudge(0, step) })
            tool(directions, "→", 0, Runnable { nudge(step, 0) })
            return
        }
        val vertical: LinearLayout = row(49)
        positionTool(vertical, "Top", R.drawable.align_bottom_controls, 180, false, 0)
        positionTool(vertical, "Vertical center", R.drawable.align_horizontal_center, 90, false, 1)
        positionTool(vertical, "Bottom", R.drawable.align_bottom_controls, 0, false, 2)
        val horizontal: LinearLayout = row(49)
        positionTool(horizontal, "Left", R.drawable.align_bottom_controls, 90, true, 0)
        positionTool(
            horizontal,
            "Horizontal center",
            R.drawable.align_horizontal_center,
            0,
            true,
            1
        )
        positionTool(horizontal, "Right", R.drawable.align_bottom_controls, -90, true, 2)
    }

    private fun mode(tabs: LinearLayout, title: String?, manualMode: Boolean) {
        val tab: TextView = label(title, 14)
        tab.setGravity(Gravity.CENTER)
        if (manual == manualMode) {
            tab.setTextColor(Color.WHITE)
            tab.setBackground(shape(NormalTextPanels.INK, 24, false))
        }
        tabs.addView(tab, LinearLayout.LayoutParams(dp(77), dp(32)))
        tab.setOnClickListener(View.OnClickListener { view: View ->
            manual = manualMode
            show(layer, NormalTextPanels.Page.POSITION)

        })
    }

    private fun positionTool(
        row: LinearLayout,
        label: String?,
        icon: Int,
        rotation: Int,
        horizontal: Boolean,
        edge: Int
    ) {
        val button: ImageView = chip(label, icon, Color.TRANSPARENT, false, Runnable {
            change(Runnable {
                val bounds: RectF = RectF()
                layer.getBounds(bounds)
                val angle: Double = java.lang.Math.toRadians(layer.rotation.toDouble())
                val cosine: Double = kotlin.math.abs(kotlin.math.cos(angle))
                val sine: Double = kotlin.math.abs(kotlin.math.sin(angle))
                val span: Double = if (horizontal)
                    bounds.width() * cosine + bounds.height() * sine
                else
                    bounds.width() * sine + bounds.height() * cosine
                val margin: Float =
                    kotlin.math.min(EditorCanvas.SIZE / 2f, span.toFloat() * layer.scale / 2f)
                val position: Float =
                    if (edge == 0) margin else if (edge == 1) EditorCanvas.SIZE / 2f else EditorCanvas.SIZE - margin
                if (horizontal) layer.x = position
                else layer.y = position

            })
        })
        button.setColorFilter(NormalTextPanels.INK)
        button.setElevation(0f)
        button.setRotation(rotation.toFloat())
        row.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun nudge(dx: Int, dy: Int) {
        change(Runnable {
            layer.x =
                kotlin.math.max(0f, kotlin.math.min(EditorCanvas.SIZE.toFloat(), layer.x + dx))
            layer.y =
                kotlin.math.max(0f, kotlin.math.min(EditorCanvas.SIZE.toFloat(), layer.y + dy))

        })
    }

    private fun texture() {
        val images: LinearLayout = row(49)
        images.setPadding(dp(20), 0, dp(20), 0)
        images.addView(label("Images", 14), LinearLayout.LayoutParams(dp(88), -1))
        val choose: ImageView = chip(
            "Choose texture image", R.drawable.ic_gallery, NormalTextPanels.TILE, false,
            Runnable { activity.pickTextTexture(layer) })
        images.addView(choose, LinearLayout.LayoutParams(dp(49), dp(43)))
        if (effects.texture != null) {
            val clear: ImageView =
                chip("Clear texture", R.drawable.ic_none, NormalTextPanels.TILE, true, Runnable {
                    change(Runnable {
                        effects.texture = null
                        effects.texturePath = null

                    })
                    show(layer, NormalTextPanels.Page.TEXTURE)

                })
            circle(images, clear)
        }
        slider(
            "Rotate",
            effects.textureRotation,
            0,
            100,
            IntConsumer { value: Int -> effects.textureRotation = value })
        slider(
            "Vertical",
            effects.textureVertical,
            0,
            100,
            IntConsumer { value: Int -> effects.textureVertical = value })
        slider(
            "Horizontal",
            effects.textureHorizontal,
            0,
            100,
            IntConsumer { value: Int -> effects.textureHorizontal = value })
        slider(
            "Offset X",
            effects.textureX,
            0,
            100,
            IntConsumer { value: Int -> effects.textureX = value })
        slider(
            "Offset Y",
            effects.textureY,
            0,
            100,
            IntConsumer { value: Int -> effects.textureY = value })
    }

    private fun resize() {
        val bounds: RectF = RectF()
        layer.getBounds(bounds)
        val width: Int =
            if (effects.width == 0) java.lang.Math.round(bounds.width()) else effects.width
        slider("Width", width, 24, 1080, IntConsumer { value: Int -> effects.width = value })
        slider(
            "Height",
            effects.height,
            0,
            1080,
            IntConsumer { value: Int -> effects.height = value })
        slider("Text Size", layer.size, 18, 240, IntConsumer { value: Int -> layer.size = value })
        slider(
            "Line Spacing",
            effects.lineSpacing,
            -12,
            48,
            IntConsumer { value: Int -> effects.lineSpacing = value })
        slider(
            "Letter Spacing",
            effects.letterSpacing,
            0,
            8,
            IntConsumer { value: Int -> effects.letterSpacing = value })
    }

    private fun slider(
        title: String?,
        value: Int,
        min: Int,
        max: Int,
        apply: IntConsumer,
        reset: Boolean = false
    ) {
        val row: LinearLayout = row(49)
        row.setPadding(dp(20), 0, dp(18), 0)
        row.addView(label(title, 14), LinearLayout.LayoutParams(dp(if (reset) 55 else 76), -1))
        val bar: SeekBar = SeekBar(activity)
        val number: TextView = label(value.toString(), 14)
        if (reset) circle(
            row,
            chip("Reset rotation", R.drawable.ic_none, NormalTextPanels.TILE, true, Runnable {
                change(Runnable { apply.accept(0) })
                bar.setProgress(-min)
                number.setText("0")

            })
        )
        bar.setContentDescription(title)
        bar.setMax(max - min)
        bar.setProgress(value - min)
        bar.setProgressTintList(ColorStateList.valueOf(NormalTextPanels.GREEN))
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.WHITE))
        bar.setThumbTintList(ColorStateList.valueOf(NormalTextPanels.INK))
        row.addView(bar, LinearLayout.LayoutParams(0, -1, 1f))
        if (reset) {
            number.setGravity(Gravity.CENTER_VERTICAL or Gravity.END)
            row.addView(number, LinearLayout.LayoutParams(dp(30), -1))
        }
        bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            private var tracking: Boolean = false
            private var recorded: Boolean = false
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                tracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                tracking = false
                recorded = false
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, user: Boolean) {
                if (!user) return
                if (!recorded) canvas.recordChange()
                recorded = tracking
                val current: Int = progress + min
                apply.accept(current)
                number.setText(current.toString())
                layer.invalidateText()
                canvas.invalidate()
            }
        })
    }

    private fun change(action: Runnable) {
        activity.editNormal(layer, action)
    }

    private fun grid(tiles: MutableList<View?>) {
        val cell: Int = kotlin.math.min(
            45,
            (activity.getResources().getConfiguration().screenWidthDp - 28) / 6 - 4
        )
        var i: Int = 0
        while (i < tiles.size) {
            val row: LinearLayout = row(cell + 13)
            row.setPadding(dp(5), 0, dp(5), 0)
            for (index in i..<i + 6) {
                val slot: FrameLayout = FrameLayout(activity)
                row.addView(slot, LinearLayout.LayoutParams(0, -1, 1f))
                if (index < tiles.size) {
                    slot.addView(
                        tiles.get(index), FrameLayout.LayoutParams(
                            dp(cell), dp(cell), Gravity.CENTER
                        )
                    )
                }
            }
            i += 6
        }
    }

    private fun circle(row: LinearLayout, chip: View?) {
        val size: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        size.setMarginEnd(dp(5))
        row.addView(chip, size)
    }

    private fun row(height: Int): LinearLayout {
        val row: LinearLayout = LinearLayout(activity)
        row.setGravity(Gravity.CENTER_VERTICAL)
        rows.addView(row, LinearLayout.LayoutParams(-1, dp(height)))
        return row
    }

    private fun label(text: String?, size: Int): TextView {
        val view: TextView = TextView(activity)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            view.setTypeface(activity.getResources().getFont(R.font.font_default))
        } else {
            view.setTypeface(android.graphics.Typeface.create("sans-serif-light", 0))
        }
        view.setText(text)
        view.setTextColor(NormalTextPanels.INK)
        view.setTextSize(size.toFloat())
        view.setGravity(Gravity.CENTER_VERTICAL)
        return view
    }

    private fun tool(row: LinearLayout, text: String?, icon: Int, click: Runnable): TextView {
        val view: TextView = label(text, 14)
        view.setGravity(Gravity.CENTER)
        if (icon != 0) {
            val drawable: Drawable? = activity.getDrawable(icon)
            drawable?.setBounds(0, 0, dp(23), dp(23))
            view.setCompoundDrawables(null, drawable, null, null)
            view.setCompoundDrawablePadding(dp(8))
            view.setPadding(0, dp(12), 0, dp(5))
        }
        row.addView(view, LinearLayout.LayoutParams(0, -1, 1f))
        view.setOnClickListener(View.OnClickListener { ignored: View? -> click.run() })
        return view
    }

    private fun chip(
        name: String?,
        icon: Int,
        color: Int,
        round: Boolean,
        action: Runnable?
    ): ImageView {
        val view: ImageView = ImageView(activity)
        view.setContentDescription(name)
        view.setBackground(shape(color, if (round) 40 else 10, false))
        if (!round) view.setElevation(dp(2).toFloat())
        if (icon != 0) {
            view.setImageResource(icon)
            view.setScaleType(ImageView.ScaleType.FIT_CENTER)
            view.setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        if (action != null) view.setOnClickListener(View.OnClickListener { ignored: View? -> action.run() })
        return view
    }

    private fun shape(color: Int, radius: Int, border: Boolean): GradientDrawable {
        val shape: GradientDrawable = GradientDrawable()
        shape.setColor(color)
        shape.setCornerRadius(dp(radius).toFloat())
        if (border) shape.setStroke(dp(2), Color.WHITE)
        return shape
    }

    private fun dp(size: Int): Int {
        return java.lang.Math.round(size * activity.getResources().getDisplayMetrics().density)
    }

    companion object {
        private const val INK: Int = -0xaeb0b1
        private const val TILE: Int = -0x202021
        private const val GREEN: Int = -0x994496
        private val COLORS: IntArray = kotlin.intArrayOf(
            -0x1000000, -0x1, -0x40d6d6, -0x6fec93, -0x487d, -0x348708,
            -0xd6b555, -0xe08968, -0x8706, -0x16e99f, -0xac2d2, -0x4300,
            -0x4eeea7, -0x3cdcdb, -0x31c3ee, -0x119700, -0x6f00, -0x55bdc,
            -0x5c66da, -0xad78d2, -0x88c672, -0x14c5, -0xaaaaab, -0xde690d,
            -0xff6978, -0xb350b0, -0x86aab8, -0x616162
        )
        private val GRADIENTS: Array<IntArray> = kotlin.arrayOf<IntArray>(
            kotlin.intArrayOf(-0x2b00, -0x7be69), kotlin.intArrayOf(-0x8c8801, -0xac7c7),
            kotlin.intArrayOf(-0x710e0e, -0x35ff11), kotlin.intArrayOf(-0x52170d, -0x704622)
        )

    }
}
