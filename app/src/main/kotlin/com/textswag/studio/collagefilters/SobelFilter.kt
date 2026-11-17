package com.textswag.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageSobelEdgeDetectionFilter

class SobelFilter :
    GPUImageSobelEdgeDetectionFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setLineSize((amountValue * 5.0f) / 100.0f)
    }
}
