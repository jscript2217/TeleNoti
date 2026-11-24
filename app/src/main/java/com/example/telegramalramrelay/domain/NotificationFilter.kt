package com.example.telegramalramrelay.domain

import com.example.telegramalramrelay.data.TeleNotiSettings

data class NotificationCandidate(
    val packageName: String,
    val title: String,
    val text: String,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val timestampMillis: Long
)

sealed interface FilterDecision {
    data object Allow : FilterDecision
    data class Reject(val reason: String) : FilterDecision
}

object NotificationFilter {
    val TELEGRAM_PACKAGES: Set<String> = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "tw.nekomimi.nekogram"
    )

    fun isBlockedLoopPackage(packageName: String, selfPackageName: String): Boolean {
        return packageName == selfPackageName || packageName in TELEGRAM_PACKAGES
    }

    fun evaluate(
        candidate: NotificationCandidate,
        settings: TeleNotiSettings,
        selfPackageName: String,
        currentHour: Int,
        currentMinute: Int
    ): FilterDecision {
        if (!settings.forwardingEnabled) {
            return FilterDecision.Reject("포워딩 비활성화")
        }
        if (isBlockedLoopPackage(candidate.packageName, selfPackageName)) {
            return FilterDecision.Reject("루프 방지 대상 앱")
        }
        if (candidate.packageName !in settings.selectedPackages) {
            return FilterDecision.Reject("선택되지 않은 앱")
        }
        if (candidate.isGroupSummary) {
            return FilterDecision.Reject("그룹 요약 알림")
        }
        if (settings.ignoreOngoing && candidate.isOngoing) {
            return FilterDecision.Reject("진행 중 알림 제외")
        }

        val hasTitle = candidate.title.isNotBlank() && candidate.title != candidate.packageName
        val hasText = candidate.text.isNotBlank()
        if (!hasText && !hasTitle) {
            return FilterDecision.Reject("내용 없는 알림")
        }

        if (settings.quietHours.isQuietAt(currentHour, currentMinute)) {
            return FilterDecision.Reject("방해금지 시간대")
        }

        if (settings.keywordFilter.enabled) {
            val combined = "${candidate.title}\n${candidate.text}"
            val excludeList = settings.keywordFilter.excludeKeywords
            if (excludeList.any { combined.contains(it, ignoreCase = true) }) {
                return FilterDecision.Reject("제외 키워드 일치")
            }

            val includeList = settings.keywordFilter.includeKeywords
            if (includeList.isNotEmpty() && includeList.none { combined.contains(it, ignoreCase = true) }) {
                return FilterDecision.Reject("포함 키워드 불일치")
            }
        }

        return FilterDecision.Allow
    }
}

class NotificationDeduplicator(private val maxEntries: Int = 100) {
    private data class Key(val packageName: String, val title: String, val text: String)

    private val lock = Any()
    private val seenAt = LinkedHashMap<Key, Long>(16, 0.75f, true)

    fun shouldAllow(
        packageName: String,
        title: String,
        text: String,
        nowMillis: Long,
        windowMillis: Long
    ): Boolean {
        if (windowMillis <= 0L) return true
        val key = Key(packageName, title, text)
        synchronized(lock) {
            val iterator = seenAt.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val age = nowMillis - entry.value
                if (age >= windowMillis) {
                    iterator.remove()
                }
            }

            val lastSeen = seenAt[key]
            if (lastSeen != null) {
                val elapsed = nowMillis - lastSeen
                if (elapsed in 0 until windowMillis) {
                    return false
                }
            }

            if (seenAt.size >= maxEntries && !seenAt.containsKey(key)) {
                val eldestKey = seenAt.entries.firstOrNull()?.key
                if (eldestKey != null) {
                    seenAt.remove(eldestKey)
                }
            }
            seenAt[key] = nowMillis
            return true
        }
    }

    fun clear() {
        synchronized(lock) {
            seenAt.clear()
        }
    }
}
