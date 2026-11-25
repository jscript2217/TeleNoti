package com.example.telegramalramrelay.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.data.TeleNotiSettings
import com.example.telegramalramrelay.domain.CallEventDecision
import com.example.telegramalramrelay.domain.ChatTargetResolver
import com.example.telegramalramrelay.domain.INCOMING_CALL_PACKAGE
import com.example.telegramalramrelay.domain.IncomingCallFormatter
import com.example.telegramalramrelay.domain.IncomingCallPolicy
import com.example.telegramalramrelay.domain.IncomingCallStateTracker
import com.example.telegramalramrelay.network.TelegramApi
import com.example.telegramalramrelay.network.TelegramResult
import com.example.telegramalramrelay.worker.RetryDeliveryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object IncomingCallDispatcher {
    private const val TAG = "IncomingCallDispatcher"
    private const val WAIT_FOR_NUMBER_DELAY_MS = 4_500L
    private const val GO_ASYNC_MAX_HOLD_MS = 8_500L
    private const val HTTP_CONNECT_TIMEOUT_MS = 8_000
    private const val HTTP_READ_TIMEOUT_MS = 8_000

    private data class PreparedCallAlert(
        val title: String,
        val text: String,
        val timeStr: String,
        val nowMillis: Long,
        val message: String
    )

    private data class SentCallTarget(
        val chatId: String,
        val messageId: Long?,
        val logId: String
    )

    private data class FallbackDeliveryRecord(
        val sessionId: Long,
        val targets: List<SentCallTarget>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = IncomingCallStateTracker()
    private val waitLock = Any()
    private val deliveryMutex = Mutex()
    private val activeFallbackRecord = AtomicReference<FallbackDeliveryRecord?>(null)
    private var pendingWaitJob: Job? = null
    private var pendingWaitSessionId: Long = -1L
    private var pendingWaitFinish: (() -> Unit)? = null

    fun onRinging(
        context: Context,
        hasNumberExtra: Boolean,
        rawNumber: String?,
        finishOnce: () -> Unit
    ) {
        val appCtx = context.applicationContext
        val hasCallLog = ContextCompat.checkSelfPermission(
            appCtx,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        val nowMillis = System.currentTimeMillis()
        val rawLength = rawNumber?.length ?: 0
        val normalizedLength = IncomingCallFormatter.normalizeForKey(rawNumber).length
        val releaseWatchdog = scope.launch {
            delay(GO_ASYNC_MAX_HOLD_MS)
            finishOnce()
        }
        val finishAndCancelWatchdog: () -> Unit = {
            releaseWatchdog.cancel()
            finishOnce()
        }

        when (val decision = tracker.onRinging(hasNumberExtra, rawNumber, hasCallLog, nowMillis)) {
            CallEventDecision.Ignore -> {
                Log.d(
                    TAG,
                    "onRinging: hasExtra=$hasNumberExtra rawLen=$rawLength normLen=$normalizedLength decision=Ignore"
                )
                finishAndCancelWatchdog()
            }

            is CallEventDecision.WaitForNumber -> {
                Log.d(
                    TAG,
                    "onRinging: hasExtra=$hasNumberExtra rawLen=$rawLength normLen=$normalizedLength decision=WaitForNumber session=${decision.sessionId}"
                )
                synchronized(waitLock) {
                    if (pendingWaitJob?.isActive == true && pendingWaitSessionId == decision.sessionId) {
                        finishAndCancelWatchdog()
                        return
                    }
                    cancelPendingWaitLocked()
                    pendingWaitFinish = finishAndCancelWatchdog
                    pendingWaitSessionId = decision.sessionId
                    var jobRef: Job? = null
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            delay(WAIT_FOR_NUMBER_DELAY_MS)
                            withContext(NonCancellable) {
                                deliveryMutex.withLock {
                                    val fallback = tracker.onFallbackTimeout(
                                        nowMillis = System.currentTimeMillis(),
                                        expectedSessionId = decision.sessionId
                                    )
                                    if (fallback is CallEventDecision.SendNow) {
                                        deliverIncomingCall(
                                            appCtx = appCtx,
                                            sessionId = fallback.sessionId,
                                            rawNumber = fallback.rawNumber,
                                            hasCallLogPermission = hasCallLog,
                                            recordAsFallback = true
                                        )
                                    }
                                }
                            }
                        } finally {
                            synchronized(waitLock) {
                                if (pendingWaitJob === jobRef) {
                                    pendingWaitJob = null
                                    pendingWaitSessionId = -1L
                                    pendingWaitFinish = null
                                }
                            }
                            finishAndCancelWatchdog()
                        }
                    }
                    jobRef = job
                    pendingWaitJob = job
                    job.start()
                }
            }

            is CallEventDecision.SendNow -> {
                Log.d(
                    TAG,
                    "onRinging: hasExtra=$hasNumberExtra rawLen=$rawLength normLen=$normalizedLength decision=SendNow session=${decision.sessionId} upgrade=${decision.isUpgradeFromFallback}"
                )
                synchronized(waitLock) {
                    cancelPendingWaitLocked()
                }
                scope.launch {
                    try {
                        deliveryMutex.withLock {
                            val fallbackRecord = activeFallbackRecord.get()
                                ?.takeIf { it.sessionId == decision.sessionId }
                            if (fallbackRecord != null) {
                                activeFallbackRecord.compareAndSet(fallbackRecord, null)
                                upgradeFallbackMessages(
                                    appCtx = appCtx,
                                    fallbackRecord = fallbackRecord,
                                    rawNumber = decision.rawNumber,
                                    hasCallLogPermission = hasCallLog
                                )
                            } else {
                                deliverIncomingCall(
                                    appCtx = appCtx,
                                    sessionId = decision.sessionId,
                                    rawNumber = decision.rawNumber,
                                    hasCallLogPermission = hasCallLog,
                                    recordAsFallback = false
                                )
                            }
                        }
                    } finally {
                        finishAndCancelWatchdog()
                    }
                }
            }
        }
    }

    fun onOffhook() {
        synchronized(waitLock) {
            cancelPendingWaitLocked()
        }
        tracker.onOffhook()
    }

    fun onIdle() {
        synchronized(waitLock) {
            cancelPendingWaitLocked()
        }
        tracker.onIdle()
        activeFallbackRecord.set(null)
    }

    private fun cancelPendingWaitLocked() {
        pendingWaitJob?.cancel()
        pendingWaitJob = null
        pendingWaitSessionId = -1L
        val finish = pendingWaitFinish
        pendingWaitFinish = null
        finish?.invoke()
    }

    private fun buildPreparedCallAlert(
        appCtx: Context,
        settings: TeleNotiSettings,
        rawNumber: String?,
        hasCallLogPermission: Boolean
    ): PreparedCallAlert {
        val contactName = lookupContactNameIfAllowed(appCtx, rawNumber)
        val (title, text) = IncomingCallFormatter.buildCallerDisplay(
            contactName = contactName,
            rawNumber = rawNumber,
            hasCallLogPermission = hasCallLogPermission
        )
        val nowMillis = System.currentTimeMillis()
        val timeStr = try {
            SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(nowMillis))
        } catch (_: Throwable) {
            nowMillis.toString()
        }
        val message = IncomingCallFormatter.formatIncomingCallMessage(
            template = settings.messageFormat,
            title = title,
            text = text,
            time = timeStr
        )
        return PreparedCallAlert(
            title = title,
            text = text,
            timeStr = timeStr,
            nowMillis = nowMillis,
            message = message
        )
    }

    private fun recordTelegramSendOutcome(
        repo: SettingsRepository,
        logId: String,
        result: TelegramResult
    ): Boolean {
        return when {
            result.success -> {
                repo.updateLogStatus(
                    id = logId,
                    status = DeliveryStatus.SENT,
                    errorMessage = null,
                    incrementSentToday = true,
                    incrementRetryCount = false
                )
                false
            }

            result.retryable -> {
                repo.updateLogStatus(
                    id = logId,
                    status = DeliveryStatus.PENDING_RETRY,
                    errorMessage = result.message,
                    incrementSentToday = false,
                    incrementRetryCount = false
                )
                true
            }

            else -> {
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
    }

    private suspend fun deliverIncomingCall(
        appCtx: Context,
        sessionId: Long,
        rawNumber: String?,
        hasCallLogPermission: Boolean,
        recordAsFallback: Boolean
    ) {
        val repo = SettingsRepository.getInstance(appCtx)
        repo.checkAndApplyDailyResetIfDue()
        val settings = repo.currentSettings()

        val cal = Calendar.getInstance()
        if (!IncomingCallPolicy.shouldSend(
                settings = settings,
                currentHour = cal.get(Calendar.HOUR_OF_DAY),
                currentMinute = cal.get(Calendar.MINUTE)
            )
        ) {
            return
        }

        val targetRecipients = ChatTargetResolver.resolveDefaultRecipients(settings)
        if (settings.botToken.isBlank() || targetRecipients.isEmpty()) {
            return
        }

        val alert = buildPreparedCallAlert(
            appCtx = appCtx,
            settings = settings,
            rawNumber = rawNumber,
            hasCallLogPermission = hasCallLogPermission
        )

        val showRecipientLabel = targetRecipients.size > 1 || settings.chatProfiles.isNotEmpty()
        val entries = targetRecipients.map { recipient ->
            val logId = UUID.randomUUID().toString()
            val logItem = NotificationLogItem(
                id = logId,
                packageName = INCOMING_CALL_PACKAGE,
                appName = "수신 전화",
                title = alert.title,
                text = alert.text,
                formattedMessage = alert.message,
                chatId = recipient.chatId,
                timestampMillis = alert.nowMillis,
                timeDisplay = alert.timeStr,
                status = DeliveryStatus.PENDING_RETRY,
                emoji = "📞",
                recipientLabel = if (showRecipientLabel) recipient.label else null
            )
            repo.addLogItem(logItem, claimImmediately = true)
            recipient to logId
        }

        val messageIdsByLogId = ConcurrentHashMap<String, Long>()
        val retryFlags = coroutineScope {
            entries.map { (recipient, logId) ->
                async {
                    try {
                        val result = TelegramApi.sendMessage(
                            botToken = settings.botToken,
                            chatId = recipient.chatId,
                            text = alert.message,
                            connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS,
                            readTimeoutMs = HTTP_READ_TIMEOUT_MS
                        )
                        result.messageId?.takeIf { it > 0L }?.let { msgId ->
                            messageIdsByLogId[logId] = msgId
                        }
                        recordTelegramSendOutcome(repo, logId, result)
                    } finally {
                        repo.releaseLogDeliveryClaim(logId)
                    }
                }
            }.awaitAll()
        }

        if (recordAsFallback) {
            val targets = entries.map { (recipient, logId) ->
                SentCallTarget(
                    chatId = recipient.chatId,
                    messageId = messageIdsByLogId[logId],
                    logId = logId
                )
            }
            activeFallbackRecord.set(FallbackDeliveryRecord(sessionId = sessionId, targets = targets))
        }

        finalizeRetryScheduling(
            appCtx = appCtx,
            retryFlags = retryFlags
        )
    }

    private suspend fun upgradeFallbackMessages(
        appCtx: Context,
        fallbackRecord: FallbackDeliveryRecord,
        rawNumber: String?,
        hasCallLogPermission: Boolean
    ) {
        val repo = SettingsRepository.getInstance(appCtx)
        val settings = repo.currentSettings()
        if (!settings.forwardingEnabled || !settings.incomingCallAlertEnabled || settings.botToken.isBlank()) {
            return
        }

        val alert = buildPreparedCallAlert(
            appCtx = appCtx,
            settings = settings,
            rawNumber = rawNumber,
            hasCallLogPermission = hasCallLogPermission
        )

        val retryFlags = coroutineScope {
            fallbackRecord.targets.map { target ->
                async {
                    if (target.messageId != null && target.messageId > 0L) {
                        var editResult = TelegramApi.editMessageText(
                            botToken = settings.botToken,
                            chatId = target.chatId,
                            messageId = target.messageId,
                            text = alert.message,
                            connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS,
                            readTimeoutMs = HTTP_READ_TIMEOUT_MS
                        )
                        if (!editResult.success && editResult.retryable) {
                            delay(1_000L)
                            editResult = TelegramApi.editMessageText(
                                botToken = settings.botToken,
                                chatId = target.chatId,
                                messageId = target.messageId,
                                text = alert.message,
                                connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS,
                                readTimeoutMs = HTTP_READ_TIMEOUT_MS
                            )
                        }

                        repo.updateLogContent(
                            id = target.logId,
                            title = alert.title,
                            text = alert.text,
                            formattedMessage = alert.message
                        )
                        if (editResult.success || editResult.retryable) {
                            false
                        } else {
                            val sendResult = TelegramApi.sendMessage(
                                botToken = settings.botToken,
                                chatId = target.chatId,
                                text = alert.message,
                                connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS,
                                readTimeoutMs = HTTP_READ_TIMEOUT_MS
                            )
                            recordTelegramSendOutcome(repo, target.logId, sendResult)
                        }
                    } else {
                        repo.updateLogContent(
                            id = target.logId,
                            title = alert.title,
                            text = alert.text,
                            formattedMessage = alert.message
                        )
                        if (!repo.tryClaimLogForDelivery(target.logId)) {
                            false
                        } else {
                            try {
                                val sendResult = TelegramApi.sendMessage(
                                    botToken = settings.botToken,
                                    chatId = target.chatId,
                                    text = alert.message,
                                    connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS,
                                    readTimeoutMs = HTTP_READ_TIMEOUT_MS
                                )
                                recordTelegramSendOutcome(repo, target.logId, sendResult)
                            } finally {
                                repo.releaseLogDeliveryClaim(target.logId)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        finalizeRetryScheduling(
            appCtx = appCtx,
            retryFlags = retryFlags
        )
    }

    private fun finalizeRetryScheduling(
        appCtx: Context,
        retryFlags: List<Boolean>
    ) {
        if (retryFlags.any { it }) {
            RetryDeliveryWorker.enqueue(appCtx)
        }
    }

    private fun lookupContactNameIfAllowed(context: Context, rawNumber: String?): String? {
        val clean = IncomingCallFormatter.normalizeForKey(rawNumber).takeIf { it.isNotEmpty() }
            ?: return null
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(clean)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_IDLE -> {
                IncomingCallDispatcher.onIdle()
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                IncomingCallDispatcher.onOffhook()
            }

            TelephonyManager.EXTRA_STATE_RINGING -> {
                val appCtx = context.applicationContext
                val settings = SettingsRepository.getInstance(appCtx).currentSettings()
                val cal = Calendar.getInstance()
                if (!IncomingCallPolicy.shouldSend(
                        settings = settings,
                        currentHour = cal.get(Calendar.HOUR_OF_DAY),
                        currentMinute = cal.get(Calendar.MINUTE)
                    )
                ) {
                    return
                }

                @Suppress("DEPRECATION")
                val hasNumberExtra = intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                @Suppress("DEPRECATION")
                val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                val pendingResult = goAsync()
                val finished = AtomicBoolean(false)
                val finishOnce = {
                    if (finished.compareAndSet(false, true)) {
                        try {
                            pendingResult.finish()
                        } catch (_: Exception) {}
                    }
                }

                try {
                    IncomingCallDispatcher.onRinging(
                        context = appCtx,
                        hasNumberExtra = hasNumberExtra,
                        rawNumber = rawNumber,
                        finishOnce = finishOnce
                    )
                } catch (_: Exception) {
                    finishOnce()
                }
            }
        }
    }
}
