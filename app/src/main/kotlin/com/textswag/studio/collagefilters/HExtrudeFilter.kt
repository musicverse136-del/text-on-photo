package com.textswag.studio.collagefilters

class HExtrudeFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "precision highp float;\nuniform highp vec2 inputSize;\nuniform highp float iTime;\nvarying highp vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\n\nvoid main() {\n\n    vec2 pixelPos;\n    pixelPos.x = textureCoordinate[0];\n    pixelPos.y = textureCoordinate[1];\n    float time = iTime / 100.0;\n    \n    if (time < 0.5){\n        if (pixelPos.x > (time + 0.5))\n        {\n            pixelPos.x = (time + 0.5);\n        }\n    } else {\n        if (pixelPos.x < (time - 0.5))\n        {\n            pixelPos.x = (time - 0.5);\n        }\n    }\n    \n    gl_FragColor = texture2D(inputImageTexture, pixelPos);\n    \n    \n}"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
