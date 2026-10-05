package com.jax.automation.browser

import android.content.Context
import com.jax.automation.automation.ActionResult
import com.jax.automation.automation.AutomationAdapter
import com.jax.automation.automation.ErrorCodes
import com.jax.automation.logging.JaxLogger

/**
 * Thin Chrome facade over [AutomationAdapter].
 *
 * All device-level work (launching the app, opening URLs) is delegated to
 * the injected [AutomationAdapter]; this class only adds Chrome-specific
 * presence checks and error mapping.
 */
class ChromeAdapter(
    private val context: Context,
    private val adapter: AutomationAdapter,
    private val logger: JaxLogger
) {

    fun isChromeInstalled(): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) != null
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun openChrome(): ActionResult {
        logger.i(TAG, "Opening Google Chrome")
        val r = adapter.openApp(PACKAGE_NAME)
        if (!r.ok) {
            logger.e(TAG, "Chrome could not be opened: ${r.message}")
            return r.copy(
                errorCode = ErrorCodes.CHROME_NOT_FOUND,
                message = "Google Chrome is not installed or could not be opened. " +
                    "Install Chrome to use JAX browser automation."
            )
        }
        logger.i(TAG, "Chrome opened")
        return r
    }

    suspend fun openUrl(url: String): ActionResult {
        val chrome = openChrome()
        if (!chrome.ok) return chrome
        logger.i(TAG, "Opening URL: $url")
        val r = adapter.openUrl(url)
        if (!r.ok) {
            logger.e(TAG, "Could not open URL $url: ${r.message}")
        } else {
            logger.i(TAG, "URL opened: $url")
        }
        return r
    }

    companion object {
        const val PACKAGE_NAME = "com.android.chrome"
        private const val TAG = "Chrome"
    }
}
