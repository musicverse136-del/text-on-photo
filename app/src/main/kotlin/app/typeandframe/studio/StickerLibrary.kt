package app.typeandframe.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONException
import kotlin.math.roundToInt

internal class StickerLibrary(private val context: Context) {
    private val assets = DesignAssets(context)
    private val thumbnails = LruCache<String, Bitmap>(40)

    @Throws(IOException::class, JSONException::class)
    fun packs(): List<Pack> {
        val json = context.resources.openRawResource(R.raw.sticker_catalog)
            .bufferedReader().use { it.readText() }
        val definitions = JSONArray(json)
        return buildList {
            for (index in 0 until definitions.length()) {
                val definition = definitions.getJSONObject(index)
                val path = definition.getString("path")
                val files = definition.getJSONArray("data")
                val keys = List(files.length()) { path + files.getString(it) }
                    .filter(stickerKey::matches)
                if (keys.isNotEmpty()) {
                    add(Pack(definition.getString("name"), keys, definition.getString("unlock_type") == "free"))
                }
            }
        }
    }

    @Throws(IOException::class)
    fun thumbnail(key: String): Bitmap {
        thumbnails.get(key)?.let { return it }
        val source = assets.image(key, THUMBNAIL_SIZE)
        val scale = minOf(1f, THUMBNAIL_SIZE.toFloat() / maxOf(source.width, source.height))
        val preview = if (scale == 1f) source else Bitmap.createScaledBitmap(
            source, (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1), true
        )
        if (preview !== source) source.recycle()
        thumbnails.put(key, preview)
        return preview
    }

    @Throws(IOException::class)
    fun image(key: String): Bitmap = assets.image(key, 1024)

    fun restoreImage(key: String): Bitmap? {
        if (!stickerKey.matches(key)) return null
        return try {
            image(key)
        } catch (_: IOException) {
            // Older builds saved downloaded stickers under this flat cache name.
            val previous = File(context.cacheDir, "stickers/" + key.replace('/', '_'))
            BitmapFactory.decodeFile(previous.path)
        }
    }

    data class Pack(val name: String, val keys: List<String>, val free: Boolean)

    companion object {
        private const val THUMBNAIL_SIZE = 180
        private val stickerKey = Regex("""stickers/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\.(png|webp)""")

        fun bundledCategory(path: String): String {
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            val number = name.substringAfter('_').toIntOrNull()
            return when (name.substringBefore('_')) {
                "stick" -> when (number) {
                    1, 2, 4, 6, 10, 12, 14, 16, 17, 31, 36, 580 -> "Love"
                    20, 27, 32, 34, 35, 40 -> "Food & Drink"
                    9, 13, 15, 18, 21, 23, 51, 57, 58, 59, 340, 440, 500, 540 -> "Words & Bubbles"
                    28, 39, in 41..49, 100 -> "Faces"
                    in 52..56 -> "Animals"
                    else -> "Objects & Symbols"
                }
                "sticker" -> when (number) {
                    1, 22, 26, 40, 42 -> "Animals"
                    2, 3, in 5..13, 18, 19, 23, in 30..34 -> "Food & Drink"
                    21, 24, 25 -> "Halloween"
                    in 35..39 -> "Words & Bubbles"
                    else -> "Objects & Symbols"
                }
                else -> "Objects & Symbols"
            }
        }
    }
}
