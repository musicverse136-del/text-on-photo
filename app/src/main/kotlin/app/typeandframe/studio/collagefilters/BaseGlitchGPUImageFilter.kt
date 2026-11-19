package app.typeandframe.studio.collagefilters

import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.Matrix
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

open class BaseGlitchGPUImageFilter(vertexShader: String, fragmentShader: String) :
    GPUImageFilter(vertexShader, fragmentShader) {
    private var matrixUniform = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private var time = 0f
    private var sizeUniform = -1
    private var timeUniform = -1

    override fun onInit() {
        super.onInit()
        matrixUniform = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        Matrix.setIdentityM(projection, 0)
        Matrix.setIdentityM(view, 0)
        Matrix.setLookAtM(view, 0, 0.0f, 0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)
        sizeUniform = GLES20.glGetUniformLocation(program, "inputSize")
        timeUniform = GLES20.glGetUniformLocation(program, "iTime")
    }
    override fun onInitialized() {
        super.onInitialized()
        setUniformMatrix4f(matrixUniform, projection)
        setTime(time)
    }
    override fun onOutputSizeChanged(i: Int, i2: Int) {
        setInputSize(PointF(i.toFloat(), i2.toFloat()))
        super.onOutputSizeChanged(i, i2)
    }

    fun setTime(amountValue: Float) {
        time = amountValue % 101.0f
        val i = timeUniform
        if (i == -1 || amountValue < 0.0f) {
            return
        }
        setFloat(i, amountValue)
    }

    protected fun setInputSize(pointF: PointF) {
        val i = sizeUniform
        if (i != -1) {
            setPoint(i, pointF)
        }
    }
}
