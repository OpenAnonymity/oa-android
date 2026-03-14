package ai.openanonymity.android

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewStoragePersistenceTest {
    @Test
    fun localStorageAndIndexedDbSurviveActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var firstActivity: MainActivity
            scenario.onActivity { firstActivity = it }
            WebViewTestUtils.waitForPageReady(firstActivity)
            WebViewTestUtils.evaluateJavascript(
                firstActivity.webViewForTesting(),
                """
                    window.__oaStorageWrite = 'pending';
                    (async () => {
                      localStorage.setItem('oa-android-local', 'kept');
                      await new Promise((resolve, reject) => {
                        const request = indexedDB.open('oa-android-test-db', 1);
                        request.onupgradeneeded = () => request.result.createObjectStore('kv');
                        request.onerror = () => reject(request.error);
                        request.onsuccess = () => {
                          const db = request.result;
                          const tx = db.transaction('kv', 'readwrite');
                          tx.objectStore('kv').put('persisted', 'key');
                          tx.oncomplete = () => {
                            db.close();
                            resolve();
                          };
                          tx.onerror = () => reject(tx.error);
                        };
                      });
                      window.__oaStorageWrite = 'done';
                    })();
                """.trimIndent()
            )
            WebViewTestUtils.waitUntil {
                WebViewTestUtils.evaluateJavascript(
                    firstActivity.webViewForTesting(),
                    "window.__oaStorageWrite"
                ) == "done"
            }

            scenario.recreate()

            lateinit var recreatedActivity: MainActivity
            scenario.onActivity { recreatedActivity = it }
            WebViewTestUtils.waitForPageReady(recreatedActivity)
            WebViewTestUtils.evaluateJavascript(
                recreatedActivity.webViewForTesting(),
                """
                    window.__oaStorageRead = 'pending';
                    (async () => {
                      const localValue = localStorage.getItem('oa-android-local');
                      const indexedValue = await new Promise((resolve, reject) => {
                        const request = indexedDB.open('oa-android-test-db', 1);
                        request.onerror = () => reject(request.error);
                        request.onsuccess = () => {
                          const db = request.result;
                          const tx = db.transaction('kv', 'readonly');
                          const getReq = tx.objectStore('kv').get('key');
                          getReq.onerror = () => reject(getReq.error);
                          getReq.onsuccess = () => {
                            db.close();
                            resolve(getReq.result || '');
                          };
                        };
                      });
                      window.__oaStorageRead = JSON.stringify({ localValue, indexedValue });
                    })();
                """.trimIndent()
            )

            WebViewTestUtils.waitUntil {
                val value = WebViewTestUtils.evaluateJavascript(
                    recreatedActivity.webViewForTesting(),
                    "window.__oaStorageRead"
                )
                value != null && value.startsWith("{")
            }

            val result = WebViewTestUtils.evaluateJavascript(
                recreatedActivity.webViewForTesting(),
                "window.__oaStorageRead"
            )
            assertEquals("""{"localValue":"kept","indexedValue":"persisted"}""", result)
        }
    }
}
