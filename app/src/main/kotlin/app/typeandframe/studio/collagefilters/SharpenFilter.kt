package app.typeandframe.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter

class SharpenFilter :
    GPUImageSharpenFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setSharpness(amountValue * 0.06f - 3f)
    }
}
