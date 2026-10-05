package com.jax.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * The real on-device accessibility service backing automation.
 *
 * Exposes a process-wide singleton [instance] for the adapter. Event-driven
 * detection is intentionally a no-op - the adapter polls via [currentRoot].
 */
class JaxAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JaxAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = "${context.packageName}/.accessibility.JaxAccessibilityService"
            return enabled.contains(component, ignoreCase = true)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op: event-driven detection is future work; the adapter polls.
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun currentRoot(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Takes a screenshot of the default display and returns it as PNG bytes,
     * or null on failure / timeout.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotPng(timeoutMs: Long = 8000): ByteArray? =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val executor = Executor { command -> handler.post(command) }
            val timeoutRunnable = Runnable {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { handler.removeCallbacks(timeoutRunnable) }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        handler.removeCallbacks(timeoutRunnable)
                        var bytes: ByteArray? = null
                        try {
                            val hw = result.hardwareBuffer
                            try {
                                val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                                val argb = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                wrapped?.recycle()
                                if (argb != null) {
                                    val out = ByteArrayOutputStream()
                                    if (argb.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                                        bytes = out.toByteArray()
                                    }
                                    argb.recycle()
                                }
                            } finally {
                                hw.close()
                            }
                        } catch (e: Exception) {
                            bytes = null
                        }
                        if (cont.isActive) cont.resume(bytes)
                    }

                    override fun onFailure(errorCode: Int) {
                        handler.removeCallbacks(timeoutRunnable)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }
}
