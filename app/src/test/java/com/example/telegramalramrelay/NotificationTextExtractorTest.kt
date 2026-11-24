package com.example.telegramalramrelay

import com.example.telegramalramrelay.domain.NotificationTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextExtractorTest {

    @Test
    fun messagingStyleTakesHighestPriorityAndKeepsLatestFive() {
        val messages = (1..7).map { "User$it: msg $it" }
        val extracted = NotificationTextExtractor.extractBestContent(
            packageName = "com.kakao.talk",
            title = "기본 제목",
            bigTitle = null,
            conversationTitle = "단톡방",
            text = "단축 텍스트",
            bigText = "긴 텍스트",
            textLines = listOf("줄1", "줄2"),
            subText = "서브",
            messagingMessages = messages
        )

        assertEquals("단톡방", extracted.title)
        assertEquals(
            "User3: msg 3\nUser4: msg 4\nUser5: msg 5\nUser6: msg 6\nUser7: msg 7",
            extracted.text
        )
    }

    @Test
    fun textPriorityOrder_bigTextOverTextLinesOverTextOverSubText() {
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
            textLines = listOf("첫째 줄", "둘째 줄"),
            subText = "서브",
            messagingMessages = emptyList()
        )
        assertEquals("제목", linesResult.title)
        assertEquals("첫째 줄\n둘째 줄", linesResult.text)

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
