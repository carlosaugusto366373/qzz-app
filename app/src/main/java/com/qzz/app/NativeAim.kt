package com.qzz.app

object NativeAim {

    init {
        System.loadLibrary("qzzaim")
    }

    external fun init(): Boolean
    external fun stop()
    external fun setAim(on: Boolean)
    external fun setEsp(on: Boolean)
    external fun setScreen(w: Int, h: Int)

    // registra o callback que o C++ usa pra mover o toque
    private var listener: ((Float, Float) -> Unit)? = null

    fun setTouchListener(l: (Float, Float) -> Unit) {
        listener = l
    }

    // chamado do C++ via JNI (registrado abaixo)
    @JvmStatic
    fun onTouch(x: Float, y: Float) {
        listener?.invoke(x, y)
    }
}