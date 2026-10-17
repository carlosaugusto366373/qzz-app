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

    // Listener para quando o usuário aperta "Permitir" no popup do Shizuku
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 100) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            runOnUiThread {
                webView.evaluateJavascript("window.adbResult($granted, 'Permissão: $granted')", null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Registra o listener do Shizuku
        Shizuku.addRequestPermissionResultListener(permissionListener)

        NativeAim.setTouchListener { x, y ->
            AimAccessibilityService.instance?.moveTouch(x, y)
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
                    val dm = resources.displayMetrics
                    NativeAim.setScreen(dm.widthPixels, dm.heightPixels)
                    NativeAim.setAim(true)
                    NativeAim.init()
                }

                @JavascriptInterface
                fun stopAim() {
                    NativeAim.setAim(false)
                }

                @JavascriptInterface
                fun setEsp(on: Boolean) {
                    NativeAim.setEsp(on)
                }
            }, "QZZAim")

            // ============================================================
            // PONTE SHIZUKU (ADB sem root)
            // ============================================================
            addJavascriptInterface(object {
                @JavascriptInterface
                fun requestShizuku() {
                    runOnUiThread {
                        AdbManager.checkPermission { granted ->
                            if (granted) {
                                webView.evaluateJavascript("window.adbResult(true, 'Shizuku conectado!')", null)
                            } else {
                                AdbManager.requestPermission()
                                webView.evaluateJavascript("window.adbResult(false, 'Aguardando permissão...')", null)
                            }
                        }
                    }
                }

                @JavascriptInterface
                fun sendCommand(cmd: String) {
                    AdbManager.executeCommand(cmd)
                }
            }, "QZZAdb")

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}