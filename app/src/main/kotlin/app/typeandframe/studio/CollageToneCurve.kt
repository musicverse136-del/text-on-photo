package app.typeandframe.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.DataInputStream
import java.io.IOException

internal object CollageToneCurve {
    @JvmStatic
    @Throws(IOException::class)
    fun apply(context: Context, image: Bitmap, asset: String): Bitmap {
        val curves = Array(4) { IntArray(256) { it } }
        DataInputStream(context.assets.open(asset)).use { input ->
            input.readUnsignedShort()
            val count = minOf(input.readUnsignedShort(), curves.size)
            for (channel in 0 until count) {
                val points = input.readUnsignedShort()
                if (points !in 2..256) throw IOException("Invalid tone curve")
                val x = IntArray(points)
                val y = IntArray(points)
                for (index in 0 until points) {
                    y[index] = minOf(255, input.readUnsignedShort())
                    x[index] = minOf(255, input.readUnsignedShort())
                    if (index > 0 && x[index] <= x[index - 1]) throw IOException("Unordered tone curve")
                }
                interpolate(x, y, curves[channel])
            }
        }
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = Color.argb(
                Color.alpha(color),
                curves[0][curves[1][Color.red(color)]],
                curves[0][curves[2][Color.green(color)]],
                curves[0][curves[3][Color.blue(color)]]
            )
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun interpolate(x: IntArray, y: IntArray, values: IntArray) {
        val last = x.lastIndex
        val second = DoubleArray(x.size)
        val temporary = DoubleArray(x.size)
        for (index in 1 until last) {
            val span = (x[index + 1] - x[index - 1]).toDouble()
            val weight = (x[index] - x[index - 1]) / span
            val pivot = weight * second[index - 1] + 2
            second[index] = (weight - 1) / pivot
            val change = (y[index + 1] - y[index]) / (x[index + 1] - x[index]).toDouble() -
                (y[index] - y[index - 1]) / (x[index] - x[index - 1]).toDouble()
            temporary[index] = (6 * change / span - weight * temporary[index - 1]) / pivot
        }
        for (index in last - 1 downTo 0) {
            second[index] = second[index] * second[index + 1] + temporary[index]
        }
        values.fill(0, 0, x[0])
        values.fill(255, x[last], values.size)
        for (index in 0 until last) {
            val distance = (x[index + 1] - x[index]).toDouble()
            for (at in x[index]..x[index + 1]) {
                val t = (at - x[index]) / distance
                val u = 1 - t
                val result = u * y[index] + t * y[index + 1] + distance * distance / 6 *
                    ((u * u * u - u) * second[index] + (t * t * t - t) * second[index + 1])
                values[at] = Math.round(result).toInt().coerceIn(0, 255)
            }
        }
    }
}
