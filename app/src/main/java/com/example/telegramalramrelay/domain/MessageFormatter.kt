package com.example.telegramalramrelay.domain

object MessageFormatter {
    const val DEFAULT_FORMAT = "📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}"
    const val TELEGRAM_MAX_LENGTH = 4096

    private val PLACEHOLDER_REGEX = Regex("\\{(app|title|text|time|package)\\}")

    /**
     * Escapes characters that have special meaning in Telegram HTML parse_mode.
     */
    fun escapeHtml(text: String): String {
        if (text.isEmpty()) return text
        return buildString(text.length + 16) {
            for (ch in text) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * Single-pass template replacement so placeholder-like text inside notification
     * titles or bodies is never substituted a second time.
     */
    fun format(
        template: String,
        appName: String,
        title: String,
        text: String,
        time: String,
        packageName: String
    ): String {
        val activeTemplate = template.ifBlank { DEFAULT_FORMAT }
        return PLACEHOLDER_REGEX.replace(activeTemplate) { match ->
            when (match.groupValues[1]) {
                "app" -> appName
                "title" -> title
                "text" -> text
                "time" -> time
                "package" -> packageName
                else -> match.value
            }
        }
    }

    /**
     * Splits a message into Telegram-safe chunks up to [maxLength] characters.
     * Multi-part messages receive a `(i/N)\n` header while keeping every chunk <= [maxLength].
     */
    fun splitForTelegram(message: String, maxLength: Int = TELEGRAM_MAX_LENGTH): List<String> {
        require(maxLength > 0) { "maxLength must be positive" }
        if (message.length <= maxLength) return listOf(message)

        // First try splitting assuming up to 99 parts -> "(99/99)\n" = 8 chars; expand if needed.
        var headerReserve = minOf(12, (maxLength / 3).coerceAtLeast(0))
        if (maxLength <= 15) headerReserve = 0

        var rawChunks = chunkText(message, (maxLength - headerReserve).coerceAtLeast(1))
        if (rawChunks.size == 1) return rawChunks

        val maxActualHeaderLen = "(${rawChunks.size}/${rawChunks.size})\n".length
        if (headerReserve > 0 && maxActualHeaderLen > headerReserve && maxLength > maxActualHeaderLen + 1) {
            rawChunks = chunkText(message, maxLength - maxActualHeaderLen)
        }

        val total = rawChunks.size
        return rawChunks.mapIndexed { index, part ->
            val header = "(${index + 1}/$total)\n"
            if (header.length + part.length <= maxLength) {
                header + part
            } else {
                part.take(maxLength)
            }
        }
    }

    private fun chunkText(text: String, chunkLimit: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val remaining = text.length - start
            if (remaining <= chunkLimit) {
                result.add(text.substring(start))
                break
            }

            val hardEnd = start + chunkLimit
            val minBreak = start + (chunkLimit / 2)
            var splitAt = text.lastIndexOf('\n', hardEnd - 1)
            if (splitAt < minBreak) {
                splitAt = text.lastIndexOf(' ', hardEnd - 1)
            }

            if (splitAt >= minBreak) {
                result.add(text.substring(start, splitAt))
                // Skip the separator character if it was a newline or space
                start = splitAt + 1
            } else {
                result.add(text.substring(start, hardEnd))
                start = hardEnd
            }
        }
        return result.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
    }
}
