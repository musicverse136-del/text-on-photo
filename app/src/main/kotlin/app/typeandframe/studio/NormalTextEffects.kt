package app.typeandframe.studio

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Shader.TileMode
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import java.util.regex.Matcher
import kotlin.math.withSign

internal class NormalTextEffects {
    var format: Int = 0
    var alignment: Int = 1
    var shadow: Int = 0
    var shadowX: Int = 0
    var shadowY: Int = 0
    var shadowColor: Int = -0x67000000
    var neon: Int = 0
    var neonColor: Int = Color.WHITE
    var width: Int = 0
    var height: Int = 0
    var lineSpacing: Int = 0
    var letterSpacing: Int = 0
    var border: Int = 0
    var borderColor: Int = Color.WHITE
    var gradient: Int = 0
    var background: Int = 0
    var backgroundOpacity: Int = 255
    var backgroundBorder: Int = 0
    var backgroundPadding: Int = 12
    var backgroundRadius: Int = 10
    var backgroundShadow: Int = 0
    var gradientPath: String? = null
    var gradientImage: Bitmap? = null
    var backgroundGradientPath: String? = null
    var backgroundGradient: Bitmap? = null
    var gradientStart: Int = 0
    var backgroundGradientStart: Int = 0
    var backgroundGradientEnd: Int = 0
    var curve: Int = 0
    var wordIndex: Int = -1
    var wordColor: Int = 0
    var selectionColors: IntArray? = null
    var paletteColors: IntArray? = null
    var underline: Boolean = false
    var strike: Boolean = false
    var texturePath: String? = null
    var texture: Bitmap? = null
    var textureRotation: Int = 0
    var textureVertical: Int = 0
    var textureHorizontal: Int = 0
    var textureX: Int = 0
    var textureY: Int = 0

    fun alignment(): Layout.Alignment {
        if (alignment == 0) return Layout.Alignment.ALIGN_NORMAL
        if (alignment == 2) return Layout.Alignment.ALIGN_OPPOSITE
        return Layout.Alignment.ALIGN_CENTER
    }

    fun clearGradient() {
        gradient = 0
        gradientStart = 0
        gradientPath = null
        gradientImage = null
    }

    fun spaced(value: CharSequence): CharSequence {
        if (letterSpacing == 0 || value.length < 2) return value
        val text: SpannableStringBuilder = SpannableStringBuilder(value)
        val characters: java.text.BreakIterator = java.text.BreakIterator.getCharacterInstance()
        characters.setText(value.toString())
        characters.last()
        val padding: String =
            kotlin.text.String(CharArray(letterSpacing)).replace('\u0000', '\u200A')
        var boundary: Int = characters.previous()
        while (boundary != java.text.BreakIterator.DONE
        ) {
            if (boundary > 0 && !java.lang.Character.isWhitespace(value.get(boundary - 1)) && !java.lang.Character.isWhitespace(
                    value.get(boundary)
                )
            ) text.insert(boundary, padding)
            boundary = characters.previous()
        }
        return text
    }

    fun colorWord(value: String): CharSequence {
        if (curve != 0 || (selectionColors == null && paletteColors == null && wordIndex < 0)) return value
        val colored: SpannableString = SpannableString(value)
        if (paletteColors != null && requireNotNull(paletteColors).size > 0) {
            val characters: java.text.BreakIterator = java.text.BreakIterator.getCharacterInstance()
            characters.setText(value)
            var index: Int = 0
            var start: Int = characters.first()
            var end: Int = characters.next()
            while (end != java.text.BreakIterator.DONE) {
                val ink: Int =
                    requireNotNull(paletteColors)[index++ % requireNotNull(paletteColors).size]
                colored.setSpan(ForegroundColorSpan(ink), start, end, 0)
                start = end
                end = characters.next()
            }
        }
        val words: Matcher = NormalTextEffects.WORD.matcher(value)
        var index: Int = 0
        while (words.find()) {
            if (index == wordIndex) {
                colored.setSpan(ForegroundColorSpan(wordColor), words.start(), words.end(), 0)
                break
            }
            index++
        }
        if (selectionColors != null) {
            val limit: Int = kotlin.math.min(value.length, requireNotNull(selectionColors).size)
            var start: Int = 0
            while (start < limit) {
                val ink: Int = requireNotNull(selectionColors)[start]
                var end: Int = start + 1
                while (end < limit && requireNotNull(selectionColors)[end] == ink) end++
                if (ink != 0) colored.setSpan(ForegroundColorSpan(ink), start, end, 0)
                start = end
            }
        }
        return colored
    }

    fun clearWordColors() {
        wordIndex = -1
        selectionColors = null
        paletteColors = null
    }

    fun paint(font: Typeface?, size: Int, color: Int): TextPaint {
        val ink: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        ink.setTypeface(if (format == 0) font else Typeface.create(font, format))
        ink.setTextSize(size.toFloat())
        ink.setColor(color)
        ink.setUnderlineText(underline)
        ink.setStrikeThruText(strike)
        return ink
    }

    fun draw(canvas: Canvas, bounds: RectF, layout: StaticLayout, value: String, color: Int) {
        val ink: TextPaint = layout.getPaint()
        val fill: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (background != Color.TRANSPARENT || backgroundGradient != null || backgroundGradientEnd != 0 || backgroundBorder > 0) {
            val rect: RectF = RectF(bounds)
            rect.inset(-backgroundPadding.toFloat(), -backgroundPadding.toFloat())
            if (backgroundShadow > 0) fill.setShadowLayer(
                backgroundShadow.toFloat(),
                3f,
                4f,
                -0x67000000
            )
            fill.setColor(background)
            if (backgroundGradient != null || backgroundGradientEnd != 0) {
                fill.setColor(Color.WHITE)
                fill.setShader(
                    if (backgroundGradient != null)
                        fitImage(requireNotNull(backgroundGradient), rect, false)
                    else
                        LinearGradient(
                            rect.left, rect.top, rect.right, rect.bottom,
                            backgroundGradientStart, backgroundGradientEnd, Shader.TileMode.CLAMP
                        )
                )
            }
            fill.setAlpha(Color.alpha(fill.getColor()) * backgroundOpacity / 255)
            canvas.drawRoundRect(rect, backgroundRadius.toFloat(), backgroundRadius.toFloat(), fill)
            if (backgroundBorder > 0) {
                fill.setShader(null)
                fill.clearShadowLayer()
                fill.setColor(Color.BLACK)
                fill.setAlpha(backgroundOpacity)
                fill.setStyle(Paint.Style.STROKE)
                fill.setStrokeWidth(backgroundBorder.toFloat())
                canvas.drawRoundRect(
                    rect,
                    backgroundRadius.toFloat(),
                    backgroundRadius.toFloat(),
                    fill
                )
            }
        }
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        val singleLine: String = spaced(value.replace('\n', ' ')).toString()
        val arc: android.graphics.Path? = if (curve == 0) null else curvePath(singleLine, ink)
        val originalSize: Float = ink.getTextSize()
        if (arc == null) canvas.translate(0f, (bounds.height() - layout.getHeight()) / 2f)
        if (arc != null) {
            val pathBounds: RectF = RectF()
            arc.computeBounds(pathBounds, true)
            arc.offset(
                bounds.width() / 2f - pathBounds.centerX(),
                bounds.height() / 2f - pathBounds.centerY() - (ink.ascent() + ink.descent()) / 2f
            )
            ink.setTextSize(curvedTextSize(singleLine, ink))
        }
        if (Color.alpha(shadowColor) > 0 && (shadow > 0 || shadowX != 0 || shadowY != 0)) {
            ink.setShadowLayer(
                kotlin.math.max(0.1f, shadow.toFloat()),
                shadowX.toFloat(),
                shadowY.toFloat(),
                shadowColor
            )
            drawText(canvas, layout, arc, singleLine)
            ink.clearShadowLayer()
        }
        if (neon > 0 && Color.alpha(neonColor) > 0) ink.setShadowLayer(
            neon.toFloat(),
            0f,
            0f,
            neonColor
        )
        if (border > 0) {
            ink.setStyle(Paint.Style.STROKE)
            ink.setStrokeJoin(Paint.Join.ROUND)
            ink.setStrokeWidth(border.toFloat())
            ink.setColor(borderColor)
            drawText(canvas, layout, arc, singleLine)
            ink.setStyle(Paint.Style.FILL)
        }
        ink.setColor(color)
        if (texture != null) {
            ink.setShader(
                fitImage(
                    requireNotNull(texture),
                    RectF(0f, 0f, bounds.width(), bounds.height()),
                    true
                )
            )
        } else if (gradientImage != null) {
            ink.setShader(
                fitImage(
                    requireNotNull(gradientImage),
                    RectF(0f, 0f, bounds.width(), bounds.height()),
                    false
                )
            )
        } else if (gradient != Color.TRANSPARENT) {
            ink.setShader(
                LinearGradient(
                    0f, 0f, bounds.width(), bounds.height(),
                    if (gradientStart == 0) color else gradientStart, gradient,
                    Shader.TileMode.CLAMP
                )
            )
        }
        drawText(canvas, layout, arc, singleLine)
        ink.setShader(null)
        ink.clearShadowLayer()
        ink.setTextSize(originalSize)
        canvas.restore()
    }

    private fun fitImage(image: Bitmap, bounds: RectF, transform: Boolean): Shader {
        val mode: TileMode = if (transform) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
        val shader: BitmapShader = BitmapShader(image, mode, mode)
        val matrix: android.graphics.Matrix = android.graphics.Matrix()
        matrix.setScale(bounds.width() / image.getWidth(), bounds.height() / image.getHeight())
        matrix.postTranslate(bounds.left, bounds.top)
        if (transform) {
            matrix.postScale(
                1 + textureHorizontal / 50f, 1 + textureVertical / 50f,
                bounds.centerX(), bounds.centerY()
            )
            matrix.postRotate(textureRotation * 3.6f, bounds.centerX(), bounds.centerY())
            matrix.postTranslate(
                bounds.width() * textureX / 100f,
                bounds.height() * textureY / 100f
            )
        }
        shader.setLocalMatrix(matrix)
        return shader
    }

    fun curvedBounds(value: String, ink: TextPaint, output: RectF) {
        val singleLine: String = spaced(value.replace('\n', ' ')).toString()
        curvePath(singleLine, ink).computeBounds(output, true)
        val pad: Float = curvedTextSize(singleLine, ink) * 0.6f
        output.inset(-pad, -pad)
    }

    private fun curvedTextSize(text: String?, ink: TextPaint): Float {
        val availableWidth: Int = if (width == 0) 930 else width
        return ink.getTextSize() * kotlin.math.min(
            1f,
            availableWidth / kotlin.math.max(1f, ink.measureText(text))
        )
    }

    private fun curvePath(text: String?, ink: TextPaint): android.graphics.Path {
        val sweep: Float =
            kotlin.math.min(359.9f, kotlin.math.abs(curve * 3.6f)).withSign(curve.toFloat())
        val width: Float = ink.measureText(text) * curvedTextSize(text, ink) / ink.getTextSize()
        val radius: Float = (kotlin.math.max(1f, width) / java.lang.Math.toRadians(
            kotlin.math.abs(sweep).toDouble()
        )).toFloat()
        val path: android.graphics.Path = android.graphics.Path()
        path.addArc(
            -radius,
            -radius,
            radius,
            radius,
            (if (sweep > 0) 270f else 90f) - sweep / 2f,
            sweep
        )
        return path
    }

    private fun drawText(
        canvas: Canvas,
        layout: StaticLayout,
        arc: android.graphics.Path?,
        text: String
    ) {
        if (arc == null) layout.draw(canvas)
        else canvas.drawTextOnPath(text, arc, 0f, 0f, layout.getPaint())
    }

    fun save(): Bundle {
        val state: Bundle = Bundle()
        state.putInt("format", format)
        state.putInt("alignment", alignment)
        state.putInt("shadow", shadow)
        state.putInt("shadowColor", shadowColor)
        state.putInt("shadowX", shadowX)
        state.putInt("shadowY", shadowY)
        state.putInt("neon", neon)
        state.putInt("neonColor", neonColor)
        state.putInt("width", width)
        state.putInt("height", height)
        state.putInt("lineSpacing", lineSpacing)
        state.putInt("letterSpacing", letterSpacing)
        state.putInt("border", border)
        state.putInt("borderColor", borderColor)
        state.putInt("gradient", gradient)
        state.putInt("background", background)
        state.putInt("backgroundOpacity", backgroundOpacity)
        state.putInt("backgroundBorder", backgroundBorder)
        state.putInt("backgroundPadding", backgroundPadding)
        state.putInt("backgroundRadius", backgroundRadius)
        state.putInt("backgroundShadow", backgroundShadow)
        state.putString("gradientPath", gradientPath)
        state.putString("backgroundGradientPath", backgroundGradientPath)
        state.putInt("gradientStart", gradientStart)
        state.putInt("backgroundGradientStart", backgroundGradientStart)
        state.putInt("backgroundGradientEnd", backgroundGradientEnd)
        state.putInt("textureRotation", textureRotation)
        state.putInt("textureVertical", textureVertical)
        state.putInt("textureHorizontal", textureHorizontal)
        state.putInt("textureX", textureX)
        state.putInt("textureY", textureY)
        state.putInt("curve", curve)
        state.putInt("wordIndex", wordIndex)
        state.putInt("wordColor", wordColor)
        state.putIntArray("selectionColors", selectionColors?.clone())
        state.putIntArray("paletteColors", paletteColors)
        state.putBoolean("underline", underline)
        state.putBoolean("strike", strike)
        state.putString("texture", texturePath)
        return state
    }

    fun restore(state: Bundle?) {
        if (state == null) return
        format = state.getInt("format")
        alignment = state.getInt("alignment", 1)
        shadow = state.getInt("shadow")
        shadowColor = state.getInt("shadowColor", -0x67000000)
        shadowX = state.getInt("shadowX", if (shadow > 0) 4 else 0)
        shadowY = state.getInt("shadowY", if (shadow > 0) 5 else 0)
        neon = state.getInt("neon")
        neonColor = state.getInt("neonColor", Color.WHITE)
        width = state.getInt("width")
        height = state.getInt("height")
        lineSpacing = state.getInt("lineSpacing")
        letterSpacing = state.getInt("letterSpacing")
        border = state.getInt("border")
        borderColor = state.getInt("borderColor", Color.WHITE)
        gradient = state.getInt("gradient")
        background = state.getInt("background")
        backgroundOpacity = state.getInt("backgroundOpacity", 255)
        backgroundBorder = state.getInt("backgroundBorder")
        backgroundPadding = state.getInt("backgroundPadding", 12)
        backgroundRadius = state.getInt("backgroundRadius", 10)
        backgroundShadow = state.getInt("backgroundShadow")
        gradientPath = state.getString("gradientPath")
        backgroundGradientPath = state.getString("backgroundGradientPath")
        gradientStart = state.getInt("gradientStart")
        backgroundGradientStart = state.getInt("backgroundGradientStart")
        backgroundGradientEnd = state.getInt("backgroundGradientEnd")
        textureRotation = state.getInt("textureRotation")
        textureVertical = state.getInt("textureVertical")
        textureHorizontal = state.getInt("textureHorizontal")
        textureX = state.getInt("textureX")
        textureY = state.getInt("textureY")
        curve = state.getInt("curve")
        wordIndex = state.getInt("wordIndex", -1)
        wordColor = state.getInt("wordColor")
        selectionColors = state.getIntArray("selectionColors")
        paletteColors = state.getIntArray("paletteColors")
        underline = state.getBoolean("underline")
        strike = state.getBoolean("strike")
        texturePath = state.getString("texture")
    }

    companion object {
        private val WORD: java.util.regex.Pattern = java.util.regex.Pattern.compile("\\S+")
    }
}
