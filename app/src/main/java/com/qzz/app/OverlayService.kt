package com.qzz.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var webView: WebView
    private lateinit var minimizedButton: ImageView

    private var isMinimized = false

    companion object {
        var instance: OverlayService? = null
        fun start(ctx: Context) {
            ctx.startService(Intent(ctx, OverlayService::class.java))
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ============================================================
        // BOTÃO MINIMIZADO (bolinha)
        // ============================================================
        minimizedButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(20, 20, 20, 20)
            setOnClickListener { expand() }
        }

        // ============================================================
        // JANELA FLUTUANTE COM WEBVIEW
        // ============================================================
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(object {
                @JavascriptInterface
                fun minimize() { minimize() }
                @JavascriptInterface
                fun close() { stopSelf() }
                @JavascriptInterface
                fun moveTouch(x: Float, y: Float) {
                    AimAccessibilityService.instance?.moveTouch(x, y)
                }
                @JavascriptInterface
                fun startAim() {
                    val dm = resources.displayMetrics
                    NativeAim.setScreen(dm.widthPixels, dm.heightPixels)
                    NativeAim.setAim(true)
                    NativeAim.init()
                }
                @JavascriptInterface
                fun stopAim() {
                    NativeAim.setAim(false)
                }
            }, "QZZOverlay")

            loadUrl("file:///android_asset/index.html")
        }

        val webContainer = FrameLayout(this).apply {
            setBackgroundColor(0xEE000000.toInt())
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        // ============================================================
        // LAYOUT DA JANELA
        // ============================================================
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
            width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
            height = (resources.displayMetrics.heightPixels * 0.7f).toInt()
        }

        // ============================================================
        // JANELA MINIMIZADA (params separados)
        // ============================================================
        val minParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        floatingView = webContainer
        floatingView.tag = params
        minimizedButton.tag = minParams

        // barra de arrastar (topo)
        val dragBar = View(this).apply {
            setBackgroundColor(0xFFff0044.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 40
            )
            setOnTouchListener(DragListener(params))
        }
        (floatingView as FrameLayout).addView(dragBar)

        windowManager.addView(floatingView, params)
    }

    private fun minimize() {
        if (isMinimized) return
        isMinimized = true
        windowManager.removeView(floatingView)
        windowManager.addView(minimizedButton, minimizedButton.tag as WindowManager.LayoutParams)
    }

    private fun expand() {
        if (!isMinimized) return
        isMinimized = false
        windowManager.removeView(minimizedButton)
        windowManager.addView(floatingView, floatingView.tag as WindowManager.LayoutParams)
    }

    override fun onDestroy() {
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
        if (::minimizedButton.isInitialized) {
            try { windowManager.removeView(minimizedButton) } catch (_: Exception) {}
        }
        instance = null
        super.onDestroy()
    }

    // listener de drag
    private inner class DragListener(private val params: WindowManager.LayoutParams) :
        View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    return true
                }
            }
            return false
        }
    }
}