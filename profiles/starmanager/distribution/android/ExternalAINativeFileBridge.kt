package com.armsone.starmanager.ui.externalai

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** 공개 WebChromeClient 파일 패널 계약으로 정규화된 전체 사진 배치를 전달한다. */
class ExternalAINativeAttachmentBatch private constructor(
    private val directory: File?,
    val uris: List<Uri>
) {
    private var nextSingleIndex = 0

    @Synchronized
    fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        parameters: WebChromeClient.FileChooserParams
    ): Boolean {
        if (uris.isEmpty()) return false
        Log.d("AIBIFileChooser", "request mode=${parameters.mode} prepared=${uris.size} next=$nextSingleIndex")
        if (parameters.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            nextSingleIndex = uris.size
            callback.onReceiveValue(uris.toTypedArray())
        } else {
            val next = uris.getOrNull(nextSingleIndex)
            if (next == null) {
                callback.onReceiveValue(null)
            } else {
                nextSingleIndex += 1
                callback.onReceiveValue(arrayOf(next))
                Log.d("AIBIFileChooser", "delivered single index=$nextSingleIndex")
            }
        }
        return true
    }

    @Synchronized
    fun resetDelivery() {
        nextSingleIndex = 0
    }

    fun dispose() {
        directory?.deleteRecursively()
    }

    companion object {
        val EMPTY = ExternalAINativeAttachmentBatch(null, emptyList())

        fun prepare(context: Context, attachments: List<ExternalAIAttachment>): ExternalAINativeAttachmentBatch {
            if (attachments.isEmpty()) return EMPTY
            require(attachments.size in 1..8)
            val root = File(context.cacheDir, "aibi")
            root.mkdirs()
            root.listFiles()?.filter { it.isDirectory }?.forEach { stale ->
                if (System.currentTimeMillis() - stale.lastModified() > 15 * 60 * 1_000L) stale.deleteRecursively()
            }
            val directory = File(root, "batch-${UUID.randomUUID()}").apply { mkdirs() }
            return try {
                val uris = attachments.sortedBy { it.sourceIndex }.mapIndexed { index, attachment ->
                    val file = File(directory, "aibi-${(index + 1).toString().padStart(2, '0')}.jpg")
                    file.writeBytes(attachment.data)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
                ExternalAINativeAttachmentBatch(directory, uris)
            } catch (error: Exception) {
                directory.deleteRecursively()
                throw error
            }
        }
    }
}
