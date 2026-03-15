package ai.openanonymity.android.bridge

object SaveFilePickerPolyfill {
    const val JS_INTERFACE_NAME = "oaSavePickerNative"
    const val JS_INTERFACE_FALLBACK_NAME = "oaSavePickerAndroid"
    const val CALLBACK_NAME = "__oaAndroidSavePickerNativeResult"

    fun documentStartScript(): String {
        return """
            (() => {
              if (window.showSaveFilePicker && window.${CALLBACK_NAME}) {
                return;
              }

              const pending = new Map();

              const makeToken = () => `oa-save-${'$'}{Date.now()}-${'$'}{Math.random().toString(16).slice(2)}`;

              const toText = async (chunk) => {
                if (chunk == null) return '';
                if (typeof chunk === 'string') return chunk;
                if (chunk instanceof Blob) return chunk.text();
                if (chunk instanceof ArrayBuffer) return new TextDecoder().decode(new Uint8Array(chunk));
                if (ArrayBuffer.isView(chunk)) return new TextDecoder().decode(chunk);
                if (typeof chunk === 'object' && 'data' in chunk) return toText(chunk.data);
                return String(chunk);
              };

              window.${CALLBACK_NAME} = (rawMessage) => {
                try {
                  const payload = typeof rawMessage === 'string' ? JSON.parse(rawMessage) : rawMessage;
                  const pendingRequest = pending.get(payload.token);
                  if (!pendingRequest) return;
                  pending.delete(payload.token);
                  if (payload.saved) {
                    pendingRequest.resolve();
                  } else {
                    pendingRequest.reject(new DOMException(payload.error || 'The user aborted a request.', 'AbortError'));
                  }
                } catch (error) {
                  console.error('oa-android save picker callback failed', error);
                }
              };

              window.showSaveFilePicker = async function(options = {}) {
                const token = makeToken();
                const accept = options.types?.[0]?.accept || {};
                const mimeType = Object.keys(accept)[0] || 'application/octet-stream';
                const suggestedName = options.suggestedName || 'download.json';
                let body = '';
                const bridge = (
                  (window.${JS_INTERFACE_NAME} && typeof window.${JS_INTERFACE_NAME}.postMessage === 'function')
                    ? window.${JS_INTERFACE_NAME}
                    : ((window.${JS_INTERFACE_FALLBACK_NAME} && typeof window.${JS_INTERFACE_FALLBACK_NAME}.postMessage === 'function')
                        ? window.${JS_INTERFACE_FALLBACK_NAME}
                        : null)
                );

                return {
                  async createWritable() {
                    return {
                      async write(chunk) {
                        body += await toText(chunk);
                      },
                      async close() {
                        if (!bridge) {
                          throw new DOMException('Native save bridge is unavailable.', 'NotSupportedError');
                        }
                        const completion = new Promise((resolve, reject) => pending.set(token, { resolve, reject }));
                        bridge.postMessage(JSON.stringify({
                          token,
                          suggestedName,
                          mimeType,
                          body,
                        }));
                        return completion;
                      },
                      async abort() {
                        pending.delete(token);
                        throw new DOMException('The user aborted a request.', 'AbortError');
                      }
                    };
                  }
                };
              };
            })();
        """.trimIndent()
    }
}
