package com.example.telegramalramrelay.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.telegramalramrelay.AppKeepAliveService
import com.example.telegramalramrelay.NotificationListenerServiceImpl
import com.example.telegramalramrelay.data.AppCustomRule
import com.example.telegramalramrelay.data.ChatProfile
import com.example.telegramalramrelay.data.DailyResetConfig
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.DetectedChat
import com.example.telegramalramrelay.data.KeywordFilterConfig
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.data.PendingImageStore
import com.example.telegramalramrelay.data.QuietHoursConfig
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.data.TeleNotiSettings
import com.example.telegramalramrelay.data.ThemeMode
import com.example.telegramalramrelay.domain.ChatTargetResolver
import com.example.telegramalramrelay.domain.NotificationFilter
import com.example.telegramalramrelay.network.TelegramApi
import com.example.telegramalramrelay.network.TelegramReplyPoller
import com.example.telegramalramrelay.network.TelegramResult
import com.example.telegramalramrelay.service.TeleNotiTileService
import com.example.telegramalramrelay.util.DailyResetScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppEntry(
    val packageName: String,
    val name: String,
    val isBlockedLoop: Boolean,
    val isSystemApp: Boolean = false,
    val hasLauncherIcon: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appCtx = application.applicationContext
    private val repo = SettingsRepository.getInstance(appCtx)

    val settings: StateFlow<TeleNotiSettings> = repo.settings
    val listenerConnected: StateFlow<Boolean> = NotificationListenerServiceImpl.isConnected
    val serviceRunning: StateFlow<Boolean> = AppKeepAliveService.isRunningFlow
    val replyPollingActive: StateFlow<Boolean> = TelegramReplyPoller.isPolling

    private val _installedApps = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppEntry>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _postNotificationsGranted = MutableStateFlow(checkPostNotificationsGranted())
    val postNotificationsGranted: StateFlow<Boolean> = _postNotificationsGranted.asStateFlow()

    private val _notificationAccessEnabled = MutableStateFlow(checkNotificationAccess())
    val notificationAccessEnabled: StateFlow<Boolean> = _notificationAccessEnabled.asStateFlow()

    private val _ignoringBatteryOptimizations = MutableStateFlow(checkIgnoringBatteryOptimizations())
    val ignoringBatteryOptimizations: StateFlow<Boolean> = _ignoringBatteryOptimizations.asStateFlow()

    private val _readSmsGranted = MutableStateFlow(checkReadSmsGranted())
    val readSmsGranted: StateFlow<Boolean> = _readSmsGranted.asStateFlow()

    private val _readPhoneStateGranted = MutableStateFlow(checkReadPhoneStateGranted())
    val readPhoneStateGranted: StateFlow<Boolean> = _readPhoneStateGranted.asStateFlow()

    private val _readCallLogGranted = MutableStateFlow(checkReadCallLogGranted())
    val readCallLogGranted: StateFlow<Boolean> = _readCallLogGranted.asStateFlow()

    private val _readContactsGranted = MutableStateFlow(checkReadContactsGranted())
    val readContactsGranted: StateFlow<Boolean> = _readContactsGranted.asStateFlow()

    private val _retryingIds = MutableStateFlow<Set<String>>(emptySet())
    val retryingIds: StateFlow<Set<String>> = _retryingIds.asStateFlow()

    init {
        refreshSystemStatus()
        repo.checkAndApplyDailyResetIfDue()
        refreshInstalledApps()
    }

    fun refreshSystemStatus() {
        _postNotificationsGranted.value = checkPostNotificationsGranted()
        _notificationAccessEnabled.value = checkNotificationAccess()
        _ignoringBatteryOptimizations.value = checkIgnoringBatteryOptimizations()
        _readSmsGranted.value = checkReadSmsGranted()
        _readPhoneStateGranted.value = checkReadPhoneStateGranted()
        _readCallLogGranted.value = checkReadCallLogGranted()
        _readContactsGranted.value = checkReadContactsGranted()
    }

    fun checkDailyReset() {
        repo.checkAndApplyDailyResetIfDue()
    }

    fun rebindNotificationListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(appCtx, NotificationListenerServiceImpl::class.java)
                )
            } catch (_: Exception) {}
        }
        refreshSystemStatus()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val list = withContext(Dispatchers.IO) {
                loadAllInstalledApps()
            }
            _installedApps.value = list
            _isLoadingApps.value = false
        }
    }

    private fun loadAllInstalledApps(): List<InstalledAppEntry> {
        return try {
            val pm = appCtx.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherPackages = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()

            val appFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_META_DATA
            } else {
                PackageManager.GET_META_DATA
            }
            val installedInfos = pm.getInstalledApplications(appFlags)
            val entriesByPkg = LinkedHashMap<String, InstalledAppEntry>(installedInfos.size)

            for (ai in installedInfos) {
                val pkg = ai.packageName ?: continue
                if (entriesByPkg.containsKey(pkg)) continue
                val label = try {
                    pm.getApplicationLabel(ai).toString().ifBlank { pkg }
                } catch (_: Exception) {
                    pkg
                }
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                entriesByPkg[pkg] = InstalledAppEntry(
                    packageName = pkg,
                    name = label,
                    isBlockedLoop = NotificationFilter.isBlockedLoopPackage(pkg, appCtx.packageName),
                    isSystemApp = isSystem,
                    hasLauncherIcon = pkg in launcherPackages
                )
            }

            // Ensure any manually added or previously selected/observed package is always visible
            val extraPackages = repo.currentSettings().selectedPackages + repo.currentSettings().observedPackages
            for (pkg in extraPackages) {
                if (pkg.isBlank() || entriesByPkg.containsKey(pkg)) continue
                entriesByPkg[pkg] = InstalledAppEntry(
                    packageName = pkg,
                    name = pkg,
                    isBlockedLoop = NotificationFilter.isBlockedLoopPackage(pkg, appCtx.packageName),
                    isSystemApp = false,
                    hasLauncherIcon = pkg in launcherPackages
                )
            }

            entriesByPkg.values.sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setForwardingEnabled(enabled: Boolean) {
        repo.setForwardingEnabled(enabled)
        TeleNotiTileService.requestUpdate(appCtx)
    }

    fun saveCredentials(botToken: String, chatId: String) {
        repo.saveCredentials(botToken, chatId)
        TelegramReplyPoller.syncState(appCtx)
    }

    fun sendTestMessage(botToken: String, chatId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (ok, msg) = TelegramApi.sendTestMessage(
                botToken = botToken.trim(),
                chatId = chatId.trim(),
                text = "[테스트 메시지] 이 메시지는 텔레노티에서 보냈습니다."
            )
            onResult(ok, msg)
        }
    }

    fun sendTestMessageToDefaultRecipients(onResult: (Int, Int, String) -> Unit) {
        val current = repo.currentSettings()
        val recipients = ChatTargetResolver.resolveDefaultRecipients(current)
        if (current.botToken.isBlank() || recipients.isEmpty()) {
            onResult(0, 1, "봇 토큰과 기본 Chat ID 또는 프로필을 먼저 설정하세요.")
            return
        }
        viewModelScope.launch {
            var okCount = 0
            var failCount = 0
            var lastMsg = ""
            for (recipient in recipients) {
                val (ok, msg) = TelegramApi.sendTestMessage(
                    botToken = current.botToken.trim(),
                    chatId = recipient.chatId,
                    text = "[테스트 메시지 · ${recipient.label}] 이 메시지는 텔레노티에서 보냈습니다."
                )
                if (ok) {
                    okCount++
                } else {
                    failCount++
                    lastMsg = msg
                }
            }
            onResult(okCount, failCount, lastMsg)
        }
    }

    fun fetchChats(botToken: String, onResult: (List<DetectedChat>, String?) -> Unit) {
        viewModelScope.launch {
            val (chats, err) = TelegramReplyPoller.withPausedPolling(appCtx) {
                TelegramApi.fetchLatestChats(botToken.trim())
            }
            onResult(chats, err)
        }
    }

    fun togglePackage(packageName: String) {
        if (NotificationFilter.isBlockedLoopPackage(packageName, appCtx.packageName)) return
        repo.togglePackage(packageName)
    }

    fun addManualPackage(packageName: String): Boolean {
        val clean = packageName.trim()
        if (clean.isBlank() || NotificationFilter.isBlockedLoopPackage(clean, appCtx.packageName)) {
            return false
        }
        val current = repo.currentSettings().selectedPackages
        repo.setSelectedPackages(current + clean)
        repo.recordObservedPackage(clean)
        refreshInstalledApps()
        return true
    }

    fun selectPackages(packages: Set<String>) {
        val safe = packages.filterNot {
            NotificationFilter.isBlockedLoopPackage(it, appCtx.packageName)
        }.toSet()
        repo.setSelectedPackages(repo.currentSettings().selectedPackages + safe)
    }

    fun selectAllSafeApps() {
        val safe = _installedApps.value
            .filter { !it.isBlockedLoop && (!it.isSystemApp || it.hasLauncherIcon) }
            .map { it.packageName }
            .toSet()
        repo.setSelectedPackages(safe)
    }

    fun clearAllSelectedApps() {
        repo.setSelectedPackages(emptySet())
    }

    fun setAppCustomRule(rule: AppCustomRule) {
        repo.setAppCustomRule(rule)
        TelegramReplyPoller.syncState(appCtx)
    }

    fun removeAppCustomRule(packageName: String) {
        repo.removeAppCustomRule(packageName)
        TelegramReplyPoller.syncState(appCtx)
    }

    fun saveChatProfile(id: String?, name: String, chatId: String): Boolean {
        val cleanChatId = chatId.trim()
        if (cleanChatId.isEmpty()) return false
        val cleanName = name.trim().ifEmpty { cleanChatId }
        repo.upsertChatProfile(
            ChatProfile(
                id = id?.trim().orEmpty(),
                name = cleanName,
                chatId = cleanChatId
            )
        )
        TelegramReplyPoller.syncState(appCtx)
        return true
    }

    fun deleteChatProfile(profileId: String) {
        repo.deleteChatProfile(profileId)
        TelegramReplyPoller.syncState(appCtx)
    }

    fun toggleDefaultProfile(profileId: String) {
        repo.toggleDefaultProfile(profileId)
        TelegramReplyPoller.syncState(appCtx)
    }

    fun setMessageFormat(format: String) {
        repo.setMessageFormat(format)
    }

    fun setKeywordFilter(config: KeywordFilterConfig) {
        repo.setKeywordFilter(config)
    }

    fun setQuietHours(config: QuietHoursConfig) {
        repo.setQuietHours(config)
    }

    fun setDedupeWindowSeconds(seconds: Int) {
        repo.setDedupeWindowSeconds(seconds)
    }

    fun setIgnoreOngoing(ignore: Boolean) {
        repo.setIgnoreOngoing(ignore)
    }

    fun setLmsEnrichmentEnabled(enabled: Boolean) {
        repo.setLmsEnrichmentEnabled(enabled)
    }

    fun setImageForwardingEnabled(enabled: Boolean) {
        repo.setImageForwardingEnabled(enabled)
    }

    fun setTelegramReplyEnabled(enabled: Boolean) {
        repo.setTelegramReplyEnabled(enabled)
        TelegramReplyPoller.syncState(appCtx)
    }

    fun setIncomingCallAlertEnabled(enabled: Boolean) {
        repo.setIncomingCallAlertEnabled(enabled)
    }

    fun setDailyResetConfig(config: DailyResetConfig) {
        repo.setDailyResetConfig(config)
        DailyResetScheduler.scheduleNextReset(appCtx)
    }

    fun setThemeMode(mode: ThemeMode) {
        repo.setThemeMode(mode)
    }

    fun setBackgroundEnabled(enabled: Boolean): Result<Unit> {
        repo.setBackgroundEnabled(enabled)
        return try {
            if (enabled) {
                AppKeepAliveService.start(appCtx)
            } else {
                AppKeepAliveService.stop(appCtx)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun retryItem(id: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val item = repo.currentSettings().recentNotifications.firstOrNull { it.id == id } ?: return
        val currentSettings = repo.currentSettings()
        val targetChatId = item.chatId.ifBlank { currentSettings.chatId }
        if (currentSettings.botToken.isBlank() || targetChatId.isBlank()) {
            onResult(false, "봇 토큰 또는 Chat ID를 먼저 설정하세요.")
            return
        }

        viewModelScope.launch {
            _retryingIds.value = _retryingIds.value + id
            try {
                val res = deliverLogItem(item, currentSettings.botToken, targetChatId)
                if (res.success) {
                    val shouldIncrementToday = item.status != DeliveryStatus.SENT
                    repo.updateLogStatus(
                        id = id,
                        status = DeliveryStatus.SENT,
                        errorMessage = null,
                        incrementSentToday = shouldIncrementToday,
                        incrementRetryCount = false,
                        clearImageCache = true
                    )
                    onResult(true, "재전송 성공")
                } else {
                    repo.updateLogStatus(
                        id = id,
                        status = if (res.retryable) DeliveryStatus.PENDING_RETRY else DeliveryStatus.FAILED,
                        errorMessage = res.message,
                        incrementSentToday = false,
                        incrementRetryCount = true
                    )
                    onResult(false, res.message)
                }
            } finally {
                _retryingIds.value = _retryingIds.value - id
            }
        }
    }

    fun retryAllPendingOrFailed(onResult: (Int, Int) -> Unit = { _, _ -> }) {
        val currentSettings = repo.currentSettings()
        val targets = currentSettings.recentNotifications
            .filter { it.status == DeliveryStatus.PENDING_RETRY || it.status == DeliveryStatus.FAILED }
            .asReversed()
        if (targets.isEmpty()) {
            onResult(0, 0)
            return
        }
        if (currentSettings.botToken.isBlank()) {
            onResult(0, targets.size)
            return
        }

        viewModelScope.launch {
            var succeeded = 0
            var failed = 0
            for (item in targets) {
                val targetChatId = item.chatId.ifBlank { currentSettings.chatId }
                if (targetChatId.isBlank()) {
                    failed++
                    continue
                }
                _retryingIds.value = _retryingIds.value + item.id
                try {
                    val res = deliverLogItem(item, currentSettings.botToken, targetChatId)
                    if (res.success) {
                        repo.updateLogStatus(
                            id = item.id,
                            status = DeliveryStatus.SENT,
                            errorMessage = null,
                            incrementSentToday = true,
                            incrementRetryCount = false,
                            clearImageCache = true
                        )
                        succeeded++
                    } else {
                        repo.updateLogStatus(
                            id = item.id,
                            status = if (res.retryable) DeliveryStatus.PENDING_RETRY else DeliveryStatus.FAILED,
                            errorMessage = res.message,
                            incrementSentToday = false,
                            incrementRetryCount = true
                        )
                        failed++
                    }
                } finally {
                    _retryingIds.value = _retryingIds.value - item.id
                }
            }
            onResult(succeeded, failed)
        }
    }

    private suspend fun deliverLogItem(
        item: NotificationLogItem,
        botToken: String,
        targetChatId: String
    ): TelegramResult {
        val cachedPhoto = PendingImageStore.readBytes(item.imageCachePath)
        return if (cachedPhoto != null) {
            TelegramApi.sendPhotoWithText(
                botToken = botToken,
                chatId = targetChatId,
                photoBytes = cachedPhoto,
                text = item.formattedMessage
            )
        } else {
            TelegramApi.sendMessage(
                botToken = botToken,
                chatId = targetChatId,
                text = item.formattedMessage
            )
        }
    }

    fun clearRecentNotifications() {
        repo.clearRecentNotifications()
    }

    fun clearLastCrash() {
        repo.clearLastCrash()
    }

    private fun checkPostNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkNotificationAccess(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                appCtx.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            enabled.contains(appCtx.packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun checkIgnoringBatteryOptimizations(): Boolean {
        return try {
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(appCtx.packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun checkReadSmsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkReadPhoneStateGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkReadCallLogGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkReadContactsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
