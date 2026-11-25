package com.example.telegramalramrelay.domain

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat

data class ExtractedNotificationContent(
    val title: String,
    val text: String
)

object NotificationTextExtractor {
    fun extractFromNotification(
        @Suppress("UNUSED_PARAMETER") context: Context,
        packageName: String,
        notification: Notification
    ): ExtractedNotificationContent {
        val extras = notification.extras

        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (_: Throwable) {
            null
        }

        val styleMessages = style?.messages
            ?.mapNotNull { msg ->
                val rawBody = msg.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val mimeLabel = attachmentLabelForMime(msg.dataMimeType)
                val body = when {
                    rawBody != null && mimeLabel != null -> "$rawBody $mimeLabel"
                    rawBody != null -> rawBody
                    mimeLabel != null -> mimeLabel
                    else -> null
                } ?: return@mapNotNull null

                val sender = msg.person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (sender != null) "$sender: $body" else body
            }

        val messagingMessages = if (!styleMessages.isNullOrEmpty()) {
            styleMessages
        } else {
            extractRawExtraMessages(extras)
        }

        val bigTitle = extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        val conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: style?.conversationTitle?.toString()
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString() }
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val tickerText = notification.tickerText?.toString()

        return extractBestContent(
            packageName = packageName,
            title = title,
            bigTitle = bigTitle,
            conversationTitle = conversationTitle,
            text = text,
            bigText = bigText,
            textLines = textLines,
            subText = subText,
            messagingMessages = messagingMessages,
            tickerText = tickerText
        )
    }

    fun extractBestContent(
        packageName: String,
        title: String?,
        bigTitle: String?,
        conversationTitle: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>?,
        subText: String?,
        messagingMessages: List<String>?,
        tickerText: String? = null
    ): ExtractedNotificationContent {
        val resolvedTitle = listOf(bigTitle, conversationTitle, title)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: packageName

        val nonBlankMessaging = messagingMessages?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val nonBlankLines = textLines?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val trimmedBigText = bigText?.trim()?.takeIf { it.isNotEmpty() }
        val latestMessaging = nonBlankMessaging.lastOrNull()

        val rawResolvedText = when {
            latestMessaging != null && trimmedBigText != null -> {
                // Prefer bigText only for single-message notifications (e.g. long LMS);
                // multi-message MessagingStyle stacks would otherwise pull older unread history.
                if (nonBlankMessaging.size == 1 && trimmedBigText.length > latestMessaging.length) {
                    trimmedBigText
                } else {
                    latestMessaging
                }
            }
            latestMessaging != null -> latestMessaging
            trimmedBigText != null -> trimmedBigText
            nonBlankLines.isNotEmpty() -> nonBlankLines.last()
            !text.isNullOrBlank() -> text.trim()
            !subText.isNullOrBlank() -> subText.trim()
            !tickerText.isNullOrBlank() -> tickerText.trim()
            else -> ""
        }

        val resolvedText = stripRedundantSenderPrefix(rawResolvedText, resolvedTitle)

        return ExtractedNotificationContent(
            title = resolvedTitle,
            text = resolvedText
        )
    }

    internal fun isSameSenderAsTitle(sender: String, title: String): Boolean {
        val cleanSender = sender.trim()
        val cleanTitle = title.trim()
        if (cleanSender.isEmpty() || cleanTitle.isEmpty()) return false
        if (cleanSender.equals(cleanTitle, ignoreCase = true)) return true

        val normalizedSender = IncomingCallFormatter.normalizeForKey(cleanSender)
        val normalizedTitle = IncomingCallFormatter.normalizeForKey(cleanTitle)
        return normalizedSender.length >= 7 &&
            normalizedTitle.length >= 7 &&
            normalizedSender == normalizedTitle
    }

    internal fun stripRedundantSenderPrefix(text: String, title: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed

        val firstNewlineIdx = trimmed.indexOf('\n')
        val firstLine = if (firstNewlineIdx >= 0) trimmed.substring(0, firstNewlineIdx) else trimmed
        val colonIdx = firstLine.indexOf(':')
        if (colonIdx <= 0) return trimmed

        val prefix = firstLine.substring(0, colonIdx)
        if (!isSameSenderAsTitle(prefix, title)) return trimmed

        val afterColonOnFirstLine = firstLine.substring(colonIdx + 1).trimStart()
        val remainingLines = if (firstNewlineIdx >= 0) trimmed.substring(firstNewlineIdx) else ""
        val stripped = (afterColonOnFirstLine + remainingLines).trim()
        return if (stripped.isNotEmpty()) stripped else trimmed
    }

    private fun extractRawExtraMessages(extras: Bundle?): List<String> {
        if (extras == null) return emptyList()
        return try {
            val rawArray: Array<Bundle>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    ?.mapNotNull { it as? Bundle }
                    ?.toTypedArray()
            }
            if (rawArray.isNullOrEmpty()) return emptyList()

            rawArray.mapNotNull { bundle ->
                val rawBody = bundle.getCharSequence("text")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val mimeLabel = attachmentLabelForMime(bundle.getString("type"))
                val body = when {
                    rawBody != null && mimeLabel != null -> "$rawBody $mimeLabel"
                    rawBody != null -> rawBody
                    mimeLabel != null -> mimeLabel
                    else -> null
                } ?: return@mapNotNull null

                val sender = extractSenderName(bundle)
                if (sender != null) "$sender: $body" else body
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun extractSenderName(bundle: Bundle): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable("sender_person", Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable("sender_person") as? Person
            }
            val personName = person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            if (personName != null) return personName
        }
        return bundle.getCharSequence("sender")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun attachmentLabelForMime(mimeType: String?): String? {
        val normalized = mimeType?.lowercase()?.trim() ?: return null
        return when {
            normalized.startsWith("image/") -> "[이미지 첨부]"
            normalized.startsWith("video/") -> "[동영상 첨부]"
            normalized.startsWith("audio/") -> "[음성 첨부]"
            normalized.isNotEmpty() -> "[멀티미디어 첨부]"
            else -> null
        }
    }
}
