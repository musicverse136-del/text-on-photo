package com.textswag.studio.collagefilters

import android.content.Context
import android.graphics.Bitmap
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

enum class CollageGlitch(
    @JvmField val label: String,
    private val factory: () -> GPUImageFilter
) {
    SHAKE_RGB("Shake RGB", ::ShakeRGBFilter), SHAKE_GB("Shake GB", ::ShakeGBFilter),
    SHAKE_RB("Shake RB", ::ShakeRBFilter), SHAKE_RG("Shake RG", ::ShakeRGFilter),
    GLITCH("Glitch", ::GlitchFilter), PIXEL("Pixel", ::PixelFilter),
    CARTOON("Cartoon", ::CartoonFilter), CARTOON_2("Cartoon2", ::Cartoon2Filter),
    GALAXY("Galaxy", ::GalaxyFilter), VORTEX("Vortex", ::VortexFilter),
    FISHEYE("Fisheye", ::FishEyeFilter), BULGE("Bulge", ::BulgeFilter),
    SPEED("Speed", ::SpeedFilter), SOBEL("Sobel", ::SobelFilter),
    SHARPEN("Sharpen", ::SharpenFilter), SCANLINE("Scanline", ::ScanLineFilter),
    BLOWOUT("Blowout", ::BlowoutFilter), ABERRATION("Aberration", ::AberrationFilter),
    BAD_SIGNAL("Bad Signal", ::BadSignalColoredTVFilter), BAD_TV("Bad TV", ::BadSignalTVFilter),
    BROKEN_GLASS("Broken Glass", ::BrokenGlassFilter), COLOR_TINT("Color Tint", ::ColorTintFilter),
    DROSTE("Droste", ::DrosteFilter), EMBOSS("Emboss", ::EmbossFilter),
    H_EXTRUDE("H-Extrude", ::HExtrudeFilter), MARBLING("Marbling", ::MarblingFilter),
    NEON("Neon", ::NeonFilter), RADIAL("Radial", ::RadialFilter),
    SOFT_FLIP("SoftFlip", ::SoftFlipFilter), TRIANGLE("Triangle", ::TriangleFilter),
    V_EXTRUDE("V-Extrude", ::VExtrudeFilter), VISION("Vision", ::VisionFilter);

    fun render(context: Context, source: Bitmap, amount: Int): Bitmap {
        val renderer = GPUImage(context)
        val filter = factory()
        (filter as AdjustableFilter).setAmount(amount.toFloat())
        renderer.setFilter(filter)
        return renderer.getBitmapWithFilterApplied(source)
    }
}
