package ai.openanonymity.android

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
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

    fun tapElement(webView: WebView, selector: String) {
        val descriptor = evaluateJavascript(
            webView,
            """
                (() => {
                  const element = document.querySelector(${JSONObject.quote(selector)});
                  if (!element) {
                    return null;
                  }
                  const rect = element.getBoundingClientRect();
                  return JSON.stringify({
                    centerX: (rect.left + rect.right) / 2,
                    centerY: (rect.top + rect.bottom) / 2,
                    devicePixelRatio: window.devicePixelRatio || 1,
                  });
                })();
            """.trimIndent()
        ) ?: error("Unable to find DOM element for selector: $selector")

        val point = JSONObject(descriptor)
        val dpr = point.optDouble("devicePixelRatio", 1.0).toFloat()
        val localX = point.getDouble("centerX").toFloat() * dpr
        val localY = point.getDouble("centerY").toFloat() * dpr

        val webViewLocation = IntArray(2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.getLocationOnScreen(webViewLocation)
        }

        val screenX = webViewLocation[0] + localX
        val screenY = webViewLocation[1] + localY
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val upTime = downTime + 60
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, screenX, screenY, 0)
        val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, screenX, screenY, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN

        try {
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
            instrumentation.waitForIdleSync()
        } finally {
            down.recycle()
            up.recycle()
        }
    }
}
