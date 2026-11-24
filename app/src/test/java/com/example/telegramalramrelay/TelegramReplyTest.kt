package com.example.telegramalramrelay

import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.domain.ReplyActionRegistry
import com.example.telegramalramrelay.domain.ReplyRouteAction
import com.example.telegramalramrelay.domain.ReplyTarget
import com.example.telegramalramrelay.domain.TelegramReplyRouter
import com.example.telegramalramrelay.network.IncomingTelegramReply
import com.example.telegramalramrelay.network.TelegramApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramReplyTest {

    @Test
    fun parseIncomingUpdates_extractsRepliesIgnoresNonTextAndMalformedJson() {
        val json = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1001,
                  "message": {
                    "message_id": 55,
                    "chat": { "id": 777888, "type": "private" },
                    "reply_to_message": { "message_id": 40 },
                    "text": "  응 지금 갈게!  "
                  }
                },
                {
                  "update_id": 1002,
                  "message": {
                    "message_id": 56,
                    "chat": { "id": -100999, "type": "supergroup" },
                    "text": "일반 대화"
                  }
                },
                {
                  "update_id": 1003,
                  "message": {
                    "message_id": 57,
                    "chat": { "id": 777888, "type": "private" }
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = TelegramApi.parseIncomingUpdates(json)
        assertEquals(2, parsed.size)

        assertEquals(1001L, parsed[0].updateId)
        assertEquals("777888", parsed[0].chatId)
        assertEquals(55L, parsed[0].messageId)
        assertEquals(40L, parsed[0].replyToMessageId)
        assertEquals("응 지금 갈게!", parsed[0].text)
        assertEquals("private", parsed[0].chatType)

        assertEquals(1002L, parsed[1].updateId)
        assertEquals("-100999", parsed[1].chatId)
        assertNull(parsed[1].replyToMessageId)
        assertEquals("supergroup", parsed[1].chatType)

        assertTrue(TelegramApi.parseIncomingUpdates("not valid json").isEmpty())
    }

    @Test
    fun replyActionRegistry_supportsMultiMessageIdsLatestUpdateAndChatIsolation() {
        val registry = ReplyActionRegistry(maxEntries = 3)
        val olderTarget = ReplyTarget(
            telegramChatId = "100",
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            senderTitle = "철수",
            notificationKey = "0|com.kakao.talk|1|null|100",
            createdAtMillis = 1000L
        )
        val newerTarget = olderTarget.copy(createdAtMillis = 2000L)

        // Register photo + text message IDs for older notification, then newer notification on same room
        registry.register("100", listOf(10L, 11L), olderTarget)
        registry.register("100", listOf(12L), newerTarget)

        // Replying to the older message ID resolves to the latest PendingIntent for the same conversation key
        val resolvedFromPhoto = registry.findTarget("100", 10L)
        val resolvedFromText = registry.findTarget("100", 11L)
        assertNotNull(resolvedFromPhoto)
        assertEquals(2000L, resolvedFromPhoto?.createdAtMillis)
        assertEquals(2000L, resolvedFromText?.createdAtMillis)

        // Strict chatId isolation
        assertNull(registry.findTarget("999", 10L))

        // LRU eviction when exceeding maxEntries = 3 (10, 11, 12 currently stored; adding 13 evicts oldest)
        val otherRoom = olderTarget.copy(notificationKey = "other", createdAtMillis = 3000L)
        registry.register("100", listOf(13L), otherRoom)
        assertNotNull(registry.findTarget("100", 13L))
    }

    @Test
    fun telegramReplyRouter_enforcesAuthAndRoutesCorrectActions() {
        val registry = ReplyActionRegistry()
        val target = ReplyTarget(
            telegramChatId = "100",
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            senderTitle = "영희",
            notificationKey = "room-1",
            createdAtMillis = 1000L
        )
        registry.register("100", listOf(42L), target)
        val authorized = setOf("100", "-100555")

        // Unauthorized chatId -> completely ignored
        val unauthorized = TelegramReplyRouter.route(
            IncomingTelegramReply(1L, "999", 10L, 42L, "해킹 시도", "private"),
            authorized,
            registry
        )
        assertEquals(ReplyRouteAction.Ignore, unauthorized)

        // Authorized swipe reply -> ExecuteReply
        val validReply = TelegramReplyRouter.route(
            IncomingTelegramReply(2L, "100", 11L, 42L, "오케이!", "private"),
            authorized,
            registry
        )
        assertTrue(validReply is ReplyRouteAction.ExecuteReply)
        assertEquals("오케이!", (validReply as ReplyRouteAction.ExecuteReply).replyText)
        assertEquals("영희", validReply.target.senderTitle)

        // Expired or non-replyable message ID -> SendFeedback
        val expiredReply = TelegramReplyRouter.route(
            IncomingTelegramReply(3L, "100", 12L, 9999L, "답장", "private"),
            authorized,
            registry
        )
        assertTrue(expiredReply is ReplyRouteAction.SendFeedback)

        // Direct text in private chat without reply -> SendFeedback guidance; /start ignored
        val directPrivate = TelegramReplyRouter.route(
            IncomingTelegramReply(4L, "100", 13L, null, "안녕하세요", "private"),
            authorized,
            registry
        )
        assertTrue(directPrivate is ReplyRouteAction.SendFeedback)

        val slashCmd = TelegramReplyRouter.route(
            IncomingTelegramReply(5L, "100", 14L, null, "/start", "private"),
            authorized,
            registry
        )
        assertEquals(ReplyRouteAction.Ignore, slashCmd)

        // Group chat normal message without swipe reply -> Ignore
        val groupChatter = TelegramReplyRouter.route(
            IncomingTelegramReply(6L, "-100555", 15L, null, "그룹 일반 대화", "supergroup"),
            authorized,
            registry
        )
        assertEquals(ReplyRouteAction.Ignore, groupChatter)
    }

    @Test
    fun notificationLogItem_serializesCanReplyAndKeepsLegacyDefault() {
        val item = NotificationLogItem(
            id = "rep-1",
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            title = "친구",
            text = "안녕",
            formattedMessage = "📱 카카오톡\n📌 친구\n💬 안녕",
            chatId = "100",
            timestampMillis = 12345L,
            timeDisplay = "26-11-24 20:00:00",
            status = DeliveryStatus.SENT,
            canReply = true
        )
        val parsed = NotificationLogItem.parseList(NotificationLogItem.toJsonArrayString(listOf(item)))
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].canReply)

        val legacy = NotificationLogItem.parseList(
            """[{"emoji":"🔔","app":"문자","message":"hi","time":"12:00","package":"com.android.mms"}]"""
        )
        assertFalse(legacy[0].canReply)
    }
}
