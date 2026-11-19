package app.typeandframe.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.OnHeaderDecodedListener
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays

internal class DesignAssets(context: Context) {
    private val context: Context

    init {
        this.context = context
    }

    fun files(folder: String, vararg extensions: String): MutableList<String> {
        val paths: MutableList<String> = ArrayList<String>()
        try {
            val names: Array<String>? = context.getAssets().list(folder)
            if (names == null) return paths
            Arrays.sort<String?>(names, java.lang.String.CASE_INSENSITIVE_ORDER)
            for (name in names) {
                val lower: String = name.lowercase()
                for (extension in extensions) {
                    if (lower.endsWith(extension)) {
                        paths.add(folder + "/" + name)
                        break
                    }
                }
            }
        } catch (ignored: IOException) {
            // The picker displays an empty state if a directory cannot be read.
        }
        return paths
    }

    fun font(path: String): Typeface? {
        if (path.isEmpty()) return Typeface.DEFAULT
        try {
            if (path.startsWith("local-font:")) {
                val name: String = path.substring("local-font:".length)
                return Typeface.createFromFile(File(File(context.getFilesDir(), "fonts"), name))
            }
            return Typeface.createFromAsset(context.getAssets(), path)
        } catch (e: java.lang.RuntimeException) {
            return Typeface.DEFAULT
        }
    }

    @Throws(IOException::class)
    fun text(path: String): String {
        val result: java.lang.StringBuilder = java.lang.StringBuilder()
        BufferedReader(
            InputStreamReader(
                context.getAssets().open(path), StandardCharsets.UTF_8
            )
        ).use { reader ->
            var line: String?
            while ((reader.readLine().also { line = it }) != null) result.append(line).append('\n')

        }
        return result.toString()
    }

    @Throws(IOException::class)
    fun image(path: String, limit: Int): Bitmap {
        return decode(DesignAssets.StreamSource { context.getAssets().open(path) }, limit)
    }

    @Throws(IOException::class)
    fun photo(uri: Uri, limit: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri),
                OnHeaderDecodedListener { decoder: ImageDecoder, info: ImageInfo, source: ImageDecoder.Source ->
                    val width: Int = info.getSize().getWidth()
                    val height: Int = info.getSize().getHeight()
                    val scale: Float =
                        kotlin.math.min(1f, limit / kotlin.math.max(width, height).toFloat())
                    decoder.setTargetSize(
                        kotlin.math.max(1, java.lang.Math.round(width * scale)),
                        kotlin.math.max(1, java.lang.Math.round(height * scale))
                    )
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)

                })
        }
        return decode(
            DesignAssets.StreamSource { context.contentResolver.openInputStream(uri) },
            limit
        )
    }

    @Throws(IOException::class)
    private fun decode(source: DesignAssets.StreamSource, limit: Int): Bitmap {
        val options: Options = Options()
        options.inJustDecodeBounds = true
        source.open().use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Unsupported image")
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (kotlin.math.max(
                options.outWidth,
                options.outHeight
            ) / options.inSampleSize > 2 * limit
        ) {
            options.inSampleSize *= 2
        }
        source.open().use { stream ->
            val image: Bitmap? = BitmapFactory.decodeStream(stream, null, options)
            if (image == null) throw IOException("Image could not be decoded")
            return image

        }
    }

    private fun interface StreamSource {
        @Throws(IOException::class)
        fun open(): InputStream?
    }

    companion object {
        val photoCategories = listOf(
            "Holiday" to "holiday",
            "Lifestyle" to "life_style",
            "Light" to "light",
            "Love" to "love",
            "Macro" to "macro",
            "Nature" to "nature"
        )

        fun label(path: String): String {
            return path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'))
                .replace('_', ' ')
        }

    }
}
