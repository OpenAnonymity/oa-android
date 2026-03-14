package ai.openanonymity.android

import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object WebViewTestUtils {
    fun waitForPageReady(activity: MainActivity, timeoutSeconds: Long = 15) {
        waitUntil(timeoutSeconds) {
            val readyState = evaluateJavascript(activity.webViewForTesting(), "document.readyState")
            readyState == "complete"
        }
    }

    fun evaluateJavascript(webView: WebView, script: String): String? {
        val latch = CountDownLatch(1)
        var rawResult: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) {
                rawResult = it
                latch.countDown()
            }
        }
        check(latch.await(15, TimeUnit.SECONDS)) { "Timed out waiting for evaluateJavascript." }
        val value = rawResult ?: return null
        return JSONArray("[$value]").get(0)?.toString()
    }

    fun waitUntil(timeoutSeconds: Long = 15, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(200)
        }
        error("Condition was not met within ${timeoutSeconds}s.")
    }
}
