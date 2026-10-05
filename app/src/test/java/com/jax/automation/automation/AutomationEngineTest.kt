package com.jax.automation.automation

import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.AutomationLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class MockAutomationAdapter(
    var openAppHandler: (String) -> ActionResult = { ActionResult(true) },
    var openUrlHandler: (String) -> ActionResult = { ActionResult(true) },
    var clickHandler: (Selector) -> ActionResult = { ActionResult(true) },
    var typeTextHandler: (Selector, String) -> ActionResult = { _, _ -> ActionResult(true) },
    var clearTextHandler: (Selector) -> ActionResult = { ActionResult(true) },
    var scrollHandler: (Selector, ScrollDirection) -> ActionResult = { _, _ -> ActionResult(true) },
    var swipeHandler: (Int, Int, Int, Int, Int) -> ActionResult =
        { _, _, _, _, _ -> ActionResult(true) },
    var longPressHandler: (Selector) -> ActionResult = { ActionResult(true) },
    var backHandler: () -> ActionResult = { ActionResult(true) },
    var homeHandler: () -> ActionResult = { ActionResult(true) },
    var waitForTextHandler: (String, Long) -> ActionResult = { _, _ -> ActionResult(true) },
    var waitForElementHandler: (Selector, Long) -> ActionResult = { _, _ -> ActionResult(true) },
    var waitForElementGoneHandler: (Selector, Long) -> ActionResult =
        { _, _ -> ActionResult(true) },
    var screenshotHandler: () -> ByteArray? = { null },
    var readScreenHandler: () -> ScreenSnapshot = { ScreenSnapshot(emptyList(), 0L) },
    var uploadFileHandler: (Selector, File) -> ActionResult = { _, _ -> ActionResult(true) }
) : AutomationAdapter {

    val calls = mutableListOf<String>()

    override suspend fun openApp(packageName: String): ActionResult {
        calls.add("openApp")
        return openAppHandler(packageName)
    }

    override suspend fun openUrl(url: String): ActionResult {
        calls.add("openUrl")
        return openUrlHandler(url)
    }

    override suspend fun click(sel: Selector): ActionResult {
        calls.add("click")
        return clickHandler(sel)
    }

    override suspend fun typeText(sel: Selector, text: String): ActionResult {
        calls.add("typeText")
        return typeTextHandler(sel, text)
    }

    override suspend fun clearText(sel: Selector): ActionResult {
        calls.add("clearText")
        return clearTextHandler(sel)
    }

    override suspend fun scroll(sel: Selector, direction: ScrollDirection): ActionResult {
        calls.add("scroll")
        return scrollHandler(sel, direction)
    }

    override suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int
    ): ActionResult {
        calls.add("swipe")
        return swipeHandler(x1, y1, x2, y2, durationMs)
    }

    override suspend fun longPress(sel: Selector): ActionResult {
        calls.add("longPress")
        return longPressHandler(sel)
    }

    override suspend fun back(): ActionResult {
        calls.add("back")
        return backHandler()
    }

    override suspend fun home(): ActionResult {
        calls.add("home")
        return homeHandler()
    }

    override suspend fun waitForText(text: String, timeoutMs: Long): ActionResult {
        calls.add("waitForText")
        return waitForTextHandler(text, timeoutMs)
    }

    override suspend fun waitForElement(sel: Selector, timeoutMs: Long): ActionResult {
        calls.add("waitForElement")
        return waitForElementHandler(sel, timeoutMs)
    }

    override suspend fun waitForElementGone(sel: Selector, timeoutMs: Long): ActionResult {
        calls.add("waitForElementGone")
        return waitForElementGoneHandler(sel, timeoutMs)
    }

    override suspend fun screenshot(): ByteArray? {
        calls.add("screenshot")
        return screenshotHandler()
    }

    override suspend fun readScreen(): ScreenSnapshot {
        calls.add("readScreen")
        return readScreenHandler()
    }

    override suspend fun uploadFile(sel: Selector, file: File): ActionResult {
        calls.add("uploadFile")
        return uploadFileHandler(sel, file)
    }
}

private class FakeJaxLogger : JaxLogger {
    override val liveLogs: StateFlow<List<AutomationLogEntry>> =
        MutableStateFlow(emptyList())

    override fun d(tag: String, message: String, taskId: String?) = Unit
    override fun i(tag: String, message: String, taskId: String?) = Unit
    override fun w(tag: String, message: String, taskId: String?) = Unit
    override fun e(tag: String, message: String, taskId: String?, throwable: Throwable?) = Unit
}

class AutomationEngineTest {

    private fun engine(
        adapter: MockAutomationAdapter,
        onAskUser: suspend (String) -> Unit = {}
    ): AutomationEngine = AutomationEngine(adapter, FakeJaxLogger(), onAskUser)

    @Test
    fun happyPathExecutesAllStepsInOrder() = runTest {
        val adapter = MockAutomationAdapter()
        val result = engine(adapter).execute(
            "task-1",
            listOf(
                PlannedAction(
                    AutomationAction.OPEN_APP,
                    packageName = "com.example.flow",
                    retries = 0
                ),
                PlannedAction(
                    AutomationAction.CLICK,
                    selector = Selector(text = "Generate"),
                    retries = 0
                ),
                PlannedAction(
                    AutomationAction.TYPE,
                    selector = Selector(text = "Prompt"),
                    text = "a still pond",
                    retries = 0
                ),
                PlannedAction(
                    AutomationAction.WAIT_FOR_TEXT,
                    text = "Done",
                    retries = 0
                )
            )
        )
        assertTrue(result.ok)
        assertNull(result.error)
        assertEquals(listOf("openApp", "click", "typeText", "waitForText"), adapter.calls)
    }

    @Test
    fun failedClickRetriesThenSucceeds() = runTest {
        var clicks = 0
        val adapter = MockAutomationAdapter(clickHandler = {
            clicks++
            if (clicks == 1) {
                ActionResult(false, "not found", ErrorCodes.ELEMENT_NOT_FOUND)
            } else {
                ActionResult(true)
            }
        })
        val result = engine(adapter).execute(
            "task-1",
            listOf(
                PlannedAction(
                    AutomationAction.CLICK,
                    selector = Selector(text = "Go"),
                    retries = 1
                )
            )
        )
        assertTrue(result.ok)
        assertEquals(2, adapter.calls.count { it == "click" })
    }

    @Test
    fun exhaustedRetriesReturnAdapterErrorCode() = runTest {
        val adapter = MockAutomationAdapter(clickHandler = {
            ActionResult(false, "boom", ErrorCodes.ELEMENT_NOT_FOUND)
        })
        val result = engine(adapter).execute(
            "task-1",
            listOf(
                PlannedAction(
                    AutomationAction.CLICK,
                    selector = Selector(text = "Go"),
                    retries = 1
                )
            )
        )
        assertFalse(result.ok)
        assertNotNull(result.error)
        assertEquals(ErrorCodes.ELEMENT_NOT_FOUND, result.error!!.errorCode)
    }

    @Test
    fun askUserInvokesCallbackAndFailsWithUserActionRequired() = runTest {
        var asked: String? = null
        val eng = engine(MockAutomationAdapter()) { message -> asked = message }
        val result = eng.execute(
            "task-1",
            listOf(PlannedAction(AutomationAction.ASK_USER, message = "Need input", retries = 0))
        )
        assertEquals("Need input", asked)
        assertFalse(result.ok)
        assertEquals(ErrorCodes.USER_ACTION_REQUIRED, result.error?.errorCode)
    }

    @Test
    fun pauseStepFailsWithPausedCode() = runTest {
        val result = engine(MockAutomationAdapter()).execute(
            "task-1",
            listOf(PlannedAction(AutomationAction.PAUSE, message = "stop", retries = 0))
        )
        assertFalse(result.ok)
        assertEquals(ErrorCodes.PAUSED, result.error?.errorCode)
    }

    @Test
    fun downloadFileIsRejectedByGenericEngine() = runTest {
        val result = engine(MockAutomationAdapter()).execute(
            "task-1",
            listOf(
                PlannedAction(
                    AutomationAction.DOWNLOAD_FILE,
                    url = "https://example.com/f.png",
                    text = "f.png",
                    retries = 0
                )
            )
        )
        assertFalse(result.ok)
        assertEquals(ErrorCodes.DOWNLOAD_FAILED, result.error?.errorCode)
    }

    @Test
    fun typeWithoutSelectorIsInvalid() = runTest {
        val result = engine(MockAutomationAdapter()).execute(
            "task-1",
            listOf(PlannedAction(AutomationAction.TYPE, text = "hello", retries = 0))
        )
        assertFalse(result.ok)
        assertNotNull(result.error)
    }
}
