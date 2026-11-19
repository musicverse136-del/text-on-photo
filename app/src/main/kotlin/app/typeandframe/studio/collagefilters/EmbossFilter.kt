package app.typeandframe.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageEmbossFilter

class EmbossFilter :
    GPUImageEmbossFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setIntensity((amountValue * 4.0f) / 100.0f)
    }
}
