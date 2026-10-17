package com.qzz.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Listener do Shizuku
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 100) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            runOnUiThread {
                if (::webView.isInitialized) {
                    webView.evaluateJavascript("window.adbResult($granted, 'Permissão: $granted')", null)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Tenta registrar o listener do Shizuku (SEM CRASHAR se o app Shizuku não estiver aberto)
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Exception) {
            // Ignora
        }

        // 2. Tenta inicializar o NativeAim (SEM CRASHAR se a lib C++ não existir)
        try {
            NativeAim.setTouchListener { x, y ->
                AimAccessibilityService.instance?.moveTouch(x, y)
            }
        } catch (e: Exception) {
            // Ignora
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(object {
                @JavascriptInterface
                fun moveTouch(x: Float, y: Float) {
                    AimAccessibilityService.instance?.moveTouch(x, y)
                }

                @JavascriptInterface
                fun openOverlay() {
                    runOnUiThread {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.canDrawOverlays(this@MainActivity)) {
                            val i = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(i)
                        } else {
                            OverlayService.start(this@MainActivity)
                        }
                    }
                }
            }, "AndroidAim")

            addJavascriptInterface(object {
                @JavascriptInterface
                fun startAim() {
                    try {
                        val dm = resources.displayMetrics
                        NativeAim.setScreen(dm.widthPixels, dm.heightPixels)
                        NativeAim.setAim(true)
                        NativeAim.init()
                    } catch (e: Exception) { }
                }

                @JavascriptInterface
                fun stopAim() {
                    try {
                        NativeAim.setAim(false)
                    } catch (e: Exception) { }
                }

                @JavascriptInterface
                fun setEsp(on: Boolean) {
                    try {
                        NativeAim.setEsp(on)
                    } catch (e: Exception) { }
                }
            }, "QZZAim")

            // ============================================================
            // PONTE SHIZUKU (ADB sem root)
            // ============================================================
            addJavascriptInterface(object {
                @JavascriptInterface
                fun requestShizuku() {
                    runOnUiThread {
                        try {
                            AdbManager.checkPermission { granted ->
                                if (granted) {
                                    webView.evaluateJavascript("window.adbResult(true, 'Shizuku conectado!')", null)
                                } else {
                                    AdbManager.requestPermission()
                                    webView.evaluateJavascript("window.adbResult(false, 'Aguardando permissão...')", null)
                                }
                            }
                        } catch (e: Exception) {
                            webView.evaluateJavascript("window.adbResult(false, 'Shizuku não está rodando. Abra o app Shizuku primeiro.')", null)
                        }
                    }
                }

                @JavascriptInterface
                fun sendCommand(cmd: String) {
                    try {
                        AdbManager.executeCommand(cmd)
                    } catch (e: Exception) { }
                }
            }, "QZZAdb")

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}