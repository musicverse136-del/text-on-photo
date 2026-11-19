package app.typeandframe.studio.collagefilters

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter

class ScanLineFilter :
    GPUImageFilter(GPUImageToneCurveFilter.NO_FILTER_VERTEX_SHADER, "precision highp float; varying  vec2 textureCoordinate;\n\nuniform sampler2D inputImageTexture;\nuniform float u_Offset;\n float changeval( float val){\n    if(val>1.0){\n    val=1.0;\n    }\n    if(val<0.0){\n    val=0.0;\n    }\n    return val;\n}void main(){\n  lowp vec4 fragmentColor = texture2D(inputImageTexture, textureCoordinate);\n   float minx=changeval(textureCoordinate.x+ u_Offset);\n   float maxx=changeval(textureCoordinate.x- u_Offset);\n lowp vec4 red = texture2D(inputImageTexture, vec2(minx,textureCoordinate.y)); lowp vec4 green = texture2D(inputImageTexture, vec2(maxx,textureCoordinate.y));\n   float pos=floor(textureCoordinate.y*200.0);   if(mod(pos,2.0)<1.0){   gl_FragColor = vec4(red.x*0.9,fragmentColor.y*0.9,green.z*0.9,fragmentColor.w);\n   }else{   gl_FragColor =vec4(red.x*0.8,fragmentColor.y*0.8,green.z*0.8,fragmentColor.w);\n   }}\n"), AdjustableFilter {
    private var amount = 60.0f
    private var offsetUniform = 0

    override fun setAmount(amountValue: Float) {
        amount = amountValue
        setFloat(offsetUniform, (amountValue - 50.0f) * 0.001f)
    }
    override fun onInit() {
        super.onInit()
        offsetUniform = GLES20.glGetUniformLocation(program, "u_Offset")
    }
    override fun onInitialized() {
        super.onInitialized()
        setAmount(amount)
    }
}
