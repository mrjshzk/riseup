package com.mrjshzk.riseup

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The camera gate. Full-bleed preview, three letterbox bands, one frame, one line of
 * state. Spec: docs/ui-spec.md §4. No toast, no shutter, no dialog.
 */
class ScanActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENROLL = "enroll"
        private const val REQ_CAMERA = 91
        private const val TAG = "RiseUpScan"
        private const val WRONG_MS = 1_600L
        private const val ACCEPT_MS = 400L
        private const val FAIL_MS = 1_200L
    }

    private lateinit var ui: Ui
    private lateinit var previewView: PreviewView
    private lateinit var scanner: BarcodeScanner
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var enrolling = false
    private var handled = false
    private var camera: Camera? = null
    private var torchOn = false

    private var statusView: TextView? = null
    private var frameView: View? = null
    private var torchView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        enrolling = intent.getBooleanExtra(EXTRA_ENROLL, false)
        scanner = BarcodeScanning.getClient()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Dismissing starts from the lock screen; the scan must open there too.
        // Enrolling changes the code, so it stays behind the keyguard.
        if (!enrolling) setShowWhenLocked(true)

        val root = FrameLayout(this).apply {
            setBackgroundColor(ui.c(R.color.riseup_bg))
        }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(overlay())
        setContentView(root)
        paintWindow()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    // ---------------------------------------------------------------- layout

    /** scrim / 180 dp strip / scrim, with the text column living in the bottom band. */
    private fun overlay(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        column.addView(
            View(this).apply { setBackgroundColor(ui.c(R.color.riseup_scrim)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val strip = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(180)
            )
        }
        val frame = View(this).apply {
            background = ui.block(Color.TRANSPARENT, ui.c(R.color.riseup_accent), 2)
        }
        frameView = frame
        strip.addView(
            frame,
            FrameLayout.LayoutParams(ui.dp(300), ui.dp(180), Gravity.CENTER)
        )
        column.addView(strip)

        val bottom = FrameLayout(this).apply {
            setBackgroundColor(ui.c(R.color.riseup_scrim))
        }
        bottom.addView(
            textColumn(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        column.addView(
            bottom,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.15f)
        )
        return column
    }

    private fun textColumn(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(24))
        }
        // Any inset change re-applies the gutter plus the nav-bar inset.
        column.setOnApplyWindowInsetsListener { v, insets ->
            var left = 0
            var right = 0
            var bottom = 0
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                left = bars.left
                right = bars.right
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(ui.dp(24) + left, 0, ui.dp(24) + right, ui.dp(24) + bottom)
            insets
        }

        column.addView(
            ui.headline(
                getString(if (enrolling) R.string.scan_title_enroll else R.string.scan_title),
                ui.c(R.color.riseup_text)
            )
        )
        column.addView(ui.spacer(8))

        val hintRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(48)
            )
        }
        hintRow.addView(
            ui.body(getString(R.string.scan_hint), ui.c(R.color.riseup_text_dim)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val torch = ui.textAction(getString(R.string.scan_torch), widthDp = 96, flat = true)
        torch.visibility = View.GONE
        torchView = torch
        torch.setOnClickListener { toggleTorch() }
        hintRow.addView(torch)
        column.addView(hintRow)

        column.addView(ui.spacer(4))
        val status = ui.label("", ui.c(R.color.riseup_text_dim)).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(18)
            )
        }
        statusView = status
        column.addView(status)
        column.addView(ui.spacer(24))

        if (enrolling) {
            val cancel = ui.textAction(getString(R.string.scan_cancel), widthDp = 96, flat = true)
            cancel.gravity = Gravity.CENTER
            cancel.setOnClickListener { finish() }
            column.addView(
                cancel,
                LinearLayout.LayoutParams(ui.dp(96), ui.dp(48))
            )
        }
        column.addView(ui.spacer(16))
        return column
    }

    // ---------------------------------------------------------------- camera

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            fail(getString(R.string.scan_status_camera_denied), ui.c(R.color.riseup_text_dim))
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy -> analyse(proxy) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                // Never ship a disabled control: no flash unit, no LIGHT action.
                torchView?.visibility =
                    if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "camera start failed", e)
                fail(getString(R.string.scan_status_camera_failed), ui.c(R.color.riseup_text_dim))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        torchOn = !torchOn
        try {
            cam.cameraControl.enableTorch(torchOn)
        } catch (e: Exception) {
            torchOn = false
            torchView?.visibility = View.GONE
            return
        }
        torchView?.text = getString(if (torchOn) R.string.scan_torch_on else R.string.scan_torch)
        torchView?.setTextColor(
            ui.c(if (torchOn) R.color.riseup_accent else R.color.riseup_text_dim)
        )
    }

    private fun analyse(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (value != null) onCode(value)
            }
            .addOnCompleteListener { proxy.close() }
    }

    // ---------------------------------------------------------------- states

    private fun onCode(value: String) {
        if (handled) return
        if (enrolling) {
            handled = true
            Prefs.setBarcode(this, value)
            accept()
            return
        }
        val wanted = Prefs.barcode(this)
        if (wanted != null && wanted == value) {
            handled = true
            Prefs.setScanOkAt(this, System.currentTimeMillis())
            accept()
        } else {
            wrong()
        }
    }

    /** Inline, 1.6 s, in the same place every time. Camera keeps running. */
    private fun wrong() {
        val status = statusView ?: return
        status.text = getString(R.string.scan_status_wrong)
        status.setTextColor(ui.c(R.color.riseup_accent))
        status.visibility = View.VISIBLE
        frameView?.background = ui.block(Color.TRANSPARENT, ui.c(R.color.riseup_accent), 2)
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, WRONG_MS)
    }

    private val hideStatus = Runnable {
        statusView?.visibility = View.GONE
    }

    private fun accept() {
        val status = statusView ?: return
        status.text = getString(R.string.scan_status_ok)
        status.setTextColor(ui.c(R.color.riseup_success))
        status.visibility = View.VISIBLE
        frameView?.background = ui.block(Color.TRANSPARENT, ui.c(R.color.riseup_success), 2)
        val hold = if (ValueAnimator.areAnimatorsEnabled()) ACCEPT_MS else 0L
        handler.postDelayed({
            setResult(RESULT_OK)
            finish()
        }, hold)
    }

    /** A dead end states the fact and leaves; no modal, no apology. */
    private fun fail(message: String, color: Int) {
        val status = statusView ?: return
        status.text = message
        status.setTextColor(color)
        status.visibility = View.VISIBLE
        frameView?.background = ui.block(Color.TRANSPARENT, ui.c(R.color.riseup_line), 2)
        handler.postDelayed({ finish() }, FAIL_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
        scanner.close()
        super.onDestroy()
    }
}
