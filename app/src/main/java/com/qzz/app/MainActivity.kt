package com.qzz.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val REQ_CAPTURE = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                fun startCapture() {
                    runOnUiThread { requestCapturePermission() }
                }

                @JavascriptInterface
                fun stopCapture() {
                    ScreenCaptureService.stop(this@MainActivity)
                }

                @JavascriptInterface
                fun setAimEnabled(enabled: Boolean) {
                    ScreenCaptureService.aimEnabled = enabled
                }
            }, "AndroidAim")

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    private fun requestCapturePermission() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(this, resultCode, data)
            webView.evaluateJavascript("window.captureStarted && window.captureStarted()", null)
        } else {
            webView.evaluateJavascript("window.captureDenied && window.captureDenied()", null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}