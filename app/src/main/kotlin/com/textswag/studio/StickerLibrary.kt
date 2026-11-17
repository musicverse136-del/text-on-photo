package com.textswag.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.util.LruCache
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class StickerLibrary(context: Context) {
    private val context: Context
    private val cache: File
    private val thumbnails: LruCache<String?, Bitmap?> = LruCache<String?, Bitmap?>(40)

    init {
        this.context = context
        cache = File(context.getCacheDir(), "stickers")
    }

    @Throws(IOException::class, JSONException::class)
    fun packs(): MutableList<Pack> {
        val json: java.lang.StringBuilder = java.lang.StringBuilder()
        BufferedReader(
            InputStreamReader(
                context.getResources().openRawResource(R.raw.sticker_catalog),
                StandardCharsets.UTF_8
            )
        ).use { input ->
            var line: String?
            while ((input.readLine().also { line = it }) != null) json.append(line)

        }
        val definitions: JSONArray = JSONArray(json.toString())
        val packs: MutableList<Pack> = ArrayList<Pack>()
        for (i in 0..<definitions.length()) {
            val definition: JSONObject = definitions.getJSONObject(i)
            val free: Boolean = "free" == definition.getString("unlock_type")
            val path: String = definition.getString("path")
            val files: JSONArray = definition.getJSONArray("data")
            val keys: MutableList<String> = ArrayList<String>()
            for (index in 0..<files.length()) {
                val key: String = path + files.getString(index)
                if (key.matches("stickers/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\.(png|webp)".toRegex())) keys.add(
                    key
                )
            }
            if (!keys.isEmpty()) packs.add(Pack(definition.getString("name"), keys, free))
        }
        return packs
    }

    @Throws(IOException::class)
    fun thumbnail(key: String): Bitmap {
        var thumbnail: Bitmap? = thumbnails.get(key)
        if (thumbnail != null) return thumbnail
        val icon: File = download(key)
        val source: Bitmap? = BitmapFactory.decodeFile(icon.getPath())
        if (source == null) throw IOException("Sticker preview is invalid")
        val scale: Float =
            kotlin.math.min(1f, 180f / kotlin.math.max(source.getWidth(), source.getHeight()))
        thumbnail = if (scale == 1f) source else Bitmap.createScaledBitmap(
            source,
            kotlin.math.max(1, java.lang.Math.round(source.getWidth() * scale)),
            kotlin.math.max(1, java.lang.Math.round(source.getHeight() * scale)), true
        )
        if (thumbnail != source) source.recycle()
        thumbnails.put(key, thumbnail)
        return thumbnail
    }

    @Throws(IOException::class)
    fun image(key: String): Bitmap {
        val file: File = download(key)
        val bitmap: Bitmap? = BitmapFactory.decodeFile(file.getPath())
        if (bitmap == null) throw IOException("Sticker image is invalid")
        return bitmap
    }

    fun cachedImage(key: String): Bitmap? {
        return BitmapFactory.decodeFile(cacheFile(key).getPath())
    }

    @Synchronized
    @Throws(IOException::class)
    private fun download(key: String): File {
        val target: File = cacheFile(key)
        if (target.isFile()) return target
        if (!cache.isDirectory() && !cache.mkdirs()) throw IOException("Could not create sticker cache")
        val temporary: File = File.createTempFile("sticker-", ".tmp", cache)
        val request: HttpURLConnection =
            URL(StickerLibrary.CDN + key).openConnection() as HttpURLConnection
        request.setInstanceFollowRedirects(false)
        request.setConnectTimeout(3500)
        request.setReadTimeout(3500)
        try {
            if (request.getResponseCode() != 200) throw IOException("Sticker is unavailable")
            request.getInputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer: ByteArray = ByteArray(8192)
                    var bytes: Int = 0
                    var count: Int
                    while ((input.read(buffer).also { count = it }) != -1) {
                        bytes += count
                        if (bytes > StickerLibrary.MAX_BYTES) throw IOException("Sticker exceeds size limit")
                        output.write(buffer, 0, count)
                    }

                }
            }
            val bounds: Options = Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(temporary.getPath(), bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 2048 || bounds.outHeight > 2048) {
                throw IOException("Unsupported sticker dimensions")
            }
            if (!temporary.renameTo(target)) throw IOException("Could not cache sticker")
            return target
        } finally {
            request.disconnect()
            temporary.delete()
        }
    }

    private fun cacheFile(key: String): File {
        return File(cache, key.replace('/', '_'))
    }

    internal class Pack(name: String, keys: MutableList<String>, free: Boolean) {
        val name: String
        val keys: MutableList<String>
        val free: Boolean

        init {
            this.name = name
            this.keys = keys
            this.free = free
        }
    }

    companion object {
        private const val CDN: String =
            "https://zipoapps-storage-text-art.nyc3.cdn.digitaloceanspaces.com/"
        private val MAX_BYTES: Int = 4 * 1024 * 1024
    }
}
