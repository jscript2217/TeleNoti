package com.example.telegramalramrelay.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.domain.CallEventDecision
import com.example.telegramalramrelay.domain.ChatTargetResolver
import com.example.telegramalramrelay.domain.INCOMING_CALL_PACKAGE
import com.example.telegramalramrelay.domain.IncomingCallFormatter
import com.example.telegramalramrelay.domain.IncomingCallPolicy
import com.example.telegramalramrelay.domain.IncomingCallStateTracker
import com.example.telegramalramrelay.network.TelegramApi
import com.example.telegramalramrelay.worker.RetryDeliveryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object IncomingCallDispatcher {
    private const val WAIT_FOR_NUMBER_DELAY_MS = 1_500L
    private const val DELIVERY_TIMEOUT_MS = 7_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = IncomingCallStateTracker()
    private val waitLock = Any()
    private var pendingWaitJob: Job? = null
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

        when (val decision = tracker.onRinging(hasNumberExtra, rawNumber, hasCallLog, nowMillis)) {
            CallEventDecision.Ignore -> {
                finishOnce()
            }

            CallEventDecision.WaitForNumber -> {
                synchronized(waitLock) {
                    if (pendingWaitJob?.isActive == true) {
                        finishOnce()
                        return
                    }
                    cancelPendingWaitLocked()
                    pendingWaitFinish = finishOnce
                    var jobRef: Job? = null
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            delay(WAIT_FOR_NUMBER_DELAY_MS)
                            val fallback = tracker.onFallbackTimeout(System.currentTimeMillis())
                            if (fallback is CallEventDecision.SendNow) {
                                deliverIncomingCall(appCtx, fallback.rawNumber, hasCallLog)
                            }
                        } finally {
                            synchronized(waitLock) {
                                if (pendingWaitJob === jobRef) {
                                    pendingWaitJob = null
                                    pendingWaitFinish = null
                                }
                            }
                            finishOnce()
                        }
                    }
                    jobRef = job
                    pendingWaitJob = job
                    job.start()
                }
            }

            is CallEventDecision.SendNow -> {
                synchronized(waitLock) {
                    cancelPendingWaitLocked()
                }
                scope.launch {
                    try {
                        deliverIncomingCall(appCtx, decision.rawNumber, hasCallLog)
                    } finally {
                        finishOnce()
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
    }

    private fun cancelPendingWaitLocked() {
        pendingWaitJob?.cancel()
        pendingWaitJob = null
        val finish = pendingWaitFinish
        pendingWaitFinish = null
        finish?.invoke()
    }

    private suspend fun deliverIncomingCall(
        appCtx: Context,
        rawNumber: String?,
        hasCallLogPermission: Boolean
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

        val showRecipientLabel = targetRecipients.size > 1 || settings.chatProfiles.isNotEmpty()
        val entries = targetRecipients.map { recipient ->
            val logId = UUID.randomUUID().toString()
            val logItem = NotificationLogItem(
                id = logId,
                packageName = INCOMING_CALL_PACKAGE,
                appName = "수신 전화",
                title = title,
                text = text,
                formattedMessage = message,
                chatId = recipient.chatId,
                timestampMillis = nowMillis,
                timeDisplay = timeStr,
                status = DeliveryStatus.PENDING_RETRY,
                emoji = "📞",
                recipientLabel = if (showRecipientLabel) recipient.label else null
            )
            repo.addLogItem(logItem)
            recipient to logId
        }

        val retryFlags = withTimeoutOrNull(DELIVERY_TIMEOUT_MS) {
            coroutineScope {
                entries.map { (recipient, logId) ->
                    async {
                        val result = TelegramApi.sendMessage(
                            botToken = settings.botToken,
                            chatId = recipient.chatId,
                            text = message
                        )
                        when {
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
                }.awaitAll()
            }
        }

        if (retryFlags == null) {
            val snapshotById = repo.currentSettings().recentNotifications.associateBy { it.id }
            entries.forEach { (_, logId) ->
                if (snapshotById[logId]?.status == DeliveryStatus.PENDING_RETRY) {
                    repo.updateLogStatus(
                        id = logId,
                        status = DeliveryStatus.PENDING_RETRY,
                        errorMessage = "전송 시간 초과 (백그라운드 재시도 대기)",
                        incrementSentToday = false,
                        incrementRetryCount = false
                    )
                }
            }
            RetryDeliveryWorker.enqueue(appCtx)
        } else if (retryFlags.any { it }) {
            RetryDeliveryWorker.enqueue(appCtx)
        }
    }

    private fun lookupContactNameIfAllowed(context: Context, rawNumber: String?): String? {
        val clean = rawNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return null
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
