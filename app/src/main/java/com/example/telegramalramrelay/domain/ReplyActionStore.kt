package com.example.telegramalramrelay.domain

import android.app.PendingIntent
import androidx.core.app.RemoteInput

data class ReplyTarget(
    val telegramChatId: String,
    val packageName: String,
    val appName: String,
    val senderTitle: String,
    val notificationKey: String,
    val actionIntent: PendingIntent? = null,
    val remoteInputs: Array<RemoteInput> = emptyArray(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

class ReplyActionRegistry(private val maxEntries: Int = 100) {
    private data class MappingKey(val chatId: String, val messageId: Long)

    private val lock = Any()
    private val mappings = LinkedHashMap<MappingKey, ReplyTarget>(32, 0.75f, true)
    private val latestByNotificationKey = LinkedHashMap<String, ReplyTarget>(32, 0.75f, true)

    fun register(
        telegramChatId: String,
        telegramMessageIds: List<Long>,
        target: ReplyTarget
    ) {
        val cleanChatId = telegramChatId.trim()
        if (cleanChatId.isEmpty() || telegramMessageIds.isEmpty()) return

        synchronized(lock) {
            for (msgId in telegramMessageIds) {
                if (msgId <= 0L) continue
                mappings[MappingKey(cleanChatId, msgId)] = target
                while (mappings.size > maxEntries) {
                    val eldest = mappings.entries.firstOrNull()?.key ?: break
                    mappings.remove(eldest)
                }
            }

            if (target.notificationKey.isNotBlank()) {
                val scopedKey = "$cleanChatId::${target.notificationKey}"
                latestByNotificationKey[scopedKey] = target
                while (latestByNotificationKey.size > maxEntries) {
                    val eldest = latestByNotificationKey.entries.firstOrNull()?.key ?: break
                    latestByNotificationKey.remove(eldest)
                }
            }
        }
    }

    fun findTarget(telegramChatId: String, replyToMessageId: Long): ReplyTarget? {
        val cleanChatId = telegramChatId.trim()
        if (cleanChatId.isEmpty() || replyToMessageId <= 0L) return null

        synchronized(lock) {
            val matched = mappings[MappingKey(cleanChatId, replyToMessageId)] ?: return null
            if (matched.telegramChatId.trim() != cleanChatId) return null

            if (matched.notificationKey.isNotBlank()) {
                val scopedKey = "$cleanChatId::${matched.notificationKey}"
                val latest = latestByNotificationKey[scopedKey]
                if (latest != null && latest.createdAtMillis >= matched.createdAtMillis) {
                    return latest
                }
            }
            return matched
        }
    }

    fun clear() {
        synchronized(lock) {
            mappings.clear()
            latestByNotificationKey.clear()
        }
    }
}

object ReplyActionStore {
    private val registry = ReplyActionRegistry(maxEntries = 100)

    fun register(
        telegramChatId: String,
        telegramMessageIds: List<Long>,
        target: ReplyTarget
    ) {
        registry.register(telegramChatId, telegramMessageIds, target)
    }

    fun findTarget(telegramChatId: String, replyToMessageId: Long): ReplyTarget? {
        return registry.findTarget(telegramChatId, replyToMessageId)
    }

    fun registry(): ReplyActionRegistry = registry

    fun clear() {
        registry.clear()
    }
}
