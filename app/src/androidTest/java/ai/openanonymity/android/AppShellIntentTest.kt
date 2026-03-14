package ai.openanonymity.android

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellIntentTest {
    @Test
    fun bundledAppLoadsOnProductionOrigin() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(OaAppOrigin.ROOT_URL, activity.lastRequestedUrl)
            }
        }
    }

    @Test
    fun shareLinkLoadsUnchangedIntoWebView() {
        val shareUrl = "https://chat.openanonymity.ai/?s=share-123"
        ActivityScenario.launch<MainActivity>(
            Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl))
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(shareUrl, activity.lastRequestedUrl)
            }
        }
    }

    @Test
    fun ticketLinkLoadsUnchangedIntoWebView() {
        val ticketUrl = "https://chat.openanonymity.ai/tickets/ABCDEFGHIJKLMNOPQRSTUVWX"
        ActivityScenario.launch<MainActivity>(
            Intent(Intent.ACTION_VIEW, Uri.parse(ticketUrl))
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(ticketUrl, activity.lastRequestedUrl)
            }
        }
    }
}
