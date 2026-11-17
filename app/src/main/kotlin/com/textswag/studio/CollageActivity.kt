package com.textswag.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.textswag.studio.collagefilters.CollageGlitch
import com.textswag.studio.databinding.ActivityCollageBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.IntConsumer

class CollageActivity : Activity() {
    private lateinit var binding: ActivityCollageBinding
    private lateinit var board: CollageBoard
    private lateinit var tools: LinearLayout
    private lateinit var assets: DesignAssets
    private lateinit var templates: MutableList<MutableList<android.graphics.Path>>
    private var template: Int = 0
    private var page: String = ""
    private var pattern: String? = null
    private var wallpaper: Uri? = null
    private var replaceIndex: Int = -1
    private var outputWidth: Int = 0
    private var outputHeight: Int = 0
    private var jpeg: Boolean = false
    private var busy: Boolean = false
    private var pendingFilters: Int = 0
    private val filterWorker: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityCollageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        board = binding.collageCanvas
        tools = binding.collageTools
        assets = DesignAssets(this)
        if (state == null) {
            val uris: ArrayList<Uri>? =
                intent.getParcelableArrayListExtra<Uri?>(CollageRecentActivity.PHOTOS)
            if (uris != null) for (uri in uris) addPhoto(uri)
        } else {
            val photos: ArrayList<Bundle>? = state.getParcelableArrayList<Bundle?>("photos")
            if (photos != null) for (photo in photos) {
                try {
                    val restored: CollageBoard.Photo =
                        CollageBoard.Photo.Companion.load(this, assets, photo)
                    board.photos.add(restored)
                    if (restored.glitch != null) {
                        renderGlitch(
                            restored,
                            CollageGlitch.valueOf(requireNotNull(restored.glitch)),
                            restored.glitchAmount
                        )
                    }
                } catch (error: IOException) {
                    message("A photo is no longer available.")
                } catch (error: java.lang.SecurityException) {
                    message("A photo is no longer available.")
                }
            }
            board.aspect = state.getFloat("aspect", 1f)
            board.paper = state.getInt("paper", Color.WHITE)
            board.gradientEnd = state.getInt("gradient")
            board.padding = state.getInt("padding")
            board.spacing = state.getInt("spacing")
            board.radius = state.getInt("radius")
            template = state.getInt("template")
            replaceIndex = state.getInt("replaceIndex", -1)
            outputWidth = state.getInt("outputWidth")
            outputHeight = state.getInt("outputHeight")
            jpeg = state.getBoolean("jpeg")
            wallpaper = state.getParcelable<Uri?>("wallpaper")
            pattern = state.getString("pattern")
            restoreBackground()
        }
        if (board.photos.isEmpty()) {
            message("Could not open these photos.")
            finish()
            return
        }
        loadTemplates()
        binding.collageBack.setOnClickListener(View.OnClickListener { view: View -> onBackPressed() })
        binding.collageDone.setOnClickListener(View.OnClickListener { view: View -> done() })
        binding.collageToolBack.setOnClickListener(View.OnClickListener { view: View -> onBackPressed() })
        board.selectionChanged = Runnable {
            if (board.selected < 0) mainTools()
            else imageTools()
        }
        mainTools()
    }

    private fun loadTemplates() {
        templates = CollageTemplates.load(this, board.photos.size)
        template = kotlin.math.max(0, kotlin.math.min(template, templates.size - 1))
        board.setLayoutPieces(templates.get(template))
    }

    private fun addPhoto(uri: Uri?): Boolean {
        try {
            board.photos.add(CollageBoard.Photo(requireNotNull(uri), assets.photo(uri, 1200)))
            return true
        } catch (error: IOException) {
            message("Could not open a selected photo.")
            return false
        } catch (error: java.lang.SecurityException) {
            message("Could not open a selected photo.")
            return false
        }
    }

    private fun showPage(name: String) {
        page = name
        tools.removeAllViews()
        (tools.getParent() as ScrollView).scrollTo(0, 0)
        binding.collageToolBack.setVisibility(if (name.isEmpty()) View.GONE else View.VISIBLE)
    }

    private fun mainTools() {
        board.selected = -1
        board.invalidate()
        showPage("")
        val row: LinearLayout = row(77)
        button(row, "Layout", R.drawable.ic_feature_collage_layout, Runnable { this.layouts() })
        button(
            row,
            "Background",
            R.drawable.ic_feature_collage_background,
            Runnable { this.backgrounds() })
        button(row, "Spacing", R.drawable.ic_feature_collage_space, Runnable { this.spacing() })
        val second: LinearLayout = row(77)
        button(second, "Ratio", R.drawable.ic_resize_text, Runnable { this.ratios() })
        button(second, "Images", R.drawable.ic_replace, Runnable { this.images() })
        second.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun layouts() {
        showPage("Layout")
        var i: Int = 0
        while (i < templates.size) {
            val line: LinearLayout = row(65)
            for (j in i..<i + 5) {
                if (j >= templates.size) {
                    line.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f))
                    continue
                }
                val index: Int = j
                val thumbnail: View = object : View(this) {
                    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(canvas: Canvas) {
                        val side: Float =
                            (kotlin.math.min(getWidth(), getHeight()) - dp(16)).toFloat()
                        canvas.translate((getWidth() - side) / 2, (getHeight() - side) / 2)
                        canvas.scale(side, side)
                        paint.setColor(if (index == template) -0x954241 else -0x676768)
                        paint.setStyle(Paint.Style.FILL)
                        for (path in templates.get(index)) canvas.drawPath(path, paint)
                        paint.setColor(Color.WHITE)
                        paint.setStyle(Paint.Style.STROKE)
                        paint.setStrokeWidth(.035f)
                        for (path in templates.get(index)) canvas.drawPath(path, paint)
                    }
                }
                thumbnail.setContentDescription("Layout " + (index + 1))
                thumbnail.setOnClickListener(View.OnClickListener { view: View ->
                    template = index
                    board.setLayoutPieces(templates.get(index))
                    tools.invalidate()
                    for (row in 0..<tools.getChildCount()) {
                        val group: LinearLayout = tools.getChildAt(row) as LinearLayout
                        for (cell in 0..<group.getChildCount()) group.getChildAt(cell).invalidate()
                    }

                })
                line.addView(thumbnail, LinearLayout.LayoutParams(0, -1, 1f))
            }
            i += 5
        }
    }

    private fun spacing() {
        showPage("Spacing")
        slider("Resize", board.spacing, 40, IntConsumer { value: Int -> board.spacing = value })
        slider("Padding", board.padding, 80, IntConsumer { value: Int -> board.padding = value })
        slider("Radius", board.radius, 30, IntConsumer { value: Int -> board.radius = value })
    }

    private fun ratios() {
        showPage("Ratio")
        val names: Array<String> = arrayOf(
            "1:1", "4:5", "Story", "5:4", "3:4", "4:3", "FB Post", "FB Cover",
            "2:3", "3:2", "9:16", "16:9", "1:2", "YouTube", "X Post", "X Header", "A4", "A5"
        )
        val values: FloatArray = kotlin.floatArrayOf(
            1f, .8f, 9f / 16, 1.25f, .75f, 4f / 3, 1.91f, 2.62f,
            2f / 3, 1.5f, 9f / 16, 16f / 9, .5f, 1.77f, 1.91f, 3f, .7f, .7f
        )
        val scroll: HorizontalScrollView = HorizontalScrollView(this)
        scroll.setHorizontalScrollBarEnabled(false)
        val strip: LinearLayout = LinearLayout(this)
        strip.setPadding(dp(5), dp(30), dp(5), 0)
        scroll.addView(strip)
        tools.addView(scroll, LinearLayout.LayoutParams(-1, dp(132)))
        for (i in names.indices) {
            val aspect: Float = values[i]
            val choice: TextView = label(names[i])
            val outline: GradientDrawable = GradientDrawable()
            outline.setColor(Color.TRANSPARENT)
            outline.setStroke(dp(1), -0x646465)
            outline.setSize(
                dp(if (aspect >= 1) 30 else java.lang.Math.round(30 * aspect)),
                dp(if (aspect >= 1) java.lang.Math.round(30 / aspect) else 30)
            )
            outline.setBounds(0, 0, outline.getIntrinsicWidth(), outline.getIntrinsicHeight())
            choice.setCompoundDrawables(null, outline, null, null)
            choice.setCompoundDrawablePadding(dp(5))
            choice.setPadding(0, dp(9), 0, 0)
            strip.addView(choice, LinearLayout.LayoutParams(dp(52), dp(69)))
            if (board.aspect == aspect) choice.setBackgroundColor(-0x2f1516)
            choice.setOnClickListener(View.OnClickListener { view: View ->
                board.aspect = aspect
                board.invalidate()
                for (j in 0..<strip.getChildCount()) {
                    strip.getChildAt(j)
                        .setBackgroundColor(if (strip.getChildAt(j) === choice) -0x2f1516 else Color.TRANSPARENT)
                }

            })
        }
    }

    private fun backgrounds() {
        showPage("Background")
        val line: LinearLayout = row(50)
        button(line, "Color", 0, Runnable { this.backgrounds() })
        button(line, "Gradient", 0, Runnable { this.backgroundGradients() })
        button(line, "Pattern", 0, Runnable { this.backgroundPatterns() })
        button(line, "Images", 0, Runnable { openPhoto(CollageActivity.WALLPAPER) })
        colorChoices()
    }

    private fun colorChoices() {
        val line: LinearLayout = row(60)
        button(line, "Clear", R.drawable.ic_none, Runnable { setBackground(Color.TRANSPARENT, 0) })
        button(
            line, "Pick", R.drawable.ic_color_picker,
            Runnable {
                ColorWheelDialog.show(
                    this,
                    board.paper,
                    IntConsumer { color: Int -> setBackground(color, 0) })
            })
        val colors: IntArray = kotlin.intArrayOf(
            Color.WHITE, Color.BLACK, -0x4e2423, -0xb2720, -0x93463,
            -0x434d24, -0xbbcca, -0xde690d, -0x3ef9, -0x7f43b1
        )
        var i: Int = 0
        while (i < colors.size) {
            val group: LinearLayout = row(50)
            for (j in i..<i + 5) {
                val color: Int = colors[j]
                swatch(
                    group,
                    "Background " + (j + 1),
                    kotlin.intArrayOf(color),
                    Runnable { setBackground(color, 0) })
            }
            i += 5
        }
    }

    private fun setBackground(color: Int, end: Int) {
        board.paper = color
        board.gradientEnd = end
        board.background = null
        wallpaper = null
        pattern = null
        board.invalidate()
    }

    private fun backgroundGradients() {
        showPage("Gradient")
        val colors: Array<IntArray> = kotlin.arrayOf<IntArray>(
            kotlin.intArrayOf(-0x2b00, -0x7be69),
            kotlin.intArrayOf(-0x8c8801, -0xac7c7),
            kotlin.intArrayOf(-0x710e0e, -0x35ff11),
            kotlin.intArrayOf(-0x52170d, -0x704622),
            kotlin.intArrayOf(-0x571c7d, -0xdc3f54),
            kotlin.intArrayOf(-0x56373, -0x2a9c)
        )
        var i: Int = 0
        while (i < colors.size) {
            val line: LinearLayout = row(62)
            for (j in i..<i + 3) {
                val pair: IntArray = colors[j]
                swatch(
                    line,
                    "Gradient " + (j + 1),
                    pair,
                    Runnable { setBackground(pair[0], pair[1]) })
            }
            i += 3
        }
        imageBackgrounds(assets.files("text_gradient", ".jpg", ".jpeg", ".png", ".webp"))
    }

    private fun backgroundPatterns() {
        showPage("Pattern")
        val images: MutableList<String> = ArrayList<String>()
        for (folder in kotlin.arrayOf<String>("light", "macro", "nature", "life_style")) {
            images.addAll(assets.files("backgrounds/" + folder, ".jpg", ".jpeg", ".png", ".webp"))
        }
        imageBackgrounds(images)
    }

    private fun imageBackgrounds(images: MutableList<String>) {
        var line: LinearLayout? = null
        for (i in 0..<kotlin.math.min(images.size, 30)) {
            if (i % 5 == 0) line = row(65)
            val path = images[i]
            try {
                val tile: ImageView =
                    thumbnail(assets.image(path, 96), "Background image " + (i + 1))
                requireNotNull(line).addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
                tile.setOnClickListener(View.OnClickListener { view: View ->
                    try {
                        val image: Bitmap = assets.image(path, 1200)
                        setBackground(Color.WHITE, 0)
                        pattern = path
                        board.background = image
                        board.invalidate()
                    } catch (error: IOException) {
                        message("Could not load this background.")
                    }
                })
            } catch (ignored: IOException) {
            }
        }
    }

    private fun swatch(row: LinearLayout, label: String?, colors: IntArray, apply: Runnable) {
        val tile: View = View(this)
        val fill: GradientDrawable = if (colors.size == 1)
            GradientDrawable()
        else
            GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors)
        if (colors.size == 1) fill.setColor(colors[0])
        fill.setCornerRadius(dp(10).toFloat())
        fill.setStroke(dp(1), -0x222223)
        tile.setBackground(fill)
        val size: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        size.setMargins(dp(7), 0, dp(7), 0)
        row.addView(tile, size)
        tile.setContentDescription(label)
        tile.setOnClickListener(View.OnClickListener { view: View -> apply.run() })
    }

    private fun images() {
        showPage("Images")
        var line: LinearLayout = row(74)
        for (i in board.photos.indices) {
            if (i > 0 && i % 4 == 0) line = row(74)
            val index: Int = i
            val photo: ImageView = thumbnail(board.photos.get(i).image, "Edit photo " + (i + 1))
            line.addView(photo, LinearLayout.LayoutParams(0, -1, 1f))
            photo.setOnClickListener(View.OnClickListener { view: View ->
                board.selected = index
                board.invalidate()
                imageTools()

            })
        }
        if (board.photos.size < 9) button(
            row(58),
            "Add photos",
            R.drawable.ic_gallery,
            Runnable { CollagePicker.open(this, CollageActivity.ADD) })
    }

    private fun imageTools() {
        if (board.selected < 0) {
            images()
            return
        }
        showPage("Photo")
        val photo: CollageBoard.Photo = board.photos.get(board.selected)
        val top: LinearLayout = row(74)
        button(top, "Replace", R.drawable.ic_replace, Runnable {
            replaceIndex = board.selected
            openPhoto(CollageActivity.REPLACE)

        })
        button(top, "Filter", R.drawable.ic_overlay, Runnable { this.filters() })
        button(top, "Crop", R.drawable.ic_crop, Runnable { this.crop() })
        val bottom: LinearLayout = row(74)
        button(bottom, "Rotate", R.drawable.ic_rotate, Runnable {
            photo.angle = (photo.angle + 90) % 360
            board.invalidate()

        })
        button(bottom, "Flip horizontal", R.drawable.ic_flip_h, Runnable {
            photo.flipX = !photo.flipX
            board.invalidate()

        })
        button(bottom, "Flip vertical", R.drawable.ic_flip_v, Runnable {
            photo.flipY = !photo.flipY
            board.invalidate()

        })
    }

    private fun filters() {
        showPage("Filter")
        filterTabs()
        val photo: CollageBoard.Photo = board.photos.get(board.selected)
        var i: Int = 0
        while (i < CollageBoard.FILTERS.size) {
            val line: LinearLayout = row(65)
            for (j in i..<kotlin.math.min(i + 3, CollageBoard.FILTERS.size)) {
                val index: Int = j
                val tile: ImageView = thumbnail(photo.image, CollageBoard.FILTERS[j])
                tile.setColorFilter(CollageBoard.filter(j))
                requireNotNull(line).addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
                tile.setOnClickListener(View.OnClickListener { view: View ->
                    photo.filter = index
                    photo.toneCurve = null
                    photo.glitch = null
                    photo.toned = null
                    board.invalidate()

                })
            }
            i += 3
        }
        curveFilters(photo)
    }

    private fun curveFilters(photo: CollageBoard.Photo) {
        val curves: MutableList<String> = assets.files("filters", ".acv")
        val sample: Bitmap = Bitmap.createScaledBitmap(photo.image, dp(40), dp(40), true)
        var line: LinearLayout? = null
        for (i in curves.indices) {
            if (i % 3 == 0) line = row(65)
            val path: String = curves.get(i)
            try {
                val tile: ImageView =
                    thumbnail(CollageToneCurve.apply(this, sample, path), DesignAssets.label(path))
                requireNotNull(line).addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
                tile.setOnClickListener(View.OnClickListener { view: View ->
                    try {
                        photo.toned = CollageToneCurve.apply(this, photo.image, path)
                        photo.toneCurve = path
                        photo.glitch = null
                        photo.filter = 0
                        board.invalidate()
                    } catch (error: IOException) {
                        message("Could not apply this filter.")
                    }
                })
            } catch (ignored: IOException) {
            }
        }
    }

    private fun filterTabs() {
        val tabs: LinearLayout = row(44)
        button(tabs, "Normal", 0, Runnable { this.filters() })
        button(tabs, "Glitch", 0, Runnable { this.glitches() })
    }

    private fun glitches() {
        showPage("Glitch")
        filterTabs()
        val photo: CollageBoard.Photo = board.photos.get(board.selected)
        if (photo.glitch != null) {
            val effect: CollageGlitch = CollageGlitch.valueOf(requireNotNull(photo.glitch))
            val setting: LinearLayout = row(52)
            setting.addView(label("Intensity"), LinearLayout.LayoutParams(dp(95), -1))
            val amount: SeekBar = SeekBar(this)
            amount.setContentDescription("Intensity")
            amount.setMax(100)
            amount.setProgress(photo.glitchAmount)
            setting.addView(amount, LinearLayout.LayoutParams(0, -1, 1f))
            amount.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {}
                override fun onStopTrackingTouch(bar: SeekBar) {
                    renderGlitch(photo, effect, bar.getProgress())
                }
            })
        }
        var line: LinearLayout? = null
        for (effect in CollageGlitch.values()) {
            if (effect.ordinal % 3 == 0) line = row(54)
            button(requireNotNull(line), effect.label, 0, Runnable {
                renderGlitch(photo, effect, 70)
                glitches()

            })
        }
    }

    private fun renderGlitch(photo: CollageBoard.Photo, effect: CollageGlitch, amount: Int) {
        photo.glitch = effect.name
        photo.glitchAmount = amount
        pendingFilters++
        binding.collageDone.setEnabled(false)
        filterWorker.execute(Runnable {
            var rendered: Bitmap? = null
            try {
                rendered = effect.render(this, photo.image, amount)
            } catch (ignored: java.lang.RuntimeException) {
            }
            val filtered: Bitmap? = rendered
            runOnUiThread(Runnable {
                if (isDestroyed()) return@Runnable
                pendingFilters--
                binding.collageDone.setEnabled(!busy && pendingFilters == 0)
                if (effect.name != photo.glitch || photo.glitchAmount != amount) return@Runnable
                if (filtered == null) {
                    photo.glitch = null
                    message("Could not apply this filter.")
                    return@Runnable
                }
                photo.toned = filtered
                photo.filter = 0
                photo.toneCurve = null
                board.invalidate()

            })

        })
    }

    private fun crop() {
        showPage("Crop")
        val photo: CollageBoard.Photo = board.photos.get(board.selected)
        slider(
            "Zoom",
            java.lang.Math.round((photo.zoom - 1) * 100),
            300,
            IntConsumer { value: Int -> photo.zoom = 1 + value / 100f })
        slider(
            "Horizontal",
            java.lang.Math.round(photo.x * 100) + 200,
            400,
            IntConsumer { value: Int -> photo.x = (value - 200) / 100f })
        slider(
            "Vertical",
            java.lang.Math.round(photo.y * 100) + 200,
            400,
            IntConsumer { value: Int -> photo.y = (value - 200) / 100f })
    }

    private fun slider(title: String?, value: Int, max: Int, update: IntConsumer) {
        val row: LinearLayout = row(55)
        val name: TextView = label(title)
        row.addView(name, LinearLayout.LayoutParams(dp(100), -1))
        val slider: SeekBar = SeekBar(this)
        slider.setContentDescription(title)
        slider.setMax(max)
        slider.setProgress(value)
        slider.setProgressTintList(ColorStateList.valueOf(-0x994496))
        slider.setThumbTintList(ColorStateList.valueOf(-0xaeb0b1))
        row.addView(slider, LinearLayout.LayoutParams(0, -1, 1f))
        slider.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
            override fun onProgressChanged(bar: SeekBar?, progress: Int, user: Boolean) {
                if (user) {
                    update.accept(progress)
                    board.invalidate()
                }
            }
        })
    }

    private fun thumbnail(bitmap: Bitmap?, name: String?): ImageView {
        val image: ImageView = ImageView(this)
        image.setPadding(dp(4), dp(4), dp(4), dp(4))
        image.setScaleType(ImageView.ScaleType.CENTER_CROP)
        image.setImageBitmap(bitmap)
        image.setContentDescription(name)
        return image
    }

    private fun row(height: Int): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(dp(6), 0, dp(6), 0)
        tools.addView(row, LinearLayout.LayoutParams(-1, dp(height)))
        return row
    }

    private fun label(title: String?): TextView {
        val text: TextView = TextView(this)
        if (Build.VERSION.SDK_INT >= 26) text.setTypeface(getResources().getFont(R.font.font_default))
        text.setText(title)
        text.setTextSize(14f)
        text.setTextColor(-0xaeb0b1)
        text.setGravity(Gravity.CENTER)
        return text
    }

    private fun button(row: LinearLayout, title: String?, icon: Int, click: Runnable): TextView {
        val text: TextView = label(title)
        if (icon != 0) {
            val image: Drawable? = getDrawable(icon)
            image?.setBounds(0, 0, dp(24), dp(24))
            text.setCompoundDrawables(null, image, null, null)
            text.setCompoundDrawablePadding(dp(5))
            text.setPadding(0, dp(9), 0, 0)
        }
        row.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        text.setOnClickListener(View.OnClickListener { view: View -> click.run() })
        return text
    }

    private fun openPhoto(request: Int) {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE), request
        )
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (result != Activity.RESULT_OK) return
        if (request == CollageActivity.PREVIEW) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        if (data == null) return
        if (request == CollageActivity.SAVE) {
            write(data.getData(), false)
            return
        }
        val photos: ArrayList<Uri> = CollagePicker.photos(data)
        if (photos.isEmpty()) return
        CollagePicker.keepAccess(this, photos)
        val uri: Uri? = photos.get(0)
        if (request == CollageActivity.ADD) {
            val previous: Int = board.photos.size
            for (photo in photos) {
                if (board.photos.size >= 9) break
                var exists: Boolean = false
                for (item in board.photos) if (item.uri == photo) exists = true
                if (!exists) addPhoto(photo)
            }
            if (board.photos.size != previous) {
                template = 0
                loadTemplates()
            }
            images()
        } else if (request == CollageActivity.REPLACE && replaceIndex >= 0 && replaceIndex < board.photos.size) {
            try {
                val replacement: CollageBoard.Photo =
                    CollageBoard.Photo(requireNotNull(uri), assets.photo(uri, 1200))
                board.photos.set(replaceIndex, replacement)
                board.selected = replaceIndex
                imageTools()
            } catch (error: IOException) {
                message("Could not open that photo.")
            } catch (error: java.lang.SecurityException) {
                message("Could not open that photo.")
            }
        } else if (request == CollageActivity.WALLPAPER) {
            try {
                val image: Bitmap? = assets.photo(requireNotNull(uri), 1200)
                setBackground(Color.WHITE, 0)
                wallpaper = uri
                board.background = image
            } catch (error: IOException) {
                message("Could not open that background.")
            } catch (error: java.lang.SecurityException) {
                message("Could not open that background.")
            }
        }
        board.invalidate()
    }

    private fun restoreBackground() {
        try {
            if (wallpaper != null) board.background = assets.photo(requireNotNull(wallpaper), 1200)
            else if (pattern != null) board.background = assets.image(requireNotNull(pattern), 1200)
        } catch (ignored: IOException) {
        } catch (ignored: java.lang.SecurityException) {
        }
    }

    private fun done() {
        if (busy) return
        if (intent.getBooleanExtra(CollageRecentActivity.REPLACE, false)) {
            outputWidth = dimension(1440, true)
            outputHeight = dimension(1440, false)
            jpeg = false
            write(null, true)
        } else {
            chooseExportSize()
        }
    }

    private fun dimension(longest: Int, horizontal: Boolean): Int {
        return kotlin.math.max(
            1,
            java.lang.Math.round(
                longest * kotlin.math.min(
                    if (horizontal) board.aspect else 1 / board.aspect,
                    1f
                )
            )
        )
    }

    private fun chooseExportSize() {
        val options: LinearLayout = LinearLayout(this)
        options.setOrientation(LinearLayout.VERTICAL)
        options.setPadding(dp(18), dp(8), dp(18), 0)
        val preset: Spinner = Spinner(this)
        val names: Array<String> = arrayOf(
            "Auto (Device Screen)", "Full HD (1920px)", "HD (1080px)",
            "HQ (720px)", "SD (480px)", "Custom"
        )
        val sizes: IntArray = kotlin.intArrayOf(
            kotlin.math.max(
                1,
                java.lang.Math.round(kotlin.math.max(board.board.width(), board.board.height()))
            ),
            1920, 1080, 720, 480
        )
        val adapter: ArrayAdapter<String?> =
            ArrayAdapter<String?>(this, android.R.layout.simple_spinner_dropdown_item, names)
        preset.setAdapter(adapter)
        options.addView(preset, LinearLayout.LayoutParams(-1, dp(52)))
        val fields: LinearLayout = LinearLayout(this)
        val width: EditText = sizeField("Width", dimension(sizes[0], true))
        val height: EditText = sizeField("Height", dimension(sizes[0], false))
        fields.addView(width, LinearLayout.LayoutParams(0, -2, 1f))
        fields.addView(height, LinearLayout.LayoutParams(0, -2, 1f))
        fields.setVisibility(View.GONE)
        options.addView(fields)
        preset.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                fields.setVisibility(if (position == sizes.size) View.VISIBLE else View.GONE)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
        val formats: RadioGroup = RadioGroup(this)
        formats.setOrientation(LinearLayout.HORIZONTAL)
        formats.setGravity(Gravity.CENTER)
        val png: RadioButton = RadioButton(this)
        png.setId(View.generateViewId())
        png.setText("PNG")
        val jpg: RadioButton = RadioButton(this)
        jpg.setId(View.generateViewId())
        jpg.setText("JPG")
        formats.addView(png)
        formats.addView(jpg)
        formats.check(png.getId())
        options.addView(formats, LinearLayout.LayoutParams(-1, dp(55)))
        val dialog: AlertDialog = AlertDialog.Builder(this).setTitle("Select size").setView(options)
            .setNegativeButton("Cancel", null).setPositiveButton("Confirm", null).create()
        dialog.setOnShowListener(OnShowListener { ignored: DialogInterface? ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(View.OnClickListener { view: View ->
                    val index: Int = preset.getSelectedItemPosition()
                    try {
                        outputWidth = if (index == sizes.size) width.getText().toString()
                            .toInt() else dimension(sizes[index], true)
                        outputHeight = if (index == sizes.size) height.getText().toString()
                            .toInt() else dimension(sizes[index], false)
                        if (outputWidth < 1 || outputWidth > 4096 || outputHeight < 1 || outputHeight > 4096) {
                            throw java.lang.NumberFormatException()
                        }
                    } catch (error: java.lang.NumberFormatException) {
                        message("Enter dimensions from 1 to 4096 pixels.")
                        return@OnClickListener
                    }
                    jpeg = formats.getCheckedRadioButtonId() == jpg.getId()
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setMessage("Continue editing this collage in Text Swag?")
                        .setPositiveButton(
                            "Edit",
                            DialogInterface.OnClickListener { prompt: DialogInterface?, which: Int ->
                                write(
                                    null,
                                    true
                                )
                            })
                        .setNegativeButton(
                            "Later",
                            DialogInterface.OnClickListener { prompt: DialogInterface?, which: Int ->
                                if (Build.VERSION.SDK_INT >= 29) write(null, false)
                                else startActivityForResult(
                                    Intent(Intent.ACTION_CREATE_DOCUMENT).setType(outputMime())
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .putExtra(Intent.EXTRA_TITLE, "Collage" + extension()),
                                    CollageActivity.SAVE
                                )
                            }).show()

                })
        })
        dialog.show()
    }

    private fun sizeField(name: String?, dimension: Int): EditText {
        val value: EditText = EditText(this)
        value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER)
        value.setSingleLine(true)
        value.setHint(name)
        value.setContentDescription(name)
        value.setText(dimension.toString())
        return value
    }

    private fun outputMime(): String {
        return if (jpeg) "image/jpeg" else "image/png"
    }

    private fun extension(): String {
        return if (jpeg) ".jpg" else ".png"
    }

    private fun encode(image: Bitmap, output: OutputStream): Boolean {
        return image.compress(
            if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
            95,
            output
        )
    }

    private fun write(destination: Uri?, returnToEditor: Boolean) {
        if (busy) return
        busy = true
        binding.collageDone.setEnabled(false)
        val image: Bitmap = board.render(outputWidth, outputHeight, jpeg)
        java.lang.Thread(Runnable {
            var result: Uri? = null
            try {
                if (returnToEditor) {
                    val folder: File = File(getCacheDir(), "collages")
                    if (!folder.isDirectory() && !folder.mkdirs()) throw IOException("Collage cache unavailable")
                    val file: File = File.createTempFile("collage-", extension(), folder)
                    try {
                        FileOutputStream(file).use { output ->
                            if (!encode(image, output)) throw IOException("Collage encoding failed")
                        }
                    } catch (error: IOException) {
                        file.delete()
                        throw error
                    }
                    result = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".collages",
                        file
                    )
                } else {
                    result = writeLibrary(destination, image)
                }
            } catch (ignored: IOException) {
            } catch (ignored: java.lang.SecurityException) {
            } finally {
                image.recycle()
            }
            val output: Uri? = result
            runOnUiThread(Runnable {
                if (isDestroyed() || isFinishing()) return@Runnable
                busy = false
                binding.collageDone.setEnabled(true)
                if (output == null) {
                    message("Could not save the collage.")
                    return@Runnable
                }
                if (returnToEditor) {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().setData(output).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                    finish()
                } else {
                    startActivityForResult(
                        Intent(this, SavedImageActivity::class.java).setData(output)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        CollageActivity.PREVIEW
                    )
                }

            })

        }, "collage-export").start()
    }

    @Throws(IOException::class)
    private fun writeLibrary(destination: Uri?, image: Bitmap): Uri {
        var target: Uri? = destination
        if (target == null) {
            val values: ContentValues = ContentValues()
            values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "Collage-" + java.lang.System.currentTimeMillis() + extension()
            )
            values.put(MediaStore.Images.Media.MIME_TYPE, outputMime())
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Text Swag"
            )
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
            target = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
        if (target == null) throw IOException("No collage destination")
        try {
            contentResolver.openOutputStream(target, "w").use { output ->
                if (output == null || !encode(image, output)) {
                    throw IOException("Collage encoding failed")
                }
            }
            if (destination == null) {
                val ready: ContentValues = ContentValues()
                ready.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(target, ready, null, null)
            }
            return target
        } catch (error: IOException) {
            if (destination == null) contentResolver.delete(target, null, null)
            throw error
        } catch (error: java.lang.SecurityException) {
            if (destination == null) contentResolver.delete(target, null, null)
            throw error
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        val photos: ArrayList<Bundle?> = ArrayList<Bundle?>()
        for (photo in board.photos) photos.add(photo.save())
        state.putParcelableArrayList("photos", photos)
        state.putInt("template", template)
        state.putInt("replaceIndex", replaceIndex)
        state.putInt("outputWidth", outputWidth)
        state.putInt("outputHeight", outputHeight)
        state.putBoolean("jpeg", jpeg)
        state.putInt("paper", board.paper)
        state.putInt("gradient", board.gradientEnd)
        state.putInt("padding", board.padding)
        state.putInt("spacing", board.spacing)
        state.putInt("radius", board.radius)
        state.putFloat("aspect", board.aspect)
        state.putParcelable("wallpaper", wallpaper)
        state.putString("pattern", pattern)
    }

    override fun onBackPressed() {
        when (page) {
            "Crop", "Filter", "Glitch" -> imageTools()
            "Gradient", "Pattern" -> backgrounds()
            "" -> AlertDialog.Builder(this).setTitle("Close collage?")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton(
                    "Close",
                    DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> finish() })
                .show()

            else -> mainTools()
        }
    }

    override fun onDestroy() {
        filterWorker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(size: Int): Int {
        return java.lang.Math.round(size * getResources().getDisplayMetrics().density)
    }

    private fun message(text: String?) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REPLACE: Int = 1
        private const val ADD: Int = 2
        private const val WALLPAPER: Int = 3
        private const val SAVE: Int = 4
        private const val PREVIEW: Int = 5
    }
}
