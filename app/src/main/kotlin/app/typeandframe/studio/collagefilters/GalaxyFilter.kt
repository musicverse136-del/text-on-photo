package app.typeandframe.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageSwirlFilter

class GalaxyFilter :
    GPUImageSwirlFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setAngle(((amountValue - 50.0f) * 0.3f) / 8.0f)
    }
}
