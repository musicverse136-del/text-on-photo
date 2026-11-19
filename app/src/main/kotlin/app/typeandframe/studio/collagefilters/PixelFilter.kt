package app.typeandframe.studio.collagefilters

import jp.co.cyberagent.android.gpuimage.filter.GPUImagePixelationFilter

class PixelFilter :
    GPUImagePixelationFilter(), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setPixel(amountValue)
    }
    override fun onOutputSizeChanged(i: Int, i2: Int) {
        super.onOutputSizeChanged(1080, 1920)
    }
}
