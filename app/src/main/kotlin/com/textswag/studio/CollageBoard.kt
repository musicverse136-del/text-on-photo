package com.textswag.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.Shader.TileMode
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import java.io.IOException
import java.util.ArrayList

internal class CollageBoard(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    internal class Photo(uri: Uri?, image: Bitmap) {
        val uri: Uri?
        val image: Bitmap
        var angle: Int = 0
        var filter: Int = 0
        var toneCurve: String? = null
        var glitch: String? = null
        var glitchAmount: Int = 70
        var toned: Bitmap? = null
        var flipX: Boolean = false
        var flipY: Boolean = false
        var zoom: Float = 1f
        var x: Float = 0f
        var y: Float = 0f

        init {
            this.uri = uri
            this.image = image
        }

        fun save(): Bundle {
            val state: Bundle = Bundle()
            state.putParcelable("uri", uri)
            state.putInt("angle", angle)
            state.putInt("filter", filter)
            state.putString("toneCurve", toneCurve)
            state.putString("glitch", glitch)
            state.putInt("glitchAmount", glitchAmount)
            state.putBoolean("xFlip", flipX)
            state.putBoolean("yFlip", flipY)
            state.putFloat("zoom", zoom)
            state.putFloat("x", x)
            state.putFloat("y", y)
            return state
        }

        companion object {
            @Throws(IOException::class)
            fun load(context: Context, assets: DesignAssets, state: Bundle): CollageBoard.Photo {
                val uri: Uri? = state.getParcelable<Uri?>("uri")
                val photo: CollageBoard.Photo =
                    CollageBoard.Photo(requireNotNull(uri), assets.photo(uri, 1400))
                photo.angle = state.getInt("angle")
                photo.filter = state.getInt("filter")
                photo.toneCurve = state.getString("toneCurve")
                photo.glitch = state.getString("glitch")
                photo.glitchAmount = state.getInt("glitchAmount", 70)
                if (photo.toneCurve != null) {
                    try {
                        photo.toned = CollageToneCurve.apply(
                            context,
                            photo.image,
                            requireNotNull(photo.toneCurve)
                        )
                    } catch (ignored: IOException) {
                        photo.toneCurve = null
                    }
                }
                photo.flipX = state.getBoolean("xFlip")
                photo.flipY = state.getBoolean("yFlip")
                photo.zoom = state.getFloat("zoom", 1f)
                photo.x = state.getFloat("x")
                photo.y = state.getFloat("y")
                return photo
            }
        }
    }

    val photos: ArrayList<CollageBoard.Photo> = ArrayList<CollageBoard.Photo>()
    val board: RectF = RectF()
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pinch: ScaleGestureDetector
    private var pieces: MutableList<android.graphics.Path> = ArrayList<android.graphics.Path>()
    private val hitRegions: MutableList<Region> = ArrayList<Region>()
    var padding: Int = 0
    var spacing: Int = 0
    var radius: Int = 0
    var paper: Int = Color.WHITE
    var gradientEnd: Int = 0
    var background: Bitmap? = null
    var aspect: Float = 1f
    var selected: Int = -1
    var selectionChanged: Runnable? = null
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var touchPiece: Int = -1
    private var dragged: Boolean = false

    init {
        pinch = ScaleGestureDetector(context, object : SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (touchPiece < 0) return false
                val photo: CollageBoard.Photo = photos.get(touchPiece)
                photo.zoom =
                    kotlin.math.max(1f, kotlin.math.min(4f, photo.zoom * detector.getScaleFactor()))
                invalidate()
                return true
            }
        })
        setClickable(true)
    }

    fun setLayoutPieces(pieces: MutableList<android.graphics.Path>) {
        this.pieces = pieces
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val margin: Float = getResources().getDisplayMetrics().density * 8
        val width: Float =
            kotlin.math.min(getWidth() - margin * 2, (getHeight() - margin * 2) * aspect)
        if (width <= 0) return
        val height: Float = width / aspect
        board.set(
            (getWidth() - width) / 2, (getHeight() - height) / 2,
            (getWidth() + width) / 2, (getHeight() + height) / 2
        )
        drawComposition(canvas, board, true)
    }

    fun render(width: Int, height: Int, jpeg: Boolean): Bitmap {
        val output: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(output)
        if (jpeg) canvas.drawColor(Color.WHITE)
        drawComposition(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), false)
        return output
    }

    private fun drawComposition(canvas: Canvas, rect: RectF, preview: Boolean) {
        canvas.save()
        canvas.clipRect(rect)
        paint.setColor(paper)
        if (gradientEnd != 0) {
            paint.setShader(
                LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    paper, gradientEnd, Shader.TileMode.CLAMP
                )
            )
        }
        canvas.drawRect(rect, paint)
        paint.setShader(null)
        if (background != null) {
            paint.setColor(Color.WHITE)
            drawCover(canvas, requireNotNull(background), rect, null)
        }
        if (preview) hitRegions.clear()
        val unit: Float = kotlin.math.min(rect.width(), rect.height()) / 360f
        val scale: android.graphics.Matrix = android.graphics.Matrix()
        scale.setScale(rect.width() - padding * unit * 2, rect.height() - padding * unit * 2)
        scale.postTranslate(rect.left + padding * unit, rect.top + padding * unit)
        for (i in 0..<kotlin.math.min(pieces.size, photos.size)) {
            var shape: android.graphics.Path = android.graphics.Path()
            pieces.get(i).transform(scale, shape)
            if (spacing > 0) {
                val stroke: Paint = Paint()
                stroke.setStyle(Paint.Style.STROKE)
                stroke.setStrokeWidth(spacing * unit)
                val edge: android.graphics.Path = android.graphics.Path()
                stroke.getFillPath(shape, edge)
                shape.op(edge, android.graphics.Path.Op.DIFFERENCE)
            }
            if (radius > 0) {
                val corners: Paint = Paint()
                corners.setPathEffect(CornerPathEffect(radius * unit))
                val rounded: android.graphics.Path = android.graphics.Path()
                corners.getFillPath(shape, rounded)
                if (!rounded.isEmpty()) shape = rounded
            }
            val bounds: RectF = CollageTemplates.bounds(shape)
            if (preview) {
                val hit: Region = Region()
                hit.setPath(shape, Region(0, 0, getWidth(), getHeight()))
                hitRegions.add(hit)
            }
            paint.setColor(Color.WHITE)
            val photo: CollageBoard.Photo = photos.get(i)
            paint.setColorFilter(CollageBoard.filter(photo.filter))
            canvas.save()
            canvas.clipPath(shape)
            drawCover(canvas, photo.toned ?: photo.image, bounds, photo)
            canvas.restore()
            paint.setColorFilter(null)
            if (preview && selected == i) {
                paint.setStyle(Paint.Style.STROKE)
                paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density)
                paint.setColor(-0xbf514c)
                canvas.drawPath(shape, paint)
                paint.setStyle(Paint.Style.FILL)
            }
        }
        canvas.restore()
    }

    private fun drawCover(canvas: Canvas, image: Bitmap, rect: RectF, photo: CollageBoard.Photo?) {
        val angle: Int = if (photo == null) 0 else photo.angle
        val quarter: Boolean = angle % 180 != 0
        val width: Float = (if (quarter) image.getHeight() else image.getWidth()).toFloat()
        val height: Float = (if (quarter) image.getWidth() else image.getHeight()).toFloat()
        var scale: Float = kotlin.math.max(rect.width() / width, rect.height() / height)
        if (photo != null) scale *= photo.zoom
        val limitX: Float = kotlin.math.max(0f, (width * scale - rect.width()) / 2)
        val limitY: Float = kotlin.math.max(0f, (height * scale - rect.height()) / 2)
        val x: Float = if (photo == null) 0f else kotlin.math.max(
            -limitX,
            kotlin.math.min(limitX, photo.x * rect.width())
        )
        val y: Float = if (photo == null) 0f else kotlin.math.max(
            -limitY,
            kotlin.math.min(limitY, photo.y * rect.height())
        )
        canvas.save()
        canvas.translate(rect.centerX() + x, rect.centerY() + y)
        canvas.scale(
            (if (photo != null && photo.flipX) -1 else 1).toFloat(),
            (if (photo != null && photo.flipY) -1 else 1).toFloat()
        )
        canvas.rotate(angle.toFloat())
        canvas.scale(scale, scale)
        canvas.drawBitmap(image, -image.getWidth() / 2f, -image.getHeight() / 2f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action: Int = event.getActionMasked()
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX()
            lastY = event.getY()
            touchPiece = -1
            dragged = false
            for (i in hitRegions.indices.reversed()) {
                if (hitRegions.get(i).contains(lastX.toInt(), lastY.toInt())) {
                    touchPiece = i
                    break
                }
            }
        }
        pinch.onTouchEvent(event)
        if (action == MotionEvent.ACTION_MOVE && touchPiece >= 0) {
            if (event.getPointerCount() == 1 && !pinch.isInProgress()) {
                val dx: Float = event.getX() - lastX
                val dy: Float = event.getY() - lastY
                val photo: CollageBoard.Photo = photos.get(touchPiece)
                val bounds: RectF = CollageTemplates.bounds(pieces.get(touchPiece))
                photo.x = kotlin.math.max(
                    -2f,
                    kotlin.math.min(
                        2f,
                        photo.x + dx / kotlin.math.max(1f, bounds.width() * board.width())
                    )
                )
                photo.y = kotlin.math.max(
                    -2f,
                    kotlin.math.min(
                        2f,
                        photo.y + dy / kotlin.math.max(1f, bounds.height() * board.height())
                    )
                )
                lastX = event.getX()
                lastY = event.getY()
            }
            dragged = true
            invalidate()
        }
        if (action == MotionEvent.ACTION_UP && !dragged) {
            performClick()
            selected = touchPiece
            selectionChanged?.run()
            invalidate()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        val FILTERS: Array<String> =
            arrayOf("Original", "Black & White", "Sepia", "Warm", "Cool", "Vivid", "Fade")

        fun filter(type: Int): ColorMatrixColorFilter? {
            if (type == 0) return null
            val matrix: ColorMatrix = ColorMatrix()
            when (type) {
                1 -> matrix.setSaturation(0f)
                2 -> matrix.set(
                    kotlin.floatArrayOf(
                        .393f,
                        .769f,
                        .189f,
                        0f,
                        0f,
                        .349f,
                        .686f,
                        .168f,
                        0f,
                        0f,
                        .272f,
                        .534f,
                        .131f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )

                3 -> matrix.setScale(1.12f, 1.02f, .87f, 1f)
                4 -> matrix.setScale(.87f, 1.03f, 1.14f, 1f)
                5 -> matrix.setSaturation(1.6f)
                6 -> matrix.set(
                    kotlin.floatArrayOf(
                        .8f,
                        0f,
                        0f,
                        0f,
                        28f,
                        0f,
                        .8f,
                        0f,
                        0f,
                        28f,
                        0f,
                        0f,
                        .8f,
                        0f,
                        28f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )

                else -> return null
            }
            return ColorMatrixColorFilter(matrix)
        }

    }
}
