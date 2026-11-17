package com.textswag.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageBulgeDistortionFilter

class BulgeFilter :
    GPUImageBulgeDistortionFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setRadius((0.75f * amountValue) / 100.0f)
        setScale((amountValue * (-0.75f)) / 100.0f)
    }
}
