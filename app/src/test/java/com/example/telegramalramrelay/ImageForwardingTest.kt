package com.example.telegramalramrelay

import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.domain.NotificationImageExtractor
import com.example.telegramalramrelay.network.TelegramApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageForwardingTest {

    @Test
    fun computeInSampleSize_and_scaledDimensions_workCorrectly() {
        assertEquals(1, NotificationImageExtractor.computeInSampleSize(1280, 720, 1920))
        assertEquals(2, NotificationImageExtractor.computeInSampleSize(4000, 3000, 1920))
        assertEquals(4, NotificationImageExtractor.computeInSampleSize(8000, 6000, 1920))
        assertEquals(1, NotificationImageExtractor.computeInSampleSize(0, -10, 1920))

        assertEquals(Pair(1080, 1920), NotificationImageExtractor.computeScaledDimensions(1080, 1920, 1920))
        assertEquals(Pair(1920, 1440), NotificationImageExtractor.computeScaledDimensions(4000, 3000, 1920))
        assertEquals(Pair(960, 1920), NotificationImageExtractor.computeScaledDimensions(2000, 4000, 1920))
        assertEquals(Pair(1, 1), NotificationImageExtractor.computeScaledDimensions(0, 0, 1920))
    }

    @Test
    fun buildMultipartPhotoBody_containsExpectedHeadersCaptionAndBinaryPayload() {
        val boundary = "TestBoundary123"
        val sampleJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte())
        val body = TelegramApi.buildMultipartPhotoBody(
            boundary = boundary,
            chatId = "-100123456",
            photoBytes = sampleJpeg,
            filename = "alert.jpg",
            caption = "📱 카카오톡\n사진 테스트"
        )

        val asText = String(body, Charsets.UTF_8)
        assertTrue(asText.contains("--TestBoundary123\r\n"))
        assertTrue(asText.contains("name=\"chat_id\"\r\n\r\n-100123456\r\n"))
        assertTrue(asText.contains("name=\"caption\"\r\n\r\n📱 카카오톡\n사진 테스트\r\n"))
        assertTrue(asText.contains("name=\"photo\"; filename=\"alert.jpg\"\r\n"))
        assertTrue(asText.contains("Content-Type: image/jpeg\r\n\r\n"))
        assertTrue(asText.endsWith("\r\n--TestBoundary123--\r\n"))

        // Verify exact binary sequence is preserved in body
        val needle = sampleJpeg
        var found = false
        for (i in 0..(body.size - needle.size)) {
            if (body.copyOfRange(i, i + needle.size).contentEquals(needle)) {
                found = true
                break
            }
        }
        assertTrue("Expected raw photo bytes inside multipart body", found)

        val noCaptionBody = String(
            TelegramApi.buildMultipartPhotoBody(
                boundary = boundary,
                chatId = "12345",
                photoBytes = sampleJpeg,
                caption = null
            ),
            Charsets.UTF_8
        )
        assertFalse(noCaptionBody.contains("name=\"caption\""))
    }

    @Test
    fun notificationLogItem_serializesImageFieldsAndKeepsLegacyCompatibility() {
        val item = NotificationLogItem(
            id = "img-1",
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            title = "친구",
            text = "사진",
            formattedMessage = "📱 카카오톡\n📌 친구\n💬 사진",
            chatId = "999",
            timestampMillis = 123456L,
            timeDisplay = "26-11-24 19:50:00",
            status = DeliveryStatus.PENDING_RETRY,
            hasImage = true,
            imageCachePath = "/data/cache/pending_images/img-1.jpg"
        )

        val parsed = NotificationLogItem.parseList(NotificationLogItem.toJsonArrayString(listOf(item)))
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].hasImage)
        assertEquals("/data/cache/pending_images/img-1.jpg", parsed[0].imageCachePath)

        val legacyJson = """
            [{"emoji":"🔔","app":"문자","message":"안녕","time":"12:00","package":"com.samsung.android.messaging"}]
        """.trimIndent()
        val legacyParsed = NotificationLogItem.parseList(legacyJson)
        assertEquals(1, legacyParsed.size)
        assertFalse(legacyParsed[0].hasImage)
        assertNull(legacyParsed[0].imageCachePath)
    }
}
