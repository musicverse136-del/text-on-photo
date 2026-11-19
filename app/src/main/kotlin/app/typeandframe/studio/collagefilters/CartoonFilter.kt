package app.typeandframe.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageToonFilter

class CartoonFilter :
    GPUImageToonFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setThreshold((amountValue * 0.6f) / 100.0f)
        setQuantizationLevels(10.0f)
    }
}
