package com.example.telegramalramrelay.domain

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

data class ExtractedImageSources(
    val bitmap: Bitmap? = null,
    val pictureIcon: Icon? = null,
    val imageUris: List<Uri> = emptyList()
) {
    val hasAnySource: Boolean
        get() = bitmap != null || pictureIcon != null || imageUris.isNotEmpty()
}

object NotificationImageExtractor {
    private const val DEFAULT_MAX_DIMENSION = 1920
    private const val MAX_TELEGRAM_PHOTO_BYTES = 9 * 1024 * 1024

    fun computeInSampleSize(width: Int, height: Int, maxDimension: Int = DEFAULT_MAX_DIMENSION): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var inSampleSize = 1
        var halfW = width
        var halfH = height
        while ((halfW / inSampleSize) > maxDimension * 2 || (halfH / inSampleSize) > maxDimension * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun computeScaledDimensions(
        width: Int,
        height: Int,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxDimension <= 0) {
            return Pair(width.coerceAtLeast(1), height.coerceAtLeast(1))
        }
        val longest = maxOf(width, height)
        if (longest <= maxDimension) return Pair(width, height)
        val scale = maxDimension.toDouble() / longest.toDouble()
        val scaledW = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledH = (height * scale).roundToInt().coerceAtLeast(1)
        return Pair(scaledW, scaledH)
    }

    fun extractImageSources(notification: Notification): ExtractedImageSources {
        val extras = notification.extras ?: return ExtractedImageSources()

        val pictureIcon: Icon? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(Notification.EXTRA_PICTURE_ICON) as? Icon
                }
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        val pictureBitmap: Bitmap? = try {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
            }
            bmp?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        } catch (_: Throwable) {
            null
        }

        val uris = LinkedHashSet<Uri>()

        // MessagingStyle attachments (reverse order -> newest first)
        try {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            style?.messages?.asReversed()?.forEach { msg ->
                val mime = msg.dataMimeType?.lowercase()?.trim()
                val uri = msg.dataUri
                if (mime != null && mime.startsWith("image/") && uri != null) {
                    uris.add(uri)
                }
            }
        } catch (_: Throwable) {}

        // Raw EXTRA_MESSAGES fallback
        try {
            val rawArray: Array<Bundle>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    ?.mapNotNull { it as? Bundle }
                    ?.toTypedArray()
            }
            rawArray?.reversed()?.forEach { bundle ->
                val mime = bundle.getString("type")?.lowercase()?.trim()
                if (mime != null && mime.startsWith("image/")) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bundle.getParcelable("uri", Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        bundle.getParcelable("uri") as? Uri
                    }
                    if (uri != null) uris.add(uri)
                }
            }
        } catch (_: Throwable) {}

        return ExtractedImageSources(
            bitmap = pictureBitmap,
            pictureIcon = pictureIcon,
            imageUris = uris.toList()
        )
    }

    suspend fun loadCompressedJpeg(
        context: Context,
        sources: ExtractedImageSources,
        extraUris: List<Uri> = emptyList(),
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): ByteArray? = withContext(Dispatchers.IO) {
        var ownedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            val candidateBitmap: Bitmap? = when {
                sources.bitmap != null && !sources.bitmap.isRecycled -> sources.bitmap
                sources.pictureIcon != null -> {
                    decodeIconToBitmap(context, sources.pictureIcon, maxDimension).also {
                        ownedBitmap = it
                    }
                }
                else -> null
            } ?: decodeFirstUri(
                context = context,
                uris = (sources.imageUris + extraUris).distinct(),
                maxDimension = maxDimension
            ).also {
                ownedBitmap = it
            }

            val sourceBitmap = candidateBitmap ?: return@withContext null
            if (sourceBitmap.isRecycled || sourceBitmap.width <= 0 || sourceBitmap.height <= 0) {
                return@withContext null
            }

            val (targetW, targetH) = computeScaledDimensions(
                sourceBitmap.width,
                sourceBitmap.height,
                maxDimension
            )
            val finalBitmap = if (targetW != sourceBitmap.width || targetH != sourceBitmap.height) {
                Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true).also {
                    scaledBitmap = it
                }
            } else {
                sourceBitmap
            }

            var bytes = compressJpeg(finalBitmap, quality = 85)
            if (bytes.size > MAX_TELEGRAM_PHOTO_BYTES) {
                bytes = compressJpeg(finalBitmap, quality = 65)
            }
            bytes.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        } finally {
            if (scaledBitmap != null && scaledBitmap !== sources.bitmap && !scaledBitmap!!.isRecycled) {
                scaledBitmap!!.recycle()
            }
            if (ownedBitmap != null && ownedBitmap !== scaledBitmap && ownedBitmap !== sources.bitmap && !ownedBitmap!!.isRecycled) {
                ownedBitmap!!.recycle()
            }
        }
    }

    private fun decodeIconToBitmap(context: Context, icon: Icon, maxDimension: Int): Bitmap? {
        return try {
            val drawable = icon.loadDrawable(context) ?: return null
            if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                val orig = drawable.bitmap
                val (w, h) = computeScaledDimensions(orig.width, orig.height, maxDimension)
                return if (w != orig.width || h != orig.height) {
                    Bitmap.createScaledBitmap(orig, w, h, true)
                } else {
                    orig.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
            val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
            val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
            val (w, h) = computeScaledDimensions(rawW, rawH, maxDimension)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeFirstUri(context: Context, uris: List<Uri>, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver
        for (uri in uris) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) continue

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                }
                val decoded = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
                if (decoded != null && decoded.width > 0 && decoded.height > 0) {
                    return decoded
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
