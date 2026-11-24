package com.example.telegramalramrelay.data

import android.content.Context
import android.content.SharedPreferences
import com.example.telegramalramrelay.domain.MessageFormatter
import com.example.telegramalramrelay.domain.NotificationFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.Calendar
import java.util.TimeZone

class SettingsRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tokenStore = SecureTokenStore(appContext)
    private val lock = Any()

    private val _settings: MutableStateFlow<TeleNotiSettings>
    val settings: StateFlow<TeleNotiSettings>

    init {
        tokenStore.migrateFromLegacyIfNeeded(prefs)
        val loaded = loadFromStorage()
        _settings = MutableStateFlow(loaded)
        settings = _settings.asStateFlow()
    }

    fun currentSettings(): TeleNotiSettings = _settings.value

    fun saveCredentials(botToken: String, chatId: String) {
        synchronized(lock) {
            val cleanToken = botToken.trim()
            val cleanChatId = chatId.trim()
            tokenStore.saveCredentials(cleanToken, cleanChatId)
            _settings.value = _settings.value.copy(
                botToken = cleanToken,
                chatId = cleanChatId
            )
        }
    }

    fun setForwardingEnabled(enabled: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, enabled).apply()
            _settings.value = _settings.value.copy(forwardingEnabled = enabled)
        }
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        synchronized(lock) {
            prefs.edit()
                .putBoolean(KEY_BACKGROUND_ENABLED, enabled)
                .putBoolean(KEY_PIN_NOTIFICATION, enabled)
                .putBoolean(KEY_SERVICE_RUNNING, enabled)
                .apply()
            _settings.value = _settings.value.copy(backgroundEnabled = enabled)
        }
    }

    fun setSelectedPackages(packages: Set<String>) {
        synchronized(lock) {
            val sorted = packages.toSortedSet()
            prefs.edit()
                .putString(KEY_SELECTED_APPS, JSONArray(sorted.toList()).toString())
                .putInt(KEY_CONNECTED_APPS, sorted.size)
                .apply()
            _settings.value = _settings.value.copy(selectedPackages = sorted)
        }
    }

    fun togglePackage(packageName: String) {
        synchronized(lock) {
            val current = _settings.value.selectedPackages.toMutableSet()
            if (!current.add(packageName)) {
                current.remove(packageName)
            }
            setSelectedPackages(current)
        }
    }

    fun setMessageFormat(format: String) {
        synchronized(lock) {
            prefs.edit().putString(KEY_MESSAGE_FORMAT, format).apply()
            _settings.value = _settings.value.copy(messageFormat = format)
        }
    }

    fun setKeywordFilter(config: KeywordFilterConfig) {
        synchronized(lock) {
            prefs.edit()
                .putBoolean(KEY_KEYWORD_FILTER_ENABLED, config.enabled)
                .putString(KEY_INCLUDE_KEYWORDS, config.includeKeywordsRaw)
                .putString(KEY_EXCLUDE_KEYWORDS, config.excludeKeywordsRaw)
                .apply()
            _settings.value = _settings.value.copy(keywordFilter = config)
        }
    }

    fun setQuietHours(config: QuietHoursConfig) {
        synchronized(lock) {
            prefs.edit()
                .putBoolean(KEY_QUIET_HOURS_ENABLED, config.enabled)
                .putInt(KEY_QUIET_START_HOUR, config.startHour.coerceIn(0, 23))
                .putInt(KEY_QUIET_START_MINUTE, config.startMinute.coerceIn(0, 59))
                .putInt(KEY_QUIET_END_HOUR, config.endHour.coerceIn(0, 23))
                .putInt(KEY_QUIET_END_MINUTE, config.endMinute.coerceIn(0, 59))
                .apply()
            _settings.value = _settings.value.copy(quietHours = config)
        }
    }

    fun setDedupeWindowSeconds(seconds: Int) {
        synchronized(lock) {
            val normalized = seconds.coerceAtLeast(0)
            prefs.edit().putInt(KEY_DEDUPE_SECONDS, normalized).apply()
            _settings.value = _settings.value.copy(dedupeWindowSeconds = normalized)
        }
    }

    fun setIgnoreOngoing(ignore: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_IGNORE_ONGOING, ignore).apply()
            _settings.value = _settings.value.copy(ignoreOngoing = ignore)
        }
    }

    fun setDailyResetConfig(config: DailyResetConfig) {
        synchronized(lock) {
            val normalized = config.copy(
                hour = config.hour.coerceIn(0, 23),
                minute = config.minute.coerceIn(0, 59)
            )
            prefs.edit()
                .putBoolean(KEY_DAILY_RESET_ENABLED, normalized.enabled)
                .putInt(KEY_DAILY_RESET_HOUR, normalized.hour)
                .putInt(KEY_DAILY_RESET_MINUTE, normalized.minute)
                .apply()
            _settings.value = _settings.value.copy(dailyReset = normalized)
        }
    }

    fun setAppCustomRule(rule: AppCustomRule) {
        synchronized(lock) {
            val cleanRule = rule.copy(customChatId = rule.customChatId.trim())
            val next = _settings.value.appCustomRules.toMutableMap()
            if (cleanRule.hasCustomizations) {
                next[cleanRule.packageName] = cleanRule
            } else {
                next.remove(cleanRule.packageName)
            }
            prefs.edit().putString(KEY_APP_CUSTOM_RULES, AppCustomRule.toJsonString(next)).apply()
            _settings.value = _settings.value.copy(appCustomRules = next)
        }
    }

    fun removeAppCustomRule(packageName: String) {
        synchronized(lock) {
            val next = _settings.value.appCustomRules.toMutableMap()
            if (next.remove(packageName) != null) {
                prefs.edit().putString(KEY_APP_CUSTOM_RULES, AppCustomRule.toJsonString(next)).apply()
                _settings.value = _settings.value.copy(appCustomRules = next)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        synchronized(lock) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
            _settings.value = _settings.value.copy(themeMode = mode)
        }
    }

    fun setLmsEnrichmentEnabled(enabled: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_LMS_ENRICHMENT_ENABLED, enabled).apply()
            _settings.value = _settings.value.copy(lmsEnrichmentEnabled = enabled)
        }
    }

    fun setImageForwardingEnabled(enabled: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_IMAGE_FORWARDING_ENABLED, enabled).apply()
            _settings.value = _settings.value.copy(imageForwardingEnabled = enabled)
        }
    }

    fun setTelegramReplyEnabled(enabled: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_TELEGRAM_REPLY_ENABLED, enabled).apply()
            _settings.value = _settings.value.copy(telegramReplyEnabled = enabled)
        }
    }

    fun setIncomingCallAlertEnabled(enabled: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEY_INCOMING_CALL_ALERT_ENABLED, enabled).apply()
            _settings.value = _settings.value.copy(incomingCallAlertEnabled = enabled)
        }
    }

    fun getLastTelegramUpdateId(): Long = synchronized(lock) {
        prefs.getLong(KEY_LAST_TELEGRAM_UPDATE_ID, 0L)
    }

    fun setLastTelegramUpdateId(updateId: Long) {
        synchronized(lock) {
            prefs.edit().putLong(KEY_LAST_TELEGRAM_UPDATE_ID, updateId).apply()
        }
    }

    fun getAuthorizedChatIds(): Set<String> {
        val current = _settings.value
        val out = LinkedHashSet<String>()
        if (current.chatId.isNotBlank()) {
            out.add(current.chatId.trim())
        }
        current.appCustomRules.values.forEach { rule ->
            if (rule.customChatId.isNotBlank()) {
                out.add(rule.customChatId.trim())
            }
        }
        return out
    }

    fun recordObservedPackage(packageName: String) {
        if (packageName.isBlank() || NotificationFilter.isBlockedLoopPackage(packageName, appContext.packageName)) {
            return
        }
        synchronized(lock) {
            val current = _settings.value.observedPackages
            if (packageName in current && current.size <= MAX_OBSERVED_PACKAGES) {
                return
            }
            val next = LinkedHashSet<String>(current)
            next.remove(packageName)
            next.add(packageName)
            while (next.size > MAX_OBSERVED_PACKAGES) {
                val oldest = next.firstOrNull() ?: break
                next.remove(oldest)
            }
            prefs.edit().putString(KEY_OBSERVED_PACKAGES, JSONArray(next.toList()).toString()).apply()
            _settings.value = _settings.value.copy(observedPackages = next.toSet())
        }
    }

    fun addLogItem(item: NotificationLogItem) {
        synchronized(lock) {
            val updated = listOf(item) + _settings.value.recentNotifications
            val capped = updated.take(MAX_RECENT_LOGS)
            prefs.edit()
                .putString(KEY_RECENT_NOTIFICATIONS, NotificationLogItem.toJsonArrayString(capped))
                .apply()
            _settings.value = _settings.value.copy(recentNotifications = capped)
        }
    }

    fun updateLogStatus(
        id: String,
        status: DeliveryStatus,
        errorMessage: String?,
        incrementSentToday: Boolean,
        incrementRetryCount: Boolean,
        clearImageCache: Boolean = false
    ) {
        synchronized(lock) {
            val current = _settings.value
            val updatedList = current.recentNotifications.map { item ->
                if (item.id == id) {
                    if (clearImageCache && !item.imageCachePath.isNullOrBlank()) {
                        PendingImageStore.delete(item.imageCachePath)
                    }
                    item.copy(
                        status = status,
                        errorMessage = errorMessage,
                        retryCount = if (incrementRetryCount) item.retryCount + 1 else item.retryCount,
                        imageCachePath = if (clearImageCache) null else item.imageCachePath
                    )
                } else {
                    item
                }
            }
            val nextSentToday = if (incrementSentToday) current.sentToday + 1 else current.sentToday
            prefs.edit()
                .putString(KEY_RECENT_NOTIFICATIONS, NotificationLogItem.toJsonArrayString(updatedList))
                .putInt(KEY_SENT_TODAY, nextSentToday)
                .apply()
            _settings.value = current.copy(
                recentNotifications = updatedList,
                sentToday = nextSentToday
            )
        }
    }

    fun getPendingRetryItems(): List<NotificationLogItem> {
        synchronized(lock) {
            return _settings.value.recentNotifications
                .filter { it.status == DeliveryStatus.PENDING_RETRY }
                .asReversed()
        }
    }

    fun clearRecentNotifications() {
        synchronized(lock) {
            PendingImageStore.clearAll(appContext)
            prefs.edit().putString(KEY_RECENT_NOTIFICATIONS, "[]").apply()
            _settings.value = _settings.value.copy(recentNotifications = emptyList())
        }
    }

    fun clearLastCrash() {
        synchronized(lock) {
            prefs.edit().remove(KEY_LAST_CRASH).apply()
            _settings.value = _settings.value.copy(lastCrash = null)
        }
    }

    fun resetDailyStats(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            prefs.edit()
                .putInt(KEY_SENT_TODAY, 0)
                .putLong(KEY_LAST_RESET_MILLIS, nowMillis)
                .apply()
            _settings.value = _settings.value.copy(
                sentToday = 0,
                lastResetMillis = nowMillis
            )
        }
    }

    fun checkAndApplyDailyResetIfDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            val current = _settings.value
            if (!current.dailyReset.enabled) return false

            if (current.lastResetMillis == 0L) {
                prefs.edit().putLong(KEY_LAST_RESET_MILLIS, nowMillis).apply()
                _settings.value = current.copy(lastResetMillis = nowMillis)
                return false
            }

            val boundary = computeMostRecentResetBoundary(
                nowMillis = nowMillis,
                resetHour = current.dailyReset.hour,
                resetMinute = current.dailyReset.minute
            )
            if (current.lastResetMillis < boundary) {
                prefs.edit()
                    .putInt(KEY_SENT_TODAY, 0)
                    .putLong(KEY_LAST_RESET_MILLIS, nowMillis)
                    .apply()
                _settings.value = current.copy(
                    sentToday = 0,
                    lastResetMillis = nowMillis
                )
                return true
            }
            return false
        }
    }

    private fun loadFromStorage(): TeleNotiSettings {
        val selected = mutableSetOf<String>()
        val selectedJson = prefs.getString(KEY_SELECTED_APPS, null)
        if (!selectedJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(selectedJson)
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotBlank() && !NotificationFilter.isBlockedLoopPackage(pkg, appContext.packageName)) {
                        selected.add(pkg)
                    }
                }
            } catch (_: Exception) {}
        }

        val recent = NotificationLogItem.parseList(prefs.getString(KEY_RECENT_NOTIFICATIONS, null))
        val appRules = AppCustomRule.parseMap(prefs.getString(KEY_APP_CUSTOM_RULES, null))
        val observed = linkedSetOf<String>()
        val observedJson = prefs.getString(KEY_OBSERVED_PACKAGES, null)
        if (!observedJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(observedJson)
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotBlank() && !NotificationFilter.isBlockedLoopPackage(pkg, appContext.packageName)) {
                        observed.add(pkg)
                    }
                }
            } catch (_: Exception) {}
        }

        return TeleNotiSettings(
            botToken = tokenStore.getBotToken(),
            chatId = tokenStore.getChatId(),
            forwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, true),
            backgroundEnabled = prefs.getBoolean(KEY_BACKGROUND_ENABLED, false),
            selectedPackages = selected,
            messageFormat = prefs.getString(KEY_MESSAGE_FORMAT, null)
                ?.takeIf { it.isNotEmpty() }
                ?: MessageFormatter.DEFAULT_FORMAT,
            recentNotifications = recent,
            sentToday = prefs.getInt(KEY_SENT_TODAY, 0),
            lastResetMillis = prefs.getLong(KEY_LAST_RESET_MILLIS, 0L),
            lastCrash = prefs.getString(KEY_LAST_CRASH, null)?.takeIf { it.isNotBlank() },
            keywordFilter = KeywordFilterConfig(
                enabled = prefs.getBoolean(KEY_KEYWORD_FILTER_ENABLED, false),
                includeKeywordsRaw = prefs.getString(KEY_INCLUDE_KEYWORDS, "") ?: "",
                excludeKeywordsRaw = prefs.getString(KEY_EXCLUDE_KEYWORDS, "") ?: ""
            ),
            quietHours = QuietHoursConfig(
                enabled = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false),
                startHour = prefs.getInt(KEY_QUIET_START_HOUR, 23).coerceIn(0, 23),
                startMinute = prefs.getInt(KEY_QUIET_START_MINUTE, 0).coerceIn(0, 59),
                endHour = prefs.getInt(KEY_QUIET_END_HOUR, 7).coerceIn(0, 23),
                endMinute = prefs.getInt(KEY_QUIET_END_MINUTE, 0).coerceIn(0, 59)
            ),
            dedupeWindowSeconds = prefs.getInt(KEY_DEDUPE_SECONDS, 5).coerceAtLeast(0),
            ignoreOngoing = prefs.getBoolean(KEY_IGNORE_ONGOING, true),
            dailyReset = DailyResetConfig(
                enabled = prefs.getBoolean(KEY_DAILY_RESET_ENABLED, true),
                hour = prefs.getInt(KEY_DAILY_RESET_HOUR, 0).coerceIn(0, 23),
                minute = prefs.getInt(KEY_DAILY_RESET_MINUTE, 0).coerceIn(0, 59)
            ),
            appCustomRules = appRules,
            themeMode = ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)),
            lmsEnrichmentEnabled = prefs.getBoolean(KEY_LMS_ENRICHMENT_ENABLED, true),
            observedPackages = observed.toSet(),
            imageForwardingEnabled = prefs.getBoolean(KEY_IMAGE_FORWARDING_ENABLED, true),
            telegramReplyEnabled = prefs.getBoolean(KEY_TELEGRAM_REPLY_ENABLED, true),
            incomingCallAlertEnabled = prefs.getBoolean(KEY_INCOMING_CALL_ALERT_ENABLED, true)
        )
    }

    companion object {
        const val PREFS_NAME = "settings"
        const val MAX_RECENT_LOGS = 50
        const val MAX_OBSERVED_PACKAGES = 200

        const val KEY_FORWARDING_ENABLED = "forwardingEnabled"
        const val KEY_BACKGROUND_ENABLED = "backgroundEnabled"
        const val KEY_PIN_NOTIFICATION = "pinNotification"
        const val KEY_SERVICE_RUNNING = "serviceRunning"
        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_CONNECTED_APPS = "connected_apps"
        const val KEY_MESSAGE_FORMAT = "message_format"
        const val KEY_RECENT_NOTIFICATIONS = "recent_notifications"
        const val KEY_SENT_TODAY = "sent_notifications_today"
        const val KEY_LAST_RESET_MILLIS = "last_reset_millis"
        const val KEY_LAST_CRASH = "lastCrash"
        const val KEY_KEYWORD_FILTER_ENABLED = "keyword_filter_enabled"
        const val KEY_INCLUDE_KEYWORDS = "include_keywords"
        const val KEY_EXCLUDE_KEYWORDS = "exclude_keywords"
        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_START_HOUR = "quiet_start_hour"
        const val KEY_QUIET_START_MINUTE = "quiet_start_minute"
        const val KEY_QUIET_END_HOUR = "quiet_end_hour"
        const val KEY_QUIET_END_MINUTE = "quiet_end_minute"
        const val KEY_DEDUPE_SECONDS = "dedupe_seconds"
        const val KEY_IGNORE_ONGOING = "ignore_ongoing"
        const val KEY_DAILY_RESET_ENABLED = "daily_reset_enabled"
        const val KEY_DAILY_RESET_HOUR = "daily_reset_hour"
        const val KEY_DAILY_RESET_MINUTE = "daily_reset_minute"
        const val KEY_APP_CUSTOM_RULES = "app_custom_rules"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LMS_ENRICHMENT_ENABLED = "lms_enrichment_enabled"
        const val KEY_OBSERVED_PACKAGES = "observed_packages"
        const val KEY_IMAGE_FORWARDING_ENABLED = "image_forwarding_enabled"
        const val KEY_TELEGRAM_REPLY_ENABLED = "telegram_reply_enabled"
        const val KEY_INCOMING_CALL_ALERT_ENABLED = "incoming_call_alert_enabled"
        const val KEY_LAST_TELEGRAM_UPDATE_ID = "last_telegram_update_id"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
        }

        fun computeMostRecentResetBoundary(
            nowMillis: Long,
            resetHour: Int,
            resetMinute: Int,
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long {
            val cal = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, resetHour.coerceIn(0, 23))
                set(Calendar.MINUTE, resetMinute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > nowMillis) {
                cal.add(Calendar.DATE, -1)
            }
            return cal.timeInMillis
        }
    }
}
