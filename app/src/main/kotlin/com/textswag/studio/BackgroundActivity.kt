package com.textswag.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.textswag.studio.databinding.ActivityBackgroundBinding
import java.io.IOException
import java.util.function.IntConsumer

class BackgroundActivity : Activity() {
    private lateinit var binding: ActivityBackgroundBinding
    private lateinit var sections: LinearLayout
    private var selection: View? = null
    private var choice: Intent? = null
    private lateinit var assets: DesignAssets

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        assets = DesignAssets(this)
        sections = binding.backgroundSections
        binding.close.setOnClickListener(View.OnClickListener { view: View -> finish() })
        binding.next.setOnClickListener(View.OnClickListener { view: View -> finishWithChoice(choice) })

        addColors()
        addMediaSources()
        addTransparentTile()
        for (i in BackgroundActivity.CATEGORY_NAMES.indices) {
            addCategory(
                BackgroundActivity.CATEGORY_NAMES[i],
                BackgroundActivity.CATEGORY_FOLDERS[i]
            )
        }
        if (state != null) {
            choice = state.getParcelable<Intent?>("choice")
            if (choice != null) binding.next.setVisibility(View.VISIBLE)
        }
    }

    private fun addColors() {
        heading("Colors")
        val row: LinearLayout = horizontalRow()
        val pick: ImageView = ImageView(this)
        pick.setImageResource(R.drawable.ic_color_picker)
        pick.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        pick.setContentDescription("Custom color")
        pick.setOnClickListener(View.OnClickListener { view: View ->
            ColorWheelDialog.show(
                this,
                choice?.getIntExtra(COLOR, Color.WHITE) ?: Color.WHITE,
                IntConsumer { color: Int ->
                    select(
                        view,
                        Intent().putExtra(BackgroundActivity.COLOR, color)
                    )
                })
        })
        row.addView(pick, tileParams(88))
        val colors: IntArray = kotlin.intArrayOf(
            -0x1, -0x1000000, -0x4e2423, -0xb2720, -0x93463,
            -0x111529, -0x434d24, -0x8a684b, -0x7d687f
        )
        val names: Array<String> =
            arrayOf("White", "Black", "Teal", "Rose", "Peach", "Cream", "Lavender", "Blue", "Sage")
        for (i in colors.indices) {
            val color: Int = colors[i]
            val swatch: View = View(this)
            val tile: GradientDrawable = GradientDrawable()
            tile.setColor(color)
            tile.setCornerRadius(dp(4).toFloat())
            tile.setStroke(dp(1), -0x1b1b1c)
            swatch.setBackground(tile)
            swatch.setContentDescription(names[i])
            swatch.setOnClickListener(View.OnClickListener { view: View ->
                select(
                    view,
                    Intent().putExtra(BackgroundActivity.COLOR, color)
                )
            })
            row.addView(swatch, tileParams(88))
        }
    }

    private fun addMediaSources() {
        heading("More Background")
        val row: LinearLayout = LinearLayout(this)
        row.setGravity(Gravity.CENTER)
        sections.addView(row, LinearLayout.LayoutParams(-1, dp(132)))
        addSource(row, "Collage", R.drawable.ic_home_collage, Runnable { pickMedia(true) })
        addSource(row, "Gallery", R.drawable.ic_home_gallery, Runnable { pickMedia(false) })
        addSource(row, "Stock Images", R.drawable.ic_background, Runnable {
            Toast.makeText(
                this,
                "Stock service is unavailable. Browse the bundled images below.", Toast.LENGTH_SHORT
            ).show()
        })
    }

    private fun addSource(row: LinearLayout, title: String?, icon: Int, action: Runnable) {
        val item: TextView = TextView(this)
        item.setGravity(Gravity.CENTER)
        item.setText(title)
        item.setTextColor(getColor(R.color.foreground))
        item.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
        item.setCompoundDrawablePadding(dp(3))
        item.setPadding(dp(3), dp(8), dp(3), dp(5))
        val background: GradientDrawable = GradientDrawable()
        background.setColor(getColor(R.color.panel))
        background.setCornerRadius(dp(9).toFloat())
        item.setBackground(background)
        item.setOnClickListener(View.OnClickListener { view: View -> action.run() })
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(108), 1f)
        params.setMargins(dp(6), 0, dp(6), 0)
        row.addView(item, params)
    }

    private fun addTransparentTile() {
        heading("Transparent Background")
        val tile: ImageView = ImageView(this)
        tile.setScaleType(ImageView.ScaleType.CENTER_CROP)
        tile.setImageResource(R.drawable.transparent_background)
        tile.setContentDescription("Select transparent background")
        tile.setOnClickListener(View.OnClickListener { view: View ->
            select(
                view,
                Intent().putExtra(BackgroundActivity.COLOR, Color.TRANSPARENT)
            )
        })
        val row: LinearLayout = horizontalRow()
        row.addView(tile, tileParams(124))
    }

    private fun addCategory(label: String?, category: String?) {
        val paths: MutableList<String> =
            assets.files("backgrounds/" + category, ".webp", ".png", ".jpeg", ".jpg")
        if (paths.isEmpty()) return
        heading(label)
        val row: LinearLayout = horizontalRow()
        for (path in paths) {
            val thumbnail: ImageView = ImageView(this)
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP)
            thumbnail.setContentDescription(label + " " + DesignAssets.label(path))
            try {
                thumbnail.setImageBitmap(assets.image(path, 128))
            } catch (ignored: IOException) {
                continue
            }
            thumbnail.setOnClickListener(View.OnClickListener { view: View ->
                select(
                    view,
                    Intent().putExtra(BackgroundActivity.ASSET, path)
                )
            })
            row.addView(thumbnail, tileParams(88))
        }
    }

    private fun heading(value: String?): TextView {
        val title: TextView = TextView(this)
        title.setText(value)
        title.setTextColor(getColor(R.color.foreground))
        title.setTextSize(18f)
        title.setGravity(Gravity.CENTER_VERTICAL)
        title.setPadding(dp(18), dp(14), dp(12), dp(12))
        sections.addView(title, LinearLayout.LayoutParams(-1, -2))
        return title
    }

    private fun horizontalRow(): LinearLayout {
        val strip: HorizontalScrollView = HorizontalScrollView(this)
        strip.setHorizontalScrollBarEnabled(false)
        val row: LinearLayout = LinearLayout(this)
        row.setPadding(dp(7), 0, dp(7), 0)
        strip.addView(row)
        sections.addView(strip, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    private fun tileParams(size: Int): LinearLayout.LayoutParams {
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        params.setMargins(dp(4), dp(2), dp(4), dp(5))
        return params
    }

    private fun select(tile: View, value: Intent?) {
        selection?.foreground = null
        selection = tile
        val border: GradientDrawable = GradientDrawable()
        border.setColor(Color.TRANSPARENT)
        border.setCornerRadius(dp(4).toFloat())
        border.setStroke(dp(3), getColor(R.color.accent))
        tile.setForeground(border)
        choice = value
        binding.next.setVisibility(View.VISIBLE)
    }

    private fun pickMedia(multiple: Boolean) {
        if (multiple) {
            startActivityForResult(
                Intent(this, CollageRecentActivity::class.java).putExtra(
                    CollageRecentActivity.REPLACE,
                    true
                ), BackgroundActivity.PICK_COLLAGE
            )
            return
        }
        val picker: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        startActivityForResult(picker, BackgroundActivity.PICK_MEDIA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == BackgroundActivity.PICK_COLLAGE || requestCode == BackgroundActivity.PICK_MEDIA) finishWithChoice(
            data
        )
    }

    private fun finishWithChoice(selected: Intent?) {
        if (selected == null) return
        setResult(Activity.RESULT_OK, selected)
        finish()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putParcelable("choice", choice)
    }

    private fun dp(value: Int): Int {
        return java.lang.Math.round(value * getResources().getDisplayMetrics().density)
    }

    companion object {
        const val COLOR: String = "backgroundColor"
        const val ASSET: String = "backgroundAsset"
        private const val PICK_MEDIA: Int = 1
        private const val PICK_COLLAGE: Int = 2
        private val CATEGORY_NAMES: Array<String> =
            arrayOf("Holiday", "Lifestyle", "Light", "Love", "Macro", "Nature")
        private val CATEGORY_FOLDERS: Array<String> =
            arrayOf("holiday", "life_style", "light", "love", "macro", "nature")
    }
}
