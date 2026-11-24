package com.example.telegramalramrelay.data

import android.content.Context
import java.io.File

object PendingImageStore {
    private const val DIR_NAME = "pending_images"
    private const val MAX_FILES = 20
    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    fun save(context: Context, logId: String, bytes: ByteArray): String? {
        if (bytes.isEmpty() || logId.isBlank()) return null
        return try {
            val dir = File(context.applicationContext.cacheDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            pruneOldFiles(dir)
            val safeId = logId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(dir, "$safeId.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    fun readBytes(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (file.exists() && file.isFile && file.length() > 0L) {
                file.readBytes()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (_: Throwable) {}
    }

    fun clearAll(context: Context) {
        try {
            val dir = File(context.applicationContext.cacheDir, DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    private fun pruneOldFiles(dir: File) {
        val now = System.currentTimeMillis()
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val remaining = mutableListOf<File>()
        for (file in files) {
            if (now - file.lastModified() > MAX_AGE_MILLIS) {
                file.delete()
            } else {
                remaining.add(file)
            }
        }
        if (remaining.size >= MAX_FILES) {
            remaining.sortBy { it.lastModified() }
            val deleteCount = remaining.size - (MAX_FILES - 1)
            for (i in 0 until deleteCount) {
                remaining[i].delete()
            }
        }
    }
}
