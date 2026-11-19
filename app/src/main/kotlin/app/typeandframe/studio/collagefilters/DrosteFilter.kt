package app.typeandframe.studio.collagefilters

class DrosteFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "precision highp float;\n\nvarying highp vec2 textureCoordinate;\n\nuniform sampler2D inputImageTexture;\nuniform highp float iTime;\n\nuniform highp vec2 inputSize;\n\nvoid main() {\n    float repeat = iTime / 15.5 + 1.2;\n\n    highp vec2 uv = textureCoordinate;\n    highp vec2 uv_inverted = 1.0 - uv;\n\n    highp vec2 fiter = floor(uv * repeat * 2.0);\n    highp vec2 riter = floor(uv_inverted * repeat * 2.0);\n    highp vec2 iter = min(fiter, riter);\n    float offset_mul = min(iter.x, iter.y);\n\n    highp vec2 offset = (vec2(0.5, 0.5) / repeat) * offset_mul;\n\n    highp vec2 uv_mul = 1.0 / (vec2(1.0, 1.0) - offset * 2.0);\n\n    highp vec2 finaluv = (uv - offset) * uv_mul;\n\n    gl_FragColor = texture2D(inputImageTexture, finaluv);\n}\n"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
