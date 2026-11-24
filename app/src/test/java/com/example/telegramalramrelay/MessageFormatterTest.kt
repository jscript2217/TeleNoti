package com.example.telegramalramrelay

import com.example.telegramalramrelay.domain.MessageFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFormatterTest {

    @Test
    fun format_replacesAllPlaceholders() {
        val result = MessageFormatter.format(
            template = "[{app}/{package}] {title}: {text} @ {time}",
            appName = "카카오톡",
            title = "홍길동",
            text = "안녕하세요",
            time = "26-11-24 12:00:00",
            packageName = "com.kakao.talk"
        )
        assertEquals("[카카오톡/com.kakao.talk] 홍길동: 안녕하세요 @ 26-11-24 12:00:00", result)
    }

    @Test
    fun format_preventsDoubleSubstitution() {
        val result = MessageFormatter.format(
            template = "{title} -> {text}",
            appName = "App",
            title = "Literal {text} and {app}",
            text = "RealBody",
            time = "12:00",
            packageName = "com.example.app"
        )
        assertEquals("Literal {text} and {app} -> RealBody", result)
    }

    @Test
    fun format_usesDefaultTemplateWhenBlank() {
        val result = MessageFormatter.format(
            template = "   ",
            appName = "App",
            title = "Title",
            text = "Body",
            time = "12:00",
            packageName = "com.example.app"
        )
        assertEquals("📱 App\n📌 Title\n💬 Body\n🕐 12:00", result)
    }

    @Test
    fun escapeHtml_escapesSpecialCharactersProperly() {
        val escaped = MessageFormatter.escapeHtml("A & B <test> \"quote\" > 0")
        assertEquals("A &amp; B &lt;test&gt; \"quote\" &gt; 0", escaped)
    }

    @Test
    fun splitForTelegram_returnsSingleChunkWithinLimit() {
        val chunks = MessageFormatter.splitForTelegram("short message", maxLength = 50)
        assertEquals(listOf("short message"), chunks)
    }

    @Test
    fun splitForTelegram_splitsLongTextWithHeadersUnderMaxLength() {
        val lines = (1..25).joinToString("\n") { "Line $it with some repeated sample words here" }
        val maxLen = 120
        val chunks = MessageFormatter.splitForTelegram(lines, maxLength = maxLen)

        assertTrue(chunks.size > 1)
        chunks.forEachIndexed { idx, chunk ->
            assertTrue("Chunk $idx exceeded max length: ${chunk.length}", chunk.length <= maxLen)
            assertTrue("Chunk $idx missing part header", chunk.startsWith("(${idx + 1}/${chunks.size})\n"))
        }
        val rejoined = chunks.joinToString("\n") { it.substringAfter('\n') }
        assertTrue(rejoined.contains("Line 1 "))
        assertTrue(rejoined.contains("Line 25 "))
    }

    @Test
    fun splitForTelegram_handlesUnbrokenLongWord() {
        val longToken = "x".repeat(250)
        val chunks = MessageFormatter.splitForTelegram(longToken, maxLength = 80)
        assertTrue(chunks.size >= 4)
        chunks.forEach { assertTrue(it.length <= 80) }
    }
}
