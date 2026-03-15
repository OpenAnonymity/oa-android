package ai.openanonymity.android.background

import android.content.Context
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object NativeInferenceRegistry {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val executor = Executors.newCachedThreadPool()
    private val jobs = ConcurrentHashMap<String, NativeInferenceJob>()

    fun startJob(
        context: Context,
        request: NativeInferenceStartRequest,
    ): String {
        val jobId = "oa-android-${UUID.randomUUID()}"
        val job = NativeInferenceJob(
            id = jobId,
            request = request,
        )
        jobs[jobId] = job
        InferenceForegroundService.sync(context.applicationContext)

        executor.execute {
            try {
                if (request.debugMock) {
                    runDebugMock(job)
                } else {
                    runHttpRequest(job)
                }
            } finally {
                job.active = false
                InferenceForegroundService.sync(context.applicationContext)
            }
        }

        return jobId
    }

    fun pollEvents(jobId: String, afterSequence: Long): Pair<List<NativeInferenceEvent>, Boolean> {
        val job = jobs[jobId] ?: error("Unknown native inference job: $jobId")
        val events = synchronized(job.events) {
            job.events.filter { it.sequence > afterSequence }
        }
        return events to job.terminal
    }

    fun cancelJob(jobId: String) {
        val job = jobs[jobId] ?: return
        job.cancelled = true
        job.call?.cancel()
        appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
    }

    fun activeJobCount(): Int {
        return jobs.values.count { it.active }
    }

    private fun runHttpRequest(job: NativeInferenceJob) {
        if (job.cancelled || job.terminal) {
            appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
            return
        }

        val requestBody = job.request.body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val requestBuilder = Request.Builder()
            .url(job.request.url)

        job.request.headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        requestBuilder.method(job.request.method, requestBody)

        val call = httpClient.newCall(requestBuilder.build())
        job.call = call

        try {
            call.execute().use { response ->
                if (job.cancelled) {
                    appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
                    return
                }

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    appendTerminalEvent(
                        job,
                        type = "failed",
                        status = response.code,
                        message = errorBody?.takeIf { it.isNotBlank() } ?: "HTTP error ${response.code}",
                    )
                    return
                }

                appendEvent(job, type = "stream-open", status = response.code)

                val reader = response.body?.charStream()?.buffered()
                    ?: run {
                        appendTerminalEvent(job, type = "failed", status = response.code, message = "Missing response body.")
                        return
                    }

                reader.use { stream ->
                    while (true) {
                        if (job.cancelled) {
                            appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
                            return
                        }

                        val line = stream.readLine() ?: break
                        appendEvent(job, type = "sse-line", line = line)
                    }
                }

                appendEvent(job, type = "sse-line", line = "data: [DONE]")
                appendTerminalEvent(job, type = "completed")
            }
        } catch (error: IOException) {
            if (job.cancelled) {
                appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
                return
            }

            appendTerminalEvent(job, type = "failed", message = error.message ?: "Network request failed.")
        }
    }

    private fun runDebugMock(job: NativeInferenceJob) {
        if (job.cancelled) {
            appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
            return
        }

        appendEvent(job, type = "stream-open", status = 200)
        sleepWithCancellation(job, 300)
        if (job.terminal) return

        appendEvent(
            job,
            type = "sse-line",
            line = """data: {"type":"response.reasoning.delta","delta":"Thinking through the request... "}""",
        )
        sleepWithCancellation(job, 500)
        if (job.terminal) return

        appendEvent(
            job,
            type = "sse-line",
            line = """data: {"type":"response.reasoning.delta","delta":"still working in the background."}""",
        )
        sleepWithCancellation(job, 500)
        if (job.terminal) return

        appendEvent(
            job,
            type = "sse-line",
            line = """data: {"choices":[{"delta":{"content":"Background-native streaming stayed alive. "}}]}""",
        )
        sleepWithCancellation(job, 500)
        if (job.terminal) return

        appendEvent(
            job,
            type = "sse-line",
            line = """data: {"choices":[{"delta":{"content":"Home and return should not interrupt this response."}}]}""",
        )
        sleepWithCancellation(job, 200)

        appendEvent(
            job,
            type = "sse-line",
            line = """data: {"usage":{"prompt_tokens":7,"completion_tokens":24,"total_tokens":31},"model":"openai/gpt-5.2-chat"}""",
        )
        appendEvent(job, type = "sse-line", line = "data: [DONE]")
        appendTerminalEvent(job, type = "completed")
    }

    private fun sleepWithCancellation(job: NativeInferenceJob, millis: Long) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            if (job.cancelled) {
                appendTerminalEvent(job, type = "cancelled", message = "The operation was aborted.")
                return
            }
            Thread.sleep(50)
        }
    }

    private fun appendEvent(
        job: NativeInferenceJob,
        type: String,
        line: String? = null,
        status: Int? = null,
        message: String? = null,
        code: String? = null,
        allowWhenTerminal: Boolean = false,
    ) {
        if (job.terminal && !allowWhenTerminal) {
            return
        }
        synchronized(job.events) {
            job.events.add(
                NativeInferenceEvent(
                    sequence = job.nextSequence.getAndIncrement(),
                    type = type,
                    line = line,
                    status = status,
                    message = message,
                    code = code,
                )
            )
        }
    }

    private fun appendTerminalEvent(
        job: NativeInferenceJob,
        type: String,
        status: Int? = null,
        message: String? = null,
        code: String? = null,
    ) {
        if (job.terminal) {
            return
        }
        job.terminal = true
        appendEvent(
            job = job,
            type = type,
            status = status,
            message = message,
            code = code,
            allowWhenTerminal = true,
        )
    }
}

private data class NativeInferenceJob(
    val id: String,
    val request: NativeInferenceStartRequest,
    val events: MutableList<NativeInferenceEvent> = mutableListOf(),
    val nextSequence: AtomicLong = AtomicLong(1),
    @Volatile var terminal: Boolean = false,
    @Volatile var active: Boolean = true,
    @Volatile var cancelled: Boolean = false,
    @Volatile var call: Call? = null,
)
