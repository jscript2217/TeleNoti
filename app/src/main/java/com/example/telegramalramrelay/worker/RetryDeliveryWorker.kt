package com.example.telegramalramrelay.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.PendingImageStore
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.network.TelegramApi
import java.util.concurrent.TimeUnit

class RetryDeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SettingsRepository.getInstance(applicationContext)
        repo.checkAndApplyDailyResetIfDue()
        val settings = repo.currentSettings()
        val pending = repo.getPendingRetryItems()
        if (pending.isEmpty()) return Result.success()
        if (settings.botToken.isBlank()) return Result.success()

        var needsRetry = false

        for (item in pending) {
            if (!repo.tryClaimLogForDelivery(item.id)) {
                continue
            }
            try {
                val targetChatId = item.chatId.ifBlank { settings.chatId }
                if (targetChatId.isBlank()) {
                    repo.updateLogStatus(
                        id = item.id,
                        status = DeliveryStatus.FAILED,
                        errorMessage = "Chat ID가 설정되지 않았습니다.",
                        incrementSentToday = false,
                        incrementRetryCount = false
                    )
                    continue
                }

                val cachedPhoto = PendingImageStore.readBytes(item.imageCachePath)
                val result = if (cachedPhoto != null) {
                    TelegramApi.sendPhotoWithText(
                        botToken = settings.botToken,
                        chatId = targetChatId,
                        photoBytes = cachedPhoto,
                        text = item.formattedMessage
                    )
                } else {
                    TelegramApi.sendMessage(
                        botToken = settings.botToken,
                        chatId = targetChatId,
                        text = item.formattedMessage
                    )
                }

                if (result.success) {
                    repo.updateLogStatus(
                        id = item.id,
                        status = DeliveryStatus.SENT,
                        errorMessage = null,
                        incrementSentToday = true,
                        incrementRetryCount = false,
                        clearImageCache = true
                    )
                } else if (result.retryable) {
                    val nextCount = item.retryCount + 1
                    if (nextCount >= MAX_RETRY_COUNT) {
                        repo.updateLogStatus(
                            id = item.id,
                            status = DeliveryStatus.FAILED,
                            errorMessage = "${result.message} (재시도 한도 초과)",
                            incrementSentToday = false,
                            incrementRetryCount = true
                        )
                    } else {
                        repo.updateLogStatus(
                            id = item.id,
                            status = DeliveryStatus.PENDING_RETRY,
                            errorMessage = result.message,
                            incrementSentToday = false,
                            incrementRetryCount = true
                        )
                        needsRetry = true
                    }
                } else {
                    repo.updateLogStatus(
                        id = item.id,
                        status = DeliveryStatus.FAILED,
                        errorMessage = result.message,
                        incrementSentToday = false,
                        incrementRetryCount = false
                    )
                }
            } finally {
                repo.releaseLogDeliveryClaim(item.id)
            }
        }

        return if (needsRetry) Result.retry() else Result.success()
    }

    companion object {
        const val MAX_RETRY_COUNT = 5
        private const val WORK_NAME = "telenoti_retry_delivery"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RetryDeliveryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
