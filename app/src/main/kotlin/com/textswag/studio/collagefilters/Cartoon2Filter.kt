package com.textswag.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageSmoothToonFilter

class Cartoon2Filter :
    GPUImageSmoothToonFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setQuantizationLevels(25.0f)
        setThreshold((amountValue * 0.6f) / 100.0f)
    }
}
