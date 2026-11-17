package com.textswag.studio.collagefilters

class ShakeRGFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "varying highp vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\nuniform highp vec2 inputSize;\nuniform highp float iTime;\n\nconst highp float amount = .3;\nconst highp float speed = .0168;\n\nvoid main()\n{\n    highp vec2 uv = textureCoordinate;\n    highp float s = abs(sin(iTime * speed)) * amount;\n\n    highp vec4 left = texture2D(inputImageTexture, uv);\n    highp vec4 right = texture2D(inputImageTexture, uv + vec2(s, 0.0));\n\n    highp vec3 color = vec3(left.rg, right.b);\n\n    color = clamp(color, 0.0, 1.0);\n    gl_FragColor = vec4(color, 1.0);\n}"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
