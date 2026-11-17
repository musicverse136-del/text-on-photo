package com.textswag.studio

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import org.json.JSONArray
import org.json.JSONObject

internal class EmojiPanel(
    context: Context,
    assets: DesignAssets,
    select: java.util.function.Consumer<String?>
) : BaseAdapter() {
    private val context: Context
    private val assets: DesignAssets
    private val select: java.util.function.Consumer<String?>
    private val rows: MutableList<EmojiPanel.Row> = ArrayList<EmojiPanel.Row>()
    private val previews: LruCache<String?, Bitmap?> = LruCache<String?, Bitmap?>(48)

    init {
        this.context = context
        this.assets = assets
        this.select = select
        val info: java.lang.StringBuilder = java.lang.StringBuilder()
        BufferedReader(
            InputStreamReader(
                context.getAssets().open("emoji/info.json"), StandardCharsets.UTF_8
            )
        ).use { input ->
            var line: String?
            while ((input.readLine().also { line = it }) != null) info.append(line)

        }
        val groups: JSONArray = JSONArray(info.toString())
        for (i in 0..<groups.length()) {
            val group: JSONObject = groups.getJSONObject(i)
            val paths: MutableList<String> =
                assets.files(group.getString("relativePath"), ".png", ".webp")
            if (paths.isEmpty()) continue
            rows.add(EmojiPanel.Row(group.getString("name"), null))
            var start: Int = 0
            while (start < paths.size) {
                rows.add(
                    EmojiPanel.Row(
                        null,
                        paths.subList(
                            start,
                            kotlin.math.min(start + EmojiPanel.COLUMNS, paths.size)
                        )
                    )
                )
                start += EmojiPanel.COLUMNS
            }
        }
    }

    override fun getCount(): Int {
        return rows.size
    }

    override fun getItem(position: Int): EmojiPanel.Row {
        return rows.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getItemViewType(index: Int): Int {
        return if (getItem(index).heading == null) 1 else 0
    }

    override fun isEnabled(position: Int): Boolean {
        return false
    }

    override fun areAllItemsEnabled(): Boolean {
        return false
    }

    override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
        val row: EmojiPanel.Row = getItem(position)
        if (row.heading != null) {
            val title: TextView = if (recycled == null) TextView(context) else recycled as TextView
            title.setText(row.heading)
            title.setTextColor(context.getColor(R.color.foreground))
            title.setTextSize(16f)
            title.setGravity(Gravity.CENTER_VERTICAL)
            title.setPadding(dp(14), dp(8), 0, dp(8))
            return title
        }

        val strip: LinearLayout = if (recycled == null) makeStrip() else recycled as LinearLayout
        for (i in 0..<EmojiPanel.COLUMNS) {
            val button: ImageButton = strip.getChildAt(i) as ImageButton
            if (i >= requireNotNull(row.paths).size) {
                button.setVisibility(View.INVISIBLE)
                continue
            }
            val path: String = requireNotNull(row.paths)[i]
            button.setVisibility(View.VISIBLE)
            var bitmap: Bitmap? = previews.get(path)
            if (bitmap == null) {
                try {
                    bitmap = assets.image(path, 112)
                    previews.put(path, bitmap)
                } catch (ignored: IOException) {
                    // The asset's name remains available to accessibility services.
                }
            }
            button.setImageBitmap(bitmap)
            button.setContentDescription(DesignAssets.label(path))
            button.setOnClickListener(View.OnClickListener { view: View -> select.accept(path) })
        }
        return strip
    }

    private fun makeStrip(): LinearLayout {
        val strip: LinearLayout = LinearLayout(context)
        for (i in 0..<EmojiPanel.COLUMNS) {
            val icon: ImageButton = ImageButton(context)
            icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER)
            icon.setPadding(dp(6), dp(6), dp(6), dp(6))
            icon.setBackgroundResource(android.R.drawable.list_selector_background)
            strip.addView(icon, LinearLayout.LayoutParams(0, dp(45), 1f))
        }
        return strip
    }

    private fun dp(value: Int): Int {
        return java.lang.Math.round(value * context.getResources().getDisplayMetrics().density)
    }

    internal class Row(heading: String?, paths: MutableList<String>?) {
        val heading: String?
        val paths: MutableList<String>?

        init {
            this.heading = heading
            this.paths = paths
        }
    }

    companion object {
        private const val COLUMNS: Int = 6
    }
}
