package app.typeandframe.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.typeandframe.studio.databinding.ActivityBackgroundBinding
import java.io.IOException
import java.util.function.IntConsumer

class BackgroundActivity : Activity() {
    private data class Category(val name: String, val folder: String, val images: List<String>)
    private data class Tile(val paths: List<String>, val label: String, val count: Int? = null)

    private lateinit var binding: ActivityBackgroundBinding
    private lateinit var designAssets: DesignAssets
    private lateinit var categories: List<Category>
    private var currentCategory: Category? = null
    private var categoryPosition = 0
    private var choice: Intent? = null
    private val colorTiles = mutableListOf<Pair<View, Int?>>()
    private val thumbnails = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        designAssets = DesignAssets(this)
        choice = state?.getParcelable("choice")
        categoryPosition = state?.getInt("categoryPosition") ?: 0

        val originalNames = DesignAssets.photoCategories.associate { (name, folder) -> folder to name }
        categories = assets.list("backgrounds").orEmpty().mapNotNull { folder ->
            val images = designAssets.files("backgrounds/$folder", ".jpg", ".jpeg", ".png", ".webp")
            val name = originalNames[folder] ?: folder.split('_').joinToString(" ") {
                it.replaceFirstChar(Char::uppercaseChar)
            }
            if (images.isEmpty()) null else Category(name, folder, images)
        }.sortedBy { it.name }

        addColors()
        binding.close.setOnClickListener { goBack() }
        binding.next.setOnClickListener { choice?.let(::finishWithChoice) }
        showGallery(categories.find { it.folder == state?.getString("category") }, state?.getInt("imagePosition") ?: 0)
        updateSelection()
    }

    private fun showGallery(category: Category?, imagePosition: Int = 0) {
        currentCategory = category
        binding.title.text = category?.name ?: getString(R.string.choose_background)
        binding.colorsSection.visibility = if (category == null) View.VISIBLE else View.GONE
        binding.galleryHeading.text = if (category == null) {
            getString(R.string.background_categories_count, categories.size)
        } else {
            getString(R.string.background_images_count, category.images.size)
        }
        val tiles = category?.images?.mapIndexed { index, path ->
            Tile(listOf(path), getString(R.string.background_image_description, category.name, index + 1))
        } ?: categories.map { item ->
            val previews = item.images.filterIndexed { index, _ -> index % ((item.images.size + 2) / 3) == 0 }
            Tile(previews, item.name, item.images.size)
        }
        binding.gallery.numColumns = if (category == null) 1 else GridView.AUTO_FIT
        binding.gallery.columnWidth = dp(100)
        binding.gallery.adapter = GalleryAdapter(tiles, category == null)
        binding.gallery.setSelection(if (category == null) categoryPosition else imagePosition)
        binding.gallery.setOnItemClickListener { _, _, position, _ ->
            if (category == null) {
                categoryPosition = binding.gallery.firstVisiblePosition
                showGallery(categories[position])
            } else {
                select(Intent().putExtra(ASSET, tiles[position].paths.single()))
            }
        }
    }

    private fun addColors() {
        val custom = ImageView(this).apply {
            setImageResource(R.drawable.ic_color_picker)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = getString(R.string.background_custom_color)
            setBackgroundResource(R.drawable.card_studio_tile)
            setOnClickListener {
                val color = choice?.getIntExtra(COLOR, Color.WHITE) ?: Color.WHITE
                ColorWheelDialog.show(this@BackgroundActivity, color, IntConsumer {
                    select(Intent().putExtra(COLOR, it))
                })
            }
        }
        addColorTile(custom, null)
        val transparent = ImageView(this).apply {
            setImageResource(R.drawable.transparent_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedFill(Color.WHITE)
            clipToOutline = true
        }
        addColorTile(transparent, Color.TRANSPARENT, getString(R.string.background_transparent))
        val colors = linkedMapOf(
            "White" to Color.WHITE, "Black" to Color.BLACK, "Teal" to 0xffb1dbdd.toInt(),
            "Rose" to 0xfff4d8e0.toInt(), "Peach" to 0xfff6cb9d.toInt(),
            "Cream" to 0xffeeead7.toInt(), "Lavender" to 0xffbcb2dc.toInt(),
            "Blue" to 0xff7597b5.toInt(), "Sage" to 0xff829781.toInt()
        )
        colors.forEach { (name, color) ->
            addColorTile(View(this).apply { background = roundedFill(color) }, color, name)
        }
    }

    private fun addColorTile(tile: View, color: Int?, label: String? = null) {
        tile.isFocusable = true
        if (color != null) {
            tile.contentDescription = label
            tile.setOnClickListener { select(Intent().putExtra(COLOR, color)) }
        }
        colorTiles.add(tile to color)
        binding.colorTiles.addView(tile, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            marginEnd = dp(8)
        })
    }

    private fun roundedFill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), getColor(R.color.card_stroke))
    }

    private fun outline() = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(3), getColor(R.color.accent))
    }

    private fun select(value: Intent) {
        if (intent.getBooleanExtra(APPLY_ON_TAP, false)) {
            finishWithChoice(value)
        } else {
            choice = value
            updateSelection()
        }
    }

    private fun updateSelection() {
        val color = choice?.takeIf { it.hasExtra(COLOR) }?.getIntExtra(COLOR, Color.WHITE)
        for ((tile, value) in colorTiles) {
            val selected = color != null &&
                (color == value || (value == null && colorTiles.none { it.second == color }))
            tile.foreground = if (selected) outline() else null
            tile.isSelected = selected
        }
        (binding.gallery.adapter as GalleryAdapter).notifyDataSetChanged()
        binding.next.visibility = if (choice == null) View.GONE else View.VISIBLE
        binding.gallery.setPadding(dp(14), 0, dp(14), dp(if (choice == null) 12 else 78))
    }

    private inner class GalleryAdapter(private val tiles: List<Tile>, private val covers: Boolean) : BaseAdapter() {
        override fun getCount() = tiles.size
        override fun getItem(position: Int) = tiles[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tile = convertView as? GalleryTile ?: GalleryTile(covers)
            tile.bind(getItem(position))
            return tile
        }
    }

    private inner class GalleryTile(isCover: Boolean) : FrameLayout(this) {
        private val images = List(if (isCover) 3 else 1) {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedFill(getColor(R.color.panel))
                clipToOutline = true
            }
        }
        private val name = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8), 0)
        }
        private val count = TextView(context).apply {
            setTextColor(0xfff0dee5.toInt())
            textSize = 12f
        }

        init {
            layoutParams = AbsListView.LayoutParams(-1, dp(if (isCover) 140 else 106))
            if (isCover) {
                val header = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(count, LinearLayout.LayoutParams(-2, -2))
                }
                val previews = LinearLayout(context).apply {
                    images.forEachIndexed { index, image ->
                        addView(image, LinearLayout.LayoutParams(0, -1, 1f).apply {
                            if (index > 0) marginStart = dp(8)
                        })
                    }
                }
                val categoryRow = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(header, LinearLayout.LayoutParams(-1, dp(34)))
                    addView(previews, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                addView(categoryRow, LayoutParams(-1, -1))
            } else {
                addView(images.single(), LayoutParams(-1, -1))
            }
        }

        fun bind(tile: Tile) {
            images.forEachIndexed { index, image ->
                val path = tile.paths.getOrNull(index)
                image.visibility = if (path == null) View.INVISIBLE else View.VISIBLE
                image.setImageBitmap(path?.let(::thumbnail))
            }
            name.text = tile.label
            count.text = tile.count?.let { getString(R.string.background_images_count, it) }
            contentDescription = listOfNotNull(tile.label, count.text?.takeIf { it.isNotEmpty() }).joinToString(", ")
            foreground = if (tile.count == null && choice?.getStringExtra(ASSET) == tile.paths.single()) outline() else null
        }
    }

    private fun thumbnail(path: String): Bitmap? = thumbnails.get(path) ?: try {
        designAssets.image(path, dp(120)).also { thumbnails.put(path, it) }
    } catch (_: IOException) {
        null
    }

    @Deprecated("Uses the Activity back callback for category navigation.")
    override fun onBackPressed() = goBack()

    private fun goBack() {
        if (currentCategory == null) finish() else showGallery(null)
    }

    private fun finishWithChoice(selected: Intent) {
        setResult(RESULT_OK, selected)
        finish()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putParcelable("choice", choice)
        state.putString("category", currentCategory?.folder)
        state.putInt("imagePosition", binding.gallery.firstVisiblePosition)
        state.putInt("categoryPosition", if (currentCategory == null) binding.gallery.firstVisiblePosition else categoryPosition)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        const val COLOR = "backgroundColor"
        const val ASSET = "backgroundAsset"
        const val APPLY_ON_TAP = "applyBackgroundOnTap"
    }
}
