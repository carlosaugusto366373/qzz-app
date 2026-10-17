package com.qzz.app

import android.annotation.SuppressLint
import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o ADB com o contexto do app
        AdbManager.init(this)

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
            // PONTE ADB WI-FI
            // ============================================================
            addJavascriptInterface(object {
                @JavascriptInterface
                fun pairAdb(ip: String, pairPort: String, code: String, connectPort: String) {
                    try {
                        AdbManager.pairAndConnect(
                            ip = ip,
                            pairPort = pairPort.toInt(),
                            pairCode = code,
                            connectPort = connectPort.toInt()
                        ) { ok, msg ->
                            runOnUiThread {
                                webView.evaluateJavascript(
                                    "window.adbResult($ok, '$msg')", null
                                )
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            webView.evaluateJavascript(
                                "window.adbResult(false, 'Erro: ${e.message}')", null
                            )
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

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}