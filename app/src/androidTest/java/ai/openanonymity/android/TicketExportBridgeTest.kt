package ai.openanonymity.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TicketExportBridgeTest {
    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun ticketExportCancelKeepsTickets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )

            val result = runTicketExport(activity)
            assertEquals("""{"success":false,"remaining":1,"cancelled":true}""", result)
        }
    }

    @Test
    fun ticketExportSuccessClearsTicketsAfterSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            val outputFile = File(activity.cacheDir, "tickets-export.json").apply {
                if (exists()) delete()
            }
            val outputUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                outputFile,
            )

            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        data = outputUri
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            )

            val result = runTicketExport(activity)
            assertEquals("""{"success":true,"remaining":0,"cancelled":false}""", result)
            assertTrue(outputFile.readText().contains("\"exportType\": \"tickets\""))
        }
    }

    private fun runTicketExport(activity: MainActivity): String? {
        WebViewTestUtils.waitForPageReady(activity)
        WebViewTestUtils.evaluateJavascript(
            activity.webViewForTesting(),
            """
                window.__oaTicketExportResult = 'pending';
                window.__oaTicketExportReady = false;
                (() => {
                  const existingScript = document.getElementById('oa-test-export-module');
                  if (existingScript) existingScript.remove();
                  const existingButton = document.getElementById('oa-test-export-button');
                  if (existingButton) existingButton.remove();

                  const script = document.createElement('script');
                  script.id = 'oa-test-export-module';
                  script.type = 'module';
                  script.textContent = `
                    import ticketStore from '/services/ticketStore.js';
                    import * as exportsModule from '/services/globalExport.js';

                    (async () => {
                      try {
                        await ticketStore.setActiveTickets([{ finalized_ticket: 'ticket-1' }]);
                        const button = document.createElement('button');
                        button.id = 'oa-test-export-button';
                        button.textContent = 'Export tickets';
                        button.style.position = 'fixed';
                        button.style.left = '24px';
                        button.style.top = '24px';
                        button.style.width = '240px';
                        button.style.height = '56px';
                        button.style.zIndex = '2147483647';
                        button.style.background = '#ffffff';
                        button.style.color = '#000000';
                        button.addEventListener('click', async () => {
                          const result = await exportsModule.exportTickets();
                          const remaining = ticketStore.getCount();
                          window.__oaTicketExportResult = JSON.stringify({
                            success: !!result.success,
                            remaining,
                            cancelled: !!result.cancelled,
                          });
                        });
                        document.body.appendChild(button);
                        window.__oaTicketExportReady = true;
                      } catch (error) {
                        window.__oaTicketExportResult = JSON.stringify({
                          success: false,
                          remaining: -1,
                          cancelled: false,
                          error: String(error),
                        });
                      }
                    })();
                  `;
                  document.body.appendChild(script);
                })();
            """.trimIndent()
        )
        WebViewTestUtils.waitUntil {
            WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                "window.__oaTicketExportReady"
            ) == "true"
        }
        WebViewTestUtils.tapElement(activity.webViewForTesting(), "#oa-test-export-button")

        WebViewTestUtils.waitUntil {
            val value = WebViewTestUtils.evaluateJavascript(
                activity.webViewForTesting(),
                "window.__oaTicketExportResult"
            )
            value != null && value.startsWith("{")
        }

        return WebViewTestUtils.evaluateJavascript(
            activity.webViewForTesting(),
            "window.__oaTicketExportResult"
        )
    }
}
