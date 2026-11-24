package com.example.telegramalramrelay.domain

import com.example.telegramalramrelay.network.IncomingTelegramReply

sealed interface ReplyRouteAction {
    data object Ignore : ReplyRouteAction
    data class ExecuteReply(
        val target: ReplyTarget,
        val replyText: String
    ) : ReplyRouteAction

    data class SendFeedback(
        val chatId: String,
        val replyToMessageId: Long,
        val message: String
    ) : ReplyRouteAction
}

object TelegramReplyRouter {
    fun route(
        incoming: IncomingTelegramReply,
        authorizedChatIds: Set<String>,
        registry: ReplyActionRegistry
    ): ReplyRouteAction {
        val cleanChatId = incoming.chatId.trim()
        if (cleanChatId.isEmpty() || cleanChatId !in authorizedChatIds) {
            return ReplyRouteAction.Ignore
        }

        val replyText = incoming.text.trim()
        if (replyText.isEmpty()) {
            return ReplyRouteAction.Ignore
        }

        val replyToId = incoming.replyToMessageId
        if (replyToId == null || replyToId <= 0L) {
            return if (incoming.chatType == "private" && !replyText.startsWith("/")) {
                ReplyRouteAction.SendFeedback(
                    chatId = cleanChatId,
                    replyToMessageId = incoming.messageId,
                    message = "💡 카톡·문자에 답장하려면 받은 알림 메시지를 스와이프(답장)해서 보내주세요."
                )
            } else {
                ReplyRouteAction.Ignore
            }
        }

        val target = registry.findTarget(cleanChatId, replyToId)
        return if (target != null) {
            ReplyRouteAction.ExecuteReply(
                target = target,
                replyText = replyText
            )
        } else {
            ReplyRouteAction.SendFeedback(
                chatId = cleanChatId,
                replyToMessageId = incoming.messageId,
                message = "⚠️ 답장할 수 없는 알림입니다. 알림이 만료되었거나(앱 재시작 등) 답장을 지원하지 않는 앱입니다."
            )
        }
    }
}
