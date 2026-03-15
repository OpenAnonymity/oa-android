package ai.openanonymity.android.bridge

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

private data class ThemeSurfacePayload(
    val backgroundColor: Int,
)

class ThemeSurfaceBridge(
    private val webView: WebView,
    private val applySurfaceColor: (Int) -> Unit,
) {
    @SuppressLint("JavascriptInterface")
    fun attach() {
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun postMessage(rawMessage: String) {
                    val payload = runCatching { parsePayload(rawMessage) }.getOrNull() ?: return
                    webView.post {
                        applySurfaceColor(payload.backgroundColor)
                    }
                }
            },
            JS_INTERFACE_NAME,
        )
    }

    fun ensurePageObserver() {
        webView.post {
            webView.evaluateJavascript(observerScript(), null)
        }
    }

    private fun parsePayload(rawMessage: String): ThemeSurfacePayload {
        val json = JSONObject(rawMessage)
        return ThemeSurfacePayload(
            backgroundColor = Color.parseColor(json.getString("backgroundColor")),
        )
    }

    companion object {
        private const val JS_INTERFACE_NAME = "oaThemeSurfaceAndroid"

        private fun observerScript(): String {
            return """
                (() => {
                  const bridge = window.${JS_INTERFACE_NAME};
                  if (!bridge || typeof bridge.postMessage !== 'function') {
                    return;
                  }

                  const installKey = '__oaAndroidThemeSurfaceInstalled';
                  const syncKey = '__oaAndroidThemeSurfaceSync';

                  const normalizeHex = (value) => {
                    if (!value || typeof value !== 'string') {
                      return '#ffffff';
                    }
                    const color = value.trim();
                    if (/^#[0-9a-f]{6}$/i.test(color)) {
                      return color;
                    }
                    if (/^#[0-9a-f]{3}$/i.test(color)) {
                      return '#' + color[1] + color[1] + color[2] + color[2] + color[3] + color[3];
                    }
                    const match = color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
                    if (!match) {
                      return '#ffffff';
                    }
                    return '#' + [match[1], match[2], match[3]]
                      .map(function(part) { return Number(part).toString(16).padStart(2, '0'); })
                      .join('');
                  };

                  const readBackgroundColor = () => {
                    const root = document.documentElement;
                    const body = document.body;
                    const bodyColor = body ? getComputedStyle(body).backgroundColor : '';
                    const rootColor = root ? getComputedStyle(root).backgroundColor : '';
                    return normalizeHex(bodyColor || rootColor || '#ffffff');
                  };

                  if (window[installKey]) {
                    window[syncKey]?.();
                    return;
                  }

                  let scheduled = false;
                  let bodyObserved = false;

                  const sync = () => {
                    scheduled = false;
                    try {
                      bridge.postMessage(JSON.stringify({
                        backgroundColor: readBackgroundColor(),
                      }));
                    } catch (error) {
                      console.error('oa-android theme surface sync failed', error);
                    }
                  };

                  const requestSync = () => {
                    if (scheduled) {
                      return;
                    }
                    scheduled = true;
                    requestAnimationFrame(sync);
                  };

                  const observer = new MutationObserver(requestSync);
                  observer.observe(document.documentElement, {
                    attributes: true,
                    attributeFilter: ['class', 'data-theme', 'style'],
                  });

                  const observeBody = () => {
                    if (!document.body || bodyObserved) {
                      return;
                    }
                    observer.observe(document.body, {
                      attributes: true,
                      attributeFilter: ['class', 'style'],
                    });
                    bodyObserved = true;
                  };

                  if (document.body) {
                    observeBody();
                  } else {
                    const bodyObserver = new MutationObserver(() => {
                      if (!document.body) {
                        return;
                      }
                      observeBody();
                      requestSync();
                      bodyObserver.disconnect();
                    });
                    bodyObserver.observe(document.documentElement, { childList: true, subtree: true });
                  }

                  document.addEventListener('DOMContentLoaded', () => {
                    observeBody();
                    requestSync();
                  }, { once: true });
                  window.addEventListener('load', requestSync);

                  window[syncKey] = requestSync;
                  window[installKey] = true;
                  requestSync();
                })();
            """.trimIndent()
        }
    }
}
