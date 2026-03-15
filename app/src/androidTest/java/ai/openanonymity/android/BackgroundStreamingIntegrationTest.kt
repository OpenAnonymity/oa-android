package ai.openanonymity.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundStreamingIntegrationTest {
    @Test
    fun nativeStreamSurvivesHomeAndResume() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            WebViewTestUtils.waitForPageReady(activity)

            WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                """
                    window.__oaBackgroundStreamState = 'booting';
                    window.__oaBackgroundStreamReasoning = '';
                    window.__oaBackgroundStreamContent = '';
                    window.__oaBackgroundStreamError = null;
                    window.__oaBackgroundStreamOpened = false;
                    (async () => {
                      try {
                        window.__oaBackgroundStreamState = 'running';
                        const result = await window.openRouterAPI.streamCompletion(
                          [{ role: 'user', content: 'Test Android background streaming' }],
                          'openai/gpt-5.2-chat',
                          'oa-debug-stream',
                          (chunk) => {
                            if (chunk) {
                              window.__oaBackgroundStreamContent += chunk;
                            }
                          },
                          () => {},
                          [],
                          false,
                          null,
                          async () => {
                            window.__oaBackgroundStreamOpened = true;
                          },
                          async (reasoningChunk) => {
                            window.__oaBackgroundStreamReasoning += reasoningChunk;
                          },
                          true,
                          'medium'
                        );
                        window.__oaBackgroundStreamResult = JSON.stringify(result);
                        window.__oaBackgroundStreamState = 'completed';
                      } catch (error) {
                        window.__oaBackgroundStreamError = String(error);
                        window.__oaBackgroundStreamState = 'failed';
                      }
                    })();
                """.trimIndent()
            )

            WebViewTestUtils.waitUntil {
                WebViewTestUtils.evaluateJavascript(
                    activity.webViewForTesting(),
                    "window.__oaBackgroundStreamOpened"
                ) == "true"
            }

            backgroundApp()
            Thread.sleep(2400)
            reopenApp()

            scenario.onActivity { activity = it }
            WebViewTestUtils.waitUntil(timeoutSeconds = 20) {
                WebViewTestUtils.evaluateJavascript(
                    activity.webViewForTesting(),
                    "window.__oaBackgroundStreamState"
                ) == "completed"
            }

            val reasoning = WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                "window.__oaBackgroundStreamReasoning"
            ) ?: ""
            val content = WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                "window.__oaBackgroundStreamContent"
            ) ?: ""
            val error = WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                "window.__oaBackgroundStreamError"
            )

            assertTrue(reasoning.contains("Thinking through the request"))
            assertTrue(content.contains("Background-native streaming stayed alive."))
            assertTrue(content.contains("Home and return should not interrupt this response."))
            assertTrue(error == null || error == "null")
        } finally {
            runCatching { scenario.close() }
        }
    }

    private fun backgroundApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun reopenApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
