package com.textswag.studio.collagefilters

class BrokenGlassFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "precision highp float;\nvarying highp vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\nuniform highp vec2 inputSize;\nuniform highp float iTime;\n\nconst highp float MULTIPLIER = 1.25;\nconst highp float UV = .99;\n\nhighp vec3 broken(){\n  highp vec2 uv = textureCoordinate - 0.51;\n  uv *= UV - mod(uv.x + uv.y, uv.y * (iTime+1.0) / 100.0) * MULTIPLIER;\n  return texture2D(inputImageTexture, uv + 0.51).rgb;\n}\n\nvoid main() {\n  gl_FragColor = vec4(broken(), 1.0);\n}"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
