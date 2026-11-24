package com.example.telegramalramrelay.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(128)

    fun getCached(packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        return cache.get(packageName)
    }

    suspend fun getOrLoad(context: Context, packageName: String, sizePx: Int = 96): ImageBitmap? {
        if (packageName.isBlank()) return null
        cache.get(packageName)?.let { return it }

        val appCtx = context.applicationContext
        return withContext(Dispatchers.IO) {
            cache.get(packageName)?.let { return@withContext it }
            try {
                val drawable = appCtx.packageManager.getApplicationIcon(packageName)
                val targetSize = sizePx.coerceIn(24, 192)
                val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, targetSize, targetSize)
                drawable.draw(canvas)
                val imageBitmap = bitmap.asImageBitmap()
                cache.put(packageName, imageBitmap)
                imageBitmap
            } catch (_: Throwable) {
                null
            }
        }
    }
}
