package com.example.telegramalramrelay.domain

import com.example.telegramalramrelay.data.TeleNotiSettings

const val INCOMING_CALL_PACKAGE = "com.example.telegramalramrelay.incoming_call"

object IncomingCallFormatter {
    /**
     * Normalizes a raw phone number string into a canonical key for deduplication
     * and Korean number formatting. Converts Korean `+82` prefixes (followed by 1..8)
     * into `0...`, preserves non-KR international `+<digits>`, and returns `""` when blank.
     */
    fun normalizeForKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""

        val hasPlus = trimmed.startsWith("+")
        val digits = buildString(trimmed.length) {
            for (ch in trimmed) {
                if (ch.isDigit()) append(ch)
            }
        }
        if (digits.isEmpty()) return ""

        if (hasPlus) {
            if (digits.startsWith("82") && digits.length >= 4 && digits[2] in '1'..'8') {
                return "0" + digits.substring(2)
            }
            return "+$digits"
        }
        return digits
    }

    /**
     * Formats Korean mobile, landline, `050`, and `15xx/16xx/18xx` representative numbers
     * with hyphens while leaving non-Korean `+<digits>` numbers intact.
     */
    fun formatKoreanPhoneNumber(raw: String?): String {
        val normalized = normalizeForKey(raw)
        if (normalized.isEmpty()) return ""
        if (normalized.startsWith("+")) return normalized

        return when {
            normalized.startsWith("02") && normalized.length == 9 ->
                "${normalized.substring(0, 2)}-${normalized.substring(2, 5)}-${normalized.substring(5)}"
            normalized.startsWith("02") && normalized.length == 10 ->
                "${normalized.substring(0, 2)}-${normalized.substring(2, 6)}-${normalized.substring(6)}"
            normalized.startsWith("050") && normalized.length == 12 ->
                "${normalized.substring(0, 4)}-${normalized.substring(4, 8)}-${normalized.substring(8)}"
            normalized.startsWith("0") && normalized.length == 11 ->
                "${normalized.substring(0, 3)}-${normalized.substring(3, 7)}-${normalized.substring(7)}"
            normalized.startsWith("0") && normalized.length == 10 ->
                "${normalized.substring(0, 3)}-${normalized.substring(3, 6)}-${normalized.substring(6)}"
            normalized.startsWith("1") && normalized.length == 8 ->
                "${normalized.substring(0, 4)}-${normalized.substring(4)}"
            else -> normalized
        }
    }

    fun buildCallerDisplay(
        contactName: String?,
        rawNumber: String?,
        hasCallLogPermission: Boolean
    ): Pair<String, String> {
        val formattedNumber = formatKoreanPhoneNumber(rawNumber)
        val cleanContact = contactName?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            cleanContact != null && formattedNumber.isNotEmpty() ->
                "$cleanContact ($formattedNumber)" to "전화가 걸려오고 있습니다"
            formattedNumber.isNotEmpty() ->
                formattedNumber to "전화가 걸려오고 있습니다"
            cleanContact != null ->
                cleanContact to "전화가 걸려오고 있습니다"
            hasCallLogPermission ->
                "발신번호 표시 제한" to "발신번호가 표시되지 않은 전화가 걸려오고 있습니다"
            else ->
                "발신번호 미확인" to "전화가 걸려오고 있습니다 (통화 기록 권한을 허용하면 번호가 표시됩니다)"
        }
    }

    fun formatIncomingCallMessage(
        template: String,
        title: String,
        text: String,
        time: String
    ): String {
        val useCallDefault = template.isBlank() || template == MessageFormatter.DEFAULT_FORMAT
        val activeTemplate = if (useCallDefault) {
            "📞 {app}\n📌 {title}\n💬 {text}\n🕐 {time}"
        } else {
            template
        }
        val appLabel = if (useCallDefault) "수신 전화" else "📞 수신 전화"
        return MessageFormatter.format(
            template = activeTemplate,
            appName = appLabel,
            title = title,
            text = text,
            time = time,
            packageName = INCOMING_CALL_PACKAGE
        )
    }
}

object IncomingCallPolicy {
    fun shouldSend(
        settings: TeleNotiSettings,
        currentHour: Int,
        currentMinute: Int
    ): Boolean {
        return settings.forwardingEnabled &&
            settings.incomingCallAlertEnabled &&
            settings.botToken.isNotBlank() &&
            settings.chatId.isNotBlank() &&
            !settings.quietHours.isQuietAt(currentHour, currentMinute)
    }
}

sealed interface CallEventDecision {
    data class SendNow(val rawNumber: String?) : CallEventDecision
    data object WaitForNumber : CallEventDecision
    data object Ignore : CallEventDecision
}

/**
 * Coordinates Android 9+ dual `PHONE_STATE` RINGING broadcasts (broadcast #1 without
 * `EXTRA_INCOMING_NUMBER` when `READ_CALL_LOG` is granted, followed immediately by #2 with
 * the number) and prevents duplicate Telegram alerts during the same ringing session.
 */
class IncomingCallStateTracker(
    private val dedupeWindowMs: Long = 15_000L
) {
    private val lock = Any()
    private var isRingingSessionActive = false
    private var awaitingNumber = false
    private var lastNotifiedNormalized = ""
    private var lastNotifiedAtMillis = 0L

    fun onRinging(
        hasNumberExtra: Boolean,
        rawNumber: String?,
        hasCallLogPermission: Boolean,
        nowMillis: Long
    ): CallEventDecision = synchronized(lock) {
        if (!hasNumberExtra && hasCallLogPermission) {
            if (isRingingSessionActive && (nowMillis - lastNotifiedAtMillis) in 0 until dedupeWindowMs) {
                return CallEventDecision.Ignore
            }
            awaitingNumber = true
            return CallEventDecision.WaitForNumber
        }

        val normalized = IncomingCallFormatter.normalizeForKey(rawNumber)
        if (isRingingSessionActive &&
            normalized == lastNotifiedNormalized &&
            (nowMillis - lastNotifiedAtMillis) in 0 until dedupeWindowMs
        ) {
            awaitingNumber = false
            return CallEventDecision.Ignore
        }

        awaitingNumber = false
        isRingingSessionActive = true
        lastNotifiedNormalized = normalized
        lastNotifiedAtMillis = nowMillis
        return CallEventDecision.SendNow(rawNumber?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun onFallbackTimeout(nowMillis: Long): CallEventDecision = synchronized(lock) {
        if (!awaitingNumber) {
            return CallEventDecision.Ignore
        }
        if (isRingingSessionActive && (nowMillis - lastNotifiedAtMillis) in 0 until dedupeWindowMs) {
            awaitingNumber = false
            return CallEventDecision.Ignore
        }
        awaitingNumber = false
        isRingingSessionActive = true
        lastNotifiedNormalized = ""
        lastNotifiedAtMillis = nowMillis
        return CallEventDecision.SendNow(null)
    }

    fun onOffhook() = synchronized(lock) {
        awaitingNumber = false
    }

    fun onIdle() = synchronized(lock) {
        awaitingNumber = false
        isRingingSessionActive = false
        lastNotifiedNormalized = ""
        lastNotifiedAtMillis = 0L
    }
}
