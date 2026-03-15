package ai.openanonymity.android.background

import org.json.JSONArray
import org.json.JSONObject

data class NativeInferenceStartRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String,
    val debugMock: Boolean,
)

data class NativeInferencePollRequest(
    val jobId: String,
    val afterSequence: Long,
)

data class NativeInferenceCancelRequest(
    val jobId: String,
)

data class NativeInferenceEvent(
    val sequence: Long,
    val type: String,
    val line: String? = null,
    val status: Int? = null,
    val message: String? = null,
    val code: String? = null,
)

object NativeInferenceProtocol {
    fun parseStartRequest(rawMessage: String): NativeInferenceStartRequest {
        val json = JSONObject(rawMessage)
        val headers = mutableMapOf<String, String>()
        val rawHeaders = json.optJSONObject("headers")
        if (rawHeaders != null) {
            rawHeaders.keys().forEach { key ->
                headers[key] = rawHeaders.optString(key)
            }
        }

        return NativeInferenceStartRequest(
            url = json.getString("url"),
            method = json.optString("method", "POST").ifBlank { "POST" },
            headers = headers,
            body = json.optString("body"),
            debugMock = json.optBoolean("debugMock", false),
        )
    }

    fun parsePollRequest(rawMessage: String): NativeInferencePollRequest {
        val json = JSONObject(rawMessage)
        return NativeInferencePollRequest(
            jobId = json.getString("jobId"),
            afterSequence = json.optLong("afterSequence", 0L),
        )
    }

    fun parseCancelRequest(rawMessage: String): NativeInferenceCancelRequest {
        val json = JSONObject(rawMessage)
        return NativeInferenceCancelRequest(
            jobId = json.getString("jobId"),
        )
    }

    fun startResponse(jobId: String): String {
        return JSONObject()
            .put("jobId", jobId)
            .toString()
    }

    fun pollResponse(events: List<NativeInferenceEvent>, terminal: Boolean): String {
        val payload = JSONArray()
        events.forEach { event ->
            payload.put(
                JSONObject()
                    .put("sequence", event.sequence)
                    .put("type", event.type)
                    .put("line", event.line)
                    .put("status", event.status)
                    .put("message", event.message)
                    .put("code", event.code)
            )
        }

        return JSONObject()
            .put("events", payload)
            .put("terminal", terminal)
            .toString()
    }

    fun okResponse(): String {
        return JSONObject().put("ok", true).toString()
    }

    fun errorResponse(message: String): String {
        return JSONObject().put("error", message).toString()
    }
}
