package app.typeandframe.studio.collagefilters

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter

class BlowoutFilter :
    GPUImageFilter(GPUImageToneCurveFilter.NO_FILTER_VERTEX_SHADER, "precision highp float;\nvarying vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\nuniform float u_Offset;\n  void main(){\n    vec4 wh=texture2D(inputImageTexture, textureCoordinate);    if(textureCoordinate.x>u_Offset\n        &&textureCoordinate.x<(1.0-u_Offset)\n        &&textureCoordinate.y>u_Offset\n        &&textureCoordinate.y<(1.0-u_Offset)){\n        gl_FragColor =wh;\n    } else {\n       vec4 vw=vec4(0.0);\n       vec4 vh=vec4(0.0);\n       float usex=textureCoordinate.x;\n       float usey=textureCoordinate.y;\n       usex=min(usex,(1.0-u_Offset));\n       usex=max(usex,u_Offset);\n       usey=min(usey,(1.0-u_Offset));\n       usey=max(usey,u_Offset);\n       float usex2=textureCoordinate.x+0.1;\n       if(usex<0.5){\n          usex2=textureCoordinate.x-0.1;\n       };\n       float usey2=textureCoordinate.x+0.1;\n       if(usey<0.5){\n           usey2=textureCoordinate.x-0.1;\n       };\n       if(textureCoordinate.x<u_Offset||textureCoordinate.x>(1.0-u_Offset)){\n           vw=texture2D(inputImageTexture, vec2(usex,textureCoordinate.y)); }else{vw=texture2D(inputImageTexture, vec2(usex2,textureCoordinate.y));};if(textureCoordinate.y<u_Offset||textureCoordinate.y>(1.0-u_Offset)){vh=texture2D(inputImageTexture, vec2(textureCoordinate.x,usey)); }else{vh=texture2D(inputImageTexture, vec2(textureCoordinate.x,usey2));};gl_FragColor =vw*0.5+vh*0.5;\n     }\n  }"), AdjustableFilter {
    private var amount = 20.0f
    private var offsetUniform = 0

    override fun setAmount(amountValue: Float) {
        amount = amountValue
        setFloat(offsetUniform, (amountValue * 0.35f) / 100.0f)
    }
    override fun onInit() {
        super.onInit()
        offsetUniform = GLES20.glGetUniformLocation(program, "u_Offset")
    }
    override fun onInitialized() {
        super.onInitialized()
        setFloat(offsetUniform, (amount * 0.35f) / 100.0f)
    }
}
