package com.jax.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.jax.automation.automation.ActionResult
import com.jax.automation.automation.AutomationAdapter
import com.jax.automation.automation.ErrorCodes
import com.jax.automation.automation.ScreenSnapshot
import com.jax.automation.automation.ScrollDirection
import com.jax.automation.automation.Selector
import com.jax.automation.models.now
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real on-device implementation of [AutomationAdapter] driven by
 * [JaxAccessibilityService]. Every action fails with PERMISSION_MISSING
 * when the accessibility service is not enabled.
 */
class AccessibilityAutomationAdapter(private val context: Context) : AutomationAdapter {

    companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
    }

    private fun service(): JaxAccessibilityService? = JaxAccessibilityService.instance

    private fun notEnabled(): ActionResult =
        ActionResult(false, "JAX accessibility service is not enabled", ErrorCodes.PERMISSION_MISSING)

    private fun notFound(): ActionResult =
        ActionResult(false, "No element found for selector", ErrorCodes.ELEMENT_NOT_FOUND)

    @Suppress("DEPRECATION")
    override suspend fun openApp(packageName: String): ActionResult {
        return try {
            service() ?: return notEnabled()
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent == null) {
                val code = if (packageName == CHROME_PACKAGE) ErrorCodes.CHROME_NOT_FOUND
                else ErrorCodes.UNKNOWN_SCREEN
                return ActionResult(false, "No launch intent for package $packageName", code)
            }
            context.startActivity(intent)
            ActionResult(true, "Opened $packageName")
        } catch (e: Exception) {
            val code = if (packageName == CHROME_PACKAGE) ErrorCodes.CHROME_NOT_FOUND
            else ErrorCodes.UNKNOWN_SCREEN
            ActionResult(false, e.message ?: "Failed to open app $packageName", code)
        }
    }

    override suspend fun openUrl(url: String): ActionResult {
        return try {
            service() ?: return notEnabled()
            val normalized = if (url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
            ) {
                url
            } else {
                "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
            ActionResult(true, "Opened $normalized")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Failed to open URL $url", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun click(sel: Selector): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val node = NodeSearch.find(svc.currentRoot(), sel) ?: return notFound()
            val target = NodeSearch.findClickable(node) ?: node
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(300)
            ActionResult(true, "Clicked")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Click failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun typeText(sel: Selector, text: String): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val node = NodeSearch.find(svc.currentRoot(), sel) ?: return notFound()
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val done = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (done) {
                ActionResult(true, "Typed text")
            } else {
                ActionResult(false, "System rejected the set-text action", ErrorCodes.ELEMENT_NOT_FOUND)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Type text failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun clearText(sel: Selector): ActionResult = typeText(sel, "")

    override suspend fun scroll(sel: Selector, direction: ScrollDirection): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val node = NodeSearch.find(svc.currentRoot(), sel) ?: return notFound()
            when (direction) {
                ScrollDirection.UP -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                ScrollDirection.DOWN -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                ScrollDirection.LEFT, ScrollDirection.RIGHT -> {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val y = rect.centerY()
                    val startX = if (direction == ScrollDirection.LEFT) rect.right - 10 else rect.left + 10
                    val endX = if (direction == ScrollDirection.LEFT) rect.left + 10 else rect.right - 10
                    swipe(startX, y, endX, y, 400)
                    return ActionResult(true, "Swiped ${direction.name}")
                }
            }
            delay(300)
            ActionResult(true, "Scrolled ${direction.name}")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Scroll failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val path = Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            }
            val stroke = AccessibilityService.GestureDescription.StrokeDescription(
                path, 0, durationMs.toLong()
            )
            val gesture = AccessibilityService.GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            val latch = CountDownLatch(1)
            var completed = false
            val dispatched = svc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: AccessibilityService.GestureDescription?) {
                        completed = true
                        latch.countDown()
                    }

                    override fun onCancelled(gestureDescription: AccessibilityService.GestureDescription?) {
                        latch.countDown()
                    }
                },
                null
            )
            if (!dispatched) {
                return ActionResult(false, "Gesture was not dispatched", ErrorCodes.UNKNOWN_SCREEN)
            }
            val finished = latch.await(durationMs + 3000L, TimeUnit.MILLISECONDS)
            if (finished && completed) {
                ActionResult(true, "Swipe completed")
            } else {
                ActionResult(false, "Swipe gesture timed out or was cancelled", ErrorCodes.UNKNOWN_SCREEN)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Swipe failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun longPress(sel: Selector): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val node = NodeSearch.find(svc.currentRoot(), sel) ?: return notFound()
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            delay(300)
            ActionResult(true, "Long-pressed")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Long-press failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun back(): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(300)
            ActionResult(true, "Pressed back")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Back action failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun home(): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            delay(300)
            ActionResult(true, "Pressed home")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Home action failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun waitForText(text: String, timeoutMs: Long): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (true) {
                val texts = NodeSearch.collectTexts(svc.currentRoot())
                if (texts.any { it.contains(text, ignoreCase = true) }) {
                    return ActionResult(true, "Found text: $text")
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    return ActionResult(
                        false,
                        "Timed out waiting for text: $text",
                        ErrorCodes.ELEMENT_NOT_FOUND
                    )
                }
                delay(300)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Wait for text failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun waitForElement(sel: Selector, timeoutMs: Long): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (true) {
                if (NodeSearch.find(svc.currentRoot(), sel) != null) {
                    return ActionResult(true, "Element appeared")
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    return ActionResult(
                        false,
                        "Timed out waiting for element",
                        ErrorCodes.ELEMENT_NOT_FOUND
                    )
                }
                delay(300)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Wait for element failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    override suspend fun waitForElementGone(sel: Selector, timeoutMs: Long): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (true) {
                if (NodeSearch.find(svc.currentRoot(), sel) == null) {
                    return ActionResult(true, "Element is gone")
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    return ActionResult(
                        false,
                        "Timed out waiting for element to disappear",
                        ErrorCodes.ELEMENT_NOT_FOUND
                    )
                }
                delay(300)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Wait for element gone failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }

    @SuppressLint("NewApi")
    override suspend fun screenshot(): ByteArray? {
        return try {
            val svc = service() ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) svc.takeScreenshotPng() else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun readScreen(): ScreenSnapshot {
        val texts = NodeSearch.collectTexts(service()?.currentRoot())
        return ScreenSnapshot(texts, now())
    }

    override suspend fun uploadFile(sel: Selector, file: File): ActionResult {
        return try {
            val svc = service() ?: return notEnabled()
            val node = NodeSearch.find(svc.currentRoot(), sel) ?: return notFound()
            val target = NodeSearch.findClickable(node) ?: node
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ActionResult(
                false,
                "File picker opened - select the file manually. Programmatic file selection is not reliably automatable.",
                ErrorCodes.USER_ACTION_REQUIRED
            )
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Upload failed", ErrorCodes.UNKNOWN_SCREEN)
        }
    }
}
