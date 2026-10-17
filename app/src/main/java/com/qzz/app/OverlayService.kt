package com.qzz.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: FrameLayout
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

        minimizedButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(30, 30, 30, 30)
            setOnClickListener { expand() }
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true

            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION")
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            setBackgroundColor(0xEE000000.toInt())

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(object {
                @JavascriptInterface
                fun minimize() {
                    this@OverlayService.minimize()
                }
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

            loadUrl("file:///android_asset/index.html?overlay=1")
        }

        val scale = 0.7f
        webView.setInitialScale((scale * 100).toInt())

        floatingView = FrameLayout(this).apply {
            setBackgroundColor(0xEE000000.toInt())
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            (resources.displayMetrics.heightPixels * 0.6f).toInt(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val minParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        floatingView.tag = params
        minimizedButton.tag = minParams

        // ============================================================
        // DRAG LISTENER (Arrastar pela barra do topo)
        // ============================================================
        // O cálculo de y < 150 (pixels) garante que só ativa o arrasto se
        // o dedo do usuário estiver tocando a barra vermelha no topo do HTML.
        webView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Checa se o toque foi na área da barra do topo
                        if (event.y < 150) {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = true
                            return true // Consome o evento
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingView, params)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            isDragging = false
                            return true
                        }
                    }
                }
                return false
            }
        })

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
}