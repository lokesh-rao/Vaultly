package com.vaultly.core.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardHelper {
    private var clearJob: Job? = null

    fun copyWithTimeout(
        context: Context,
        label: String,
        value: String,
        timeoutSeconds: Int,
        scope: CoroutineScope
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        clearJob?.cancel()
        clearJob = scope.launch(Dispatchers.Main) {
            delay(timeoutSeconds.coerceIn(10, 300) * 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
