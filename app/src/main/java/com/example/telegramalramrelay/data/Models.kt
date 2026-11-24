package com.example.telegramalramrelay.data

import com.example.telegramalramrelay.domain.MessageFormatter
import org.json.JSONArray
import org.json.JSONObject

enum class DeliveryStatus {
    SENT,
    PENDING_RETRY,
    FAILED
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}

data class NotificationLogItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val formattedMessage: String,
    val chatId: String,
    val timestampMillis: Long,
    val timeDisplay: String,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val emoji: String = "🔔",
    val hasImage: Boolean = false,
    val imageCachePath: String? = null,
    val canReply: Boolean = false,
    val recipientLabel: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("packageName", packageName)
        put("appName", appName)
        put("title", title)
        put("text", text)
        put("formattedMessage", formattedMessage)
        put("chatId", chatId)
        put("timestampMillis", timestampMillis)
        put("timeDisplay", timeDisplay)
        put("status", status.name)
        if (!errorMessage.isNullOrBlank()) put("errorMessage", errorMessage)
        put("retryCount", retryCount)
        put("emoji", emoji)
        if (hasImage) put("hasImage", true)
        if (!imageCachePath.isNullOrBlank()) put("imageCachePath", imageCachePath)
        if (canReply) put("canReply", true)
        if (!recipientLabel.isNullOrBlank()) put("recipientLabel", recipientLabel)
    }

    companion object {
        fun fromJson(obj: JSONObject, fallbackIndex: Int = 0): NotificationLogItem? {
            val pkg = obj.optString("packageName").ifBlank { obj.optString("package", "") }
            val app = obj.optString("appName").ifBlank { obj.optString("app", "알림") }
            val formatted = obj.optString("formattedMessage").ifBlank { obj.optString("message", "") }
            val timeStr = obj.optString("timeDisplay").ifBlank { obj.optString("time", "") }
            if (formatted.isBlank() && app.isBlank() && pkg.isBlank()) return null

            val ts = obj.optLong("timestampMillis", 0L)
            val title = obj.optString("title", "")
            val text = obj.optString("text", "")
            val id = obj.optString("id").ifBlank {
                "legacy_${ts}_${pkg}_${fallbackIndex}"
            }
            val statusRaw = obj.optString("status", DeliveryStatus.SENT.name)
            val status = DeliveryStatus.entries.firstOrNull { it.name == statusRaw } ?: DeliveryStatus.SENT
            val error = obj.optString("errorMessage").takeIf { it.isNotBlank() }

            return NotificationLogItem(
                id = id,
                packageName = pkg,
                appName = app,
                title = title,
                text = text,
                formattedMessage = formatted,
                chatId = obj.optString("chatId", ""),
                timestampMillis = ts,
                timeDisplay = timeStr,
                status = status,
                errorMessage = error,
                retryCount = obj.optInt("retryCount", 0),
                emoji = obj.optString("emoji", "🔔"),
                hasImage = obj.optBoolean("hasImage", false),
                imageCachePath = obj.optString("imageCachePath").takeIf { it.isNotBlank() },
                canReply = obj.optBoolean("canReply", false),
                recipientLabel = obj.optString("recipientLabel").takeIf { it.isNotBlank() }
            )
        }

        fun parseList(json: String?): List<NotificationLogItem> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val out = mutableListOf<NotificationLogItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    fromJson(obj, i)?.let { out.add(it) }
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArrayString(items: List<NotificationLogItem>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

data class QuietHoursConfig(
    val enabled: Boolean = false,
    val startHour: Int = 23,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0
) {
    fun isQuietAt(hour: Int, minute: Int): Boolean {
        if (!enabled) return false
        val nowMinutes = hour * 60 + minute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }
}

data class KeywordFilterConfig(
    val enabled: Boolean = false,
    val includeKeywordsRaw: String = "",
    val excludeKeywordsRaw: String = ""
) {
    val includeKeywords: List<String>
        get() = parseTokens(includeKeywordsRaw)

    val excludeKeywords: List<String>
        get() = parseTokens(excludeKeywordsRaw)

    companion object {
        fun parseTokens(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return raw.split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}

data class ChatProfile(
    val id: String,
    val name: String,
    val chatId: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("chatId", chatId)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChatProfile? {
            val cleanChatId = obj.optString("chatId", "").trim()
            if (cleanChatId.isEmpty()) return null
            val cleanId = obj.optString("id", "").trim().ifEmpty { "profile_$cleanChatId" }
            val cleanName = obj.optString("name", "").trim().ifEmpty { cleanChatId }
            return ChatProfile(
                id = cleanId,
                name = cleanName,
                chatId = cleanChatId
            )
        }

        fun parseList(json: String?): List<ChatProfile> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val out = mutableListOf<ChatProfile>()
                val seenIds = HashSet<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val profile = fromJson(obj) ?: continue
                    if (seenIds.add(profile.id)) {
                        out.add(profile)
                    }
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArrayString(profiles: List<ChatProfile>): String {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

data class AppCustomRule(
    val packageName: String,
    val customChatId: String = "",
    val customFormat: String = "",
    val targetProfileIds: List<String> = emptyList()
) {
    val hasCustomRecipients: Boolean
        get() = targetProfileIds.isNotEmpty() || customChatId.isNotBlank()

    val hasCustomizations: Boolean
        get() = hasCustomRecipients || customFormat.isNotBlank()

    companion object {
        fun parseMap(json: String?): Map<String, AppCustomRule> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val root = JSONObject(json)
                val out = linkedMapOf<String, AppCustomRule>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val obj = root.optJSONObject(pkg) ?: continue
                    val profileIds = mutableListOf<String>()
                    val seenProfileIds = HashSet<String>()
                    val idsArr = obj.optJSONArray("targetProfileIds")
                    if (idsArr != null) {
                        for (i in 0 until idsArr.length()) {
                            val pid = idsArr.optString(i, "").trim()
                            if (pid.isNotEmpty() && seenProfileIds.add(pid)) {
                                profileIds.add(pid)
                            }
                        }
                    }
                    val rule = AppCustomRule(
                        packageName = pkg,
                        customChatId = obj.optString("customChatId", "").trim(),
                        customFormat = obj.optString("customFormat", ""),
                        targetProfileIds = profileIds
                    )
                    if (rule.hasCustomizations) {
                        out[pkg] = rule
                    }
                }
                out
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun toJsonString(rules: Map<String, AppCustomRule>): String {
            val root = JSONObject()
            rules.values.filter { it.hasCustomizations }.forEach { rule ->
                root.put(
                    rule.packageName,
                    JSONObject().apply {
                        put("customChatId", rule.customChatId.trim())
                        put("customFormat", rule.customFormat)
                        if (rule.targetProfileIds.isNotEmpty()) {
                            put("targetProfileIds", JSONArray(rule.targetProfileIds))
                        }
                    }
                )
            }
            return root.toString()
        }
    }
}

data class DailyResetConfig(
    val enabled: Boolean = true,
    val hour: Int = 0,
    val minute: Int = 0
)

data class DetectedChat(
    val chatId: String,
    val title: String,
    val type: String,
    val username: String = ""
)

data class TeleNotiSettings(
    val botToken: String = "",
    val chatId: String = "",
    val forwardingEnabled: Boolean = true,
    val backgroundEnabled: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val messageFormat: String = MessageFormatter.DEFAULT_FORMAT,
    val recentNotifications: List<NotificationLogItem> = emptyList(),
    val sentToday: Int = 0,
    val lastResetMillis: Long = 0L,
    val lastCrash: String? = null,
    val keywordFilter: KeywordFilterConfig = KeywordFilterConfig(),
    val quietHours: QuietHoursConfig = QuietHoursConfig(),
    val dedupeWindowSeconds: Int = 5,
    val ignoreOngoing: Boolean = true,
    val dailyReset: DailyResetConfig = DailyResetConfig(),
    val appCustomRules: Map<String, AppCustomRule> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lmsEnrichmentEnabled: Boolean = true,
    val observedPackages: Set<String> = emptySet(),
    val imageForwardingEnabled: Boolean = true,
    val telegramReplyEnabled: Boolean = true,
    val incomingCallAlertEnabled: Boolean = true,
    val chatProfiles: List<ChatProfile> = emptyList(),
    val defaultProfileIds: Set<String> = emptySet()
)
