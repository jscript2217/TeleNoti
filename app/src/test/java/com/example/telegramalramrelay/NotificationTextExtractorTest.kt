package com.example.telegramalramrelay

import com.example.telegramalramrelay.domain.NotificationTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextExtractorTest {

    @Test
    fun messagingStyleTakesOnlyLatestMessageEvenWhenStackedAndKeepsGroupSender() {
        val messages = (1..7).map { "User$it: msg $it" }
        val extracted = NotificationTextExtractor.extractBestContent(
            packageName = "com.kakao.talk",
            title = "기본 제목",
            bigTitle = null,
            conversationTitle = "단톡방",
            text = "단축 텍스트",
            bigText = messages.joinToString("\n"),
            textLines = listOf("줄1", "줄2"),
            subText = "서브",
            messagingMessages = messages
        )

        assertEquals("단톡방", extracted.title)
        assertEquals("User7: msg 7", extracted.text)
    }

    @Test
    fun stripsRedundantSenderPrefixForDirectMessagesAndPhoneNumbers() {
        // Direct SMS with stacked unread history and identical phone number in title & sender prefix
        val stackedSms = NotificationTextExtractor.extractBestContent(
            packageName = "com.samsung.android.messaging",
            title = "010-9262-6749",
            bigTitle = null,
            conversationTitle = null,
            text = null,
            bigText = null,
            textLines = null,
            subText = null,
            messagingMessages = listOf(
                "010-9262-6749: ㅇ ㅇ\n123",
                "010-9262-6749: ㅇㅇㅇㅇㄴㄴㄷ"
            )
        )
        assertEquals("010-9262-6749", stackedSms.title)
        assertEquals("ㅇㅇㅇㅇㄴㄴㄷ", stackedSms.text)

        // +82 normalized match and multi-line body preservation
        val intlSms = NotificationTextExtractor.extractBestContent(
            packageName = "com.samsung.android.messaging",
            title = "010-9262-6749",
            bigTitle = null,
            conversationTitle = null,
            text = null,
            bigText = null,
            textLines = null,
            subText = null,
            messagingMessages = listOf("+82 10-9262-6749: 첫째 줄\n둘째 줄")
        )
        assertEquals("첫째 줄\n둘째 줄", intlSms.text)

        // Exact display-name match in 1:1 chat strips prefix, while time colons in body are untouched
        val oneToOneChat = NotificationTextExtractor.extractBestContent(
            packageName = "com.kakao.talk",
            title = "홍길동",
            bigTitle = null,
            conversationTitle = null,
            text = null,
            bigText = null,
            textLines = null,
            subText = null,
            messagingMessages = listOf("홍길동: 12:30에 만나요")
        )
        assertEquals("12:30에 만나요", oneToOneChat.text)
    }

    @Test
    fun textPriorityOrder_bigTextOverLatestLineOverTextOverSubText() {
        val bigTextResult = NotificationTextExtractor.extractBestContent(
            packageName = "pkg",
            title = "제목",
            bigTitle = "큰 제목",
            conversationTitle = null,
            text = "기본",
            bigText = "긴 본문",
            textLines = listOf("줄1"),
            subText = "서브",
            messagingMessages = null
        )
        assertEquals("큰 제목", bigTextResult.title)
        assertEquals("긴 본문", bigTextResult.text)

        val linesResult = NotificationTextExtractor.extractBestContent(
            packageName = "pkg",
            title = "제목",
            bigTitle = null,
            conversationTitle = null,
            text = "기본",
            bigText = null,
            textLines = listOf("이전 알림 줄", "가장 최신 줄"),
            subText = "서브",
            messagingMessages = emptyList()
        )
        assertEquals("제목", linesResult.title)
        assertEquals("가장 최신 줄", linesResult.text)

        val subTextResult = NotificationTextExtractor.extractBestContent(
            packageName = "pkg",
            title = null,
            bigTitle = null,
            conversationTitle = null,
            text = null,
            bigText = null,
            textLines = null,
            subText = "보조 텍스트",
            messagingMessages = null
        )
        assertEquals("pkg", subTextResult.title)
        assertEquals("보조 텍스트", subTextResult.text)
    }

    @Test
    fun prefersLongerBigTextOverShorterMessagingSummaryAndFallsBackToTicker() {
        val longLms = NotificationTextExtractor.extractBestContent(
            packageName = "com.samsung.android.messaging",
            title = "010-1234-5678",
            bigTitle = null,
            conversationTitle = null,
            text = "요약",
            bigText = "안녕하세요 고객님, 이번 달 이용 명세서 전체 장문 안내 내용입니다. 확인해 주세요.",
            textLines = null,
            subText = null,
            messagingMessages = listOf("요약 메시지")
        )
        assertEquals(
            "안녕하세요 고객님, 이번 달 이용 명세서 전체 장문 안내 내용입니다. 확인해 주세요.",
            longLms.text
        )

        val tickerFallback = NotificationTextExtractor.extractBestContent(
            packageName = "com.example.app",
            title = "제목",
            bigTitle = null,
            conversationTitle = null,
            text = null,
            bigText = null,
            textLines = null,
            subText = null,
            messagingMessages = null,
            tickerText = "티커 알림 본문"
        )
        assertEquals("티커 알림 본문", tickerFallback.text)
    }
}
