package com.example.telegramalramrelay.domain

import com.example.telegramalramrelay.data.ChatProfile
import com.example.telegramalramrelay.data.TeleNotiSettings

data class ResolvedRecipient(
    val chatId: String,
    val label: String
)

object ChatTargetResolver {
    private val CHAT_ID_DELIMITERS = Regex("[,;\\s\\n]+")

    fun parseChatIds(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(CHAT_ID_DELIMITERS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun resolveDefaultRecipients(settings: TeleNotiSettings): List<ResolvedRecipient> {
        val out = mutableListOf<ResolvedRecipient>()
        val seenChatIds = HashSet<String>()
        val profileByChatId = HashMap<String, ChatProfile>()
        settings.chatProfiles.forEach { profile ->
            val cid = profile.chatId.trim()
            if (cid.isNotEmpty() && !profileByChatId.containsKey(cid)) {
                profileByChatId[cid] = profile
            }
        }

        for (cid in parseChatIds(settings.chatId)) {
            if (seenChatIds.add(cid)) {
                val label = profileByChatId[cid]?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "기본"
                out.add(ResolvedRecipient(chatId = cid, label = label))
            }
        }

        for (profile in settings.chatProfiles) {
            if (profile.id !in settings.defaultProfileIds) continue
            val cid = profile.chatId.trim()
            if (cid.isEmpty() || !seenChatIds.add(cid)) continue
            val label = profile.name.trim().ifEmpty { cid }
            out.add(ResolvedRecipient(chatId = cid, label = label))
        }

        return out
    }

    fun resolveRecipientsForPackage(
        settings: TeleNotiSettings,
        packageName: String
    ): List<ResolvedRecipient> {
        val rule = settings.appCustomRules[packageName]
        if (rule == null || !rule.hasCustomRecipients) {
            return resolveDefaultRecipients(settings)
        }

        val out = mutableListOf<ResolvedRecipient>()
        val seenChatIds = HashSet<String>()
        val profileById = settings.chatProfiles.associateBy { it.id }
        val profileByChatId = HashMap<String, ChatProfile>()
        settings.chatProfiles.forEach { profile ->
            val cid = profile.chatId.trim()
            if (cid.isNotEmpty() && !profileByChatId.containsKey(cid)) {
                profileByChatId[cid] = profile
            }
        }

        for (pid in rule.targetProfileIds) {
            val profile = profileById[pid] ?: continue
            val cid = profile.chatId.trim()
            if (cid.isEmpty() || !seenChatIds.add(cid)) continue
            val label = profile.name.trim().ifEmpty { cid }
            out.add(ResolvedRecipient(chatId = cid, label = label))
        }

        for (directId in parseChatIds(rule.customChatId)) {
            if (seenChatIds.add(directId)) {
                val label = profileByChatId[directId]?.name?.trim()?.takeIf { it.isNotEmpty() } ?: directId
                out.add(ResolvedRecipient(chatId = directId, label = label))
            }
        }

        return if (out.isNotEmpty()) out else resolveDefaultRecipients(settings)
    }

    fun formatRecipientSummary(recipients: List<ResolvedRecipient>): String {
        return when {
            recipients.isEmpty() -> ""
            recipients.size == 1 -> recipients[0].label
            else -> "${recipients[0].label} 외 ${recipients.size - 1}명"
        }
    }

    fun hasAnyConfiguredRecipient(settings: TeleNotiSettings): Boolean {
        if (resolveDefaultRecipients(settings).isNotEmpty()) return true
        return settings.appCustomRules.values.any { rule ->
            rule.hasCustomRecipients && resolveRecipientsForPackage(settings, rule.packageName).isNotEmpty()
        }
    }

    fun collectAllAuthorizedChatIds(settings: TeleNotiSettings): Set<String> {
        val out = LinkedHashSet<String>()
        out.addAll(parseChatIds(settings.chatId))
        settings.chatProfiles.forEach { profile ->
            parseChatIds(profile.chatId).forEach { out.add(it) }
        }
        settings.appCustomRules.values.forEach { rule ->
            out.addAll(parseChatIds(rule.customChatId))
        }
        return out
    }
}
