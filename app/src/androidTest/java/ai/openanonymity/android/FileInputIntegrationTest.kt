package ai.openanonymity.android

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileInputIntegrationTest {
    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun fileInputSelectionReachesDom() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            val sampleFile = File(activity.cacheDir, "upload.txt").apply {
                writeText("open anonymity")
            }
            val sampleUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                sampleFile,
            )

            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        data = sampleUri
                        clipData = ClipData.newUri(activity.contentResolver, "upload", sampleUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            )

            WebViewTestUtils.waitForPageReady(activity)
            WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                """
                    window.__oaUploadResult = 'pending';
                    (() => {
                      const existing = document.getElementById('oa-test-file-input');
                      if (existing) existing.remove();

                      const input = document.createElement('input');
                      input.id = 'oa-test-file-input';
                      input.type = 'file';
                      input.style.position = 'fixed';
                      input.style.left = '24px';
                      input.style.top = '24px';
                      input.style.width = '240px';
                      input.style.height = '56px';
                      input.style.opacity = '1';
                      input.style.zIndex = '2147483647';
                      input.style.background = '#ffffff';
                      input.style.color = '#000000';
                      input.onchange = async () => {
                        const file = input.files && input.files[0];
                        window.__oaUploadResult = JSON.stringify({
                          count: input.files ? input.files.length : 0,
                          name: file ? file.name : null,
                          text: file ? await file.text() : null,
                        });
                      };
                      document.body.appendChild(input);
                    })();
                """.trimIndent()
            )
            WebViewTestUtils.waitUntil {
                WebViewTestUtils.evaluateJavascript(
                    activity.webViewForTesting(),
                    "!!document.getElementById('oa-test-file-input')"
                ) == "true"
            }
            WebViewTestUtils.tapElement(activity.webViewForTesting(), "#oa-test-file-input")

            WebViewTestUtils.waitUntil {
                val value = WebViewTestUtils.evaluateJavascript(
                    activity.webViewForTesting(),
                    "window.__oaUploadResult"
                )
                value != null && value.startsWith("{")
            }

            val result = WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                "window.__oaUploadResult"
            )
            assertEquals(
                """{"count":1,"name":"upload.txt","text":"open anonymity"}""",
                result
            )
        }
    }
}
