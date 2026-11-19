package app.typeandframe.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.util.LruCache
import app.typeandframe.studio.EditorCanvas.Layer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayList

internal class TextStyles(context: Context, assets: DesignAssets) {
    private val context: Context
    private val assets: DesignAssets
    private val presets: MutableList<Preset> = ArrayList<Preset>()
    private val fonts: LruCache<String?, Typeface?> = LruCache<String?, Typeface?>(24)
    private val masks: LruCache<Int?, Bitmap?> = LruCache<Int?, Bitmap?>(4)

    init {
        this.context = context
        this.assets = assets
        try {
            BufferedReader(
                InputStreamReader(
                    context.getResources().openRawResource(R.raw.text_style_catalog),
                    StandardCharsets.UTF_8
                )
            ).use { input ->
                var line: String?
                while ((input.readLine().also { line = it }) != null) {
                    val fields = requireNotNull(line).split("|").dropLastWhile { it.isEmpty() }
                    if (fields.size != 3) throw IOException("Invalid text style definition")
                    presets.add(
                        Preset(
                            fields[0],
                            fields[1],
                            fields[2].split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
                        )
                    )
                }

            }
        } catch (e: IOException) {
            throw java.lang.IllegalStateException("Could not load bundled text styles", e)
        }
    }

    fun size(): Int {
        return presets.size
    }

    fun name(index: Int): String? {
        return presets.get(index).name
    }

    fun renderLayer(layer: Layer, presetIndex: Int): Bitmap {
        return render(
            layer.text, presetIndex, layer.styleVariant,
            layer.styleSpacing, layer.styleInvert, layer.color, 720
        )
    }

    fun render(
        value: String,
        index: Int,
        variant: Int,
        spacing: Int,
        invert: Boolean,
        color: Int,
        width: Int
    ): Bitmap {
        val preset: Preset = presets.get(index)
        val random: java.util.Random = java.util.Random(variant * 173L + index * 43L)
        val ink: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        ink.setTypeface(font(preset.fonts[java.lang.Math.floorMod(variant - 1, preset.fonts.size)]))
        ink.setColor(color)
        ink.setTextSize(width * (if (value.length > 150) 0.085f else 0.165f))
        val text: CharSequence = TextStyles.arrange(value, preset.family, variant, random)
        val padding: Int = kotlin.math.max(4, width / 12)
        val contentWidth: Int = kotlin.math.min(
            width - padding * 2,
            kotlin.math.max(
                width / 6,
                kotlin.math.ceil(Layout.getDesiredWidth(text, ink).toDouble()).toInt() + 2
            )
        )
        val layout: StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, ink, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true)
                .setLineSpacing(spacing * width / 1200f, 1f).build()
        val mask: Int = TextStyles.maskFor(preset.family)
        val rotated: Boolean = "rotated" == preset.family
        val boxed: Boolean = mask != 0 || invert
        val outputWidth: Int = if (boxed) width else layout.getWidth() + padding * 2
        val outputHeight: Int = layout.getHeight() + padding * (if (boxed || rotated) 4 else 2)
        val result: Bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(result)
        val shape: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        shape.setColor(color)
        if (boxed) {
            if (mask != 0) {
                var outline: Bitmap? = masks.get(mask)
                if (outline == null) {
                    outline = BitmapFactory.decodeResource(context.getResources(), mask)
                    masks.put(mask, outline)
                }
                shape.setColorFilter(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN))
                canvas.drawBitmap(
                    outline,
                    null,
                    RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
                    shape
                )
            } else {
                canvas.drawRoundRect(
                    0f,
                    0f,
                    outputWidth.toFloat(),
                    outputHeight.toFloat(),
                    padding / 2f,
                    padding / 2f,
                    shape
                )
            }
            ink.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_OUT))
            if (mask != 0 && invert) {
                canvas.drawColor(color, PorterDuff.Mode.SRC_OVER)
            }
        }
        if (rotated) canvas.rotate(
            if (variant % 2 == 0) 7f else -7f,
            outputWidth / 2f,
            outputHeight / 2f
        )
        canvas.translate(
            (outputWidth - layout.getWidth()) / 2f,
            (outputHeight - layout.getHeight()) / 2f
        )
        layout.draw(canvas)
        return result
    }

    private fun font(path: String): Typeface? {
        var face: Typeface? = fonts.get(path)
        if (face == null) {
            face = assets.font(path)
            fonts.put(path, face)
        }
        return face
    }

    private class Preset(name: String?, family: String, fonts: Array<String>) {
        val name: String?
        val family: String
        val fonts: Array<String>

        init {
            this.name = name
            this.family = family
            this.fonts = fonts
        }
    }

    companion object {
        private fun maskFor(family: String): Int {
            when (family) {
                "masked" -> return R.drawable.text_template_asset_black_background
                "badge" -> return R.drawable.text_template_asset_badge1
                "bubble" -> return R.drawable.text_template_asset_speech_bubble3
                "celebrate" -> return R.drawable.text_template_asset_celebrate_01
                else -> return 0
            }
        }

        private fun arrange(
            value: String,
            family: String?,
            variant: Int,
            random: java.util.Random
        ): CharSequence {
            val blocks: Boolean = "blocks" == family
            val equal: Boolean = "equal" == family
            var text: String = if (blocks || equal) value.uppercase() else value
            if (!blocks && !equal && ("masked" != family)) return text

            if (!text.contains("\n")) {
                val words: Array<String> =
                    text.split("\\s+".toRegex()).dropLastWhile { it.isNullOrEmpty() }.toTypedArray()
                val lines: java.lang.StringBuilder = java.lang.StringBuilder()
                val groupSize: Int =
                    kotlin.math.max(if (equal) 1 else variant % 3 + 1, (words.size + 9) / 10)
                for (i in words.indices) {
                    if (i > 0) {
                        val newLine: Boolean = i % groupSize == 0
                        lines.append(if (newLine) '\n' else ' ')
                    }
                    lines.append(words[i])
                }
                text = lines.toString()
            }
            val styled: SpannableString = SpannableString(text)
            var start: Int = 0
            for (line in text.split("\n".toRegex()).toTypedArray()) {
                if (!line.isEmpty()) {
                    val scale: Float = if (equal) kotlin.math.max(
                        0.65f,
                        kotlin.math.min(1.45f, 9f / line.length)
                    ) else
                        0.83f + random.nextFloat() * 0.37f
                    styled.setSpan(RelativeSizeSpan(scale), start, start + line.length, 0)
                }
                start += line.length + 1
            }
            return styled
        }

    }
}
