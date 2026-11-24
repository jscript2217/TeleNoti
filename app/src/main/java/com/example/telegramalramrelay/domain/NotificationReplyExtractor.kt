package com.example.telegramalramrelay.domain

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

data class ExtractedReplyAction(
    val actionIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
)

object NotificationReplyExtractor {
    private val REPLY_HINT_KEYWORDS = listOf("답장", "reply", "response")

    fun extractReplyAction(notification: Notification): ExtractedReplyAction? {
        val candidates = mutableListOf<Pair<Int, ExtractedReplyAction>>()

        try {
            val actionCount = NotificationCompat.getActionCount(notification)
            for (i in 0 until actionCount) {
                val action = NotificationCompat.getAction(notification, i) ?: continue
                scoreAction(action)?.let { candidates.add(it) }
            }
        } catch (_: Throwable) {}

        try {
            val wearableActions = NotificationCompat.WearableExtender(notification).actions
            for (action in wearableActions) {
                scoreAction(action)?.let { candidates.add(it) }
            }
        } catch (_: Throwable) {}

        return candidates.maxByOrNull { it.first }?.second
    }

    fun sendInlineReply(
        context: Context,
        target: ReplyTarget,
        replyText: String
    ): Result<Unit> {
        val pendingIntent = target.actionIntent
            ?: return Result.failure(IllegalStateException("알림 답장 인텐트가 유효하지 않습니다."))
        val freeFormInputs = target.remoteInputs.filter { it.allowFreeFormInput }.toTypedArray()
        if (freeFormInputs.isEmpty()) {
            return Result.failure(IllegalStateException("입력 가능한 답장 필드가 없습니다."))
        }

        return try {
            val fillInIntent = Intent()
            val results = Bundle()
            for (ri in freeFormInputs) {
                results.putCharSequence(ri.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(freeFormInputs, fillInIntent, results)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                RemoteInput.setResultsSource(fillInIntent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            }
            pendingIntent.send(context, 0, fillInIntent)
            Result.success(Unit)
        } catch (_: PendingIntent.CanceledException) {
            Result.failure(IllegalStateException("이미 닫히거나 만료된 알림입니다."))
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(t.message ?: "알림 답장 전송 실패"))
        }
    }

    private fun scoreAction(action: NotificationCompat.Action): Pair<Int, ExtractedReplyAction>? {
        val intent = action.actionIntent ?: return null
        val remoteInputs = action.remoteInputs ?: return null
        val freeForm = remoteInputs.filter { it.allowFreeFormInput }
        if (freeForm.isEmpty()) return null

        var score = 1
        if (action.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY) {
            score += 10
        }
        val title = action.title?.toString().orEmpty()
        val hasKeywordInTitle = REPLY_HINT_KEYWORDS.any { title.contains(it, ignoreCase = true) }
        val hasKeywordInKey = freeForm.any { ri ->
            REPLY_HINT_KEYWORDS.any { ri.resultKey.contains(it, ignoreCase = true) }
        }
        if (hasKeywordInTitle || hasKeywordInKey) {
            score += 5
        }

        return score to ExtractedReplyAction(
            actionIntent = intent,
            remoteInputs = remoteInputs
        )
    }
}
