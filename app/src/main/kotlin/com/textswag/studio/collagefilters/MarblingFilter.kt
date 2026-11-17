package com.textswag.studio.collagefilters

class MarblingFilter :
    BaseGlitchGPUImageFilter("uniform mat4 uMVPMatrix;attribute vec4 position;\nattribute vec4 inputTextureCoordinate;\n \nvarying vec2 textureCoordinate;\n \nvoid main()\n{\n    gl_Position = uMVPMatrix * position;\n    textureCoordinate = inputTextureCoordinate.xy;\n}", "precision highp float;\n\nvarying highp vec2 textureCoordinate;\n\nuniform sampler2D inputImageTexture;\n\nuniform highp float iTime;\n\nuniform highp vec2 inputSize;\n\n\nvec2 pa(in vec2 uv, in float per){\n    vec2 result = (sin(uv.x*per)) * normalize(vec2(cos((uv.x)*per), .5));\n    return result;\n}\n\nvec2 pb(in vec2 uv, in float per){\n    vec2 result = (cos(uv.y*per)) * normalize(vec2(1., cos((uv.y)*per)));\n    return result;\n}\n\nvoid main()\n{\n    float Amplitude = 0.07 + iTime/769.23;\n\n    vec2 uv = textureCoordinate;\n\n    vec2 vpert = pa(uv/.7,  0.08  *100.0);\n    uv += vpert * sin(Amplitude) *.2;\n\n    vec2 hpert = pb(uv/.7,   0.25  *100.0);\n    uv += hpert * sin(Amplitude) * .108;\n\n    vec4 col = texture2D(inputImageTexture, uv);\n    gl_FragColor = mix(vec4(sin(uv.y*2.), 0., cos(uv.y*3.), 1.), col, .9);\n}"), AdjustableFilter {
    override fun setAmount(amountValue: Float) {
        setTime(amountValue)
    }
}
