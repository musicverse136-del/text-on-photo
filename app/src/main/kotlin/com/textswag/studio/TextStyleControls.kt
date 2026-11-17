package com.textswag.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Switch
import android.widget.TextView
import com.textswag.studio.EditorCanvas.Layer
import com.textswag.studio.databinding.PanelTextStyleBinding

internal class TextStyleControls(
    views: PanelTextStyleBinding,
    styles: TextStyles,
    beforeChange: Runnable,
    update: Runnable
) {
    private val root: View
    private val styles: TextStyles
    private val beforeChange: Runnable
    private val update: Runnable
    private val presets: LinearLayout
    private val scroller: HorizontalScrollView
    private val chips: Array<TextView?> = kotlin.arrayOfNulls<TextView>(5)
    private val invert: Switch
    private val spacing: SeekBar
    private val random: java.util.Random = java.util.Random()
    private lateinit var layer: Layer
    private var binding: Boolean = false
    private var previewVersion: Int = 0

    init {
        this.root = views.root
        this.styles = styles
        this.beforeChange = beforeChange
        this.update = update
        presets = views.stylePresets
        scroller = views.styleScroller
        invert = views.styleInvert
        spacing = views.styleSpacing
        initPresets()
        initVariants(views.styleVariants)
        invert.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { button: CompoundButton?, checked: Boolean ->
            if (binding || !this@TextStyleControls::layer.isInitialized) return@OnCheckedChangeListener
            beforeChange.run()
            layer.styleInvert = checked
            update.run()

        })
        spacing.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) {
                if (this@TextStyleControls::layer.isInitialized) beforeChange.run()
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {}
            override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                if (!user || !this@TextStyleControls::layer.isInitialized) return
                layer.styleSpacing = value
                update.run()
            }
        })
    }

    fun show(selected: Layer) {
        binding = true
        layer = selected
        invert.setChecked(layer.styleInvert)
        spacing.setProgress(layer.styleSpacing)
        updateSelection()
        refreshPreviews()
        root.setVisibility(View.VISIBLE)
        val tile: View = presets.getChildAt(layer.styleIndex)
        scroller.post(Runnable { scroller.scrollTo(tile.getLeft(), 0) })
        binding = false
    }

    fun selectedLayer(): Layer {
        return layer
    }

    fun refreshPreviews() {
        previewVersion++
        for (i in 0..<presets.getChildCount()) presets.getChildAt(i).invalidate()
    }

    private fun initPresets() {
        for (index in 0..<styles.size()) {
            val presetIndex: Int = index
            val preview: View = Preview(root.getContext(), index)
            preview.setContentDescription(styles.name(index))
            preview.setOnClickListener(View.OnClickListener { view: View ->
                if (layer.styleIndex == presetIndex) return@OnClickListener
                beforeChange.run()
                layer.styleIndex = presetIndex
                updateSelection()
                update.run()

            })
            val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
            params.setMargins(dp(4), dp(5), dp(4), dp(5))
            presets.addView(preview, params)
        }
    }

    private fun initVariants(row: LinearLayout) {
        for (i in chips.indices) {
            val variant: Int = i + 1
            val chip: TextView = TextView(root.getContext())
            chip.setText(variant.toString())
            chip.setTextColor(root.getContext().getColor(R.color.foreground))
            chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            chip.setGravity(Gravity.CENTER)
            chip.setBackgroundResource(R.drawable.style_variant_chip)
            chip.setOnClickListener(View.OnClickListener { view: View -> selectVariant(variant) })
            val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            params.setMarginEnd(dp(4))
            row.addView(chip, params)
            chips[i] = chip
        }
        val dice: ImageButton = ImageButton(root.getContext())
        dice.setImageResource(R.drawable.ic_variant_dice)
        dice.setBackgroundColor(Color.TRANSPARENT)
        dice.setContentDescription(root.getContext().getString(R.string.style_random))
        dice.setOnClickListener(View.OnClickListener { view: View ->
            selectVariant(
                6 + random.nextInt(
                    1000
                )
            )
        })
        row.addView(dice, LinearLayout.LayoutParams(dp(36), dp(36)))
    }

    private fun selectVariant(variant: Int) {
        if (!this@TextStyleControls::layer.isInitialized) return
        if (layer.styleVariant == variant) return
        beforeChange.run()
        layer.styleVariant = variant
        updateSelection()
        update.run()
    }

    private fun updateSelection() {
        for (i in chips.indices) chips[i]?.setSelected(layer.styleVariant == i + 1)
        for (i in 0..<presets.getChildCount()) {
            val child: View = presets.getChildAt(i)
            val selected: Boolean = i == layer.styleIndex
            if (child.isSelected() != selected) child.setSelected(selected)
        }
    }

    private fun dp(size: Int): Int {
        return java.lang.Math.round(size * root.getResources().getDisplayMetrics().density)
    }

    private inner class Preview(context: Context?, index: Int) : View(context) {
        private val index: Int
        private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val bounds: RectF = RectF()
        private lateinit var sample: Bitmap
        private var renderedVersion: Int = -1

        init {
            this.index = index
            val card: GradientDrawable = GradientDrawable()
            card.setCornerRadius(dp(8).toFloat())
            card.setColor(-0x1f1f20)
            setBackground(card)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!this@TextStyleControls::layer.isInitialized || getWidth() <= dp(10)) return
            if (renderedVersion != previewVersion) {
                val rendered: Bitmap = styles.renderLayer(layer, index)
                val ratio: Float = (getWidth() - dp(10)) / kotlin.math.max(
                    rendered.getWidth(),
                    rendered.getHeight()
                ).toFloat()
                sample = Bitmap.createScaledBitmap(
                    rendered, kotlin.math.max(1, java.lang.Math.round(ratio * rendered.getWidth())),
                    kotlin.math.max(1, java.lang.Math.round(ratio * rendered.getHeight())), true
                )
                if (sample != rendered) rendered.recycle()
                renderedVersion = previewVersion
            }
            val scale: Float =
                (getWidth() - dp(10)) / kotlin.math.max(sample.getWidth(), sample.getHeight())
                    .toFloat()
            val width: Float = sample.getWidth() * scale
            val height: Float = sample.getHeight() * scale
            bounds.set(
                (getWidth() - width) / 2, (getHeight() - height) / 2,
                (getWidth() + width) / 2, (getHeight() + height) / 2
            )
            canvas.drawBitmap(sample, null, bounds, paint)
            if (isSelected()) {
                paint.setColor(getContext().getColor(R.color.accent))
                paint.setStrokeWidth(dp(2).toFloat())
                paint.setStyle(Paint.Style.STROKE)
                canvas.drawRoundRect(
                    1f,
                    1f,
                    (getWidth() - 1).toFloat(),
                    (getHeight() - 1).toFloat(),
                    dp(8).toFloat(),
                    dp(8).toFloat(),
                    paint
                )
                paint.setStyle(Paint.Style.FILL)
            }
        }
    }
}
