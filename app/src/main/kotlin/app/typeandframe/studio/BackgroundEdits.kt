package app.typeandframe.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle

internal class BackgroundEdits {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private var filter: ColorMatrixColorFilter? = null

    @JvmField var rotation = 0
    @JvmField var horizontalFlip = false
    @JvmField var verticalFlip = false
    @JvmField var grayscale = false
    @JvmField var fit = false
    @JvmField var brightness = 0
    @JvmField var contrast = 0
    @JvmField var zoom = 0
    @JvmField var overlay = Color.TRANSPARENT

    fun reset() {
        restore(Bundle())
    }

    fun draw(canvas: Canvas, image: Bitmap?, paper: Int) {
        val size = EditorCanvas.SIZE
        canvas.drawColor(paper)
        if (image != null) {
            val reference = if (fit) maxOf(image.width, image.height) else minOf(image.width, image.height)
            val ratio = size / reference.toFloat() * (1f + zoom / 100f)
            val width = image.width * ratio
            val height = image.height * ratio
            bounds.set((size - width) / 2, (size - height) / 2, (size + width) / 2, (size + height) / 2)
            canvas.save()
            canvas.rotate(rotation.toFloat(), size / 2f, size / 2f)
            canvas.scale(if (horizontalFlip) -1f else 1f, if (verticalFlip) -1f else 1f, size / 2f, size / 2f)
            paint.colorFilter = filter
            canvas.drawBitmap(image, null, bounds, paint)
            canvas.restore()
        }
        if (overlay != Color.TRANSPARENT) canvas.drawColor(overlay)
    }

    fun updateFilter() {
        if (!grayscale && brightness == 0 && contrast == 0) {
            filter = null
            return
        }
        val colors = ColorMatrix()
        if (grayscale) colors.setSaturation(0f)
        val amount = 1f + contrast / 100f
        val offset = brightness + (1f - amount) * 128
        colors.postConcat(ColorMatrix(floatArrayOf(
            amount, 0f, 0f, 0f, offset,
            0f, amount, 0f, 0f, offset,
            0f, 0f, amount, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )))
        filter = ColorMatrixColorFilter(colors)
    }

    fun save(): Bundle = Bundle().apply {
        putInt("rotation", rotation)
        putBoolean("flipH", horizontalFlip)
        putBoolean("flipV", verticalFlip)
        putBoolean("grayscale", grayscale)
        putBoolean("fit", fit)
        putInt("brightness", brightness)
        putInt("contrast", contrast)
        putInt("zoom", zoom)
        putInt("overlay", overlay)
    }

    fun restore(state: Bundle?) {
        if (state == null) return
        rotation = state.getInt("rotation")
        horizontalFlip = state.getBoolean("flipH")
        verticalFlip = state.getBoolean("flipV")
        grayscale = state.getBoolean("grayscale")
        fit = state.getBoolean("fit")
        brightness = state.getInt("brightness")
        contrast = state.getInt("contrast")
        zoom = state.getInt("zoom")
        overlay = state.getInt("overlay")
        updateFilter()
    }
}
