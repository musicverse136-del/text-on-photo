package com.textswag.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import java.util.ArrayList
import java.util.Collections

internal class EditorCanvas(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val board: RectF = RectF()
    private val bounds: RectF = RectF()
    private val layers: MutableList<Layer> = ArrayList<Layer>()
    private var background: Bitmap? = null
    val backgroundEdits: BackgroundEdits = BackgroundEdits()
    private var paper: Int = Color.WHITE
    private var selected: Int = -1
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private val gestures: GestureDetector
    private var editText: Runnable? = null
    private var selectionChanged: Runnable? = null
    private var historyChanged: Runnable? = null
    private val undo: java.util.ArrayDeque<Snapshot> = java.util.ArrayDeque<Snapshot>()
    private val redo: java.util.ArrayDeque<Snapshot> = java.util.ArrayDeque<Snapshot>()
    private var autoSelect: Boolean = true
    private var moved: Boolean = false
    private val stickerHandles: Array<Drawable?> = kotlin.arrayOfNulls<Drawable>(4)
    private val handlePoints: FloatArray = FloatArray(8)
    private val density: Float
    private var activeHandle: Int = -1
    private var initialDistance: Float = 0f
    private var initialAngle: Float = 0f
    private var initialScale: Float = 0f
    private var initialRotation: Float = 0f
    private var deleteLayer: java.util.function.Consumer<Layer>? = null

    init {
        density = getResources().getDisplayMetrics().density
        stickerHandles[0] = context.getDrawable(R.drawable.sticker_handle_delete)
        stickerHandles[1] = context.getDrawable(R.drawable.sticker_handle_flip)
        stickerHandles[2] = context.getDrawable(R.drawable.sticker_handle_front)
        stickerHandles[3] = context.getDrawable(R.drawable.sticker_handle_transform)
        setClickable(true)
        gestures = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (selected < 0 || editText == null) return false
                val layer: Layer = layers.get(selected)
                if (layer.asset != null || layer.hidden || layer.locked) return false
                post(editText)
                return true
            }
        })
    }

    fun setTextEditAction(action: Runnable?) {
        editText = action
    }

    fun setDeleteLayerAction(action: java.util.function.Consumer<Layer>?) {
        deleteLayer = action
    }

    fun setSelectionChangedAction(action: Runnable?) {
        selectionChanged = action
    }

    fun setHistoryChangedAction(action: Runnable?) {
        historyChanged = action
    }

    fun setAutoSelect(enabled: Boolean) {
        autoSelect = enabled
    }

    fun canUndo(): Boolean {
        return !undo.isEmpty()
    }

    fun canRedo(): Boolean {
        return !redo.isEmpty()
    }

    fun recordChange() {
        undo.addLast(Snapshot(layers, selected))
        if (undo.size > 16) undo.removeFirst()
        redo.clear()
        notifyHistory()
    }

    fun clearHistory() {
        undo.clear()
        redo.clear()
        notifyHistory()
    }

    fun undo() {
        restore(undo, redo)
    }

    fun redo() {
        restore(redo, undo)
    }

    private fun restore(
        source: java.util.ArrayDeque<Snapshot>,
        destination: java.util.ArrayDeque<Snapshot>
    ) {
        if (source.isEmpty()) return
        destination.addLast(Snapshot(layers, selected))
        val snapshot: Snapshot = source.removeLast()
        layers.clear()
        layers.addAll(snapshot.layers)
        selected = snapshot.selected
        invalidate()
        selectionChanged?.run()
        notifyHistory()
    }

    private fun notifyHistory() {
        historyChanged?.run()
    }

    fun setPaper(color: Int, image: Bitmap?) {
        paper = color
        background = image
        backgroundEdits.reset()
        invalidate()
    }

    fun hasBackgroundImage(): Boolean {
        return background != null
    }

    fun layers(): MutableList<Layer> {
        return Collections.unmodifiableList<Layer?>(layers)
    }

    fun selectedIndex(): Int {
        return selected
    }

    fun add(layer: Layer) {
        recordChange()
        layers.add(layer)
        select(layers.size - 1)
    }

    fun select(index: Int) {
        val changed: Boolean = selected != index
        selected = index
        invalidate()
        if (changed) selectionChanged?.run()
    }

    fun remove(index: Int) {
        recordChange()
        layers.removeAt(index)
        select(-1)
    }

    fun reorder(index: Int, offset: Int) {
        val target: Int = index + offset
        if (target < 0 || target >= layers.size || target == index) return
        recordChange()
        layers.add(target, layers.removeAt(index))
        select(target)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val margin: Float = 12 * getResources().getDisplayMetrics().density
        val side: Float = kotlin.math.min(getHeight(), getWidth()) - margin * 2
        if (side <= 0) return
        board.set(
            (getWidth() - side) / 2, (getHeight() - side) / 2,
            (getWidth() + side) / 2, (getHeight() + side) / 2
        )
        canvas.save()
        canvas.translate(board.left, board.top)
        canvas.scale(side / EditorCanvas.SIZE, side / EditorCanvas.SIZE)
        canvas.clipRect(0, 0, EditorCanvas.SIZE, EditorCanvas.SIZE)
        if (background == null && paper == Color.TRANSPARENT) {
            paint.setColor(-0x2e3624)
            canvas.drawRect(0f, 0f, EditorCanvas.SIZE.toFloat(), EditorCanvas.SIZE.toFloat(), paint)
        }
        drawDesign(canvas)
        canvas.restore()
        if (positionStickerHandles()) drawStickerHandles(canvas)
    }

    private fun positionStickerHandles(): Boolean {
        if (selected < 0 || board.isEmpty()) return false
        val layer: Layer = layers.get(selected)
        if (layer.hidden || layer.locked) return false
        layer.getBounds(bounds)
        val screenScale: Float = board.width() / EditorCanvas.SIZE
        val halfWidth: Float = kotlin.math.max(
            26 * density,
            bounds.width() * layer.scale * screenScale / 2 + 14 * density
        )
        val halfHeight: Float = kotlin.math.max(
            26 * density,
            bounds.height() * layer.scale * screenScale / 2 + 14 * density
        )
        val centerX: Float = board.left + layer.x * screenScale
        val centerY: Float = board.top + layer.y * screenScale
        val angle: Double = java.lang.Math.toRadians(layer.rotation.toDouble())
        val cosine: Float = kotlin.math.cos(angle).toFloat()
        val sine: Float = kotlin.math.sin(angle).toFloat()
        for (i in stickerHandles.indices) {
            val x: Float = if ((i and 1) == 0) -halfWidth else halfWidth
            val y: Float = if (i < 2) -halfHeight else halfHeight
            handlePoints[i * 2] = centerX + x * cosine - y * sine
            handlePoints[i * 2 + 1] = centerY + x * sine + y * cosine
        }
        return true
    }

    private fun drawStickerHandles(canvas: Canvas) {
        val radius: Int = java.lang.Math.round(12 * density)
        for (i in stickerHandles.indices) {
            val x: Int = java.lang.Math.round(handlePoints[i * 2])
            val y: Int = java.lang.Math.round(handlePoints[i * 2 + 1])
            val icon = stickerHandles[i] ?: continue
            icon.setBounds(x - radius, y - radius, x + radius, y + radius)
            icon.draw(canvas)
        }
    }

    private fun findStickerHandle(x: Float, y: Float): Int {
        if (!positionStickerHandles()) return -1
        for (i in stickerHandles.indices) {
            if (kotlin.math.hypot(
                    (x - handlePoints[i * 2]).toDouble(),
                    (y - handlePoints[i * 2 + 1]).toDouble()
                ) <= 17 * density
            ) return i
        }
        return -1
    }

    private fun handleStickerTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            activeHandle = findStickerHandle(event.getX(), event.getY())
            if (activeHandle < 0) return false
            val layer: Layer = layers.get(selected)
            initialDistance = kotlin.math.max(
                1f,
                kotlin.math.hypot((x - layer.x).toDouble(), (y - layer.y).toDouble()).toFloat()
            )
            initialAngle = java.lang.Math.toDegrees(
                kotlin.math.atan2(
                    (y - layer.y).toDouble(),
                    (x - layer.x).toDouble()
                )
            ).toFloat()
            initialScale = layer.scale
            initialRotation = layer.rotation
            moved = false
            return true
        }
        if (activeHandle < 0 || selected < 0) return false
        val layer: Layer = layers.get(selected)
        when (event.getActionMasked()) {
            MotionEvent.ACTION_MOVE -> if (activeHandle == 3 && event.getPointerCount() == 1) {
                if (!moved) {
                    recordChange()
                    moved = true
                }
                val distance: Float =
                    kotlin.math.hypot((x - layer.x).toDouble(), (y - layer.y).toDouble()).toFloat()
                layer.scale = kotlin.math.max(
                    0.2f,
                    kotlin.math.min(5f, initialScale * distance / initialDistance)
                )
                layer.rotation = initialRotation + java.lang.Math.toDegrees(
                    kotlin.math.atan2(
                        (y - layer.y).toDouble(),
                        (x - layer.x).toDouble()
                    )
                ).toFloat() - initialAngle
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                if (activeHandle == findStickerHandle(event.getX(), event.getY())) {
                    if (activeHandle == 0) deleteLayer?.accept(layer)
                    if (activeHandle == 1) {
                        recordChange()
                        layer.flipped = !layer.flipped
                        invalidate()
                    }
                    if (activeHandle == 2) reorder(selected, layers.size - 1 - selected)
                }
                activeHandle = -1
            }

            MotionEvent.ACTION_CANCEL -> activeHandle = -1
        }
        return true
    }

    fun render(): Bitmap {
        val output: Bitmap =
            Bitmap.createBitmap(EditorCanvas.SIZE, EditorCanvas.SIZE, Bitmap.Config.ARGB_8888)
        drawDesign(Canvas(output))
        return output
    }

    private fun drawDesign(canvas: Canvas) {
        backgroundEdits.draw(canvas, background, paper)
        for (layer in layers) {
            if (layer.hidden) continue
            layer.getBounds(bounds)
            canvas.save()
            canvas.rotate(layer.rotation, layer.x, layer.y)
            canvas.scale(
                if (layer.flipped) -layer.scale else layer.scale,
                layer.scale,
                layer.x,
                layer.y
            )
            val opacity: Int = layer.opacity
            if (opacity < 255) canvas.saveLayerAlpha(null, opacity)
            if (layer.image != null) {
                paint.setColor(Color.WHITE)
                canvas.drawBitmap(requireNotNull(layer.image), null, bounds, paint)
            } else {
                layer.effects.draw(canvas, bounds, layer.textLayout(), layer.text, layer.color)
            }
            if (opacity < 255) canvas.restore()
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (board.isEmpty()) return false
        val x: Float = (event.getX() - board.left) * EditorCanvas.SIZE / board.width()
        val y: Float = (event.getY() - board.top) * EditorCanvas.SIZE / board.height()
        if (handleStickerTouch(event, x, y)) return true
        gestures.onTouchEvent(event)
        when (event.getActionMasked()) {
            MotionEvent.ACTION_DOWN -> {
                moved = false
                var hit: Int = -1
                var i: Int = layers.size - 1
                while (i >= 0) {
                    val layer: Layer = layers.get(i)
                    if (layer.hidden || layer.locked) {
                        i--
                        continue
                    }
                    if (layer.contains(x, y, bounds)) {
                        hit = i
                        break
                    }
                    i--
                }
                if (autoSelect) {
                    select(hit)
                } else if (selected >= 0) {
                    if (!layers.get(selected).contains(x, y, bounds)) {
                        lastX = Float.Companion.NaN
                        return true
                    }
                }
                lastX = x
                lastY = y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (java.lang.Float.isNaN(lastX)) return true
                if (selected >= 0 && event.getPointerCount() == 1 && (x != lastX || y != lastY)) {
                    val layer: Layer = layers.get(selected)
                    if (layer.hidden || layer.locked) return true
                    if (!moved) {
                        recordChange()
                        moved = true
                    }
                    layer.x = kotlin.math.max(
                        0f,
                        kotlin.math.min(EditorCanvas.SIZE.toFloat(), layer.x + x - lastX)
                    )
                    layer.y = kotlin.math.max(
                        0f,
                        kotlin.math.min(EditorCanvas.SIZE.toFloat(), layer.y + y - lastY)
                    )
                    invalidate()
                }
                lastX = x
                lastY = y
                return true
            }

            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }

            else -> return true
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private class Snapshot(source: MutableList<Layer>, selected: Int) {
        val layers: MutableList<Layer> = ArrayList<Layer>()
        val selected: Int

        init {
            for (layer in source) layers.add(layer.copy())
            this.selected = selected
        }
    }

    internal class Layer {
        var text: String = ""
        var font: String? = ""
        var asset: String? = null
        var image: Bitmap? = null
        var color: Int = Color.BLACK
        var size: Int = 96
        var styleIndex: Int = -1
        var styleVariant: Int = 1
        var styleSpacing: Int = 0
        var styleInvert: Boolean = false
        var hidden: Boolean = false
        var locked: Boolean = false
        var opacity: Int = 255
        var scale: Float = 1f
        var rotation: Float = 0f
        var flipped: Boolean = false
        val effects: NormalTextEffects = NormalTextEffects()
        var x: Float = EditorCanvas.SIZE / 2f
        var y: Float = EditorCanvas.SIZE / 2f
        private var typeface: Typeface? = Typeface.DEFAULT
        private var layout: StaticLayout? = null

        fun copy(): Layer {
            val copy: Layer = Layer()
            copy.text = text
            copy.font = font
            copy.asset = asset
            copy.image = image
            copy.color = color
            copy.size = size
            copy.styleIndex = styleIndex
            copy.styleVariant = styleVariant
            copy.styleSpacing = styleSpacing
            copy.styleInvert = styleInvert
            copy.hidden = hidden
            copy.locked = locked
            copy.opacity = opacity
            copy.scale = scale
            copy.rotation = rotation
            copy.flipped = flipped
            copy.effects.restore(effects.save())
            copy.effects.texture = effects.texture
            copy.effects.gradientImage = effects.gradientImage
            copy.effects.backgroundGradient = effects.backgroundGradient
            copy.x = x
            copy.y = y
            copy.typeface = typeface
            copy.layout = layout
            return copy
        }

        fun applyText(value: String, fontPath: String?, face: Typeface?, ink: Int, scale: Int) {
            text = value
            font = fontPath
            typeface = face
            color = ink
            size = scale
            layout = null
        }

        fun textLayout(): StaticLayout {
            if (layout == null) {
                val ink: TextPaint = effects.paint(typeface, size, color)
                val content: CharSequence = effects.spaced(effects.colorWord(text))
                var width: Int = kotlin.math.min(
                    980,
                    kotlin.math.max(
                        12,
                        kotlin.math.ceil(Layout.getDesiredWidth(content, ink).toDouble())
                            .toInt() + 4
                    )
                )
                if (effects.width != 0 && effects.curve == 0) width = effects.width
                layout = StaticLayout.Builder.obtain(content, 0, content.length, ink, width)
                    .setAlignment(effects.alignment())
                    .setLineSpacing(effects.lineSpacing.toFloat(), 1f).build()
            }
            return requireNotNull(layout)
        }

        fun invalidateText() {
            layout = null
        }

        fun getBounds(rect: RectF) {
            if (image == null && effects.curve != 0) {
                effects.curvedBounds(text, textLayout().getPaint(), rect)
                rect.inset(
                    -kotlin.math.max(0f, effects.width - rect.width()) / 2f,
                    -kotlin.math.max(0f, effects.height - rect.height()) / 2f
                )
                rect.offset(x - rect.centerX(), y - rect.centerY())
                return
            }
            val width: Float = (if (image == null) textLayout().getWidth() else size).toFloat()
            val height: Float =
                if (image == null) kotlin.math.max(effects.height, textLayout().getHeight())
                    .toFloat() else
                    size * requireNotNull(image).height / requireNotNull(image).width.toFloat()
            rect.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
        }

        fun contains(touchX: Float, touchY: Float, scratch: RectF): Boolean {
            getBounds(scratch)
            val dx: Float = (touchX - x) / scale
            val dy: Float = (touchY - y) / scale
            val angle: Double = java.lang.Math.toRadians(rotation.toDouble())
            return scratch.contains(
                x + (dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)).toFloat(),
                y + (dy * kotlin.math.cos(angle) - dx * kotlin.math.sin(angle)).toFloat()
            )
        }

        fun label(): String {
            if (asset == null) return text.replace('\n', ' ')
            if (requireNotNull(asset).startsWith("content:")) return "Photo"
            if (requireNotNull(asset).startsWith("drawing:")) return "Hand Draw"
            return DesignAssets.label(requireNotNull(asset))
        }

        fun save(): Bundle {
            val data: Bundle = Bundle()
            data.putString("text", text)
            data.putString("font", font)
            data.putString("asset", asset)
            data.putInt("color", color)
            data.putInt("size", size)
            data.putInt("styleIndex", styleIndex)
            data.putInt("styleVariant", styleVariant)
            data.putInt("styleSpacing", styleSpacing)
            data.putBoolean("styleInvert", styleInvert)
            data.putBoolean("hidden", hidden)
            data.putBoolean("locked", locked)
            data.putInt("opacity", opacity)
            data.putFloat("scale", scale)
            data.putFloat("rotation", rotation)
            data.putBoolean("flipped", flipped)
            data.putBundle("textEffects", effects.save())
            data.putFloat("x", x)
            data.putFloat("y", y)
            return data
        }
    }

    companion object {
        const val SIZE: Int = 1080
    }
}
