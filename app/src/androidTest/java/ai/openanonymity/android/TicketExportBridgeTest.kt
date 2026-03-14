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
                (async () => {
                  const ticketStore = (await import('/services/ticketStore.js')).default;
                  await ticketStore.setActiveTickets([{ finalized_ticket: 'ticket-1' }]);
                  const exports = await import('/services/globalExport.js');
                  const result = await exports.exportTickets();
                  const remaining = ticketStore.getCount();
                  window.__oaTicketExportResult = JSON.stringify({
                    success: !!result.success,
                    remaining,
                    cancelled: !!result.cancelled,
                  });
                })();
            """.trimIndent()
        )

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
