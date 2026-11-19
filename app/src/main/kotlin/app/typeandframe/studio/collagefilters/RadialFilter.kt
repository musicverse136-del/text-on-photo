package app.typeandframe.studio.collagefilters

class RadialFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "precision highp float;\nvarying highp vec2 textureCoordinate;\nuniform sampler2D inputImageTexture;\nuniform highp float iTime;\nvoid main() {\n    vec2 uv = textureCoordinate;\n    float dx = cos(iTime/16.0) * 0.1;\n    float dy = sin(iTime/16.0) * 0.1;\n    vec4 c0 = texture2D(inputImageTexture,uv);\n    vec4 c1 = texture2D(inputImageTexture,uv + vec2(dx,dy));\n    vec4 c2 = texture2D(inputImageTexture,uv - vec2(dx,dy));\n    gl_FragColor = mix(mix(c0,c1,0.5),c2,1.0/3.0);\n}"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
