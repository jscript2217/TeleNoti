package com.example.telegramalramrelay

import com.example.telegramalramrelay.domain.SmsMmsEnricher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMmsEnricherTest {

    @Test
    fun detectsTruncatedAndMmsPlaceholderTexts() {
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder(""))
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("   "))
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("앞부분만 표시되고 잘린 문자..."))
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("앞부분만 표시…"))
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("멀티미디어 메시지"))
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("메시지 다운로드 중"))
        assertTrue(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("Multimedia message"))
        assertFalse(SmsMmsEnricher.isLikelyTruncatedOrMmsPlaceholder("[Web발신] 인증번호는 482910입니다."))
    }

    @Test
    fun decodesMmsSubjectSafely() {
        assertNull(SmsMmsEnricher.decodeMmsSubject(null, 106))
        assertNull(SmsMmsEnricher.decodeMmsSubject("제목 없음", 106))
        assertNull(SmsMmsEnricher.decodeMmsSubject("No subject", 106))

        // Simulate ISO-8859-1 mangled UTF-8 bytes as often returned by Android MMS Provider
        val originalKorean = "긴 문자 제목 테스트"
        val mangled = String(originalKorean.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        assertEquals(originalKorean, SmsMmsEnricher.decodeMmsSubject(mangled, 106))

        // Already-decoded Korean stays intact
        assertEquals(originalKorean, SmsMmsEnricher.decodeMmsSubject(originalKorean, 106))
    }

    @Test
    fun selectEnrichedBody_prefersLongerOrPlaceholderReplacement() {
        val enrichedLongLms = SmsMmsEnricher.selectEnrichedBody(
            notificationText = "긴 문자가 잘렸습니다...",
            dbSubject = "배송 안내",
            dbBody = "고객님의 소중한 상품이 오늘 오후 도착 예정입니다. 문 앞 보관 요청 시 회신 주세요.",
            attachments = emptyList()
        )
        assertTrue(enrichedLongLms.contains("[제목] 배송 안내"))
        assertTrue(enrichedLongLms.contains("고객님의 소중한 상품이 오늘 오후 도착 예정입니다."))

        val enrichedMmsAttachment = SmsMmsEnricher.selectEnrichedBody(
            notificationText = "멀티미디어 메시지",
            dbSubject = null,
            dbBody = null,
            attachments = listOf("[이미지 첨부]", "[이미지 첨부]")
        )
        assertEquals("[이미지 첨부]", enrichedMmsAttachment)

        val keepsCompleteNotification = SmsMmsEnricher.selectEnrichedBody(
            notificationText = "[Web발신] 완전히 수신된 알림 본문입니다. 매우 길고 빠진 내용이 없습니다.",
            dbSubject = null,
            dbBody = "짧은 이전 문자",
            attachments = emptyList()
        )
        assertEquals("[Web발신] 완전히 수신된 알림 본문입니다. 매우 길고 빠진 내용이 없습니다.", keepsCompleteNotification)
    }
}
