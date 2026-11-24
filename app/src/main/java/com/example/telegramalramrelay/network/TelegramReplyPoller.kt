package com.example.telegramalramrelay.network

import android.content.Context
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.domain.NotificationReplyExtractor
import com.example.telegramalramrelay.domain.ReplyActionStore
import com.example.telegramalramrelay.domain.ReplyRouteAction
import com.example.telegramalramrelay.domain.TelegramReplyRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object TelegramReplyPoller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val backoffMillis = longArrayOf(3000L, 6000L, 15000L, 30000L)

    private var pollingJob: Job? = null
    private var activeBotToken: String? = null

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    fun syncState(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            syncStateInternal(appCtx)
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                pollingJob?.cancelAndJoin()
                pollingJob = null
                activeBotToken = null
                _isPolling.value = false
            }
        }
    }

    suspend fun <T> withPausedPolling(context: Context, block: suspend () -> T): T {
        val appCtx = context.applicationContext
        mutex.withLock {
            pollingJob?.cancelAndJoin()
            pollingJob = null
            activeBotToken = null
            _isPolling.value = false
        }
        return try {
            block()
        } finally {
            syncStateInternal(appCtx)
        }
    }

    private suspend fun syncStateInternal(appCtx: Context) {
        mutex.withLock {
            val repo = SettingsRepository.getInstance(appCtx)
            val settings = repo.currentSettings()
            val cleanToken = settings.botToken.trim()
            val shouldRun = settings.telegramReplyEnabled &&
                TelegramApi.isValidBotToken(cleanToken) &&
                repo.getAuthorizedChatIds().isNotEmpty()

            if (!shouldRun) {
                pollingJob?.cancelAndJoin()
                pollingJob = null
                activeBotToken = null
                _isPolling.value = false
                return
            }

            if (pollingJob?.isActive == true && activeBotToken == cleanToken) {
                _isPolling.value = true
                return
            }

            pollingJob?.cancelAndJoin()
            activeBotToken = cleanToken
            _isPolling.value = true
            pollingJob = scope.launch {
                runPollingLoop(appCtx)
            }
        }
    }

    private suspend fun runPollingLoop(appCtx: Context) {
        val repo = SettingsRepository.getInstance(appCtx)
        var errorStreak = 0

        try {
            while (scope.isActive) {
                val settings = repo.currentSettings()
                val authorizedChatIds = repo.getAuthorizedChatIds()
                if (!settings.telegramReplyEnabled ||
                    !TelegramApi.isValidBotToken(settings.botToken) ||
                    authorizedChatIds.isEmpty()
                ) {
                    break
                }

                val cleanToken = settings.botToken.trim()
                var lastUpdateId = repo.getLastTelegramUpdateId()

                // On first launch, skip historical updates so old Telegram messages never trigger replies
                if (lastUpdateId == 0L) {
                    val (latestId, initResult) = TelegramApi.fetchLatestUpdateId(cleanToken)
                    if (!initResult.success) {
                        val waitMs = backoffMillis[errorStreak.coerceAtMost(backoffMillis.lastIndex)]
                        errorStreak++
                        delay(waitMs)
                        continue
                    }
                    lastUpdateId = latestId ?: 1L
                    repo.setLastTelegramUpdateId(lastUpdateId)
                    errorStreak = 0
                }

                val (updates, pollResult) = TelegramApi.pollUpdates(
                    botToken = cleanToken,
                    offset = lastUpdateId + 1L,
                    timeoutSeconds = 25
                )

                if (!pollResult.success) {
                    val waitMs = when (pollResult.httpCode) {
                        401, 404 -> 30000L
                        409 -> 5000L
                        else -> backoffMillis[errorStreak.coerceAtMost(backoffMillis.lastIndex)]
                    }
                    errorStreak++
                    delay(waitMs)
                    continue
                }

                errorStreak = 0
                for (incoming in updates) {
                    if (incoming.updateId > lastUpdateId) {
                        lastUpdateId = incoming.updateId
                        repo.setLastTelegramUpdateId(lastUpdateId)
                    }

                    when (
                        val action = TelegramReplyRouter.route(
                            incoming = incoming,
                            authorizedChatIds = repo.getAuthorizedChatIds(),
                            registry = ReplyActionStore.registry()
                        )
                    ) {
                        ReplyRouteAction.Ignore -> Unit
                        is ReplyRouteAction.ExecuteReply -> {
                            val replyRes = NotificationReplyExtractor.sendInlineReply(
                                context = appCtx,
                                target = action.target,
                                replyText = action.replyText
                            )
                            val feedback = if (replyRes.isSuccess) {
                                "✅ [${action.target.appName}] ${action.target.senderTitle}에게 답장 전송 완료"
                            } else {
                                val reason = replyRes.exceptionOrNull()?.message ?: "알 수 없는 오류"
                                "❌ [${action.target.appName}] 답장 전송 실패: $reason"
                            }
                            TelegramApi.sendMessage(
                                botToken = cleanToken,
                                chatId = incoming.chatId,
                                text = feedback,
                                replyToMessageId = incoming.messageId
                            )
                        }
                        is ReplyRouteAction.SendFeedback -> {
                            TelegramApi.sendMessage(
                                botToken = cleanToken,
                                chatId = action.chatId,
                                text = action.message,
                                replyToMessageId = action.replyToMessageId
                            )
                        }
                    }
                }
            }
        } finally {
            _isPolling.value = false
        }
    }
}
