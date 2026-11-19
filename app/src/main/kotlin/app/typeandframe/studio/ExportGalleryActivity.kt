package app.typeandframe.studio

import android.app.Activity
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore.Images.Media
import android.util.LruCache
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.annotation.RequiresApi
import app.typeandframe.studio.databinding.ActivityExportGalleryBinding
import java.io.IOException
import java.util.concurrent.Executors

@RequiresApi(29)
class ExportGalleryActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val thumbnails = object : LruCache<Uri, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Uri, value: Bitmap) = value.byteCount
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val binding = ActivityExportGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        binding.close.setOnClickListener { finish() }
        worker.execute {
            val exports = runCatching { findExports() }.getOrNull()
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                binding.loading.visibility = View.GONE
                if (exports.isNullOrEmpty()) {
                    binding.empty.setText(if (exports == null) R.string.studio_exports_error else R.string.studio_no_exports)
                    binding.empty.visibility = View.VISIBLE
                } else {
                    binding.exports.adapter = ExportAdapter(exports)
                    binding.exports.setOnItemClickListener { _, _, position, _ ->
                        SavedImageActivity.open(this, exports[position].uri)
                    }
                }
            }
        }
    }

    private fun findExports(): List<Export> {
        val collection = Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(Media._ID, Media.DISPLAY_NAME)
        val selection = "${Media.RELATIVE_PATH} = ? AND ${Media.IS_PENDING} = 0"
        val path = arrayOf("${Environment.DIRECTORY_PICTURES}/Type & Frame/")
        return contentResolver.query(collection, columns, selection, path, "${Media.DATE_ADDED} DESC, ${Media._ID} DESC")
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(Export(ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(1)))
                    }
                }
            } ?: throw IOException("MediaStore returned no cursor")
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private data class Export(val uri: Uri, val name: String)

    private inner class ExportAdapter(private val exports: List<Export>) : BaseAdapter() {
        override fun getCount() = exports.size
        override fun getItem(position: Int) = exports[position]
        override fun getItemId(position: Int) = ContentUris.parseId(getItem(position).uri)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val edge = (parent as? android.widget.GridView)?.columnWidth?.takeIf { it > 0 }
                ?: (112 * resources.displayMetrics.density).toInt()
            val tile = convertView as? ImageView ?: ImageView(this@ExportGalleryActivity).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, edge)
                setBackgroundResource(R.drawable.card_studio_tile)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
            }
            val export = getItem(position)
            tile.contentDescription = export.name
            tile.tag = export.uri
            val cached = thumbnails.get(export.uri)
            tile.setImageBitmap(cached)
            if (cached == null) {
                worker.execute {
                    val bitmap = runCatching {
                        contentResolver.loadThumbnail(export.uri, Size(edge, edge), null)
                    }.getOrNull() ?: return@execute
                    thumbnails.put(export.uri, bitmap)
                    runOnUiThread {
                        if (!isDestroyed && tile.tag == export.uri) tile.setImageBitmap(bitmap)
                    }
                }
            }
            return tile
        }
    }
}
