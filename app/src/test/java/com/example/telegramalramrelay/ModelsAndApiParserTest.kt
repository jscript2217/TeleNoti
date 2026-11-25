package com.example.telegramalramrelay

import com.example.telegramalramrelay.data.AppCustomRule
import com.example.telegramalramrelay.data.DeliveryClaimRegistry
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.network.TelegramApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ModelsAndApiParserTest {

    @Test
    fun parsesLegacyV1RecentNotificationsWithoutErrors() {
        val legacyJson = """
            [
              {
                "emoji": "🔔",
                "app": "카카오톡",
                "message": "📱 카카오톡 📌 친구 💬 안녕",
                "time": "26-11-24 10:15:00",
                "package": "com.kakao.talk"
              }
            ]
        """.trimIndent()

        val items = NotificationLogItem.parseList(legacyJson)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("카카오톡", item.appName)
        assertEquals("com.kakao.talk", item.packageName)
        assertEquals("📱 카카오톡 📌 친구 💬 안녕", item.formattedMessage)
        assertEquals("26-11-24 10:15:00", item.timeDisplay)
        assertEquals(DeliveryStatus.SENT, item.status)
        assertTrue(item.id.isNotBlank())
    }

    @Test
    fun roundTripsV2NotificationLogsAndCustomRules() {
        val original = listOf(
            NotificationLogItem(
                id = "id-1",
                packageName = "com.kakao.talk",
                appName = "카카오톡",
                title = "알림",
                text = "테스트",
                formattedMessage = "[카카오톡] 알림",
                chatId = "-100123",
                timestampMillis = 123456789L,
                timeDisplay = "26-11-24 11:00:00",
                status = DeliveryStatus.PENDING_RETRY,
                errorMessage = "네트워크 오류",
                retryCount = 2
            )
        )
        val parsed = NotificationLogItem.parseList(NotificationLogItem.toJsonArrayString(original))
        assertEquals(original, parsed)

        val rules = mapOf(
            "com.kakao.talk" to AppCustomRule("com.kakao.talk", "-100999", "[{app}] {text}"),
            "com.empty.app" to AppCustomRule("com.empty.app", "", "")
        )
        val serialized = AppCustomRule.toJsonString(rules)
        val parsedRules = AppCustomRule.parseMap(serialized)
        assertEquals(1, parsedRules.size)
        assertEquals("-100999", parsedRules["com.kakao.talk"]?.customChatId)
        assertEquals("[{app}] {text}", parsedRules["com.kakao.talk"]?.customFormat)
    }

    @Test
    fun computeMostRecentResetBoundary_handlesBeforeAndAfterResetTime() {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val calAfter = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 24, 15, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val boundaryAfter = SettingsRepository.computeMostRecentResetBoundary(
            nowMillis = calAfter.timeInMillis,
            resetHour = 12,
            resetMinute = 0,
            timeZone = tz
        )
        val expectedTodayNoon = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedTodayNoon.timeInMillis, boundaryAfter)

        val calBefore = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 24, 8, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val boundaryBefore = SettingsRepository.computeMostRecentResetBoundary(
            nowMillis = calBefore.timeInMillis,
            resetHour = 12,
            resetMinute = 0,
            timeZone = tz
        )
        val expectedYesterdayNoon = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 23, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedYesterdayNoon.timeInMillis, boundaryBefore)
    }

    @Test
    fun validatesBotTokenAndParsesGetUpdatesChats() {
        assertTrue(TelegramApi.isValidBotToken("123456789:ABCdefGHI_jklMNO-pqr123456"))
        assertFalse(TelegramApi.isValidBotToken("invalid-token"))
        assertFalse(TelegramApi.isValidBotToken(""))
        assertFalse(TelegramApi.isValidBotToken("123:short"))

        val updatesJson = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 1,
                  "message": {
                    "chat": {
                      "id": 111222,
                      "type": "private",
                      "first_name": "길동",
                      "last_name": "홍",
                      "username": "gildong"
                    }
                  }
                },
                {
                  "update_id": 2,
                  "channel_post": {
                    "chat": {
                      "id": -100555666,
                      "type": "channel",
                      "title": "알림 채널"
                    }
                  }
                },
                {
                  "update_id": 3,
                  "message": {
                    "chat": {
                      "id": 111222,
                      "type": "private",
                      "first_name": "길동",
                      "last_name": "홍",
                      "username": "gildong"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val chats = TelegramApi.parseChatsFromUpdatesJson(updatesJson)
        assertEquals(2, chats.size)
        // Newest update first, deduplicated
        assertEquals("111222", chats[0].chatId)
        assertEquals("길동 홍", chats[0].title)
        assertEquals("gildong", chats[0].username)
        assertEquals("-100555666", chats[1].chatId)
        assertEquals("알림 채널", chats[1].title)
        assertEquals("channel", chats[1].type)

        assertTrue(TelegramApi.parseChatsFromUpdatesJson("not json").isEmpty())
    }

    @Test
    fun parsesEditMessageResponsesCorrectly() {
        val okResp = """{"ok":true,"result":{"message_id":777}}"""
        val okParsed = TelegramApi.parseEditMessageResponse(200, okResp, fallbackMessageId = 555L)
        assertTrue(okParsed.success)
        assertEquals(777L, okParsed.messageId)
        assertEquals(listOf(777L), okParsed.messageIds)

        val notModifiedResp = """{"ok":false,"error_code":400,"description":"Bad Request: message is not modified: specified new message content and reply markup are exactly the same"}"""
        val notModifiedParsed = TelegramApi.parseEditMessageResponse(400, notModifiedResp, fallbackMessageId = 555L)
        assertTrue(notModifiedParsed.success)
        assertFalse(notModifiedParsed.retryable)
        assertEquals(555L, notModifiedParsed.messageId)

        val badRequestResp = """{"ok":false,"error_code":400,"description":"Bad Request: message to edit not found"}"""
        val badRequestParsed = TelegramApi.parseEditMessageResponse(400, badRequestResp, fallbackMessageId = 555L)
        assertFalse(badRequestParsed.success)
        assertFalse(badRequestParsed.retryable)

        val serverErrResp = """{"ok":false,"error_code":502,"description":"Bad Gateway"}"""
        val serverErrParsed = TelegramApi.parseEditMessageResponse(502, serverErrResp, fallbackMessageId = 555L)
        assertFalse(serverErrParsed.success)
        assertTrue(serverErrParsed.retryable)
    }

    @Test
    fun deliveryClaimRegistryPreventsConcurrentDuplicateClaims() {
        val registry = DeliveryClaimRegistry()

        assertTrue(registry.tryClaim("log-1"))
        assertTrue(registry.isClaimed("log-1"))
        assertFalse(registry.tryClaim("log-1"))

        assertFalse(registry.tryClaim("sent-log", isEligible = false))
        assertFalse(registry.isClaimed("sent-log"))

        assertFalse(registry.tryClaim("   "))

        registry.release("log-1")
        assertFalse(registry.isClaimed("log-1"))
        assertTrue(registry.tryClaim("log-1"))
    }
}
