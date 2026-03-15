package ai.openanonymity.android

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        ActivityScenario.launch<MainActivity>(appLinkIntent(shareUrl)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(shareUrl, activity.lastRequestedUrl)
            }
        }
    }

    @Test
    fun ticketLinkLoadsUnchangedIntoWebView() {
        val ticketUrl = "https://chat.openanonymity.ai/tickets/ABCDEFGHIJKLMNOPQRSTUVWX"
        ActivityScenario.launch<MainActivity>(appLinkIntent(ticketUrl)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(ticketUrl, activity.lastRequestedUrl)
            }
        }
    }

    @Test
    fun appLinkIntentResolvesToMainActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolved = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.openanonymity.ai/?s=share-123")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                `package` = context.packageName
            },
            0,
        )

        assertNotNull(resolved)
        assertEquals(context.packageName, resolved?.activityInfo?.packageName)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
    }

    private fun appLinkIntent(url: String): Intent {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setClass(context, MainActivity::class.java)
        }
    }
}
