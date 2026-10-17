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

        try { AdbManager.init(this) } catch (e: Exception) { }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
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
                            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            startActivity(i)
                        } else {
                            OverlayService.start(this@MainActivity)
                        }
                    }
                }

                @JavascriptInterface
                fun openDebuggingSettings() {
                    runOnUiThread {
                        try {
                            // Abre a tela de Opções do Desenvolvedor
                            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (e: Exception) {
                            try {
                                // Fallback se o acima não funcionar
                                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            } catch (e2: Exception) { }
                        }
                    }
                }

                @JavascriptInterface
                fun pairAdb(ip: String, pairPort: String, code: String, connectPort: String) {
                    try {
                        AdbManager.pairAndConnect(ip, pairPort.toInt(), code, connectPort.toInt()) { ok, msg ->
                            runOnUiThread {
                                webView.evaluateJavascript("window.adbResult($ok, '$msg')", null)
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            webView.evaluateJavascript("window.adbResult(false, 'Erro: ${e.message}')", null)
                        }
                    }
                }
            }, "QZZAdb")

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }
}