package com.example.telegramalramrelay

import com.example.telegramalramrelay.data.KeywordFilterConfig
import com.example.telegramalramrelay.data.QuietHoursConfig
import com.example.telegramalramrelay.data.TeleNotiSettings
import com.example.telegramalramrelay.domain.CallEventDecision
import com.example.telegramalramrelay.domain.IncomingCallFormatter
import com.example.telegramalramrelay.domain.IncomingCallPolicy
import com.example.telegramalramrelay.domain.IncomingCallStateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallHandlerTest {

    @Test
    fun `normalize and format Korean and international phone numbers`() {
        assertEquals("010-1234-5678", IncomingCallFormatter.formatKoreanPhoneNumber("01012345678"))
        assertEquals("010-1234-5678", IncomingCallFormatter.formatKoreanPhoneNumber("+82 10-1234-5678"))
        assertEquals("02-123-4567", IncomingCallFormatter.formatKoreanPhoneNumber("021234567"))
        assertEquals("02-1234-5678", IncomingCallFormatter.formatKoreanPhoneNumber("+82-2-1234-5678"))
        assertEquals("031-123-4567", IncomingCallFormatter.formatKoreanPhoneNumber("0311234567"))
        assertEquals("0507-1234-5678", IncomingCallFormatter.formatKoreanPhoneNumber("050712345678"))
        assertEquals("1588-1234", IncomingCallFormatter.formatKoreanPhoneNumber("15881234"))
        assertEquals("+16505550199", IncomingCallFormatter.formatKoreanPhoneNumber("+1 (650) 555-0199"))
        assertEquals("", IncomingCallFormatter.formatKoreanPhoneNumber("   "))
        assertEquals("01012345678", IncomingCallFormatter.normalizeForKey("+82 10-1234-5678"))
        assertEquals("01046698599", IncomingCallFormatter.normalizeForKey("tel:+821046698599"))
        assertEquals("01046698599", IncomingCallFormatter.normalizeForKey("sip:01046698599@ims.kt.co.kr"))
        assertEquals("112", IncomingCallFormatter.normalizeForKey("112"))
        assertEquals("119", IncomingCallFormatter.normalizeForKey("119"))
        listOf("Unknown", "Private", "Restricted", "-", "null", "-1", "-2", "12").forEach { placeholder ->
            assertEquals("Expected blank key for $placeholder", "", IncomingCallFormatter.normalizeForKey(placeholder))
        }
    }

    @Test
    fun `buildCallerDisplay covers all four contact and permission combinations`() {
        val withContact = IncomingCallFormatter.buildCallerDisplay(
            contactName = "홍길동",
            rawNumber = "01012345678",
            hasCallLogPermission = true
        )
        assertEquals("홍길동 (010-1234-5678)", withContact.first)
        assertEquals("전화가 걸려오고 있습니다", withContact.second)

        val numberOnly = IncomingCallFormatter.buildCallerDisplay(
            contactName = null,
            rawNumber = "+82 10-9876-5432",
            hasCallLogPermission = true
        )
        assertEquals("010-9876-5432", numberOnly.first)
        assertEquals("전화가 걸려오고 있습니다", numberOnly.second)

        val withheld = IncomingCallFormatter.buildCallerDisplay(
            contactName = null,
            rawNumber = null,
            hasCallLogPermission = true
        )
        assertEquals("발신번호 표시 제한", withheld.first)
        assertTrue(withheld.second.contains("발신번호가 표시되지 않은"))

        val noPermission = IncomingCallFormatter.buildCallerDisplay(
            contactName = null,
            rawNumber = "",
            hasCallLogPermission = false
        )
        assertEquals("발신번호 미확인", noPermission.first)
        assertTrue(noPermission.second.contains("통화 기록 권한"))
    }

    @Test
    fun `formatIncomingCallMessage supports default and custom templates`() {
        val defaultMsg = IncomingCallFormatter.formatIncomingCallMessage(
            template = "",
            title = "홍길동 (010-1234-5678)",
            text = "전화가 걸려오고 있습니다",
            time = "26-11-24 22:45:00"
        )
        assertEquals(
            "📞 수신 전화\n📌 홍길동 (010-1234-5678)\n💬 전화가 걸려오고 있습니다\n🕐 26-11-24 22:45:00",
            defaultMsg
        )

        val customMsg = IncomingCallFormatter.formatIncomingCallMessage(
            template = "[{app}] {title} | {text} ({time})",
            title = "010-1111-2222",
            text = "전화가 걸려오고 있습니다",
            time = "22:45:00"
        )
        assertEquals(
            "[📞 수신 전화] 010-1111-2222 | 전화가 걸려오고 있습니다 (22:45:00)",
            customMsg
        )
    }

    @Test
    fun `IncomingCallPolicy respects forwarding quietHours credentials and ignores selectedPackages and keywords`() {
        val base = TeleNotiSettings(
            botToken = "123456:ABCDEF",
            chatId = "987654",
            forwardingEnabled = true,
            incomingCallAlertEnabled = true,
            selectedPackages = emptySet(),
            keywordFilter = KeywordFilterConfig(
                enabled = true,
                includeKeywordsRaw = "입금",
                excludeKeywordsRaw = "전화"
            ),
            quietHours = QuietHoursConfig(
                enabled = true,
                startHour = 23,
                startMinute = 0,
                endHour = 7,
                endMinute = 0
            )
        )

        // Allowed during daytime even though selectedPackages is empty and keywordFilter is active
        assertTrue(IncomingCallPolicy.shouldSend(base, currentHour = 14, currentMinute = 30))

        // Blocked during quiet hours
        assertFalse(IncomingCallPolicy.shouldSend(base, currentHour = 23, currentMinute = 30))

        // Blocked when forwardingEnabled = false
        assertFalse(IncomingCallPolicy.shouldSend(base.copy(forwardingEnabled = false), 14, 30))

        // Blocked when incomingCallAlertEnabled = false
        assertFalse(IncomingCallPolicy.shouldSend(base.copy(incomingCallAlertEnabled = false), 14, 30))

        // Blocked when botToken or chatId missing
        assertFalse(IncomingCallPolicy.shouldSend(base.copy(botToken = ""), 14, 30))
        assertFalse(IncomingCallPolicy.shouldSend(base.copy(chatId = "  "), 14, 30))
    }

    @Test
    fun `IncomingCallStateTracker handles dual broadcasts deduplication timeout offhook and idle`() {
        val tracker = IncomingCallStateTracker(dedupeWindowMs = 15_000L)

        // 1. Android 9+ Broadcast #1 (no number, has READ_CALL_LOG) waits
        assertEquals(
            CallEventDecision.WaitForNumber(sessionId = 1L),
            tracker.onRinging(
                hasNumberExtra = false,
                rawNumber = null,
                hasCallLogPermission = true,
                nowMillis = 1_000L
            )
        )

        // 2. Broadcast #2 (with number) triggers immediate send in same session
        assertEquals(
            CallEventDecision.SendNow("010-1234-5678", sessionId = 1L, isUpgradeFromFallback = false),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "010-1234-5678",
                hasCallLogPermission = true,
                nowMillis = 1_050L
            )
        )

        // 3. Duplicate RINGING within 15s (both with equivalent +82 number and without number) suppressed
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "+82 10 1234 5678",
                hasCallLogPermission = true,
                nowMillis = 3_000L
            )
        )
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onRinging(
                hasNumberExtra = false,
                rawNumber = null,
                hasCallLogPermission = true,
                nowMillis = 3_500L
            )
        )

        // 4. Answering call (OFFHOOK) + call waiting from a DIFFERENT number sends alert in a new session
        tracker.onOffhook()
        assertEquals(
            CallEventDecision.SendNow("010-9999-8888", sessionId = 2L, isUpgradeFromFallback = false),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "010-9999-8888",
                hasCallLogPermission = true,
                nowMillis = 5_000L
            )
        )

        // 5. IDLE resets session so same caller calling back right away alerts again
        tracker.onIdle()
        assertEquals(
            CallEventDecision.WaitForNumber(sessionId = 3L),
            tracker.onRinging(
                hasNumberExtra = false,
                rawNumber = null,
                hasCallLogPermission = true,
                nowMillis = 6_000L
            )
        )
        // Fallback timeout sends when Broadcast #2 never arrives
        assertEquals(
            CallEventDecision.SendNow(null, sessionId = 3L, isUpgradeFromFallback = false),
            tracker.onFallbackTimeout(nowMillis = 6_400L)
        )

        // 6. Without READ_CALL_LOG permission, RINGING sends immediately without waiting
        tracker.onIdle()
        assertEquals(
            CallEventDecision.SendNow(null, sessionId = 4L, isUpgradeFromFallback = false),
            tracker.onRinging(
                hasNumberExtra = false,
                rawNumber = null,
                hasCallLogPermission = false,
                nowMillis = 7_000L
            )
        )

        // 7. Regression: One UI / Android 15 broadcast with hasNumberExtra=true and null/empty number
        // must wait instead of firing a duplicate "발신번호 표시 제한" alert before the real number arrives.
        tracker.onIdle()
        assertEquals(
            CallEventDecision.WaitForNumber(sessionId = 5L),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = null,
                hasCallLogPermission = true,
                nowMillis = 8_000L
            )
        )
        assertEquals(
            CallEventDecision.WaitForNumber(sessionId = 5L),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "   ",
                hasCallLogPermission = true,
                nowMillis = 8_050L
            )
        )
        assertEquals(
            CallEventDecision.SendNow("010-4669-8599", sessionId = 5L, isUpgradeFromFallback = false),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "010-4669-8599",
                hasCallLogPermission = true,
                nowMillis = 8_300L
            )
        )
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "",
                hasCallLogPermission = true,
                nowMillis = 8_400L
            )
        )
    }

    @Test
    fun `IncomingCallStateTracker waits on placeholder extras and flags delayed fallback upgrade`() {
        val tracker = IncomingCallStateTracker(dedupeWindowMs = 15_000L)

        // 1. Placeholder/sentinel extras (< 3 digits) wait in session 1 instead of firing immediate alert
        listOf("Unknown", "Private", "-1", "12").forEachIndexed { idx, sentinel ->
            assertEquals(
                CallEventDecision.WaitForNumber(sessionId = 1L),
                tracker.onRinging(
                    hasNumberExtra = true,
                    rawNumber = sentinel,
                    hasCallLogPermission = true,
                    nowMillis = 1_000L + idx * 20L
                )
            )
        }
        assertEquals(
            CallEventDecision.SendNow("010-4669-8599", sessionId = 1L, isUpgradeFromFallback = false),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "010-4669-8599",
                hasCallLogPermission = true,
                nowMillis = 1_200L
            )
        )

        // 2. Late Broadcast #2 after fallback timeout keeps same sessionId and marks isUpgradeFromFallback=true
        tracker.onIdle()
        assertEquals(
            CallEventDecision.WaitForNumber(sessionId = 2L),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "Restricted",
                hasCallLogPermission = true,
                nowMillis = 10_000L
            )
        )
        // Stale timeout targeting session 1 is ignored
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onFallbackTimeout(nowMillis = 13_000L, expectedSessionId = 1L)
        )
        // Active timeout fires fallback
        assertEquals(
            CallEventDecision.SendNow(null, sessionId = 2L, isUpgradeFromFallback = false),
            tracker.onFallbackTimeout(nowMillis = 13_000L, expectedSessionId = 2L)
        )
        // Repeat withheld broadcast stays ignored while real number upgrades in-place
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "Unknown",
                hasCallLogPermission = true,
                nowMillis = 13_200L
            )
        )
        assertEquals(
            CallEventDecision.SendNow("010-4669-8599", sessionId = 2L, isUpgradeFromFallback = true),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "010-4669-8599",
                hasCallLogPermission = true,
                nowMillis = 13_500L
            )
        )
        // Duplicate real-number or blank broadcasts after upgrade are suppressed
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "+82 10 4669 8599",
                hasCallLogPermission = true,
                nowMillis = 13_600L
            )
        )
        assertEquals(
            CallEventDecision.Ignore,
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "",
                hasCallLogPermission = true,
                nowMillis = 13_700L
            )
        )

        // 3. New call after IDLE starts fresh session without upgrade flag
        tracker.onIdle()
        assertEquals(
            CallEventDecision.SendNow("010-4669-8599", sessionId = 3L, isUpgradeFromFallback = false),
            tracker.onRinging(
                hasNumberExtra = true,
                rawNumber = "010-4669-8599",
                hasCallLogPermission = true,
                nowMillis = 14_000L
            )
        )
    }
}
