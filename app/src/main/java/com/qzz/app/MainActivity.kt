package com.qzz.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val REQ_CAPTURE = 1001
    private val REQ_NOTIF = 1002

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler = Handler(Looper.getMainLooper())
    private var screenW = 0
    private var screenH = 0
    private var screenDpi = 0

    private var reuseBmp: Bitmap? = null
    private var reuseBuffer: ByteBuffer? = null

    @Volatile private var captureRunning = false
    @Volatile private var aimEnabled = false

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
                    runOnUiThread { requestNotifThenCapture() }
                }

                @JavascriptInterface
                fun stopCapture() {
                    runOnUiThread { stopCaptureInternal() }
                }

                @JavascriptInterface
                fun setAimEnabled(enabled: Boolean) {
                    aimEnabled = enabled
                }
            }, "AndroidAim")

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    private fun requestNotifThenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
                )
                return
            }
        }
        requestCapture()
    }

    private fun requestCapture() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) requestCapture()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            startCaptureInternal(resultCode, data)
            webView.evaluateJavascript("window.captureStarted && window.captureStarted()", null)
        } else {
            webView.evaluateJavascript("window.captureDenied && window.captureDenied()", null)
        }
    }

    private fun startCaptureInternal(resultCode: Int, data: Intent) {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        screenDpi = metrics.densityDpi

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data) ?: return

        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection!!.createVirtualDisplay(
            "qzz-capture",
            screenW, screenH, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        captureRunning = true
        handler.post(loop)
    }

    private fun stopCaptureInternal() {
        captureRunning = false
        handler.removeCallbacks(loop)
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
        imageReader?.close()
        imageReader = null
        reuseBmp?.recycle()
        reuseBmp = null
        reuseBuffer = null
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!captureRunning) return
            try { processFrame() } catch (_: Exception) {}
            handler.postDelayed(this, 66)
        }
    }

    private fun processFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenW
            val bmpW = screenW + rowPadding / pixelStride

            var bmp = reuseBmp
            if (bmp == null || bmp.width != bmpW || bmp.height != screenH) {
                bmp?.recycle()
                bmp = Bitmap.createBitmap(bmpW, screenH, Bitmap.Config.ARGB_8888)
                reuseBmp = bmp
                reuseBuffer = ByteBuffer.allocateDirect(buffer.capacity())
            }

            val dst = reuseBuffer!!
            dst.clear()
            buffer.rewind()
            dst.put(buffer)
            dst.rewind()
            bmp!!.copyPixelsFromBuffer(dst)

            if (aimEnabled) AimDetector.process(bmp, screenW, screenH)
        } finally {
            image.close()
        }
    }

    override fun onDestroy() {
        stopCaptureInternal()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}