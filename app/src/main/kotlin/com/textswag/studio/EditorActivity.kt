package com.textswag.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.View.DragShadowBuilder
import android.view.View.OnDragListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import com.textswag.studio.EditorCanvas.Layer
import com.textswag.studio.StickerLibrary.Pack
import com.textswag.studio.databinding.ActivityEditorBinding
import com.textswag.studio.databinding.ItemEditorLayerBinding
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.IntConsumer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class EditorActivity : Activity() {
    private lateinit var binding: ActivityEditorBinding
    private val writer: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var canvas: EditorCanvas
    private lateinit var assets: DesignAssets
    private lateinit var textStyles: TextStyles
    private lateinit var styleControls: TextStyleControls
    private var pendingStyleLayer: Int = -1
    private var pendingNormalLayer: Int = -1
    private var pendingFontLayer: Int = -1
    private var pendingTextureLayer: Int = -1
    private lateinit var normalPanels: NormalTextPanels
    private var normalPage: NormalTextPanels.Page? = null
    private lateinit var fonts: MutableList<String>
    private val fontNames: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
    private var paper: Int = EditorActivity.DEFAULT_PAPER
    private var backgroundAsset: String? = null
    private var backgroundPhoto: Uri? = null
    private var collagePhotos: ArrayList<Uri>? = null
    private var drawingReference: File? = null
    private var pendingDrawingLayer: Int = -1
    private val stickerLibrary by lazy { StickerLibrary(this) }
    private var assetBack: Runnable? = null
    private val stickerWorker: ExecutorService = Executors.newFixedThreadPool(2)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        assets = DesignAssets(this)
        canvas = binding.canvas
        normalPanels = NormalTextPanels(this, canvas, assets, binding.normalDetailPanel)
        canvas.setDeleteLayerAction(java.util.function.Consumer { layer: Layer ->
            AlertDialog.Builder(this)
                .setTitle("Delete layer?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Delete",
                    DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                        val index: Int = canvas.layers().indexOf(layer)
                        if (index >= 0) canvas.remove(index)
                        refreshLayerList()

                    }).show()
        })
        textStyles = TextStyles(this, assets)
        canvas.setTextEditAction(Runnable {
            val index: Int = canvas.selectedIndex()
            if (index < 0) return@Runnable
            val layer: Layer = canvas.layers().get(index)
            if (layer.styleIndex >= 0) editStyledText(layer)
            else if (layer.asset == null) openNormalTextInput(layer)

        })
        canvas.setSelectionChangedAction(Runnable {
            val index: Int = canvas.selectedIndex()
            val layer: Layer? = if (index < 0) null else canvas.layers().get(index)
            if (normalPage != null) {
                if (layer != null && layer.asset == null && layer.styleIndex < 0 && !layer.locked) {
                    showNormalDetail(layer, requireNotNull(normalPage))
                } else showFeatures()
                return@Runnable
            }
            if (binding.curvePanel.root.getVisibility() == View.VISIBLE) {
                if (layer != null && layer.asset == null && layer.styleIndex < 0) showCurvePanel(
                    layer
                )
                else showFeatures()
                return@Runnable
            }
            if (binding.drawingTools.root.getVisibility() == View.VISIBLE) {
                if (layer != null && layer.asset?.startsWith("drawing:") == true) showDrawingTools(
                    layer
                )
                else showFeatures()
                return@Runnable
            }
            if (binding.normalTextPanel.root.getVisibility() == View.VISIBLE) {
                if (layer == null || layer.asset != null || layer.styleIndex >= 0) showFeatures()
                return@Runnable
            }
            if (binding.stylePanel.root.getVisibility() != View.VISIBLE) return@Runnable
            if (layer == null || layer.styleIndex < 0) showFeatures()
            else showStylePanel(layer)

        })
        loadFonts()

        binding.close.setOnClickListener(View.OnClickListener { view: View -> onBackPressed() })
        binding.export.setOnClickListener(View.OnClickListener { view: View -> requestExport() })
        binding.undo.setOnClickListener(View.OnClickListener { view: View ->
            canvas.undo()
            refreshLayerList()

        })
        binding.redo.setOnClickListener(View.OnClickListener { view: View ->
            canvas.redo()
            refreshLayerList()

        })
        canvas.setHistoryChangedAction(Runnable { this.updateHistoryButtons() })
        updateHistoryButtons()
        binding.editSelected.setOnClickListener(View.OnClickListener { view: View ->
            val index: Int = canvas.selectedIndex()
            if (index >= 0) {
                val layer: Layer = canvas.layers().get(index)
                if (layer.asset == null && layer.styleIndex < 0) openNormalTextInput(layer)
                else editLayer(index)
            } else addNormalText()

        })
        binding.background.setOnClickListener(View.OnClickListener { view: View -> showBackgroundTools() })
        bindBackgroundTools()
        binding.addText.setOnClickListener(View.OnClickListener { view: View -> addNormalText() })
        binding.textStyle.setOnClickListener(View.OnClickListener { view: View ->
            editStyledText(
                null
            )
        })
        binding.addPhoto.setOnClickListener(View.OnClickListener { view: View ->
            pickPhoto(
                EditorActivity.PICK_LAYER
            )
        })
        binding.decorators.setOnClickListener(View.OnClickListener { view: View -> showDecoratorCategories() })
        binding.decoratorCategories.decoratorBorders.setOnClickListener(View.OnClickListener { view: View ->
            showDecorators(
                "Borders",
                "border"
            )
        })
        binding.decoratorCategories.decoratorLines.setOnClickListener(View.OnClickListener { view: View ->
            showDecorators(
                "Lines",
                "line"
            )
        })
        binding.decoratorCategories.decoratorTypography.setOnClickListener(View.OnClickListener { view: View ->
            showDecorators(
                "Typography",
                "typography"
            )
        })
        binding.stickers.setOnClickListener(View.OnClickListener { view: View -> showStickerCatalog() })
        bindNormalTools()
        binding.emoji.setOnClickListener(View.OnClickListener { view: View -> showEmoji() })
        binding.handDraw.setOnClickListener(View.OnClickListener { view: View -> openDrawing() })
        binding.autoSelect.setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { button: CompoundButton?, enabled: Boolean ->
                canvas.setAutoSelect(
                    enabled
                )
            })
        binding.layers.setOnClickListener(View.OnClickListener { view: View -> chooseLayer() })
        binding.featureTab.setOnClickListener(View.OnClickListener { view: View -> showFeatures() })
        showFeatures()

        if (state != null) {
            restoreDesign(state)
        } else {
            when (intent.getIntExtra(EditorActivity.ENTRY_MODE, EditorActivity.MODE_BLANK)) {
                EditorActivity.MODE_BACKGROUND -> chooseBackground()
                EditorActivity.MODE_PHOTO -> pickPhoto(EditorActivity.PICK_PHOTO)
                else -> intent.data?.let(::selectImportedCollage)
            }
        }
    }

    private fun loadFonts() {
        val available: MutableList<String> = assets.files("fonts", ".ttf", ".otf")
        fontNames.clear()
        fontNames.put("", "System")
        try {
            val catalog: JSONArray = JSONArray(assets.text("json/localFonts.json"))
            for (i in 0..<catalog.length()) {
                val font: JSONObject = catalog.getJSONObject(i)
                val path: String = font.getString("relativePath")
                if (available.contains(path)) fontNames.put(path, font.getString("name"))
            }
        } catch (ignored: IOException) {
            // Preserve access to all bundled fonts if the catalog cannot be read.
        } catch (ignored: JSONException) {
        }
        for (path in available) fontNames.putIfAbsent(path, DesignAssets.label(path))
        val custom: Array<File>? = File(getFilesDir(), "fonts").listFiles(
            FilenameFilter { directory: File?, name: String ->
                name.endsWith(".ttf") || name.endsWith(
                    ".otf"
                )
            })
        if (custom != null) {
            Arrays.sort(custom)
            for (file in custom) fontNames.put(
                "local-font:" + file.getName(),
                DesignAssets.label(file.getName())
            )
        }
        fonts = ArrayList<String>(fontNames.keys)
    }

    private fun showDecoratorCategories() {
        showFeatures(204)
        binding.featureGrid.setVisibility(View.GONE)
        binding.decoratorCategories.root.setVisibility(View.VISIBLE)
    }

    private fun showDecorators(title: String?, folder: String?) {
        val choices: MutableList<String> =
            assets.files("ornaments/" + folder, *EditorActivity.IMAGE_TYPES)
        showInlineAssets(title, choices, ImageSelection { path: String -> this.addSticker(path) })
        assetBack = Runnable { this.showDecoratorCategories() }
    }

    private fun openInlineAssets(): GridView {
        showFeatures(204)
        binding.featureGrid.setVisibility(View.GONE)
        binding.editorAssetsPanel.root.setVisibility(View.VISIBLE)
        val tabs: LinearLayout = binding.editorAssetsPanel.assetTabs
        tabs.removeAllViews()
        return binding.editorAssetsPanel.editorAssetGrid
    }

    private fun showInlineAssets(
        title: String?,
        paths: MutableList<String>,
        choose: ImageSelection
    ) {
        val grid: GridView = openInlineAssets()
        addAssetTab(title, true, null)
        grid.setNumColumns(4)
        grid.setAdapter(Thumbnails(paths))
        grid.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, view: View?, index: Int, id: Long ->
            choose.select(
                paths.get(index)
            )
        })
    }

    private fun addAssetTab(
        title: String?,
        selected: Boolean,
        action: View.OnClickListener?
    ): TextView {
        val tabs: LinearLayout = binding.editorAssetsPanel.assetTabs
        val tab: TextView = TextView(this)
        tab.setText(title)
        tab.setTextColor(getColor(R.color.foreground))
        tab.setTextSize(14f)
        tab.setGravity(android.view.Gravity.CENTER)
        tab.setPadding(dp(16), 0, dp(16), 0)
        tab.setBackgroundResource(R.drawable.style_variant_chip)
        tab.setSelected(selected)
        val size: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, dp(34))
        size.setMarginEnd(dp(8))
        tabs.addView(tab, size)
        tab.setOnClickListener(action)
        return tab
    }

    private fun dp(value: Int): Int {
        return java.lang.Math.round(value * getResources().getDisplayMetrics().density)
    }

    private fun showStickerCatalog() {
        val grid: GridView = openInlineAssets()
        grid.setNumColumns(4)

        val packs: MutableList<Pack>?
        try {
            packs = stickerLibrary.packs()
        } catch (e: IOException) {
            showInlineAssets(
                "Bundled",
                assets.files("stickers", *EditorActivity.IMAGE_TYPES),
                ImageSelection { path: String -> this.addSticker(path) })
            return
        } catch (e: JSONException) {
            showInlineAssets(
                "Bundled",
                assets.files("stickers", *EditorActivity.IMAGE_TYPES),
                ImageSelection { path: String -> this.addSticker(path) })
            return
        }
        var first: TextView? = null
        for (pack in packs) {
            val tab: TextView = addAssetTab(
                pack.name,
                false,
                View.OnClickListener { view: View -> showStickerPack(grid, pack, view) })
            if (first == null) first = tab
        }
        addAssetTab("Bundled", false, View.OnClickListener { view: View ->
            selectAssetTab(view)
            val paths: MutableList<String> = assets.files("stickers", *EditorActivity.IMAGE_TYPES)
            grid.setAdapter(Thumbnails(paths))
            grid.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, item: View?, index: Int, id: Long ->
                addSticker(
                    paths.get(index)
                )
            })

        })
        if (!packs.isEmpty()) showStickerPack(grid, packs.get(0), first)
    }

    private fun selectAssetTab(selection: View?) {
        val tabs: LinearLayout = binding.editorAssetsPanel.assetTabs
        for (i in 0..<tabs.getChildCount()) tabs.getChildAt(i)
            .setSelected(tabs.getChildAt(i) === selection)
    }

    private fun showStickerPack(grid: GridView, pack: Pack, tab: View?) {
        selectAssetTab(tab)
        grid.setAdapter(OnlineStickerRows(pack))
        grid.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            if (!pack.free) {
                message("This pack requires the original app's premium access.")
                return@OnItemClickListener
            }
            val key: String = pack.keys.get(position)
            stickerWorker.execute(Runnable {
                var image: Bitmap? = null
                try {
                    image = stickerLibrary.image(key)
                } catch (ignored: IOException) {
                }
                val selected: Bitmap? = image
                runOnUiThread(Runnable {
                    if (isDestroyed()) return@Runnable
                    if (selected == null) {
                        message("Could not load that sticker.")
                        return@Runnable
                    }
                    val layer: Layer = Layer()
                    layer.image = selected
                    layer.asset = "remote-sticker:" + key
                    layer.size = 280
                    canvas.add(layer)

                })

            })

        })
    }

    private inner class OnlineStickerRows(pack: Pack) : BaseAdapter() {
        private val pack: Pack

        init {
            this.pack = pack
        }

        override fun getCount(): Int {
            return pack.keys.size
        }

        override fun getItem(index: Int): String {
            return pack.keys.get(index)
        }

        override fun getItemId(index: Int): Long {
            return index.toLong()
        }

        override fun getView(index: Int, recycled: View?, parent: ViewGroup?): View {
            val tile: ImageView =
                if (recycled == null) ImageView(this@EditorActivity) else recycled as ImageView
            tile.setLayoutParams(AbsListView.LayoutParams(-1, dp(70)))
            tile.setBackgroundResource(R.drawable.home_logo_card)
            tile.setScaleType(ImageView.ScaleType.FIT_CENTER)
            tile.setPadding(dp(8), dp(8), dp(8), dp(8))
            val key: String = getItem(index)
            tile.setTag(key)
            tile.setContentDescription(pack.name + " " + (index + 1))
            tile.setImageResource(if (pack.free) R.drawable.ic_sticker else R.drawable.ic_lock_2)
            if (pack.free) {
                stickerWorker.execute(Runnable {
                    try {
                        val preview: Bitmap = stickerLibrary.thumbnail(key)
                        runOnUiThread(Runnable {
                            if (key == tile.getTag() && !isDestroyed()) tile.setImageBitmap(preview)
                        })
                    } catch (ignored: IOException) {
                        // Keep the tile available for a retry when selected.
                    }
                })
            }
            return tile
        }
    }

    private fun setControlsHeight(heightDp: Int) {
        val controls: View = binding.editorControls
        val height: Int =
            java.lang.Math.round(heightDp * getResources().getDisplayMetrics().density)
        val size: ViewGroup.LayoutParams = controls.getLayoutParams()
        if (size.height == height) return
        size.height = height
        controls.setLayoutParams(size)
    }

    private fun updateHistoryButtons() {
        val visible: Boolean = canvas.canUndo() || canvas.canRedo()
        updateHistoryButton(binding.undo, visible, canvas.canUndo())
        updateHistoryButton(binding.redo, visible, canvas.canRedo())
    }

    private fun updateHistoryButton(button: View, visible: Boolean, enabled: Boolean) {
        button.setVisibility(if (visible) View.VISIBLE else View.GONE)
        button.setEnabled(enabled)
        button.setAlpha(if (enabled) 1f else 0.35f)
    }

    private fun showEmoji() {
        val panel: ListView = binding.emojiPanel
        if (panel.getAdapter() == null) {
            try {
                panel.setAdapter(
                    EmojiPanel(
                        this,
                        assets,
                        java.util.function.Consumer { path: String -> this.addSticker(path) })
                )
            } catch (e: IOException) {
                message("Could not load bundled emoji.")
                return
            } catch (e: JSONException) {
                message("Could not load bundled emoji.")
                return
            }
        }
        showFeatures()
        binding.featureGrid.setVisibility(View.GONE)
        panel.setVisibility(View.VISIBLE)
    }

    private fun openDrawing(existing: Layer? = null) {
        pendingDrawingLayer = if (existing == null) -1 else canvas.layers().indexOf(existing)
        val intent: Intent = Intent(this, DrawingActivity::class.java)
        var original: Bitmap?
        if (existing == null) {
            original = canvas.render()
        } else {
            intent.putExtra(DrawingActivity.INITIAL, existing.asset?.removePrefix("drawing:"))
            val hidden: Boolean = existing.hidden
            try {
                existing.hidden = true
                original = canvas.render()
            } finally {
                existing.hidden = hidden
            }
        }
        val preview: Bitmap = Bitmap.createScaledBitmap(original, 480, 480, true)
        original.recycle()
        try {
            drawingReference = File.createTempFile("drawing-reference-", ".png", getCacheDir())
            FileOutputStream(drawingReference).use { output ->
                if (!preview.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )
                ) throw IOException("PNG encode failed")
            }
            intent.putExtra(DrawingActivity.REFERENCE, drawingReference?.name)
        } catch (e: IOException) {
            clearDrawingReference()
        } finally {
            preview.recycle()
        }
        startActivityForResult(intent, EditorActivity.DRAW_LAYER)
    }

    private fun clearDrawingReference() {
        if (drawingReference == null) return
        drawingReference?.delete()
        drawingReference = null
    }

    private fun addDrawing(name: String?) {
        if (name == null || !name.matches("draw-[a-f0-9-]+\\.png".toRegex())) return
        val image: Bitmap? = BitmapFactory.decodeFile(File(getCacheDir(), name).getPath())
        if (image == null) {
            message("Could not open the drawing.")
            return
        }
        if (pendingDrawingLayer >= 0 && pendingDrawingLayer < canvas.layers().size) {
            val layer: Layer = canvas.layers().get(pendingDrawingLayer)
            canvas.recordChange()
            layer.image = image
            layer.asset = "drawing:" + name
            canvas.invalidate()
            refreshLayerList()
            return
        }
        val layer: Layer = Layer()
        layer.image = image
        layer.asset = "drawing:" + name
        layer.size = EditorCanvas.SIZE
        canvas.add(layer)
        refreshLayerList()
    }

    private fun chooseBackground() {
        startActivityForResult(
            Intent(this, BackgroundActivity::class.java),
            EditorActivity.PICK_BACKGROUND
        )
    }

    private fun showBackgroundTools() {
        showFeatures(204)
        binding.featureGrid.setVisibility(View.GONE)
        binding.backgroundTools.root.setVisibility(View.VISIBLE)
    }

    private fun bindBackgroundTools() {
        val edits: BackgroundEdits = canvas.backgroundEdits
        binding.backgroundTools.bgReplace.setOnClickListener(View.OnClickListener { view: View -> chooseBackground() })
        binding.backgroundTools.bgOverlay.setOnClickListener(View.OnClickListener { view: View ->
            AlertDialog.Builder(this).setTitle("Overlay")
                .setItems(
                    kotlin.arrayOf<String>("Clear", "Dark", "Light", "Teal", "Warm"),
                    DialogInterface.OnClickListener { dialog: DialogInterface?, index: Int ->
                        edits.overlay = kotlin.intArrayOf(
                            0,
                            0x66000000,
                            0x66ffffff,
                            0x667bc8c9,
                            0x66ffa76b
                        )[index]
                        canvas.invalidate()

                    }).show()
        })
        backgroundAction(
            binding.backgroundTools.bgRotate,
            Runnable { edits.rotation = (edits.rotation + 90) % 360 })
        backgroundAction(
            binding.backgroundTools.bgBw,
            Runnable { edits.grayscale = !edits.grayscale })
        backgroundAction(
            binding.backgroundTools.bgFlipH,
            Runnable { edits.horizontalFlip = !edits.horizontalFlip })
        backgroundAction(
            binding.backgroundTools.bgFlipV,
            Runnable { edits.verticalFlip = !edits.verticalFlip })
        backgroundAction(binding.backgroundTools.bgFit, Runnable { edits.fit = !edits.fit })
        binding.backgroundTools.bgAdjust.setOnClickListener(View.OnClickListener { view: View ->
            if (!requireBackgroundImage()) return@OnClickListener
            AlertDialog.Builder(this).setTitle("Adjust")
                .setItems(
                    kotlin.arrayOf<String>("Brightness", "Contrast"),
                    DialogInterface.OnClickListener { dialog: DialogInterface?, index: Int ->
                        if (index == 0) adjustBackground(
                            "Brightness", edits.brightness, -100, 100,
                            IntConsumer { value: Int -> edits.brightness = value })
                        else adjustBackground(
                            "Contrast",
                            edits.contrast,
                            -80,
                            100,
                            IntConsumer { value: Int -> edits.contrast = value })
                    }).show()

        })
        binding.backgroundTools.bgCrop.setOnClickListener(View.OnClickListener { view: View ->
            if (requireBackgroundImage()) adjustBackground(
                "Crop zoom",
                edits.zoom,
                0,
                200,
                IntConsumer { value: Int -> edits.zoom = value })
        })
    }

    private fun backgroundAction(button: View, action: Runnable) {
        button.setOnClickListener(View.OnClickListener { view: View ->
            if (!requireBackgroundImage()) return@OnClickListener
            action.run()
            canvas.backgroundEdits.updateFilter()
            canvas.invalidate()

        })
    }

    private fun requireBackgroundImage(): Boolean {
        if (canvas.hasBackgroundImage()) return true
        message("Choose a photo background first.")
        return false
    }

    private fun adjustBackground(
        title: String?, current: Int, min: Int, max: Int,
        apply: IntConsumer
    ) {
        val slider: SeekBar = SeekBar(this)
        slider.setMax(max - min)
        slider.setProgress(current - min)
        AlertDialog.Builder(this).setTitle(title).setView(slider)
            .setNegativeButton("Cancel", null).setPositiveButton(
                "Apply",
                DialogInterface.OnClickListener { dialog: DialogInterface?, button: Int ->
                    apply.accept(slider.getProgress() + min)
                    canvas.backgroundEdits.updateFilter()
                    canvas.invalidate()

                }).show()
    }

    private fun selectColor(color: Int) {
        paper = color
        backgroundAsset = null
        backgroundPhoto = null
        collagePhotos = null
        canvas.setPaper(paper, null)
    }

    private fun selectBackgroundAsset(path: String) {
        try {
            val image: Bitmap = assets.image(path, EditorCanvas.SIZE)
            backgroundAsset = path
            backgroundPhoto = null
            collagePhotos = null
            canvas.setPaper(paper, image)
        } catch (e: IOException) {
            message("Could not open that background.")
        }
    }

    private fun selectPhoto(uri: Uri) {
        try {
            val image: Bitmap = assets.photo(uri, 1440)
            backgroundPhoto = uri
            backgroundAsset = null
            collagePhotos = null
            canvas.setPaper(paper, image)
        } catch (e: IOException) {
            message("Could not open that photo.")
        } catch (e: java.lang.SecurityException) {
            message("Could not open that photo.")
        }
    }

    private fun selectImportedCollage(uri: Uri) {
        selectPhoto(uri)
        if (uri == backgroundPhoto) {
            canvas.backgroundEdits.fit = true
            canvas.invalidate()
        }
    }

    private fun pickPhoto(request: Int) {
        val picker: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        startActivityForResult(picker, request)
    }

    private fun selectCollage(photos: ArrayList<Uri>) {
        if (photos.size == 1) {
            selectPhoto(photos.get(0))
            return
        }
        val collage: Bitmap =
            Bitmap.createBitmap(EditorCanvas.SIZE, EditorCanvas.SIZE, Bitmap.Config.ARGB_8888)
        val tiles: Canvas = Canvas(collage)
        val paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val columns: Int = if (photos.size > 4) 3 else 2
        val rows: Int = (photos.size + columns - 1) / columns
        try {
            for (row in 0..<rows) {
                val count: Int = kotlin.math.min(columns, photos.size - row * columns)
                val width: Float = EditorCanvas.SIZE / count.toFloat()
                val height: Float = EditorCanvas.SIZE / rows.toFloat()
                for (column in 0..<count) {
                    val image: Bitmap = assets.photo(photos.get(row * columns + column), 720)
                    val scale: Float =
                        kotlin.math.max(width / image.getWidth(), height / image.getHeight())
                    val drawWidth: Float = image.getWidth() * scale
                    val drawHeight: Float = image.getHeight() * scale
                    val left: Float = column * width
                    val top: Float = row * height
                    val x: Float = left + (width - drawWidth) / 2
                    val y: Float = top + (height - drawHeight) / 2
                    tiles.save()
                    tiles.clipRect(left, top, left + width, top + height)
                    tiles.drawBitmap(image, null, RectF(x, y, x + drawWidth, y + drawHeight), paint)
                    tiles.restore()
                    image.recycle()
                }
            }
        } catch (e: IOException) {
            collage.recycle()
            message("Could not open all selected photos.")
            return
        } catch (e: java.lang.SecurityException) {
            collage.recycle()
            message("Could not open all selected photos.")
            return
        }
        backgroundPhoto = null
        backgroundAsset = null
        collagePhotos = photos
        canvas.setPaper(paper, collage)
    }

    private fun addSticker(path: String) {
        try {
            val layer: Layer = Layer()
            layer.image = assets.image(path, 640)
            layer.asset = path
            layer.size = 280
            canvas.add(layer)
            refreshLayerList()
        } catch (e: IOException) {
            message("Could not open that sticker.")
        }
    }

    private fun addNormalText() {
        val layer: Layer = Layer()
        layer.applyText(
            "Double tap to edit text",
            "",
            android.graphics.Typeface.DEFAULT,
            Color.BLACK,
            76
        )
        canvas.add(layer)
        showNormalTextTools(layer)
    }

    private fun openNormalTextInput(layer: Layer) {
        pendingNormalLayer = canvas.layers().indexOf(layer)
        startActivityForResult(
            Intent(this, EditTextActivity::class.java)
                .putExtra(EditTextActivity.TEXT, layer.text), EditorActivity.EDIT_NORMAL_TEXT
        )
    }

    private fun showNormalTextTools(layer: Layer) {
        val index: Int = canvas.layers().indexOf(layer)
        if (index < 0 || layer.locked) return
        canvas.select(index)
        showFeatures(204)
        binding.featureGrid.setVisibility(View.GONE)
        binding.normalTextPanel.root.setVisibility(View.VISIBLE)
        setCurveSensitiveTool(binding.normalTextPanel.normalPalette, layer.effects.curve == 0)
        setCurveSensitiveTool(binding.normalTextPanel.normalWord, layer.effects.curve == 0)
    }

    private fun setCurveSensitiveTool(tool: View, active: Boolean) {
        tool.setEnabled(active)
        tool.setAlpha(if (active) 1f else 0.4f)
    }

    private fun showCurvePanel(layer: Layer) {
        showFeatures(244)
        binding.featureGrid.setVisibility(View.GONE)
        binding.curvePanel.root.setVisibility(View.VISIBLE)
        binding.curveBack.setVisibility(View.VISIBLE)
        binding.curveBack.setOnClickListener(View.OnClickListener { view: View ->
            showNormalTextTools(
                layer
            )
        })
        val slider: SeekBar = binding.curvePanel.curveAmount
        val value: TextView = binding.curvePanel.curveValue
        slider.setOnSeekBarChangeListener(null)
        slider.setProgress(layer.effects.curve + 100)
        value.setText(layer.effects.curve.toString())
        slider.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            private var recorded: Boolean = false
            override fun onStartTrackingTouch(bar: SeekBar?) {
                recorded = false
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                recorded = false
            }

            override fun onProgressChanged(bar: SeekBar?, progress: Int, user: Boolean) {
                if (!user) return
                if (!recorded) {
                    canvas.recordChange()
                    recorded = true
                }
                layer.effects.curve = progress - 100
                layer.invalidateText()
                canvas.invalidate()
                value.setText(layer.effects.curve.toString())
            }
        })
        binding.curvePanel.curveReset.setOnClickListener(View.OnClickListener { view: View ->
            if (layer.effects.curve != 0) editNormal(layer, Runnable { layer.effects.curve = 0 })
            slider.setProgress(100)
            value.setText("0")

        })
    }

    private fun bindNormalTools() {
        normalAction(
            binding.normalTextPanel.normalFont,
            java.util.function.Consumer { layer: Layer -> this.showNormalFonts(layer) })
        normalAction(
            binding.normalTextPanel.normalColor,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.COLOR
                )
            })
        normalAction(
            binding.normalTextPanel.normalShadow,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.SHADOW
                )
            })
        normalAction(
            binding.normalTextPanel.normalNeon,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.NEON
                )
            })
        normalAction(
            binding.normalTextPanel.normalResize,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.RESIZE
                )
            })
        normalAction(
            binding.normalTextPanel.normalBorder,
            java.util.function.Consumer { layer: Layer -> this.pickNormalBorder(layer) })
        normalAction(
            binding.normalTextPanel.normalFormat,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.FORMAT
                )
            })
        normalAction(
            binding.normalTextPanel.normalOpacity,
            java.util.function.Consumer { layer: Layer ->
                normalSlider(
                    "Opacity",
                    layer,
                    layer.opacity,
                    0,
                    255,
                    IntConsumer { value: Int -> layer.opacity = value })
            })
        normalAction(
            binding.normalTextPanel.normalGradient,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.GRADIENT
                )
            })
        normalAction(
            binding.normalTextPanel.normalPalette,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.PALETTE
                )
            })
        normalAction(
            binding.normalTextPanel.normalCurve,
            java.util.function.Consumer { layer: Layer -> this.showCurvePanel(layer) })
        normalAction(
            binding.normalTextPanel.normalTextBackground,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.BACKGROUND
                )
            })
        normalAction(
            binding.normalTextPanel.normalPosition,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.POSITION
                )
            })
        normalAction(
            binding.normalTextPanel.normalWord,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.WORD
                )
            })
        normalAction(
            binding.normalTextPanel.normalClone,
            java.util.function.Consumer { layer: Layer ->
                val clone: Layer = layer.copy()
                clone.x = kotlin.math.min(EditorCanvas.SIZE.toFloat(), layer.x + 36)
                clone.y = kotlin.math.min(EditorCanvas.SIZE.toFloat(), layer.y + 36)
                canvas.add(clone)
                showNormalTextTools(clone)

            })
        normalAction(
            binding.normalTextPanel.normalTexture,
            java.util.function.Consumer { layer: Layer ->
                showNormalDetail(
                    layer,
                    NormalTextPanels.Page.TEXTURE
                )
            })
    }

    internal fun showNormalDetail(layer: Layer, page: NormalTextPanels.Page) {
        showFeatures(kotlin.math.min(294, getResources().getConfiguration().screenHeightDp / 2))
        normalPage = page
        binding.featureGrid.setVisibility(View.GONE)
        binding.normalDetailPanel.root.setVisibility(View.VISIBLE)
        val back: View = binding.curveBack
        back.setVisibility(View.VISIBLE)
        back.setOnClickListener(View.OnClickListener { view: View ->
            if (page.name.startsWith("BG_")) showNormalDetail(
                layer,
                NormalTextPanels.Page.BACKGROUND
            )
            else showNormalTextTools(layer)
        })
        normalPanels.show(layer, page)
    }

    internal fun pickTextTexture(layer: Layer?) {
        pendingTextureLayer = canvas.layers().indexOf(layer)
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
            EditorActivity.PICK_TEXTURE
        )
    }

    private fun applyTextTexture(uri: Uri?, index: Int, flags: Int) {
        if (uri == null || index < 0 || index >= canvas.layers().size) return
        try {
            val image: Bitmap = assets.photo(uri, 640)
            val path: String = keepTextTexture(uri, flags, image)
            val layer: Layer = canvas.layers().get(index)
            editNormal(layer, Runnable {
                layer.effects.texturePath = path
                layer.effects.texture = image

            })
            showNormalDetail(layer, NormalTextPanels.Page.TEXTURE)
        } catch (error: IOException) {
            message("Could not open that texture.")
        } catch (error: java.lang.SecurityException) {
            message("Could not open that texture.")
        }
    }

    @Throws(IOException::class)
    private fun keepTextTexture(uri: Uri, flags: Int, image: Bitmap): String {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            return uri.toString()
        } catch (unavailableGrant: java.lang.SecurityException) {
            val file: File = File.createTempFile("text-texture-", ".png", getCacheDir())
            try {
                FileOutputStream(file).use { output ->
                    if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("Unable to save the texture")
                    }
                    return "local-texture:" + file.getName()

                }
            } catch (failure: IOException) {
                file.delete()
                throw failure
            }
        }
    }

    private fun normalAction(button: View, action: java.util.function.Consumer<Layer>) {
        button.setOnClickListener(View.OnClickListener { view: View ->
            val index: Int = canvas.selectedIndex()
            if (index < 0) return@OnClickListener
            val layer: Layer = canvas.layers().get(index)
            if (layer.asset == null && layer.styleIndex < 0 && !layer.locked) action.accept(layer)

        })
    }

    internal fun editNormal(layer: Layer, change: Runnable) {
        canvas.recordChange()
        change.run()
        layer.invalidateText()
        canvas.invalidate()
        refreshLayerList()
    }

    private fun normalSlider(
        title: String?, layer: Layer, current: Int, min: Int, max: Int,
        assign: IntConsumer
    ) {
        val slider: SeekBar = SeekBar(this)
        slider.setMax(max - min)
        slider.setProgress(current - min)
        AlertDialog.Builder(this).setTitle(title).setView(slider)
            .setNegativeButton("Cancel", null).setPositiveButton(
                "Apply",
                DialogInterface.OnClickListener { dialog: DialogInterface?, button: Int ->
                    editNormal(
                        layer,
                        Runnable { assign.accept(slider.getProgress() + min) })
                }).show()
    }

    private fun showNormalFonts(layer: Layer) {
        val grid: GridView = openInlineAssets()
        assetBack = Runnable { showNormalTextTools(layer) }
        addAssetTab("Fonts", true, null)
        addAssetTab("More Fonts", false, View.OnClickListener { view: View ->
            pendingFontLayer = canvas.layers().indexOf(layer)
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                EditorActivity.IMPORT_FONT
            )

        })
        grid.setNumColumns(2)
        grid.setAdapter(object : BaseAdapter() {
            override fun getCount(): Int {
                return fonts.size
            }

            override fun getItem(index: Int): String {
                return fonts.get(index)
            }

            override fun getItemId(index: Int): Long {
                return index.toLong()
            }

            override fun getView(index: Int, recycled: View?, parent: ViewGroup?): View {
                val font: TextView =
                    if (recycled == null) TextView(this@EditorActivity) else recycled as TextView
                val path: String = getItem(index)
                font.setLayoutParams(AbsListView.LayoutParams(-1, dp(43)))
                font.setGravity(android.view.Gravity.CENTER_VERTICAL)
                font.setPadding(dp(12), 0, dp(8), 0)
                font.setTypeface(assets.font(path))
                font.setSingleLine()
                font.setEllipsize(android.text.TextUtils.TruncateAt.END)
                font.setText(fontNames.get(path) + (if (path == layer.font) "  ✓" else ""))
                font.setBackgroundResource(R.drawable.home_button_outline)
                return font
            }
        })
        grid.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, view: View?, index: Int, id: Long ->
            val path: String = fonts.get(index)
            if (path != layer.font) {
                editNormal(
                    layer,
                    Runnable {
                        layer.applyText(
                            layer.text,
                            path,
                            assets.font(path),
                            layer.color,
                            layer.size
                        )
                    })
                (grid.getAdapter() as BaseAdapter).notifyDataSetChanged()
            }

        })
    }

    private fun importNormalFont(uri: Uri) {
        if (pendingFontLayer < 0 || pendingFontLayer >= canvas.layers().size) return
        var sourceName: String? = ""
        contentResolver.query(
            uri,
            kotlin.arrayOf<String>(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        ).use { files ->
            if (files != null && files.moveToFirst()) sourceName = files.getString(0)
        }
        if (sourceName == null || !sourceName.lowercase().matches(".*\\.(ttf|otf)".toRegex())) {
            message("Select a TTF or OTF font.")
            return
        }
        val suffix: String = sourceName.substring(sourceName.length - 4).lowercase()
        var label: String = sourceName.substring(0, sourceName.length - 4)
            .replace("[^A-Za-z0-9 ._-]".toRegex(), "_")
        if (label.length > 60) label = label.substring(0, 60)
        val folder: File = File(getFilesDir(), "fonts")
        var draft: File? = null
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) throw IOException("Font directory unavailable")
            var target: File = File(folder, label + suffix)
            if (target.exists()) target =
                File(folder, label + "-" + java.lang.System.currentTimeMillis() + suffix)
            draft = File.createTempFile("font-", ".tmp", folder)
            contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(draft).use { output ->
                    if (input == null) throw IOException("Font could not be read")
                    val buffer: ByteArray = ByteArray(8192)
                    var bytes: Int = 0
                    var count: Int
                    while ((input.read(buffer).also { count = it }) != -1) {
                        bytes += count
                        if (bytes > 12 * 1024 * 1024) throw IOException("Font file too large")
                        output.write(buffer, 0, count)
                    }

                }
            }
            val face: Typeface? = android.graphics.Typeface.createFromFile(draft)
            if (!draft.renameTo(target)) throw IOException("Font could not be saved")
            val path: String = "local-font:" + target.getName()
            val layer: Layer = canvas.layers().get(pendingFontLayer)
            editNormal(
                layer,
                Runnable { layer.applyText(layer.text, path, face, layer.color, layer.size) })
            loadFonts()
            showNormalFonts(layer)
        } catch (e: IOException) {
            message("Could not import that font.")
        } catch (e: java.lang.RuntimeException) {
            message("Could not import that font.")
        } finally {
            if (draft != null) draft.delete()
        }
    }

    private fun pickNormalBorder(layer: Layer) {
        AlertDialog.Builder(this).setTitle("Border").setItems(
            kotlin.arrayOf<String>("Width", "Color"),
            DialogInterface.OnClickListener { dialog: DialogInterface?, index: Int ->
                if (index == 0) normalSlider(
                    "Border width",
                    layer,
                    layer.effects.border,
                    0,
                    24,
                    IntConsumer { value: Int -> layer.effects.border = value })
                else ColorWheelDialog.show(
                    this,
                    layer.effects.borderColor,
                    IntConsumer { value: Int ->
                        editNormal(
                            layer,
                            Runnable { layer.effects.borderColor = value })
                    })
            }).show()
    }

    private fun editStyledText(existing: Layer?) {
        pendingStyleLayer = if (existing == null) -1 else canvas.layers().indexOf(existing)
        val editor: Intent = Intent(this, EditTextActivity::class.java)
        if (existing != null) editor.putExtra(EditTextActivity.TEXT, existing.text)
        startActivityForResult(editor, EditorActivity.EDIT_STYLED_TEXT)
    }

    private fun applyStyledText(value: String?) {
        if (value == null || value.trim { it <= ' ' }.isEmpty()) return
        val layer: Layer
        if (pendingStyleLayer == -1) {
            layer = Layer()
            layer.styleIndex = 0
            layer.color =
                if (paper == Color.BLACK || backgroundAsset != null || backgroundPhoto != null || collagePhotos != null) Color.WHITE else Color.BLACK
            layer.text = value
            renderStyledLayer(layer)
            canvas.add(layer)
        } else {
            if (pendingStyleLayer >= canvas.layers().size) return
            layer = canvas.layers().get(pendingStyleLayer)
            canvas.recordChange()
            layer.text = value
            renderStyledLayer(layer)
            canvas.select(pendingStyleLayer)
        }
        showStylePanel(layer)
    }

    private fun showStylePanel(layer: Layer) {
        setControlsHeight(204)
        hideOtherToolPanels()
        if (!::styleControls.isInitialized) {
            styleControls = TextStyleControls(
                binding.stylePanel, textStyles, Runnable { canvas.recordChange() },
                Runnable { renderStyledLayer(styleControls.selectedLayer()) })
        }
        binding.featureGrid.setVisibility(View.GONE)
        binding.layerPanel.setVisibility(View.GONE)
        binding.emojiPanel.setVisibility(View.GONE)
        binding.backgroundTools.root.setVisibility(View.GONE)
        binding.featureTab.setActivated(true)
        binding.layers.setActivated(false)
        styleControls.show(layer)
    }

    private fun renderStyledLayer(layer: Layer) {
        val result: Bitmap = textStyles.renderLayer(layer, layer.styleIndex)
        layer.image = result
        layer.size = kotlin.math.min(
            result.getWidth(),
            java.lang.Math.round(920f * result.getWidth() / result.getHeight())
        )
        canvas.invalidate()
        if (::styleControls.isInitialized && styleControls.selectedLayer() === layer) styleControls.refreshPreviews()
    }

    private fun chooseLayer() {
        setControlsHeight(204)
        hideOtherToolPanels()
        binding.stylePanel.root.setVisibility(View.GONE)
        binding.featureGrid.setVisibility(View.GONE)
        binding.layerPanel.setVisibility(View.VISIBLE)
        binding.emojiPanel.setVisibility(View.GONE)
        binding.backgroundTools.root.setVisibility(View.GONE)
        binding.featureTab.setActivated(false)
        binding.layers.setActivated(true)
        refreshLayerList()
    }

    private fun showFeatures(heightDp: Int = 274) {
        setControlsHeight(heightDp)
        hideOtherToolPanels()
        binding.stylePanel.root.setVisibility(View.GONE)
        binding.featureGrid.setVisibility(View.VISIBLE)
        binding.layerPanel.setVisibility(View.GONE)
        binding.emojiPanel.setVisibility(View.GONE)
        binding.backgroundTools.root.setVisibility(View.GONE)
        binding.featureTab.setActivated(true)
        binding.layers.setActivated(false)
    }

    private fun hideOtherToolPanels() {
        normalPage = null
        binding.normalDetailPanel.root.setVisibility(View.GONE)
        binding.normalTextPanel.root.setVisibility(View.GONE)
        binding.decoratorCategories.root.setVisibility(View.GONE)
        binding.editorAssetsPanel.root.setVisibility(View.GONE)
        binding.drawingTools.root.setVisibility(View.GONE)
        binding.curvePanel.root.setVisibility(View.GONE)
        binding.curveBack.setVisibility(View.GONE)
        assetBack = null
    }

    private fun refreshLayerList() {
        val list: ListView = binding.layerList
        if (binding.layerPanel.getVisibility() != View.VISIBLE) return
        val layers: MutableList<Layer> = canvas.layers()
        if (layers.isEmpty()) {
            list.setAdapter(
                ArrayAdapter<String?>(
                    this, android.R.layout.simple_list_item_1,
                    kotlin.arrayOf<String>("Add text or a sticker to create a layer.")
                )
            )
            list.setOnItemClickListener(null)
            return
        }
        val scrollPosition: Int = list.getFirstVisiblePosition()
        list.setAdapter(LayerRows())
        list.setOnItemClickListener(null)
        list.setSelection(kotlin.math.min(scrollPosition, layers.size - 1))
    }

    private inner class LayerRows : BaseAdapter() {
        override fun getCount(): Int {
            return canvas.layers().size
        }

        override fun getItem(position: Int): Layer {
            return canvas.layers().get(index(position))
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        fun index(position: Int): Int {
            return getCount() - position - 1
        }

        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            val rowBinding: ItemEditorLayerBinding = if (recycled == null)
                ItemEditorLayerBinding.inflate(layoutInflater, parent, false)
            else
                ItemEditorLayerBinding.bind(recycled)
            val row: View = rowBinding.root
            val index: Int = index(position)
            val layer: Layer = getItem(position)
            val name: TextView = rowBinding.layerLabel
            val thumbnail: ImageView = rowBinding.layerThumbnail
            name.setText(layer.label())
            name.setOnClickListener(View.OnClickListener { view: View -> editLayer(index) })
            thumbnail.setOnClickListener(View.OnClickListener { view: View -> editLayer(index) })
            if (layer.image == null) thumbnail.setImageResource(R.drawable.ic_edit_text)
            else thumbnail.setImageBitmap(layer.image)
            row.setBackgroundColor(if (canvas.selectedIndex() == index) 0x337bc8c9 else Color.TRANSPARENT)

            val visibility: View = rowBinding.layerVisibility
            visibility.setActivated(layer.hidden)
            visibility.setContentDescription(getString(if (layer.hidden) R.string.show_layer else R.string.hide_layer))
            visibility.setOnClickListener(View.OnClickListener { view: View ->
                canvas.recordChange()
                layer.hidden = !layer.hidden
                if (layer.hidden && canvas.selectedIndex() == index) canvas.select(-1)
                canvas.invalidate()
                notifyDataSetChanged()

            })

            val lock: View = rowBinding.layerLock
            lock.setActivated(layer.locked)
            lock.setContentDescription(getString(if (layer.locked) R.string.unlock_layer else R.string.lock_layer))
            lock.setOnClickListener(View.OnClickListener { view: View ->
                canvas.recordChange()
                layer.locked = !layer.locked
                if (layer.locked && canvas.selectedIndex() == index) canvas.select(-1)
                notifyDataSetChanged()

            })

            val drag: View = rowBinding.layerDrag
            drag.setOnLongClickListener(OnLongClickListener { view: View ->
                view.startDragAndDrop(
                    ClipData.newPlainText("Layer", ""), DragShadowBuilder(row), index, 0
                )
            })
            row.setOnDragListener(OnDragListener { view: View?, event: DragEvent ->
                if (event.getLocalState() !is Int) return@OnDragListener false
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    val from: Int = event.getLocalState() as Int
                    canvas.reorder(from, index - from)
                    notifyDataSetChanged()
                }
                true
            })
            return row
        }
    }

    private fun editLayer(index: Int) {
        val layer: Layer = canvas.layers().get(index)
        if (layer.locked) {
            message("Unlock the layer to edit it.")
            return
        }
        canvas.select(index)
        if (layer.styleIndex >= 0) {
            AlertDialog.Builder(this).setTitle(layer.label())
                .setItems(
                    kotlin.arrayOf<String>(
                        "Edit text",
                        "Text Style",
                        "Text color",
                        "Bring forward",
                        "Send backward",
                        "Delete"
                    ),
                    DialogInterface.OnClickListener { dialog: DialogInterface?, choice: Int ->
                        when (choice) {
                            0 -> editStyledText(layer)
                            1 -> showStylePanel(layer)
                            2 -> editStyleColor(layer)
                            3 -> canvas.reorder(index, 1)
                            4 -> canvas.reorder(index, -1)
                            5 -> canvas.remove(index)
                        }
                        refreshLayerList()

                    }).show()
            return
        }
        if (layer.asset == null) {
            showNormalTextTools(layer)
            return
        }
        if (layer.asset?.startsWith("drawing:") == true) {
            showDrawingTools(layer)
            return
        }
        val primary: String = "Resize sticker"
        AlertDialog.Builder(this).setTitle(layer.label())
            .setItems(
                kotlin.arrayOf<String>(
                    primary,
                    "Opacity",
                    "Clone",
                    "Bring forward",
                    "Send backward",
                    "Delete"
                ), DialogInterface.OnClickListener { dialog: DialogInterface?, choice: Int ->
                    when (choice) {
                        0 -> resizeSticker(layer)
                        1 -> normalSlider(
                            "Opacity",
                            layer,
                            layer.opacity,
                            0,
                            255,
                            IntConsumer { value: Int -> layer.opacity = value })

                        2 -> {
                            val clone: Layer = layer.copy()
                            clone.x = kotlin.math.min(EditorCanvas.SIZE.toFloat(), clone.x + 36)
                            clone.y = kotlin.math.min(EditorCanvas.SIZE.toFloat(), clone.y + 36)
                            canvas.add(clone)
                        }

                        3 -> canvas.reorder(index, 1)
                        4 -> canvas.reorder(index, -1)
                        5 -> canvas.remove(index)
                    }
                    refreshLayerList()

                }).show()
    }

    private fun showDrawingTools(layer: Layer) {
        showFeatures(204)
        binding.featureGrid.setVisibility(View.GONE)
        binding.drawingTools.root.setVisibility(View.VISIBLE)
        binding.drawingTools.drawingEdit.setOnClickListener(View.OnClickListener { view: View ->
            openDrawing(
                layer
            )
        })
        binding.drawingTools.drawingOpacity.setOnClickListener(View.OnClickListener { view: View ->
            normalSlider(
                "Opacity",
                layer,
                layer.opacity,
                0,
                255,
                IntConsumer { value: Int -> layer.opacity = value })
        })
        binding.drawingTools.drawingClone.setOnClickListener(View.OnClickListener { view: View ->
            val clone: Layer = layer.copy()
            clone.x = kotlin.math.min(EditorCanvas.SIZE.toFloat(), clone.x + 36)
            clone.y = kotlin.math.min(EditorCanvas.SIZE.toFloat(), clone.y + 36)
            canvas.add(clone)
            showDrawingTools(clone)

        })
    }

    private fun editStyleColor(layer: Layer) {
        ColorWheelDialog.show(this, layer.color, IntConsumer { color: Int ->
            canvas.recordChange()
            layer.color = color
            renderStyledLayer(layer)

        })
    }

    private fun resizeSticker(layer: Layer) {
        val size: SeekBar = SeekBar(this)
        size.setMax(760)
        size.setProgress(layer.size - 40)
        AlertDialog.Builder(this).setTitle("Sticker size").setView(size)
            .setNegativeButton("Cancel", null).setPositiveButton(
                "Apply",
                DialogInterface.OnClickListener { dialog: DialogInterface?, button: Int ->
                    canvas.recordChange()
                    layer.size = 40 + size.getProgress()
                    canvas.invalidate()

                }).show()
    }

    private fun requestExport() {
        if (Build.VERSION.SDK_INT >= 29) {
            exportPng(null)
        } else {
            val save: Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/png")
                .putExtra(Intent.EXTRA_TITLE, "Text Swag.png")
            startActivityForResult(save, EditorActivity.SAVE_FILE)
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == EditorActivity.PICK_TEXTURE) {
            if (result == Activity.RESULT_OK && data != null) {
                applyTextTexture(data.getData(), pendingTextureLayer, data.getFlags())
            }
            pendingTextureLayer = -1
            return
        }
        if (request == EditorActivity.SHOW_SAVED) {
            if (result == Activity.RESULT_OK) finish()
            return
        }
        if (request == EditorActivity.DRAW_LAYER) {
            clearDrawingReference()
            if (result == Activity.RESULT_OK && data != null) addDrawing(
                data.getStringExtra(
                    DrawingActivity.DRAWING
                )
            )
            pendingDrawingLayer = -1
            return
        }
        if (result != Activity.RESULT_OK || data == null) return
        if (request == EditorActivity.IMPORT_FONT) {
            data.data?.let(::importNormalFont)
            pendingFontLayer = -1
            return
        }
        if (request == EditorActivity.EDIT_NORMAL_TEXT) {
            if (pendingNormalLayer >= 0 && pendingNormalLayer < canvas.layers().size) {
                val layer: Layer = canvas.layers().get(pendingNormalLayer)
                val value: String? = data.getStringExtra(EditTextActivity.TEXT)
                if (value != null && !value.trim { it <= ' ' }.isEmpty()) {
                    editNormal(layer, Runnable {
                        if (value != layer.text) {
                            layer.effects.selectionColors = null
                            layer.effects.wordIndex = -1
                        }
                        layer.text = value

                    })
                    showNormalTextTools(layer)
                }
            }
            pendingNormalLayer = -1
            return
        }
        if (request == EditorActivity.EDIT_STYLED_TEXT) {
            applyStyledText(data.getStringExtra(EditTextActivity.TEXT))
            pendingStyleLayer = -1
            return
        }
        if (request == EditorActivity.SAVE_FILE && data.getData() != null) exportPng(data.getData())
        if (request == EditorActivity.PICK_BACKGROUND) {
            if (data.hasExtra(BackgroundActivity.COLOR)) {
                selectColor(
                    data.getIntExtra(
                        BackgroundActivity.COLOR,
                        EditorActivity.DEFAULT_PAPER
                    )
                )
                return
            }
            val asset: String? = data.getStringExtra(BackgroundActivity.ASSET)
            if (asset != null) {
                selectBackgroundAsset(asset)
                return
            }
        }
        if (request == EditorActivity.PICK_PHOTO || request == EditorActivity.PICK_BACKGROUND) {
            val photos: ArrayList<Uri> = CollagePicker.photos(data)
            CollagePicker.keepAccess(this, photos)
            if (!photos.isEmpty()) {
                val first: Uri = photos.get(0)
                if ((getPackageName() + ".collages") == first.getAuthority()) selectImportedCollage(
                    first
                )
                else selectCollage(photos)
            }
        }
        if (request == EditorActivity.PICK_LAYER && data.getData() != null) {
            val uri: Uri? = data.getData()
            try {
                val layer: Layer = Layer()
                layer.image = assets.photo(requireNotNull(uri), 820)
                layer.asset = uri.toString()
                layer.size = 430
                canvas.add(layer)
                refreshLayerList()
            } catch (e: IOException) {
                message("Could not open that photo.")
            } catch (e: java.lang.SecurityException) {
                message("Could not open that photo.")
            }
        }
    }

    private fun exportPng(destination: Uri?) {
        val image: Bitmap = canvas.render()
        binding.export.setEnabled(false)
        writer.execute(Runnable {
            val saved: Uri? = writePng(destination, image)
            image.recycle()
            runOnUiThread(Runnable {
                if (isDestroyed()) return@Runnable
                binding.export.setEnabled(true)
                if (saved == null) {
                    message("Could not save the PNG.")
                    return@Runnable
                }
                val preview: Intent = Intent(this, SavedImageActivity::class.java).setData(saved)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivityForResult(preview, EditorActivity.SHOW_SAVED)

            })

        })
    }

    private fun writePng(destination: Uri?, image: Bitmap): Uri? {
        val resolver: ContentResolver = contentResolver
        var target: Uri? = destination
        try {
            if (target == null) {
                val details: ContentValues = ContentValues()
                details.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "TextSwag-" + java.lang.System.currentTimeMillis() + ".png"
                )
                details.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                details.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Text Swag"
                )
                details.put(MediaStore.Images.Media.IS_PENDING, 1)
                target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details)
            }
            if (target == null) throw IOException("No export destination")
            resolver.openOutputStream(target, "w").use { stream ->
                if (stream == null || !image.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("PNG encoding failed")
                }
            }
            if (destination == null) {
                val details: ContentValues = ContentValues()
                details.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(target, details, null, null)
            }
            return target
        } catch (e: IOException) {
            if (destination == null && target != null) resolver.delete(target, null, null)
            return null
        } catch (e: java.lang.SecurityException) {
            if (destination == null && target != null) resolver.delete(target, null, null)
            return null
        }
    }

    override fun onBackPressed() {
        if (normalPage != null || binding.curvePanel.root.getVisibility() == View.VISIBLE) {
            binding.curveBack.performClick()
            return
        }
        if (binding.editorAssetsPanel.root.getVisibility() == View.VISIBLE && assetBack != null) {
            assetBack?.run()
            return
        }
        if (binding.stylePanel.root.getVisibility() == View.VISIBLE || binding.emojiPanel.getVisibility() == View.VISIBLE || binding.backgroundTools.root.getVisibility() == View.VISIBLE || binding.normalTextPanel.root.getVisibility() == View.VISIBLE || binding.decoratorCategories.root.getVisibility() == View.VISIBLE || binding.drawingTools.root.getVisibility() == View.VISIBLE || binding.editorAssetsPanel.root.getVisibility() == View.VISIBLE) {
            showFeatures()
            return
        }
        AlertDialog.Builder(this).setTitle("Close this design?")
            .setMessage("Export a PNG to keep a copy.")
            .setNegativeButton("Keep editing", null)
            .setPositiveButton(
                "Close",
                DialogInterface.OnClickListener { dialog: DialogInterface?, button: Int -> finish() })
            .show()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putInt("paper", paper)
        state.putBundle("backgroundEdits", canvas.backgroundEdits.save())
        state.putString(
            "drawingReference",
            if (drawingReference == null) null else drawingReference?.name
        )
        state.putInt("pendingDrawingLayer", pendingDrawingLayer)
        state.putInt("pendingStyleLayer", pendingStyleLayer)
        state.putInt("pendingNormalLayer", pendingNormalLayer)
        state.putInt("pendingFontLayer", pendingFontLayer)
        state.putInt("pendingTextureLayer", pendingTextureLayer)
        state.putString("backgroundAsset", backgroundAsset)
        state.putParcelable("backgroundPhoto", backgroundPhoto)
        state.putParcelableArrayList("collagePhotos", collagePhotos)
        val layers: ArrayList<Bundle> = ArrayList<Bundle>()
        for (layer in canvas.layers()) layers.add(layer.save())
        state.putParcelableArrayList("layers", layers)
    }

    private fun restoreTextImage(path: String?): Bitmap? {
        if (path == null) return null
        try {
            if (path.startsWith("local-texture:")) {
                val file: File = File(getCacheDir(), path.substring("local-texture:".length))
                return BitmapFactory.decodeFile(file.getPath())
            }
            return if (path.startsWith("content:")) assets.photo(
                Uri.parse(path),
                640
            ) else assets.image(path, 500)
        } catch (error: IOException) {
            return null
        } catch (error: java.lang.SecurityException) {
            return null
        }
    }

    private fun restoreDesign(state: Bundle) {
        selectColor(state.getInt("paper", EditorActivity.DEFAULT_PAPER))
        val reference: String? = state.getString("drawingReference")
        if (reference != null) drawingReference = File(getCacheDir(), reference)
        pendingDrawingLayer = state.getInt("pendingDrawingLayer", -1)
        pendingStyleLayer = state.getInt("pendingStyleLayer", -1)
        pendingNormalLayer = state.getInt("pendingNormalLayer", -1)
        pendingFontLayer = state.getInt("pendingFontLayer", -1)
        pendingTextureLayer = state.getInt("pendingTextureLayer", -1)
        val path: String? = state.getString("backgroundAsset")
        val uri: Uri? = state.getParcelable<Uri?>("backgroundPhoto")
        val photos: ArrayList<Uri>? = state.getParcelableArrayList<Uri?>("collagePhotos")
        if (path != null) selectBackgroundAsset(path)
        if (uri != null) selectPhoto(uri)
        if (photos != null && !photos.isEmpty()) selectCollage(photos)
        canvas.backgroundEdits.restore(state.getBundle("backgroundEdits"))
        val layers: ArrayList<Bundle>? = state.getParcelableArrayList<Bundle?>("layers")
        if (layers == null) return

        for (saved in layers) {
            val layer: Layer = Layer()
            layer.asset = saved.getString("asset")
            layer.size = saved.getInt("size")
            layer.styleIndex = saved.getInt("styleIndex", -1)
            layer.styleVariant = saved.getInt("styleVariant", 1)
            layer.styleSpacing = saved.getInt("styleSpacing")
            layer.styleInvert = saved.getBoolean("styleInvert")
            layer.hidden = saved.getBoolean("hidden")
            layer.locked = saved.getBoolean("locked")
            layer.opacity = saved.getInt("opacity", 255)
            layer.scale = saved.getFloat("scale", 1f)
            layer.rotation = saved.getFloat("rotation")
            layer.flipped = saved.getBoolean("flipped")
            layer.effects.restore(saved.getBundle("textEffects"))
            layer.effects.texture = restoreTextImage(layer.effects.texturePath)
            layer.effects.gradientImage = restoreTextImage(layer.effects.gradientPath)
            layer.effects.backgroundGradient =
                restoreTextImage(layer.effects.backgroundGradientPath)
            layer.x = saved.getFloat("x")
            layer.y = saved.getFloat("y")
            if (layer.styleIndex >= 0 && layer.styleIndex < textStyles.size()) {
                layer.text = saved.getString("text", "")
                layer.color = saved.getInt("color")
                renderStyledLayer(layer)
            } else if (layer.asset == null) {
                layer.styleIndex = -1
                val font: String = saved.getString("font", "")
                layer.applyText(
                    saved.getString("text", ""), font, assets.font(font),
                    saved.getInt("color"), layer.size
                )
            } else {
                try {
                    if (layer.asset?.startsWith("drawing:") == true) {
                        layer.image = BitmapFactory.decodeFile(
                            File(
                                getCacheDir(),
                                requireNotNull(layer.asset).removePrefix("drawing:")
                            ).getPath()
                        )
                        if (layer.image == null) continue
                    } else if (layer.asset?.startsWith("remote-sticker:") == true) {

                        layer.image =
                            stickerLibrary.cachedImage(requireNotNull(layer.asset).removePrefix("remote-sticker:"))
                        if (layer.image == null) continue
                    } else {
                        layer.image = if (layer.asset?.startsWith("content:") == true)
                            assets.photo(Uri.parse(layer.asset), 820)
                        else
                            assets.image(requireNotNull(layer.asset), 640)
                    }
                } catch (e: IOException) {
                    continue
                } catch (e: java.lang.SecurityException) {
                    continue
                }
            }
            canvas.add(layer)
        }
        canvas.select(-1)
        canvas.clearHistory()
    }

    override fun onDestroy() {
        writer.shutdown()
        stickerWorker.shutdownNow()
        super.onDestroy()
    }

    fun message(text: String?) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun interface ImageSelection {
        fun select(path: String)
    }

    private inner class Thumbnails(paths: MutableList<String>) : BaseAdapter() {
        private val paths: MutableList<String>
        private val cache: LruCache<String?, Bitmap?> = object : LruCache<String?, Bitmap?>(24) {}

        init {
            this.paths = paths
        }

        override fun getCount(): Int {
            return paths.size
        }

        override fun getItem(index: Int): String {
            return paths.get(index)
        }

        override fun getItemId(index: Int): Long {
            return index.toLong()
        }

        override fun getView(index: Int, recycled: View?, parent: ViewGroup?): View {
            var thumbnail: ImageView? = recycled as ImageView?
            if (thumbnail == null) {
                thumbnail = ImageView(this@EditorActivity)
                val height: Int =
                    java.lang.Math.round(90 * getResources().getDisplayMetrics().density)
                thumbnail.setLayoutParams(AbsListView.LayoutParams(-1, height))
                thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER)
                thumbnail.setBackgroundColor(getColor(R.color.panel))
            }
            val path: String = getItem(index)
            var image: Bitmap? = cache.get(path)
            if (image == null) {
                try {
                    image = assets.image(path, 160)
                    cache.put(path, image)
                } catch (ignored: IOException) {
                    // The item label remains visible to accessibility services.
                }
            }
            thumbnail.setImageBitmap(image)
            thumbnail.setContentDescription(DesignAssets.label(path))
            return thumbnail
        }
    }

    companion object {
        const val ENTRY_MODE: String = "entryMode"
        const val MODE_BLANK: Int = 0
        const val MODE_BACKGROUND: Int = 1
        const val MODE_PHOTO: Int = 2
        private const val PICK_PHOTO: Int = 1
        private const val SAVE_FILE: Int = 2
        private const val PICK_BACKGROUND: Int = 3
        private const val PICK_LAYER: Int = 4
        private const val EDIT_STYLED_TEXT: Int = 5
        private const val DRAW_LAYER: Int = 6
        private const val SHOW_SAVED: Int = 7
        private const val EDIT_NORMAL_TEXT: Int = 8
        private const val IMPORT_FONT: Int = 9
        private const val PICK_TEXTURE: Int = 10
        private val DEFAULT_PAPER: Int = Color.WHITE
        private val IMAGE_TYPES: Array<String> = arrayOf(".webp", ".png", ".jpeg", ".jpg")

    }
}
