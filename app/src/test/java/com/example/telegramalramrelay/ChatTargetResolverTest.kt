package com.example.telegramalramrelay

import com.example.telegramalramrelay.data.AppCustomRule
import com.example.telegramalramrelay.data.ChatProfile
import com.example.telegramalramrelay.data.TeleNotiSettings
import com.example.telegramalramrelay.domain.ChatTargetResolver
import com.example.telegramalramrelay.domain.IncomingCallPolicy
import com.example.telegramalramrelay.domain.ReplyActionRegistry
import com.example.telegramalramrelay.domain.ReplyTarget
import com.example.telegramalramrelay.domain.ResolvedRecipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTargetResolverTest {

    @Test
    fun `ChatProfile JSON round-trip and malformed handling`() {
        val profiles = listOf(
            ChatProfile(id = "p1", name = "나", chatId = "111111"),
            ChatProfile(id = "p2", name = "아내", chatId = "222222"),
            ChatProfile(id = "p3", name = "가족방", chatId = "-100333333")
        )
        val encoded = ChatProfile.toJsonArrayString(profiles)
        val decoded = ChatProfile.parseList(encoded)
        assertEquals(profiles, decoded)

        assertEquals(emptyList<ChatProfile>(), ChatProfile.parseList(null))
        assertEquals(emptyList<ChatProfile>(), ChatProfile.parseList("not-json"))
        val skipBlank = ChatProfile.parseList("""[{"id":"a","name":"x","chatId":""},{"id":"b","name":"ok","chatId":"999"}]""")
        assertEquals(listOf(ChatProfile(id = "b", name = "ok", chatId = "999")), skipBlank)
    }

    @Test
    fun `AppCustomRule legacy JSON compatibility and targetProfileIds round-trip`() {
        val legacyJson = """{"com.kakao.talk":{"customChatId":"-100999","customFormat":"[{app}] {text}"}}"""
        val parsedLegacy = AppCustomRule.parseMap(legacyJson)
        val legacyRule = parsedLegacy["com.kakao.talk"]
        assertNotNull(legacyRule)
        assertEquals("-100999", legacyRule!!.customChatId)
        assertEquals("[{app}] {text}", legacyRule.customFormat)
        assertEquals(emptyList<String>(), legacyRule.targetProfileIds)
        assertTrue(legacyRule.hasCustomRecipients)
        assertTrue(legacyRule.hasCustomizations)

        val multiRules = mapOf(
            "com.kakao.talk" to AppCustomRule(
                packageName = "com.kakao.talk",
                customChatId = "777777",
                customFormat = "",
                targetProfileIds = listOf("p1", "p2", "p1")
            )
        )
        val reEncoded = AppCustomRule.toJsonString(multiRules)
        val reparsed = AppCustomRule.parseMap(reEncoded)["com.kakao.talk"]
        assertNotNull(reparsed)
        assertEquals(listOf("p1", "p2"), reparsed!!.targetProfileIds)
        assertEquals("777777", reparsed.customChatId)
    }

    @Test
    fun `parseChatIds splits delimiters and deduplicates preserving order`() {
        assertEquals(
            listOf("111", "-100222", "333", "444"),
            ChatTargetResolver.parseChatIds(" 111, -100222 ; 333\n111\t444 ")
        )
        assertEquals(emptyList<String>(), ChatTargetResolver.parseChatIds("  , ; "))
    }

    @Test
    fun `resolveDefaultRecipients handles single comma-separated and defaultProfileIds with dedupe`() {
        val p1 = ChatProfile("p1", "나", "111")
        val p2 = ChatProfile("p2", "아내", "222")
        val p3 = ChatProfile("p3", "동료", "333")

        // 1. Single direct ID matching a profile gets profile label
        val singleSettings = TeleNotiSettings(
            chatId = "111",
            chatProfiles = listOf(p1, p2)
        )
        assertEquals(listOf(ResolvedRecipient("111", "나")), ChatTargetResolver.resolveDefaultRecipients(singleSettings))

        // 2. Comma-separated primary IDs
        val commaSettings = TeleNotiSettings(
            chatId = "111, 999",
            chatProfiles = listOf(p1)
        )
        assertEquals(
            listOf(ResolvedRecipient("111", "나"), ResolvedRecipient("999", "기본")),
            ChatTargetResolver.resolveDefaultRecipients(commaSettings)
        )

        // 3. Primary + defaultProfileIds simultaneously for 2+ people, deduplicating shared chatId
        val dualSettings = TeleNotiSettings(
            chatId = "111",
            chatProfiles = listOf(p1, p2, p3),
            defaultProfileIds = setOf("p1", "p2")
        )
        assertEquals(
            listOf(ResolvedRecipient("111", "나"), ResolvedRecipient("222", "아내")),
            ChatTargetResolver.resolveDefaultRecipients(dualSettings)
        )

        // 4. Blank primary chatId + defaultProfileIds
        val profileOnlySettings = TeleNotiSettings(
            chatId = "",
            chatProfiles = listOf(p1, p2),
            defaultProfileIds = setOf("p1", "p2")
        )
        assertEquals(
            listOf(ResolvedRecipient("111", "나"), ResolvedRecipient("222", "아내")),
            ChatTargetResolver.resolveDefaultRecipients(profileOnlySettings)
        )
    }

    @Test
    fun `resolveRecipientsForPackage handles fallback single multi and deleted profile cases`() {
        val p1 = ChatProfile("p1", "나", "111")
        val p2 = ChatProfile("p2", "아내", "222")
        val p3 = ChatProfile("p3", "팀방", "-100333")

        val settings = TeleNotiSettings(
            chatId = "111",
            chatProfiles = listOf(p1, p2, p3),
            appCustomRules = mapOf(
                "com.single" to AppCustomRule("com.single", targetProfileIds = listOf("p2")),
                "com.dual" to AppCustomRule("com.dual", customChatId = "222, 444", targetProfileIds = listOf("p1", "p2")),
                "com.deleted" to AppCustomRule("com.deleted", targetProfileIds = listOf("deleted_id")),
                "com.formatOnly" to AppCustomRule("com.formatOnly", customFormat = "{app}: {text}")
            )
        )

        // No rule / format-only rule falls back to default recipients
        assertEquals(
            listOf(ResolvedRecipient("111", "나")),
            ChatTargetResolver.resolveRecipientsForPackage(settings, "com.other")
        )
        assertEquals(
            listOf(ResolvedRecipient("111", "나")),
            ChatTargetResolver.resolveRecipientsForPackage(settings, "com.formatOnly")
        )

        // Single custom profile
        assertEquals(
            listOf(ResolvedRecipient("222", "아내")),
            ChatTargetResolver.resolveRecipientsForPackage(settings, "com.single")
        )

        // Multi-recipient (2+ profiles + direct custom ID deduplicated)
        val dualResolved = ChatTargetResolver.resolveRecipientsForPackage(settings, "com.dual")
        assertEquals(
            listOf(
                ResolvedRecipient("111", "나"),
                ResolvedRecipient("222", "아내"),
                ResolvedRecipient("444", "444")
            ),
            dualResolved
        )
        assertEquals("나 외 2명", ChatTargetResolver.formatRecipientSummary(dualResolved))

        // Deleted profile with no customChatId safely falls back to default recipients
        assertEquals(
            listOf(ResolvedRecipient("111", "나")),
            ChatTargetResolver.resolveRecipientsForPackage(settings, "com.deleted")
        )
    }

    @Test
    fun `collectAllAuthorizedChatIds and IncomingCallPolicy work with profiles`() {
        val settings = TeleNotiSettings(
            botToken = "123456:ABCDEF",
            chatId = "",
            forwardingEnabled = true,
            incomingCallAlertEnabled = true,
            chatProfiles = listOf(
                ChatProfile("p1", "나", "111"),
                ChatProfile("p2", "아내", "222")
            ),
            defaultProfileIds = setOf("p2"),
            appCustomRules = mapOf(
                "com.kakao.talk" to AppCustomRule("com.kakao.talk", customChatId = "333, 444")
            )
        )

        assertTrue(ChatTargetResolver.hasAnyConfiguredRecipient(settings))
        assertTrue(IncomingCallPolicy.shouldSend(settings, currentHour = 10, currentMinute = 0))
        assertEquals(
            setOf("111", "222", "333", "444"),
            ChatTargetResolver.collectAllAuthorizedChatIds(settings)
        )

        val emptyRecipients = settings.copy(defaultProfileIds = emptySet())
        assertFalse(IncomingCallPolicy.shouldSend(emptyRecipients, currentHour = 10, currentMinute = 0))
    }

    @Test
    fun `ReplyActionRegistry keeps independent mappings per recipient chatId for same notification`() {
        val registry = ReplyActionRegistry(maxEntries = 10)
        val targetForMe = ReplyTarget(
            telegramChatId = "111",
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            senderTitle = "엄마",
            notificationKey = "kakao_key_1"
        )
        val targetForSpouse = ReplyTarget(
            telegramChatId = "222",
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            senderTitle = "엄마",
            notificationKey = "kakao_key_1"
        )

        registry.register("111", listOf(5001L), targetForMe)
        registry.register("222", listOf(6001L), targetForSpouse)

        assertEquals("111", registry.findTarget("111", 5001L)?.telegramChatId)
        assertEquals("222", registry.findTarget("222", 6001L)?.telegramChatId)
        assertNull(registry.findTarget("111", 6001L))
        assertNull(registry.findTarget("222", 5001L))
    }
}
