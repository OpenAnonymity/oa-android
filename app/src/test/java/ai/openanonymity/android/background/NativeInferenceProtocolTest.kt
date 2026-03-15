package ai.openanonymity.android.background

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInferenceProtocolTest {
    @Test
    fun parseStartRequestPreservesHeadersAndBody() {
        val request = NativeInferenceProtocol.parseStartRequest(
            """
                {
                  "url": "https://openrouter.ai/api/v1/chat/completions",
                  "method": "POST",
                  "headers": {
                    "Authorization": "Bearer test",
                    "Content-Type": "application/json"
                  },
                  "body": "{\"model\":\"openai/gpt-5.2-chat\"}",
                  "debugMock": true
                }
            """.trimIndent()
        )

        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url)
        assertEquals("POST", request.method)
        assertEquals("Bearer test", request.headers["Authorization"])
        assertEquals("{\"model\":\"openai/gpt-5.2-chat\"}", request.body)
        assertTrue(request.debugMock)
    }

    @Test
    fun pollResponseIncludesEventsAndTerminalFlag() {
        val json = JSONObject(
            NativeInferenceProtocol.pollResponse(
                events = listOf(
                    NativeInferenceEvent(
                        sequence = 3,
                        type = "sse-line",
                        line = "data: {\"ok\":true}",
                    )
                ),
                terminal = false,
            )
        )

        assertFalse(json.getBoolean("terminal"))
        val event = json.getJSONArray("events").getJSONObject(0)
        assertEquals(3L, event.getLong("sequence"))
        assertEquals("sse-line", event.getString("type"))
        assertEquals("data: {\"ok\":true}", event.getString("line"))
    }
}
