package ai.openanonymity.android.files

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class OaFileChooserController(
    activity: ComponentActivity,
) {
    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var allowMultiple = false

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val callback = pendingCallback
        pendingCallback = null
        if (callback == null) {
            return@registerForActivityResult
        }

        if (uris.isNullOrEmpty()) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }

        val payload = if (allowMultiple) uris.toTypedArray() else arrayOf(uris.first())
        callback.onReceiveValue(payload)
    }

    fun show(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
    ): Boolean {
        if (callback == null) return false

        pendingCallback?.onReceiveValue(null)
        pendingCallback = callback
        allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

        val mimeTypes = params
            ?.acceptTypes
            ?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            ?.distinct()
            ?.toTypedArray()
            ?: emptyArray()

        launcher.launch(if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes)
        return true
    }

    fun cancelPending() {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = null
    }
}
