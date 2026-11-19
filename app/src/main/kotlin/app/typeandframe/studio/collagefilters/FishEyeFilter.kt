package app.typeandframe.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImageBulgeDistortionFilter

class FishEyeFilter :
    GPUImageBulgeDistortionFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setRadius((0.8f * amountValue) / 100.0f)
        setScale((amountValue * 0.75f) / 100.0f)
    }
}
