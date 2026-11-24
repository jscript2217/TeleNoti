package com.example.telegramalramrelay

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.data.PendingImageStore
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.domain.ChatTargetResolver
import com.example.telegramalramrelay.domain.ExtractedImageSources
import com.example.telegramalramrelay.domain.FilterDecision
import com.example.telegramalramrelay.domain.MessageFormatter
import com.example.telegramalramrelay.domain.NotificationCandidate
import com.example.telegramalramrelay.domain.NotificationDeduplicator
import com.example.telegramalramrelay.domain.NotificationFilter
import com.example.telegramalramrelay.domain.NotificationImageExtractor
import com.example.telegramalramrelay.domain.NotificationReplyExtractor
import com.example.telegramalramrelay.domain.NotificationTextExtractor
import com.example.telegramalramrelay.domain.ReplyActionStore
import com.example.telegramalramrelay.domain.ReplyTarget
import com.example.telegramalramrelay.domain.SmsMmsEnricher
import com.example.telegramalramrelay.network.TelegramApi
import com.example.telegramalramrelay.network.TelegramReplyPoller
import com.example.telegramalramrelay.worker.RetryDeliveryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class NotificationListenerServiceImpl : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deduplicator = NotificationDeduplicator()

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        TelegramReplyPoller.syncState(applicationContext)
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, NotificationListenerServiceImpl::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        _isConnected.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val pkg = sbn.packageName ?: return
            val repo = SettingsRepository.getInstance(applicationContext)
            repo.recordObservedPackage(pkg)
            repo.checkAndApplyDailyResetIfDue()
            val settings = repo.currentSettings()

            val extracted = NotificationTextExtractor.extractFromNotification(
                context = applicationContext,
                packageName = pkg,
                notification = sbn.notification
            )
            val imageSources = if (settings.imageForwardingEnabled) {
                NotificationImageExtractor.extractImageSources(sbn.notification)
            } else {
                ExtractedImageSources()
            }
            val replyAction = NotificationReplyExtractor.extractReplyAction(sbn.notification)
            val notifKey = sbn.key ?: "$pkg:${sbn.id}"
            val candidateText = if (imageSources.hasAnySource && extracted.text.isBlank()) {
                "[이미지]"
            } else {
                extracted.text
            }

            val flags = sbn.notification.flags
            val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0
            val postTime = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

            val candidate = NotificationCandidate(
                packageName = pkg,
                title = extracted.title,
                text = candidateText,
                isGroupSummary = isGroupSummary,
                isOngoing = isOngoing,
                timestampMillis = postTime
            )

            val cal = Calendar.getInstance()
            val decision = NotificationFilter.evaluate(
                candidate = candidate,
                settings = settings,
                selfPackageName = packageName,
                currentHour = cal.get(Calendar.HOUR_OF_DAY),
                currentMinute = cal.get(Calendar.MINUTE)
            )
            if (decision is FilterDecision.Reject) return

            val nowMillis = System.currentTimeMillis()
            val allowedByDedupe = deduplicator.shouldAllow(
                packageName = pkg,
                title = extracted.title,
                text = candidateText,
                nowMillis = nowMillis,
                windowMillis = settings.dedupeWindowSeconds * 1000L
            )
            if (!allowedByDedupe) return

            val customRule = settings.appCustomRules[pkg]
            val targetRecipients = ChatTargetResolver.resolveRecipientsForPackage(settings, pkg)
            val template = customRule?.customFormat?.takeIf { it.isNotBlank() } ?: settings.messageFormat

            if (settings.botToken.isBlank() || targetRecipients.isEmpty()) return

            val appName = try {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(ai).toString()
            } catch (_: Throwable) {
                pkg
            }

            val timeStr = try {
                val sdf = SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.format(Date(postTime))
            } catch (_: Throwable) {
                postTime.toString()
            }

            val botToken = settings.botToken
            val shouldEnrichLms = settings.lmsEnrichmentEnabled &&
                SmsMmsEnricher.isMessagingPackage(applicationContext, pkg)
            val imageForwarding = settings.imageForwardingEnabled
            val showRecipientLabel = targetRecipients.size > 1 ||
                customRule?.hasCustomRecipients == true ||
                settings.chatProfiles.isNotEmpty()

            scope.launch {
                val enrichment = if (shouldEnrichLms) {
                    SmsMmsEnricher.enrichWithMediaIfPossible(
                        context = applicationContext,
                        packageName = pkg,
                        senderHint = extracted.title,
                        notificationText = candidateText,
                        postTimeMillis = postTime
                    )
                } else {
                    null
                }

                val jpegBytes = if (imageForwarding) {
                    NotificationImageExtractor.loadCompressedJpeg(
                        context = applicationContext,
                        sources = imageSources,
                        extraUris = enrichment?.imageUris.orEmpty()
                    )
                } else {
                    null
                }

                val enrichedText = enrichment?.text ?: candidateText
                val finalText = if (jpegBytes != null && enrichedText.isBlank()) {
                    "[이미지]"
                } else {
                    enrichedText
                }

                val message = MessageFormatter.format(
                    template = template,
                    appName = appName,
                    title = extracted.title,
                    text = finalText,
                    time = timeStr,
                    packageName = pkg
                )

                val retryFlags = coroutineScope {
                    targetRecipients.map { recipient ->
                        async {
                            val logId = UUID.randomUUID().toString()
                            val cachedPath = if (jpegBytes != null) {
                                PendingImageStore.save(applicationContext, logId, jpegBytes)
                            } else {
                                null
                            }

                            val logItem = NotificationLogItem(
                                id = logId,
                                packageName = pkg,
                                appName = appName,
                                title = extracted.title,
                                text = finalText,
                                formattedMessage = message,
                                chatId = recipient.chatId,
                                timestampMillis = postTime,
                                timeDisplay = timeStr,
                                status = DeliveryStatus.PENDING_RETRY,
                                hasImage = jpegBytes != null,
                                imageCachePath = cachedPath,
                                canReply = replyAction != null,
                                recipientLabel = if (showRecipientLabel) recipient.label else null
                            )
                            repo.addLogItem(logItem)

                            val result = if (jpegBytes != null) {
                                TelegramApi.sendPhotoWithText(
                                    botToken = botToken,
                                    chatId = recipient.chatId,
                                    photoBytes = jpegBytes,
                                    text = message
                                )
                            } else {
                                TelegramApi.sendMessage(
                                    botToken = botToken,
                                    chatId = recipient.chatId,
                                    text = message
                                )
                            }

                            if (result.success) {
                                if (replyAction != null && result.messageIds.isNotEmpty()) {
                                    ReplyActionStore.register(
                                        telegramChatId = recipient.chatId,
                                        telegramMessageIds = result.messageIds,
                                        target = ReplyTarget(
                                            telegramChatId = recipient.chatId,
                                            packageName = pkg,
                                            appName = appName,
                                            senderTitle = extracted.title,
                                            notificationKey = notifKey,
                                            actionIntent = replyAction.actionIntent,
                                            remoteInputs = replyAction.remoteInputs,
                                            createdAtMillis = System.currentTimeMillis()
                                        )
                                    )
                                }
                                repo.updateLogStatus(
                                    id = logId,
                                    status = DeliveryStatus.SENT,
                                    errorMessage = null,
                                    incrementSentToday = true,
                                    incrementRetryCount = false,
                                    clearImageCache = true
                                )
                                false
                            } else if (result.retryable) {
                                repo.updateLogStatus(
                                    id = logId,
                                    status = DeliveryStatus.PENDING_RETRY,
                                    errorMessage = result.message,
                                    incrementSentToday = false,
                                    incrementRetryCount = false
                                )
                                true
                            } else {
                                repo.updateLogStatus(
                                    id = logId,
                                    status = DeliveryStatus.FAILED,
                                    errorMessage = result.message,
                                    incrementSentToday = false,
                                    incrementRetryCount = false
                                )
                                false
                            }
                        }
                    }.awaitAll()
                }

                if (retryFlags.any { it }) {
                    RetryDeliveryWorker.enqueue(applicationContext)
                }
            }
        } catch (_: Exception) {
            // Protect NotificationListenerService process from crashing on malformed notifications
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }
}
