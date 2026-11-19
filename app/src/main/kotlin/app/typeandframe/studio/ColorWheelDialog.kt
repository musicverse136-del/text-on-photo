package app.typeandframe.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Shader.TileMode
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import java.util.function.IntConsumer

internal object ColorWheelDialog {
    fun show(activity: Activity, initial: Int, apply: IntConsumer) {
        val hsv: FloatArray = kotlin.floatArrayOf(0f, 0f, 1f)
        if (initial != Color.TRANSPARENT) Color.colorToHSV(initial, hsv)
        val body: LinearLayout = LinearLayout(activity)
        body.setOrientation(LinearLayout.VERTICAL)
        body.setPadding(
            ColorWheelDialog.dp(activity, 6),
            ColorWheelDialog.dp(activity, 12),
            ColorWheelDialog.dp(activity, 6),
            ColorWheelDialog.dp(activity, 16)
        )
        val wheel: ColorWheelDialog.Control = ColorWheelDialog.Control(activity, hsv, true)
        val brightness: ColorWheelDialog.Control = ColorWheelDialog.Control(activity, hsv, false)
        wheel.peer = brightness
        brightness.peer = wheel
        val diameter: Int = kotlin.math.min(
            activity.getResources().getConfiguration().screenWidthDp - 52,
            activity.getResources().getConfiguration().screenHeightDp - 190
        )
        body.addView(
            wheel,
            LinearLayout.LayoutParams(
                -1,
                ColorWheelDialog.dp(activity, kotlin.math.max(120, diameter))
            )
        )
        val bar: LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(-1, ColorWheelDialog.dp(activity, 32))
        bar.setMargins(
            ColorWheelDialog.dp(activity, 10),
            ColorWheelDialog.dp(activity, 18),
            ColorWheelDialog.dp(activity, 10),
            0
        )
        body.addView(brightness, bar)
        AlertDialog.Builder(activity).setTitle("Select color").setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "OK",
                DialogInterface.OnClickListener { ignored: DialogInterface?, which: Int ->
                    apply.accept(Color.HSVToColor(hsv))
                }).show()
    }

    private fun dp(context: Context, value: Int): Int {
        return java.lang.Math.round(context.getResources().getDisplayMetrics().density * value)
    }

    private class Control(context: Context, hsv: FloatArray, wheel: Boolean) : View(context) {
        val hsv: FloatArray
        val wheel: Boolean
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        lateinit var peer: ColorWheelDialog.Control
        lateinit var spectrum: Shader
        var radius: Float = 0f
        val inset: Float

        init {
            this.hsv = hsv
            this.wheel = wheel
            inset = ColorWheelDialog.dp(context, 9).toFloat()
            setContentDescription(if (wheel) "Color wheel" else "Brightness")
            setClickable(true)
        }

        override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
            radius = kotlin.math.max(1f, kotlin.math.min(w, h) / 2f - inset)
            if (!wheel) return
            val x: Float = w / 2f
            val y: Float = h / 2f
            spectrum = ComposeShader(
                SweepGradient(
                    x, y, kotlin.intArrayOf(
                        Color.RED,
                        Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
                    ), null
                ),
                RadialGradient(x, y, radius, Color.WHITE, 0x00ffffff, Shader.TileMode.CLAMP),
                PorterDuff.Mode.SRC_OVER
            )
        }

        override fun onDraw(canvas: Canvas) {
            var x: Float = getWidth() / 2f
            var y: Float = getHeight() / 2f
            if (wheel) {
                paint.setShader(spectrum)
                canvas.drawCircle(x, y, radius, paint)
                val angle: Double = java.lang.Math.toRadians(hsv[0].toDouble())
                x += kotlin.math.cos(angle).toFloat() * hsv[1] * radius
                y -= kotlin.math.sin(angle).toFloat() * hsv[1] * radius
            } else {
                val right: Float = getWidth() - inset
                val pure: Int = Color.HSVToColor(kotlin.floatArrayOf(hsv[0], hsv[1], 1f))
                paint.setShader(
                    LinearGradient(
                        inset,
                        0f,
                        right,
                        0f,
                        Color.BLACK,
                        pure,
                        Shader.TileMode.CLAMP
                    )
                )
                canvas.drawRect(
                    inset,
                    ColorWheelDialog.dp(getContext(), 3).toFloat(),
                    right,
                    (getHeight() - ColorWheelDialog.dp(getContext(), 3)).toFloat(),
                    paint
                )
                x = inset + (right - inset) * hsv[2]
            }
            paint.setShader(null)
            paint.setColor(-0x545455)
            canvas.drawCircle(x, y, inset, paint)
            paint.setColor(Color.WHITE)
            canvas.drawCircle(x, y, inset - ColorWheelDialog.dp(getContext(), 1), paint)
            paint.setColor(Color.HSVToColor(hsv))
            canvas.drawCircle(x, y, inset - ColorWheelDialog.dp(getContext(), 3), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val action: Int = event.getActionMasked()
            if (action == MotionEvent.ACTION_CANCEL) return true
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) return super.onTouchEvent(
                event
            )
            if (wheel) {
                val x: Float = event.getX() - getWidth() / 2f
                val y: Float = getHeight() / 2f - event.getY()
                hsv[0] =
                    (360 + java.lang.Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble()))
                        .toFloat()) % 360
                hsv[1] = kotlin.math.min(
                    1f,
                    kotlin.math.hypot(x.toDouble(), y.toDouble()).toFloat() / radius
                )
                if (hsv[2] == 0f) hsv[2] = 1f
            } else {
                hsv[2] = kotlin.math.max(
                    0f,
                    kotlin.math.min(1f, (event.getX() - inset) / (getWidth() - 2 * inset))
                )
            }
            invalidate()
            peer.invalidate()
            if (action == MotionEvent.ACTION_UP) performClick()
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
