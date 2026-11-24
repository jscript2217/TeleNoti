package com.example.telegramalramrelay.domain

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SmsMmsEnrichmentResult(
    val text: String,
    val imageUris: List<Uri> = emptyList()
)

object SmsMmsEnricher {
    private val KNOWN_MESSAGING_PACKAGES = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.lge.message",
        "com.skt.prod.dialer"
    )

    private val PLACEHOLDER_KEYWORDS = listOf(
        "멀티미디어 메시지",
        "메시지 다운로드",
        "다운로드 중",
        "긴 문자",
        "첨부파일",
        "사진",
        "이미지",
        "multimedia message",
        "downloading",
        "new message"
    )

    private val mmsUri: Uri by lazy { Uri.parse("content://mms") }
    private val mmsPartUri: Uri by lazy { Uri.parse("content://mms/part") }
    private val smsInboxUri: Uri by lazy { Uri.parse("content://sms/inbox") }

    fun isMessagingPackage(context: Context, packageName: String): Boolean {
        if (packageName in KNOWN_MESSAGING_PACKAGES) return true
        return try {
            Telephony.Sms.getDefaultSmsPackage(context) == packageName
        } catch (_: Throwable) {
            false
        }
    }

    fun isLikelyTruncatedOrMmsPlaceholder(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) return true
        return PLACEHOLDER_KEYWORDS.any { keyword ->
            trimmed.contains(keyword, ignoreCase = true)
        }
    }

    fun decodeMmsSubject(rawSubject: String?, charset: Int): String? {
        if (rawSubject.isNullOrBlank()) return null
        val trimmed = rawSubject.trim()
        if (trimmed.equals("제목 없음", ignoreCase = true) ||
            trimmed.equals("No subject", ignoreCase = true)
        ) {
            return null
        }

        if (charset == 106) {
            val looksLatin1Encoded = trimmed.any { it.code in 0x80..0xFF } &&
                trimmed.all { it.code <= 0xFF }
            if (looksLatin1Encoded) {
                try {
                    val utf8 = String(trimmed.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8).trim()
                    if (utf8.isNotEmpty() && !utf8.contains('\uFFFD')) {
                        return utf8
                    }
                } catch (_: Throwable) {}
            }
        }
        return trimmed
    }

    fun selectEnrichedBody(
        notificationText: String,
        dbSubject: String?,
        dbBody: String?,
        attachments: List<String> = emptyList()
    ): String {
        val parts = mutableListOf<String>()
        val cleanSubject = dbSubject?.trim()?.takeIf { it.isNotEmpty() }
        val cleanBody = dbBody?.trim()?.takeIf { it.isNotEmpty() }

        if (cleanSubject != null && cleanSubject != cleanBody) {
            parts.add("[제목] $cleanSubject")
        }
        if (cleanBody != null) {
            parts.add(cleanBody)
        }
        if (attachments.isNotEmpty()) {
            parts.add(attachments.distinct().joinToString(" "))
        }

        val combined = parts.joinToString("\n").trim()
        if (combined.isEmpty()) return notificationText

        return if (isLikelyTruncatedOrMmsPlaceholder(notificationText) ||
            combined.length > notificationText.trim().length
        ) {
            combined
        } else {
            notificationText
        }
    }

    suspend fun enrichIfPossible(
        context: Context,
        packageName: String,
        senderHint: String,
        notificationText: String,
        postTimeMillis: Long
    ): String {
        return enrichWithMediaIfPossible(
            context = context,
            packageName = packageName,
            senderHint = senderHint,
            notificationText = notificationText,
            postTimeMillis = postTimeMillis
        ).text
    }

    suspend fun enrichWithMediaIfPossible(
        context: Context,
        packageName: String,
        @Suppress("UNUSED_PARAMETER") senderHint: String,
        notificationText: String,
        postTimeMillis: Long
    ): SmsMmsEnrichmentResult = withContext(Dispatchers.IO) {
        val fallback = SmsMmsEnrichmentResult(text = notificationText)
        if (!isMessagingPackage(context, packageName)) return@withContext fallback
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@withContext fallback

        val firstAttempt = queryLatestMessageCandidate(
            resolver = context.contentResolver,
            notificationText = notificationText,
            postTimeMillis = postTimeMillis
        )

        if (firstAttempt.text != notificationText || firstAttempt.imageUris.isNotEmpty()) {
            return@withContext firstAttempt
        }

        if (isLikelyTruncatedOrMmsPlaceholder(notificationText)) {
            delay(1500L)
            return@withContext queryLatestMessageCandidate(
                resolver = context.contentResolver,
                notificationText = notificationText,
                postTimeMillis = postTimeMillis
            )
        }

        firstAttempt
    }

    private fun queryLatestMessageCandidate(
        resolver: ContentResolver,
        notificationText: String,
        postTimeMillis: Long
    ): SmsMmsEnrichmentResult {
        val mmsResult = queryLatestMms(resolver, notificationText, postTimeMillis)
        val smsResult = queryLatestSms(resolver, notificationText, postTimeMillis)

        val bestTextCandidate = listOfNotNull(
            mmsResult,
            smsResult?.let { SmsMmsEnrichmentResult(it) },
            SmsMmsEnrichmentResult(notificationText)
        ).maxByOrNull { candidate ->
            when {
                candidate.imageUris.isNotEmpty() -> candidate.text.length + 100
                candidate.text == notificationText && isLikelyTruncatedOrMmsPlaceholder(candidate.text) -> -1
                else -> candidate.text.length
            }
        } ?: SmsMmsEnrichmentResult(notificationText)

        return if (bestTextCandidate.imageUris.isEmpty() && mmsResult?.imageUris?.isNotEmpty() == true) {
            bestTextCandidate.copy(imageUris = mmsResult.imageUris)
        } else {
            bestTextCandidate
        }
    }

    private fun queryLatestMms(
        resolver: ContentResolver,
        notificationText: String,
        postTimeMillis: Long
    ): SmsMmsEnrichmentResult? {
        return try {
            // Android MMS `date` column is stored in SECONDS.
            val minSeconds = (postTimeMillis / 1000L) - 90L
            val maxSeconds = (postTimeMillis / 1000L) + 60L
            resolver.query(
                mmsUri,
                arrayOf("_id", "sub", "sub_cs", "date"),
                "msg_box = 1 AND date >= ? AND date <= ?",
                arrayOf(minSeconds.toString(), maxSeconds.toString()),
                "date DESC, _id DESC LIMIT 3"
            )?.use { cursor ->
                var best: SmsMmsEnrichmentResult? = null
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                    val rawSub = cursor.getString(cursor.getColumnIndexOrThrow("sub"))
                    val subCsIdx = cursor.getColumnIndex("sub_cs")
                    val subCs = if (subCsIdx >= 0 && !cursor.isNull(subCsIdx)) cursor.getInt(subCsIdx) else 106
                    val subject = decodeMmsSubject(rawSub, subCs)

                    val parts = readMmsParts(resolver, mmsId)
                    val candidateText = selectEnrichedBody(
                        notificationText = notificationText,
                        dbSubject = subject,
                        dbBody = parts.bodyText,
                        attachments = parts.attachments
                    )
                    if (candidateText != notificationText || parts.imageUris.isNotEmpty()) {
                        val candidate = SmsMmsEnrichmentResult(
                            text = candidateText,
                            imageUris = parts.imageUris
                        )
                        if (best == null ||
                            (candidate.imageUris.isNotEmpty() && best.imageUris.isEmpty()) ||
                            candidate.text.length > best.text.length
                        ) {
                            best = candidate
                        }
                    }
                }
                best
            }
        } catch (_: Throwable) {
            null
        }
    }

    private data class MmsPartsData(
        val bodyText: String?,
        val attachments: List<String>,
        val imageUris: List<Uri>
    )

    private fun readMmsParts(resolver: ContentResolver, mmsId: Long): MmsPartsData {
        val textParts = mutableListOf<String>()
        val attachments = mutableListOf<String>()
        val imageUris = mutableListOf<Uri>()

        try {
            resolver.query(
                mmsPartUri,
                arrayOf("_id", "ct", "_data", "text"),
                "mid = ?",
                arrayOf(mmsId.toString()),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val ctIdx = cursor.getColumnIndex("ct")
                val dataIdx = cursor.getColumnIndex("_data")
                val textIdx = cursor.getColumnIndex("text")

                while (cursor.moveToNext()) {
                    val partId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val contentType = if (ctIdx >= 0) cursor.getString(ctIdx)?.lowercase().orEmpty() else ""
                    val dataPath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val rawText = if (textIdx >= 0) cursor.getString(textIdx) else null

                    when {
                        contentType == "text/plain" -> {
                            val textContent = if (!dataPath.isNullOrBlank()) {
                                readPartTextStream(resolver, partId) ?: rawText
                            } else {
                                rawText
                            }
                            if (!textContent.isNullOrBlank()) {
                                textParts.add(textContent.trim())
                            }
                        }
                        contentType.startsWith("image/") -> {
                            attachments.add("[이미지 첨부]")
                            imageUris.add(Uri.parse("content://mms/part/$partId"))
                        }
                        contentType.startsWith("video/") -> attachments.add("[동영상 첨부]")
                        contentType.startsWith("audio/") -> attachments.add("[음성 첨부]")
                    }
                }
            }
        } catch (_: Throwable) {}

        val mergedBody = textParts.joinToString("\n").trim().takeIf { it.isNotEmpty() }
        return MmsPartsData(mergedBody, attachments, imageUris)
    }

    private fun readPartTextStream(resolver: ContentResolver, partId: Long): String? {
        return try {
            val partUri = Uri.parse("content://mms/part/$partId")
            resolver.openInputStream(partUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryLatestSms(
        resolver: ContentResolver,
        notificationText: String,
        postTimeMillis: Long
    ): String? {
        return try {
            // Android SMS `date` column is stored in MILLISECONDS.
            val minMillis = postTimeMillis - 90_000L
            val maxMillis = postTimeMillis + 60_000L
            resolver.query(
                smsInboxUri,
                arrayOf("body", "date"),
                "date >= ? AND date <= ?",
                arrayOf(minMillis.toString(), maxMillis.toString()),
                "date DESC LIMIT 3"
            )?.use { cursor ->
                var best: String? = null
                val bodyIdx = cursor.getColumnIndex("body")
                if (bodyIdx < 0) return null
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyIdx)
                    val candidate = selectEnrichedBody(
                        notificationText = notificationText,
                        dbSubject = null,
                        dbBody = body
                    )
                    if (candidate != notificationText && (best == null || candidate.length > best.length)) {
                        best = candidate
                    }
                }
                best
            }
        } catch (_: Throwable) {
            null
        }
    }
}
