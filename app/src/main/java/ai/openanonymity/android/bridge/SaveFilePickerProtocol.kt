package ai.openanonymity.android.bridge

import org.json.JSONObject

data class SaveFilePickerRequest(
    val token: String,
    val suggestedName: String,
    val mimeType: String,
    val body: String,
)

data class SaveFilePickerResult(
    val token: String,
    val saved: Boolean,
    val error: String? = null,
)

object SaveFilePickerProtocol {
    fun parseRequest(rawMessage: String): SaveFilePickerRequest {
        val json = JSONObject(rawMessage)
        return SaveFilePickerRequest(
            token = json.getString("token"),
            suggestedName = json.optString("suggestedName", "download.json"),
            mimeType = json.optString("mimeType", "application/octet-stream"),
            body = json.optString("body", ""),
        )
    }

    fun toJavascript(result: SaveFilePickerResult): String {
        val payload = JSONObject()
            .put("token", result.token)
            .put("saved", result.saved)
            .put("error", result.error)
            .toString()

        return "window.${SaveFilePickerPolyfill.CALLBACK_NAME} && " +
            "window.${SaveFilePickerPolyfill.CALLBACK_NAME}(${JSONObject.quote(payload)});"
    }
}
