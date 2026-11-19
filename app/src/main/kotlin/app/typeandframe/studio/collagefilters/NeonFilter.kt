package app.typeandframe.studio.collagefilters

class NeonFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "uniform highp vec2 inputSize;\nvarying highp vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\n\nuniform highp float iTime;\nconst highp float speed = 0.175;\nconst highp float value = 5.0;\n\nvoid main() {\n    highp vec4 colorOffset = vec4((sin(textureCoordinate.x + iTime * speed * 4.0) + 1.0) / value,\n            (textureCoordinate.x * 2.0) / value,\n            (cos(textureCoordinate.x + iTime * speed) + 1.0) / value,\n            0);\n\n    gl_FragColor = texture2D(inputImageTexture, textureCoordinate) + colorOffset;\n}"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
