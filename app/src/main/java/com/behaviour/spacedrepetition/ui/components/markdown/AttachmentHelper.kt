package com.behaviour.spacedrepetition.ui.components.markdown

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

object AttachmentHelper {
    private const val ATTACHMENTS_DIR = "attachments"

    /**
     * Saves an image from a content Uri to the local internal storage.
     * Returns the name of the saved file or null if save failed.
     */
    fun saveImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri)) ?: "jpg"
            val fileName = "img_${System.currentTimeMillis()}.$extension"
            
            val dir = File(context.filesDir, ATTACHMENTS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val destFile = File(dir, fileName)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Retrieves the File object for a given local filename in the attachments folder.
     */
    fun getAttachmentFile(context: Context, fileName: String): File {
        val dir = File(context.filesDir, ATTACHMENTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName)
    }
}
