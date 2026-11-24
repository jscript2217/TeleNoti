package com.example.telegramalramrelay

import com.example.telegramalramrelay.data.KeywordFilterConfig
import com.example.telegramalramrelay.data.QuietHoursConfig
import com.example.telegramalramrelay.data.TeleNotiSettings
import com.example.telegramalramrelay.domain.FilterDecision
import com.example.telegramalramrelay.domain.NotificationCandidate
import com.example.telegramalramrelay.domain.NotificationDeduplicator
import com.example.telegramalramrelay.domain.NotificationFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {
    private val selfPkg = "com.example.telegramalramrelay"
    private val baseSettings = TeleNotiSettings(
        forwardingEnabled = true,
        selectedPackages = setOf("com.kakao.talk", selfPkg, "org.telegram.messenger"),
        ignoreOngoing = true
    )

    private fun candidate(
        pkg: String = "com.kakao.talk",
        title: String = "친구",
        text: String = "안녕하세요",
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false
    ) = NotificationCandidate(
        packageName = pkg,
        title = title,
        text = text,
        isGroupSummary = isGroupSummary,
        isOngoing = isOngoing,
        timestampMillis = 1_000_000L
    )

    @Test
    fun blocksSelfAndTelegramPackagesEvenIfInSelectedSet() {
        val selfDecision = NotificationFilter.evaluate(
            candidate(pkg = selfPkg),
            baseSettings,
            selfPkg,
            12,
            0
        )
        val tgDecision = NotificationFilter.evaluate(
            candidate(pkg = "org.telegram.messenger"),
            baseSettings,
            selfPkg,
            12,
            0
        )
        assertTrue(selfDecision is FilterDecision.Reject)
        assertTrue(tgDecision is FilterDecision.Reject)
    }

    @Test
    fun rejectsGroupSummaryAndOngoingWhenEnabled() {
        val summary = NotificationFilter.evaluate(
            candidate(isGroupSummary = true),
            baseSettings,
            selfPkg,
            12,
            0
        )
        val ongoing = NotificationFilter.evaluate(
            candidate(isOngoing = true),
            baseSettings,
            selfPkg,
            12,
            0
        )
        assertTrue(summary is FilterDecision.Reject)
        assertTrue(ongoing is FilterDecision.Reject)

        val ongoingAllowed = NotificationFilter.evaluate(
            candidate(isOngoing = true),
            baseSettings.copy(ignoreOngoing = false),
            selfPkg,
            12,
            0
        )
        assertEquals(FilterDecision.Allow, ongoingAllowed)
    }

    @Test
    fun rejectsEmptyNoiseButAllowsTitleOnlyNotifications() {
        val empty = NotificationFilter.evaluate(
            candidate(title = "com.kakao.talk", text = "   "),
            baseSettings,
            selfPkg,
            12,
            0
        )
        val titleOnly = NotificationFilter.evaluate(
            candidate(title = "부재중 전화", text = ""),
            baseSettings,
            selfPkg,
            12,
            0
        )
        assertTrue(empty is FilterDecision.Reject)
        assertEquals(FilterDecision.Allow, titleOnly)
    }

    @Test
    fun keywordFilter_includeAndExcludePrecedenceCaseInsensitive() {
        val filteredSettings = baseSettings.copy(
            keywordFilter = KeywordFilterConfig(
                enabled = true,
                includeKeywordsRaw = "입금, OTP",
                excludeKeywordsRaw = "광고, 홍보"
            )
        )

        val matchInclude = NotificationFilter.evaluate(
            candidate(text = "Your otp code is 4920"),
            filteredSettings,
            selfPkg,
            14,
            0
        )
        val excludeWins = NotificationFilter.evaluate(
            candidate(text = "입금 이벤트 광고 안내"),
            filteredSettings,
            selfPkg,
            14,
            0
        )
        val missingInclude = NotificationFilter.evaluate(
            candidate(text = "오늘 저녁 뭐 먹을래?"),
            filteredSettings,
            selfPkg,
            14,
            0
        )

        assertEquals(FilterDecision.Allow, matchInclude)
        assertTrue(excludeWins is FilterDecision.Reject)
        assertTrue(missingInclude is FilterDecision.Reject)
    }

    @Test
    fun quietHours_handlesSameDayOvernightAndBoundaries() {
        val overnight = QuietHoursConfig(
            enabled = true,
            startHour = 23,
            startMinute = 0,
            endHour = 7,
            endMinute = 0
        )
        assertTrue(overnight.isQuietAt(23, 0))
        assertTrue(overnight.isQuietAt(2, 30))
        assertTrue(overnight.isQuietAt(6, 59))
        assertFalse(overnight.isQuietAt(7, 0))
        assertFalse(overnight.isQuietAt(22, 59))

        val sameDay = QuietHoursConfig(
            enabled = true,
            startHour = 9,
            startMinute = 30,
            endHour = 18,
            endMinute = 0
        )
        assertFalse(sameDay.isQuietAt(9, 29))
        assertTrue(sameDay.isQuietAt(9, 30))
        assertTrue(sameDay.isQuietAt(12, 0))
        assertFalse(sameDay.isQuietAt(18, 0))

        assertFalse(sameDay.copy(enabled = false).isQuietAt(12, 0))
    }

    @Test
    fun deduplicator_blocksDuplicatesWithinWindowAndAllowsAfterExpiry() {
        val deduper = NotificationDeduplicator(maxEntries = 3)
        assertTrue(deduper.shouldAllow("pkg.a", "t", "b", nowMillis = 1000L, windowMillis = 5000L))
        assertFalse(deduper.shouldAllow("pkg.a", "t", "b", nowMillis = 3000L, windowMillis = 5000L))
        assertTrue(deduper.shouldAllow("pkg.a", "t", "diff", nowMillis = 3500L, windowMillis = 5000L))
        // Exactly at expiry boundary (1000 + 5000 = 6000) -> allowed again
        assertTrue(deduper.shouldAllow("pkg.a", "t", "b", nowMillis = 6000L, windowMillis = 5000L))
        // Zero window -> never blocks
        assertTrue(deduper.shouldAllow("pkg.a", "t", "b", nowMillis = 6001L, windowMillis = 0L))
    }
}
