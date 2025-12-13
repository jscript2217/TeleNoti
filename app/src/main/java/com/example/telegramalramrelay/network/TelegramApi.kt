package com.example.telegramalramrelay.network

import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramApi {
    /**
     * Sends a message using the Telegram Bot API. Returns Pair(success, message).
     */
    suspend fun sendTestMessage(botToken: String, chatId: String, text: String): Pair<Boolean, String> {
        if (botToken.isBlank() || chatId.isBlank()) return Pair(false, "botToken 또는 chatId가 비어있습니다")

        val url = try {
            URL("https://api.telegram.org/bot${URLEncoder.encode(botToken, "UTF-8")}/sendMessage")
        } catch (e: Exception) {
            return Pair(false, "유효하지 않은 봇 토큰")
        }

        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val body = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}&text=${URLEncoder.encode(text, "UTF-8")}&parse_mode=HTML"
            BufferedOutputStream(conn.outputStream).use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val json = JSONObject(resp)
            val ok = json.optBoolean("ok", false)
            if (ok) Pair(true, "테스트 메시지 전송 성공") else Pair(false, json.optString("description", "응답 오류"))
        } catch (e: Exception) {
            Pair(false, e.message ?: "네트워크 오류")
        } finally {
            conn?.disconnect()
        }
    }
}
