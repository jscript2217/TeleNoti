package com.example.telegramalramrelay.network

import com.example.telegramalramrelay.data.DetectedChat
import com.example.telegramalramrelay.domain.MessageFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class TelegramResult(
    val success: Boolean,
    val message: String,
    val httpCode: Int = 0,
    val retryable: Boolean = false,
    val retryAfterSeconds: Int? = null,
    val messageId: Long? = null,
    val messageIds: List<Long> = emptyList()
)

data class IncomingTelegramReply(
    val updateId: Long,
    val chatId: String,
    val messageId: Long,
    val replyToMessageId: Long?,
    val text: String,
    val chatType: String
)

object TelegramApi {
    const val TELEGRAM_MAX_CAPTION_LENGTH = 1024
    private val BOT_TOKEN_REGEX = Regex("^\\d+:[A-Za-z0-9_-]{10,}$")

    fun isValidBotToken(token: String): Boolean {
        return BOT_TOKEN_REGEX.matches(token.trim())
    }

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        parseMode: String? = null,
        replyToMessageId: Long? = null
    ): TelegramResult = withContext(Dispatchers.IO) {
        val validationError = validateCredentials(botToken, chatId)
        if (validationError != null) return@withContext validationError

        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val url = try {
            URL("https://api.telegram.org/bot$cleanToken/sendMessage")
        } catch (_: Exception) {
            return@withContext TelegramResult(
                success = false,
                message = "봇 토큰 URL 생성 실패",
                retryable = false
            )
        }

        val chunks = MessageFormatter.splitForTelegram(text)
        val sentIds = mutableListOf<Long>()
        for ((index, chunk) in chunks.withIndex()) {
            val payload = JSONObject().apply {
                put("chat_id", cleanChatId)
                put("text", chunk)
                if (!parseMode.isNullOrBlank()) {
                    put("parse_mode", parseMode)
                }
                if (index == 0 && replyToMessageId != null && replyToMessageId > 0L) {
                    put("reply_to_message_id", replyToMessageId)
                    put("allow_sending_without_reply", true)
                }
            }.toString()

            val chunkResult = executePost(
                url = url,
                contentType = "application/json; charset=UTF-8",
                bodyBytes = payload.toByteArray(Charsets.UTF_8)
            )
            if (!chunkResult.success) {
                return@withContext chunkResult
            }
            sentIds.addAll(chunkResult.messageIds)
        }

        TelegramResult(
            success = true,
            message = "메시지 전송 성공",
            httpCode = 200,
            retryable = false,
            messageId = sentIds.firstOrNull(),
            messageIds = sentIds
        )
    }

    suspend fun editMessageText(
        botToken: String,
        chatId: String,
        messageId: Long,
        text: String,
        parseMode: String? = null
    ): TelegramResult = withContext(Dispatchers.IO) {
        val validationError = validateCredentials(botToken, chatId)
        if (validationError != null) return@withContext validationError
        if (messageId <= 0L) {
            return@withContext TelegramResult(
                success = false,
                message = "수정할 Telegram message_id가 유효하지 않습니다.",
                retryable = false
            )
        }

        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val url = try {
            URL("https://api.telegram.org/bot$cleanToken/editMessageText")
        } catch (_: Exception) {
            return@withContext TelegramResult(
                success = false,
                message = "봇 토큰 URL 생성 실패",
                retryable = false
            )
        }

        val payload = JSONObject().apply {
            put("chat_id", cleanChatId)
            put("message_id", messageId)
            put("text", text.take(4096))
            if (!parseMode.isNullOrBlank()) {
                put("parse_mode", parseMode)
            }
        }.toString()

        executePost(
            url = url,
            contentType = "application/json; charset=UTF-8",
            bodyBytes = payload.toByteArray(Charsets.UTF_8)
        ) { code, resp ->
            parseEditMessageResponse(code, resp, fallbackMessageId = messageId)
        }
    }

    suspend fun sendPhoto(
        botToken: String,
        chatId: String,
        photoBytes: ByteArray,
        caption: String? = null,
        filename: String = "image.jpg"
    ): TelegramResult = withContext(Dispatchers.IO) {
        val validationError = validateCredentials(botToken, chatId)
        if (validationError != null) return@withContext validationError
        if (photoBytes.isEmpty()) {
            return@withContext TelegramResult(
                success = false,
                message = "전송할 이미지 데이터가 비어 있습니다.",
                retryable = false
            )
        }

        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val url = try {
            URL("https://api.telegram.org/bot$cleanToken/sendPhoto")
        } catch (_: Exception) {
            return@withContext TelegramResult(
                success = false,
                message = "봇 토큰 URL 생성 실패",
                retryable = false
            )
        }

        val boundary = "----TeleNotiBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val body = buildMultipartPhotoBody(
            boundary = boundary,
            chatId = cleanChatId,
            photoBytes = photoBytes,
            filename = filename,
            caption = caption
        )

        executePost(
            url = url,
            contentType = "multipart/form-data; boundary=$boundary",
            bodyBytes = body,
            connectTimeoutMs = 20000,
            readTimeoutMs = 30000
        )
    }

    suspend fun sendPhotoWithText(
        botToken: String,
        chatId: String,
        photoBytes: ByteArray,
        text: String,
        filename: String = "image.jpg"
    ): TelegramResult {
        val result = if (text.length <= TELEGRAM_MAX_CAPTION_LENGTH) {
            sendPhoto(
                botToken = botToken,
                chatId = chatId,
                photoBytes = photoBytes,
                caption = text.takeIf { it.isNotBlank() },
                filename = filename
            )
        } else {
            val photoResult = sendPhoto(
                botToken = botToken,
                chatId = chatId,
                photoBytes = photoBytes,
                caption = null,
                filename = filename
            )
            if (photoResult.success) {
                val msgResult = sendMessage(botToken = botToken, chatId = chatId, text = text)
                if (msgResult.success) {
                    val combinedIds = photoResult.messageIds + msgResult.messageIds
                    msgResult.copy(
                        messageId = combinedIds.firstOrNull(),
                        messageIds = combinedIds
                    )
                } else {
                    msgResult
                }
            } else {
                photoResult
            }
        }

        // If Telegram rejected the image itself with HTTP 400, preserve delivery by falling back to text
        if (!result.success && !result.retryable && result.httpCode == 400 && text.isNotBlank()) {
            return sendMessage(botToken = botToken, chatId = chatId, text = text)
        }
        return result
    }

    fun buildMultipartPhotoBody(
        boundary: String,
        chatId: String,
        photoBytes: ByteArray,
        filename: String = "image.jpg",
        caption: String? = null
    ): ByteArray {
        val safeFilename = filename.replace("\"", "").ifBlank { "image.jpg" }
        val out = ByteArrayOutputStream(photoBytes.size + 512)

        fun writeUtf8(value: String) {
            out.write(value.toByteArray(Charsets.UTF_8))
        }

        writeUtf8("--$boundary\r\n")
        writeUtf8("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
        writeUtf8("$chatId\r\n")

        if (!caption.isNullOrBlank()) {
            writeUtf8("--$boundary\r\n")
            writeUtf8("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
            writeUtf8("$caption\r\n")
        }

        writeUtf8("--$boundary\r\n")
        writeUtf8("Content-Disposition: form-data; name=\"photo\"; filename=\"$safeFilename\"\r\n")
        writeUtf8("Content-Type: image/jpeg\r\n\r\n")
        out.write(photoBytes)
        writeUtf8("\r\n--$boundary--\r\n")

        return out.toByteArray()
    }

    suspend fun sendTestMessage(botToken: String, chatId: String, text: String): Pair<Boolean, String> {
        val result = sendMessage(botToken, chatId, text, parseMode = null)
        return if (result.success) {
            Pair(true, "테스트 메시지 전송 성공")
        } else {
            Pair(false, result.message)
        }
    }

    suspend fun fetchLatestChats(botToken: String): Pair<List<DetectedChat>, String?> =
        withContext(Dispatchers.IO) {
            val cleanToken = botToken.trim()
            if (!isValidBotToken(cleanToken)) {
                return@withContext Pair(emptyList(), "올바른 봇 토큰 형식을 먼저 입력하세요.")
            }

            val url = try {
                URL("https://api.telegram.org/bot$cleanToken/getUpdates?limit=30")
            } catch (_: Exception) {
                return@withContext Pair(emptyList(), "유효하지 않은 봇 토큰입니다.")
            }

            var conn: HttpURLConnection? = null
            try {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                val code = conn.responseCode
                val body = readResponseBody(conn, code)
                val json = try {
                    if (body.isNotBlank()) JSONObject(body) else null
                } catch (_: Exception) {
                    null
                }
                val ok = json?.optBoolean("ok", false) == true
                if (code in 200..299 && ok) {
                    val chats = parseChatsFromUpdatesJson(body)
                    return@withContext Pair(chats, null)
                }

                val desc = json?.optString("description").orEmpty()
                val errMsg = when (code) {
                    401, 404 -> "봇 토큰이 올바르지 않습니다 ($code)"
                    409 -> "다른 Webhook 또는 폴링 충돌이 감지되었습니다 (409)"
                    else -> desc.ifBlank { "채팅 목록 조회 실패 ($code)" }
                }
                Pair(emptyList(), errMsg)
            } catch (e: IOException) {
                Pair(emptyList(), "네트워크 오류: ${e.message ?: "연결 실패"}")
            } catch (e: Exception) {
                Pair(emptyList(), e.message ?: "채팅 목록 조회 중 오류 발생")
            } finally {
                conn?.disconnect()
            }
        }

    suspend fun fetchLatestUpdateId(botToken: String): Pair<Long?, TelegramResult> =
        withContext(Dispatchers.IO) {
            val cleanToken = botToken.trim()
            if (!isValidBotToken(cleanToken)) {
                return@withContext Pair(
                    null,
                    TelegramResult(false, "유효하지 않은 봇 토큰 형식입니다.", retryable = false)
                )
            }

            val url = try {
                URL("https://api.telegram.org/bot$cleanToken/getUpdates?offset=-1&timeout=0")
            } catch (_: Exception) {
                return@withContext Pair(
                    null,
                    TelegramResult(false, "봇 토큰 URL 생성 실패", retryable = false)
                )
            }

            var conn: HttpURLConnection? = null
            val handle = coroutineContext[Job]?.invokeOnCompletion { conn?.disconnect() }
            try {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                val code = conn.responseCode
                val body = readResponseBody(conn, code)
                val parsedRes = parseTelegramResponse(code, body)
                if (!parsedRes.success) {
                    return@withContext Pair(null, parsedRes)
                }
                val maxId = parseMaxUpdateId(body)
                Pair(maxId, parsedRes)
            } catch (e: IOException) {
                Pair(
                    null,
                    TelegramResult(false, "네트워크 오류: ${e.message ?: "연결 실패"}", retryable = true)
                )
            } catch (e: Exception) {
                Pair(
                    null,
                    TelegramResult(false, e.message ?: "업데이트 조회 실패", retryable = false)
                )
            } finally {
                handle?.dispose()
                conn?.disconnect()
            }
        }

    suspend fun pollUpdates(
        botToken: String,
        offset: Long?,
        timeoutSeconds: Int = 25
    ): Pair<List<IncomingTelegramReply>, TelegramResult> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        if (!isValidBotToken(cleanToken)) {
            return@withContext Pair(
                emptyList(),
                TelegramResult(false, "유효하지 않은 봇 토큰 형식입니다.", retryable = false)
            )
        }

        val urlString = buildString {
            append("https://api.telegram.org/bot")
            append(cleanToken)
            append("/getUpdates?timeout=")
            append(timeoutSeconds.coerceIn(0, 50))
            append("&allowed_updates=%5B%22message%22%5D")
            if (offset != null) {
                append("&offset=")
                append(offset)
            }
        }
        val url = try {
            URL(urlString)
        } catch (_: Exception) {
            return@withContext Pair(
                emptyList(),
                TelegramResult(false, "봇 토큰 URL 생성 실패", retryable = false)
            )
        }

        var conn: HttpURLConnection? = null
        val handle = coroutineContext[Job]?.invokeOnCompletion { conn?.disconnect() }
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = (timeoutSeconds.coerceIn(0, 50) + 15) * 1000
            }
            val code = conn.responseCode
            val body = readResponseBody(conn, code)
            val parsedRes = parseTelegramResponse(code, body)
            if (!parsedRes.success) {
                return@withContext Pair(emptyList(), parsedRes)
            }
            Pair(parseIncomingUpdates(body), parsedRes)
        } catch (e: IOException) {
            Pair(
                emptyList(),
                TelegramResult(false, "네트워크 오류: ${e.message ?: "연결 실패"}", retryable = true)
            )
        } catch (e: Exception) {
            Pair(
                emptyList(),
                TelegramResult(false, e.message ?: "업데이트 폴링 오류", retryable = false)
            )
        } finally {
            handle?.dispose()
            conn?.disconnect()
        }
    }

    fun parseIncomingUpdates(jsonString: String): List<IncomingTelegramReply> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("ok", false)) return emptyList()
            val results = root.optJSONArray("result") ?: return emptyList()

            val out = mutableListOf<IncomingTelegramReply>()
            for (i in 0 until results.length()) {
                val update = results.optJSONObject(i) ?: continue
                val updateId = update.optLong("update_id", -1L)
                if (updateId < 0L) continue

                val msg = update.optJSONObject("message") ?: continue
                val chat = msg.optJSONObject("chat") ?: continue
                val chatId = chat.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val chatType = chat.optString("type", "private")
                val messageId = msg.optLong("message_id", -1L)
                if (messageId <= 0L) continue

                val text = msg.optString("text", "").trim()
                if (text.isEmpty()) continue

                val replyToId = msg.optJSONObject("reply_to_message")
                    ?.optLong("message_id", -1L)
                    ?.takeIf { it > 0L }

                out.add(
                    IncomingTelegramReply(
                        updateId = updateId,
                        chatId = chatId,
                        messageId = messageId,
                        replyToMessageId = replyToId,
                        text = text,
                        chatType = chatType
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseMaxUpdateId(jsonString: String): Long? {
        if (jsonString.isBlank()) return null
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("ok", false)) return null
            val results = root.optJSONArray("result") ?: return null
            var maxId: Long? = null
            for (i in 0 until results.length()) {
                val id = results.optJSONObject(i)?.optLong("update_id", -1L) ?: -1L
                if (id >= 0L && (maxId == null || id > maxId)) {
                    maxId = id
                }
            }
            maxId
        } catch (_: Exception) {
            null
        }
    }

    fun parseChatsFromUpdatesJson(jsonString: String): List<DetectedChat> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val root = JSONObject(jsonString)
            if (!root.optBoolean("ok", false)) return emptyList()
            val results = root.optJSONArray("result") ?: return emptyList()

            val chatsById = LinkedHashMap<String, DetectedChat>()
            for (i in results.length() - 1 downTo 0) {
                val update = results.optJSONObject(i) ?: continue
                val chat = extractChatObject(update) ?: continue
                val chatId = chat.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: continue
                if (chatsById.containsKey(chatId)) continue

                val type = chat.optString("type", "private")
                val username = chat.optString("username", "")
                val explicitTitle = chat.optString("title", "").trim()
                val fullName = listOf(
                    chat.optString("first_name", "").trim(),
                    chat.optString("last_name", "").trim()
                ).filter { it.isNotEmpty() }.joinToString(" ")

                val title = when {
                    explicitTitle.isNotEmpty() -> explicitTitle
                    fullName.isNotEmpty() -> fullName
                    username.isNotEmpty() -> "@$username"
                    else -> chatId
                }

                chatsById[chatId] = DetectedChat(
                    chatId = chatId,
                    title = title,
                    type = type,
                    username = username
                )
            }
            chatsById.values.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun validateCredentials(botToken: String, chatId: String): TelegramResult? {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        if (cleanToken.isBlank() || cleanChatId.isBlank()) {
            return TelegramResult(
                success = false,
                message = "봇 토큰 또는 Chat ID가 비어 있습니다.",
                retryable = false
            )
        }
        if (!isValidBotToken(cleanToken)) {
            return TelegramResult(
                success = false,
                message = "유효하지 않은 봇 토큰 형식입니다.",
                retryable = false
            )
        }
        return null
    }

    private fun extractChatObject(update: JSONObject): JSONObject? {
        val messageKeys = listOf("message", "edited_message", "channel_post", "edited_channel_post", "my_chat_member")
        for (key in messageKeys) {
            val chat = update.optJSONObject(key)?.optJSONObject("chat")
            if (chat != null) return chat
        }
        return update.optJSONObject("callback_query")
            ?.optJSONObject("message")
            ?.optJSONObject("chat")
    }

    private fun executePost(
        url: URL,
        contentType: String,
        bodyBytes: ByteArray,
        connectTimeoutMs: Int = 15000,
        readTimeoutMs: Int = 15000,
        responseParser: (Int, String) -> TelegramResult = ::parseTelegramResponse
    ): TelegramResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
            }

            BufferedOutputStream(conn.outputStream).use {
                it.write(bodyBytes)
                it.flush()
            }

            val code = conn.responseCode
            val resp = readResponseBody(conn, code)
            responseParser(code, resp)
        } catch (e: IOException) {
            TelegramResult(
                success = false,
                message = "네트워크 오류: ${e.message ?: "연결 실패"}",
                httpCode = 0,
                retryable = true
            )
        } catch (e: Exception) {
            TelegramResult(
                success = false,
                message = e.message ?: "전송 중 알 수 없는 오류",
                httpCode = 0,
                retryable = false
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTelegramResponse(code: Int, resp: String): TelegramResult {
        val json = try {
            if (resp.isNotBlank()) JSONObject(resp) else null
        } catch (_: Exception) {
            null
        }
        val ok = json?.optBoolean("ok", false) == true

        if (code in 200..299 && ok) {
            val msgId = json?.optJSONObject("result")
                ?.optLong("message_id", -1L)
                ?.takeIf { it > 0L }
            return TelegramResult(
                success = true,
                message = "메시지 전송 성공",
                httpCode = code,
                retryable = false,
                messageId = msgId,
                messageIds = listOfNotNull(msgId)
            )
        }

        val desc = json?.optString("description").orEmpty()
        val retryAfter = json?.optJSONObject("parameters")
            ?.optInt("retry_after", -1)
            ?.takeIf { it > 0 }
        val retryable = code == 429 || code == 408 || code >= 500
        val friendlyMessage = when (code) {
            401 -> "유효하지 않은 봇 토큰입니다 (401)"
            403 -> "봇이 채팅방에 접근할 수 없습니다. 봇에게 /start를 먼저 보내세요 (403)"
            404 -> "봇 API 경로를 찾을 수 없습니다. 봇 토큰을 확인하세요 (404)"
            400 -> "요청 오류: ${desc.ifBlank { "Chat ID 또는 메시지 내용을 확인하세요" }} (400)"
            429 -> "전송 한도 초과 (${retryAfter ?: 5}초 후 재시도) (429)"
            in 500..599 -> "텔레그램 서버 일시 오류 ($code)"
            else -> desc.ifBlank { "응답 오류 ($code)" }
        }

        return TelegramResult(
            success = false,
            message = friendlyMessage,
            httpCode = code,
            retryable = retryable,
            retryAfterSeconds = retryAfter
        )
    }

    internal fun parseEditMessageResponse(
        code: Int,
        resp: String,
        fallbackMessageId: Long
    ): TelegramResult {
        val json = try {
            if (resp.isNotBlank()) JSONObject(resp) else null
        } catch (_: Exception) {
            null
        }
        val desc = json?.optString("description").orEmpty()
        if (code == 400 && desc.contains("message is not modified", ignoreCase = true)) {
            val resolvedId = fallbackMessageId.takeIf { it > 0L }
            return TelegramResult(
                success = true,
                message = "메시지 수정 성공",
                httpCode = code,
                retryable = false,
                messageId = resolvedId,
                messageIds = listOfNotNull(resolvedId)
            )
        }

        val base = parseTelegramResponse(code, resp)
        if (!base.success) return base
        val resolvedId = base.messageId ?: fallbackMessageId.takeIf { it > 0L }
        return base.copy(
            message = "메시지 수정 성공",
            messageId = resolvedId,
            messageIds = if (base.messageIds.isNotEmpty()) base.messageIds else listOfNotNull(resolvedId)
        )
    }

    private fun readResponseBody(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
