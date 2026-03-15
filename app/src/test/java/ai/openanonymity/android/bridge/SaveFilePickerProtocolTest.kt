package ai.openanonymity.android.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFilePickerProtocolTest {
    @Test
    fun `request parser keeps suggested filename and body`() {
        val request = SaveFilePickerProtocol.parseRequest(
            """
                {
                  "token": "tok-1",
                  "suggestedName": "tickets.json",
                  "mimeType": "application/json",
                  "body": "{\"active\":[]}"
                }
            """.trimIndent()
        )

        assertEquals("tok-1", request.token)
        assertEquals("tickets.json", request.suggestedName)
        assertEquals("application/json", request.mimeType)
        assertEquals("{\"active\":[]}", request.body)
    }

    @Test
    fun `callback javascript reports saved true`() {
        val script = SaveFilePickerProtocol.toJavascript(
            SaveFilePickerResult(token = "tok-2", saved = true)
        )

        assertTrue(script.contains(SaveFilePickerPolyfill.CALLBACK_NAME))
        assertTrue(script.contains("\\\"saved\\\":true"))
        assertTrue(script.contains("\\\"token\\\":\\\"tok-2\\\""))
    }

    @Test
    fun `callback javascript reports saved false`() {
        val script = SaveFilePickerProtocol.toJavascript(
            SaveFilePickerResult(token = "tok-3", saved = false, error = "cancelled")
        )

        assertTrue(script.contains("\\\"saved\\\":false"))
        assertTrue(script.contains("\\\"error\\\":\\\"cancelled\\\""))
    }
}
