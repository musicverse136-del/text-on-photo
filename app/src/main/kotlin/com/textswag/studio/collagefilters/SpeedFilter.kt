package com.textswag.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageZoomBlurFilter

class SpeedFilter :
    GPUImageZoomBlurFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setBlurSize(amountValue / 100.0f)
    }
}
