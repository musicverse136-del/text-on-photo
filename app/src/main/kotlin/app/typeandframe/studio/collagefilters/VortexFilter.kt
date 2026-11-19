package app.typeandframe.studio.collagefilters

import android.graphics.PointF
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter

class VortexFilter(private var center: PointF = PointF(0.5f, 0.5f)) :
    GPUImageFilter(GPUImageToneCurveFilter.NO_FILTER_VERTEX_SHADER, "varying highp vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\nuniform highp vec2 center;\nuniform highp float radius;\nuniform highp float angle;\nuniform highp float scale;\nuniform highp float pixel;\nvoid main()\n{\nhighp vec2 textureCoordinateToUse = textureCoordinate;\nhighp float dist = distance(center, textureCoordinate);\nif (dist > radius)\n{\ntextureCoordinateToUse -= center;\nhighp float percent = (radius - dist) / radius;\nhighp float theta = percent * percent * angle * 1.0;\nhighp float s = sin(theta);\nhighp float c = cos(theta);\ntextureCoordinateToUse = vec2(dot(textureCoordinateToUse, vec2(c, -s)), dot(textureCoordinateToUse, vec2(s, c)));\ntextureCoordinateToUse += center;\ngl_FragColor = texture2D(inputImageTexture, textureCoordinateToUse );\n}else{\ngl_FragColor = texture2D(inputImageTexture, textureCoordinateToUse );\n}\n}\n"), AdjustableFilter {
    private var angleUniform = 0
    private var centerUniform = 0
    private var radiusUniform = 0
    private var amount = 75.0f


    private fun setAngle(amountValue: Float) {
        setFloat(angleUniform, amountValue)
    }

    private fun setCenter(pointF: PointF) {
        center = pointF
        setPoint(centerUniform, pointF)
    }

    private fun setRadius(amountValue: Float) {
        setFloat(radiusUniform, amountValue)
    }
    override fun setAmount(amountValue: Float) {
        amount = amountValue
        setRadius(amountValue / 100.0f)
        setAngle((amountValue - 50.0f) * 0.04f)
    }
    override fun onInit() {
        super.onInit()
        angleUniform = GLES20.glGetUniformLocation(program, "angle")
        radiusUniform = GLES20.glGetUniformLocation(program, "radius")
        centerUniform = GLES20.glGetUniformLocation(program, "center")
    }
    override fun onInitialized() {
        super.onInitialized()
        setAmount(amount)
        setCenter(center)
    }
}
