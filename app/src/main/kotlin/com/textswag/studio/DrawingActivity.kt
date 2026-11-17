package com.textswag.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import com.textswag.studio.databinding.ActivityDrawingBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList
import java.util.UUID
import java.util.function.IntConsumer

class DrawingActivity : Activity() {
    private lateinit var binding: ActivityDrawingBinding
    private lateinit var toolButtons: Array<View>
    private lateinit var drawing: DrawingView
    private lateinit var assets: DesignAssets
    private var imageBrushes: Boolean = false
    private var updatingControls: Boolean = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        assets = DesignAssets(this)
        val preview: String? = intent.getStringExtra(DrawingActivity.REFERENCE)
        val initial: String? = intent.getStringExtra(DrawingActivity.INITIAL)
        val reference: Bitmap? = if (preview == null) null else BitmapFactory.decodeFile(
            File(
                getCacheDir(),
                preview
            ).getPath()
        )
        val initialInk: Bitmap? = if (initial == null) null else BitmapFactory.decodeFile(
            File(
                getCacheDir(),
                initial
            ).getPath()
        )
        drawing = DrawingView(reference, initialInk)
        val surface: FrameLayout = binding.drawingSurface
        surface.addView(drawing, FrameLayout.LayoutParams(-1, -1))
        binding.close.setOnClickListener(View.OnClickListener { view: View -> finish() })
        binding.undo.setOnClickListener(View.OnClickListener { view: View -> drawing.undo() })
        binding.redo.setOnClickListener(View.OnClickListener { view: View -> drawing.redo() })
        binding.drawingDone.setOnClickListener(View.OnClickListener { view: View -> completeDrawing() })
        binding.brushColor.setOnClickListener(View.OnClickListener { view: View ->
            showBrushChoices(
                false
            )
        })
        binding.brushImages.setOnClickListener(View.OnClickListener { view: View ->
            showBrushChoices(
                true
            )
        })
        toolButtons =
            arrayOf(binding.brushLine, binding.brushDash, binding.brushNeon, binding.brushErase)
        for (i in toolButtons.indices) {
            val tool: Int = i
            toolButtons[i].setOnClickListener(View.OnClickListener { view: View -> selectTool(tool) })
        }
        selectTool(DrawingActivity.LINE)
        bindSlider(
            binding.brushSize,
            IntConsumer { value: Int -> drawing.size = (2 + value).toFloat() })
        bindSlider(binding.brushFirst, IntConsumer { value: Int ->
            if (drawing.tool == DrawingActivity.DASH) drawing.dashWidth = 2 + value
            if (drawing.tool == DrawingActivity.NEON) drawing.blur = 1 + value

        })
        bindSlider(
            binding.brushDashSpace,
            IntConsumer { value: Int -> drawing.dashSpace = 2 + value })
        updateButtons()
    }

    private fun bindSlider(slider: SeekBar, action: IntConsumer) {
        slider.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!updatingControls && fromUser) action.accept(value)
            }
        })
    }

    private fun showBrushChoices(images: Boolean) {
        imageBrushes = images
        binding.brushColor.setSelected(!images)
        binding.brushImages.setSelected(images)
        val choices: LinearLayout = binding.brushChoices
        choices.removeAllViews()
        binding.brushChoicesScroll.scrollTo(0, 0)
        if (images) {
            for (path in assets.files("brush/image", ".png", ".webp")) {
                try {
                    val stamp: Bitmap = assets.image(path, 96)
                    val tile: ImageView = ImageView(this)
                    tile.setImageBitmap(stamp)
                    tile.setScaleType(ImageView.ScaleType.FIT_CENTER)
                    tile.setContentDescription("Brush image " + DesignAssets.label(path))
                    tile.setOnClickListener(View.OnClickListener { view: View ->
                        drawing.stamp = stamp
                        markBrushChoice(choices, tile)

                    })
                    choices.addView(tile, brushTileSize())
                } catch (ignored: IOException) {
                }
            }
            return
        }
        drawing.stamp = null
        val pick: ImageView = ImageView(this)
        pick.setImageResource(R.drawable.ic_color_picker)
        pick.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        pick.setContentDescription("Custom color")
        pick.setOnClickListener(View.OnClickListener { view: View ->
            ColorWheelDialog.show(this, drawing.color, IntConsumer { color: Int ->
                drawing.color = color
                markBrushChoice(choices, view)

            })
        })
        choices.addView(pick, brushTileSize())
        val colors: IntArray = kotlin.intArrayOf(
            Color.BLACK, Color.WHITE, -0xbbcca, -0x15b078, -0xa8de,
            -0x3ef9, -0x7f43b1, -0xe9726d, -0xd9482f, -0xc8b656, -0x6fa943
        )
        for (color in colors) {
            val swatch: View = View(this)
            val tile: GradientDrawable = GradientDrawable()
            tile.setShape(GradientDrawable.OVAL)
            tile.setColor(color)
            tile.setStroke(dp(1), -0x3f3f40)
            swatch.setBackground(tile)
            swatch.setContentDescription(
                String.format(
                    java.util.Locale.ROOT,
                    "#%06X",
                    color and 0xffffff
                )
            )
            swatch.setOnClickListener(View.OnClickListener { view: View ->
                drawing.color = color
                markBrushChoice(choices, swatch)

            })
            choices.addView(swatch, brushTileSize())
            swatch.setAlpha(if (drawing.color == color) 1f else 0.72f)
        }
    }

    private fun markBrushChoice(choices: LinearLayout, selected: View?) {
        for (i in 0..<choices.getChildCount()) {
            val tile: View = choices.getChildAt(i)
            tile.setAlpha(if (tile === selected) 1f else 0.72f)
        }
    }

    private fun brushTileSize(): LinearLayout.LayoutParams {
        val size: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        size.setMargins(dp(4), 0, dp(4), 0)
        return size
    }

    private fun dp(size: Int): Int {
        return java.lang.Math.round(size * getResources().getDisplayMetrics().density)
    }

    private fun selectTool(tool: Int) {
        drawing.tool = tool
        for (i in toolButtons.indices) {
            toolButtons[i].setAlpha(if (i == tool) 1f else 0.52f)
        }
        binding.brushExtras.setVisibility(if (tool == DrawingActivity.DASH || tool == DrawingActivity.NEON) View.VISIBLE else View.GONE)
        binding.brushDashSpaceGroup.setVisibility(if (tool == DrawingActivity.DASH) View.VISIBLE else View.GONE)
        binding.brushTabs.setVisibility(if (tool == DrawingActivity.LINE) View.VISIBLE else View.GONE)
        binding.brushChoicesScroll.setVisibility(if (tool == DrawingActivity.ERASE) View.GONE else View.VISIBLE)
        val label: String = if (tool == DrawingActivity.DASH) "Dash width" else "Blur radius"
        binding.brushFirstLabel.setText(label)
        val first: SeekBar = binding.brushFirst
        first.setContentDescription(label)
        updatingControls = true
        first.setProgress(if (tool == DrawingActivity.DASH) drawing.dashWidth - 2 else drawing.blur - 1)
        binding.brushDashSpace.setProgress(drawing.dashSpace - 2)
        updatingControls = false
        if (tool != DrawingActivity.ERASE) showBrushChoices(tool == DrawingActivity.LINE && imageBrushes)
    }

    private fun updateButtons() {
        updateButton(binding.undo, !drawing.strokes.isEmpty())
        updateButton(binding.redo, !drawing.undone.isEmpty())
    }

    private fun updateButton(view: View, enabled: Boolean) {
        view.setEnabled(enabled)
        view.setAlpha(if (enabled) 1f else 0.35f)
    }

    private fun completeDrawing() {
        if (drawing.strokes.isEmpty() && drawing.initialInk == null) {
            finish()
            return
        }
        val name: String = "draw-" + UUID.randomUUID() + ".png"
        val file: File = File(getCacheDir(), name)
        val output: Bitmap =
            Bitmap.createBitmap(DrawingActivity.SIZE, DrawingActivity.SIZE, Bitmap.Config.ARGB_8888)
        drawing.drawStrokes(Canvas(output))
        try {
            FileOutputStream(file).use { stream ->
                if (!output.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        stream
                    )
                ) throw IOException("PNG encode failed")
            }
        } catch (e: IOException) {
            file.delete()
            Toast.makeText(this, "Could not add the drawing.", Toast.LENGTH_SHORT).show()
            return
        } finally {
            output.recycle()
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(DrawingActivity.DRAWING, name))
        finish()
    }

    private inner class DrawingView(reference: Bitmap?, initialInk: Bitmap?) :
        View(this@DrawingActivity) {
        private val board: RectF = RectF()
        private val bitmapPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val reference: Bitmap?
        val initialInk: Bitmap?
        val strokes: MutableList<Stroke> = ArrayList<Stroke>()
        val undone: java.util.ArrayDeque<Stroke> = java.util.ArrayDeque<Stroke>()
        private var current: Stroke? = null
        var tool: Int = DrawingActivity.LINE
        var color: Int = Color.BLACK
        var size: Float = 16f
        var dashWidth: Int = 32
        var dashSpace: Int = 24
        var blur: Int = 25
        var stamp: Bitmap? = null

        init {
            this.reference = reference
            this.initialInk = initialInk
            setContentDescription(getString(R.string.hand_draw))
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            val side: Float = kotlin.math.min(getWidth(), getHeight()).toFloat()
            if (side <= 0) return
            board.set(
                (getWidth() - side) / 2, (getHeight() - side) / 2,
                (getWidth() + side) / 2, (getHeight() + side) / 2
            )
            if (reference != null) canvas.drawBitmap(reference, null, board, bitmapPaint)
            val layer: Int = canvas.saveLayer(board, null)
            canvas.translate(board.left, board.top)
            canvas.scale(side / DrawingActivity.SIZE, side / DrawingActivity.SIZE)
            drawStrokes(canvas)
            canvas.restoreToCount(layer)
        }

        fun drawStrokes(canvas: Canvas) {
            if (initialInk != null) canvas.drawBitmap(initialInk, 0f, 0f, bitmapPaint)
            for (stroke in strokes) stroke.draw(canvas)
            current?.draw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (board.isEmpty()) return false
            val x: Float = (event.getX() - board.left) * DrawingActivity.SIZE / board.width()
            val y: Float = (event.getY() - board.top) * DrawingActivity.SIZE / board.height()
            when (event.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> {
                    if (!board.contains(event.getX(), event.getY())) return false
                    current = Stroke(x, y, color, size, tool, dashWidth, dashSpace, blur, stamp)
                }

                MotionEvent.ACTION_MOVE -> current?.path?.lineTo(x, y)
                MotionEvent.ACTION_UP -> if (current != null) {
                    requireNotNull(current).path.lineTo(x, y)
                    strokes.add(requireNotNull(current))
                    undone.clear()
                    current = null
                    updateButtons()
                }

                MotionEvent.ACTION_CANCEL -> current = null
            }
            invalidate()
            return true
        }

        fun undo() {
            if (strokes.isEmpty()) return
            undone.addLast(strokes.removeAt(strokes.size - 1))
            updateButtons()
            invalidate()
        }

        fun redo() {
            if (undone.isEmpty()) return
            strokes.add(undone.removeLast())
            updateButtons()
            invalidate()
        }
    }

    private class Stroke(
        x: Float,
        y: Float,
        color: Int,
        width: Float,
        tool: Int,
        dashWidth: Int,
        dashSpace: Int,
        blur: Int,
        texture: Bitmap?
    ) {
        val path: android.graphics.Path = android.graphics.Path()
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val glow: Paint?
        val stamp: Bitmap?
        val stampSize: Float
        private val stampBounds: RectF = RectF()
        private val stampPoint: FloatArray = FloatArray(2)

        init {
            path.moveTo(x, y)
            path.lineTo(x + 0.01f, y)
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeCap(Paint.Cap.ROUND)
            paint.setStrokeJoin(Paint.Join.ROUND)
            paint.setStrokeWidth(width)
            paint.setColor(color)
            stamp = if (tool == DrawingActivity.LINE) texture else null
            stampSize = width * 3
            if (tool == DrawingActivity.DASH) paint.setPathEffect(
                DashPathEffect(
                    kotlin.floatArrayOf(
                        dashWidth.toFloat(),
                        dashSpace.toFloat()
                    ), 0f
                )
            )
            if (tool == DrawingActivity.ERASE) paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))
            if (tool == DrawingActivity.NEON) {
                glow = Paint(paint)
                glow.setMaskFilter(BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL))
                glow.setAlpha(180)
            } else {
                glow = null
            }
        }

        fun draw(canvas: Canvas) {
            if (stamp != null) {
                val measure: PathMeasure = PathMeasure(path, false)
                val step: Float = kotlin.math.max(6f, stampSize * 0.5f)
                var distance: Float = 0f
                while (distance <= measure.getLength()) {
                    if (!measure.getPosTan(distance, stampPoint, null)) {
                        distance += step
                        continue
                    }
                    val radius: Float = stampSize / 2f
                    stampBounds.set(
                        stampPoint[0] - radius, stampPoint[1] - radius,
                        stampPoint[0] + radius, stampPoint[1] + radius
                    )
                    canvas.drawBitmap(stamp, null, stampBounds, paint)
                    distance += step
                }
                return
            }
            if (glow != null) canvas.drawPath(path, glow)
            canvas.drawPath(path, paint)
        }
    }

    companion object {
        const val REFERENCE: String = "reference"
        const val DRAWING: String = "drawing"
        const val INITIAL: String = "initialInk"
        private val SIZE: Int = EditorCanvas.SIZE
        private const val LINE: Int = 0
        private const val DASH: Int = 1
        private const val NEON: Int = 2
        private const val ERASE: Int = 3
    }
}
