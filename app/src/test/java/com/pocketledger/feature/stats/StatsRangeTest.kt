package com.pocketledger.feature.stats

import com.pocketledger.data.entity.TermEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stats window decides which rows every figure on the screen is computed from,
 * so an off-by-one here would quietly misreport a whole period.
 */
class StatsRangeTest {

    private val today = LocalDate.of(2026, 9, 16)

    private fun selection(
        mode: StatsRangeMode,
        monthKey: String = "2026-09",
        termId: Long? = null,
        terms: List<TermEntity> = emptyList(),
        pieLevel: PieLevel = PieLevel.SMALL,
        filterCategoryIds: Set<Long> = emptySet(),
    ) = StatsSelection(mode, monthKey, termId, terms, pieLevel, filterCategoryIds)

    private fun term(id: Long, name: String, start: String, end: String) = TermEntity(
        id = id,
        name = name,
        startDateKey = start,
        endDateKey = end,
    )

    @Test
    fun `month mode covers the whole selected month`() {
        val (start, end, label) = resolveRange(selection(StatsRangeMode.MONTH), today)
        assertEquals("2026-09-01", start)
        assertEquals("2026-09-30", end)
        assertEquals("2026年9月", label)
    }

    @Test
    fun `last 30 days includes today and reaches back 29 days`() {
        val (start, end, label) = resolveRange(selection(StatsRangeMode.LAST_30_DAYS), today)
        assertEquals("2026-08-18", start)
        assertEquals("2026-09-16", end)
        assertEquals("近 30 天", label)
    }

    @Test
    fun `year mode covers january first to december thirty-first`() {
        val (start, end, label) = resolveRange(selection(StatsRangeMode.YEAR), today)
        assertEquals("2026-01-01", start)
        assertEquals("2026-12-31", end)
        assertEquals("2026 年", label)
    }

    @Test
    fun `term mode uses the selected term's own dates`() {
        val terms = listOf(
            term(1, "2026 秋季学期", "2026-09-01", "2027-01-15"),
            term(2, "2026 春季学期", "2026-02-20", "2026-07-05"),
        )
        val (start, end, label) = resolveRange(
            selection(StatsRangeMode.TERM, termId = 2, terms = terms),
            today,
        )
        assertEquals("2026-02-20", start)
        assertEquals("2026-07-05", end)
        assertEquals("2026 春季学期", label)
    }

    @Test
    fun `term mode falls back to the first term when none is selected`() {
        val terms = listOf(term(1, "2026 秋季学期", "2026-09-01", "2027-01-15"))
        val (start, end, _) = resolveRange(
            selection(StatsRangeMode.TERM, termId = null, terms = terms),
            today,
        )
        assertEquals("2026-09-01", start)
        assertEquals("2027-01-15", end)
    }

    /** Selecting 学期 before any term exists must not blank the screen. */
    @Test
    fun `term mode with no terms falls back to the month window`() {
        val (start, end, label) = resolveRange(
            selection(StatsRangeMode.TERM, terms = emptyList()),
            today,
        )
        assertEquals("2026-09-01", start)
        assertEquals("2026-09-30", end)
        assertEquals("2026年9月", label)
    }

    @Test
    fun `every mode produces an ordered, non-empty range`() {
        val allTerms = listOf(term(1, "秋季", "2026-09-01", "2027-01-15"))
        for (mode in StatsRangeMode.entries) {
            val (start, end, label) = resolveRange(selection(mode, terms = allTerms), today)
            assertTrue("$mode produced an inverted range", start <= end)
            assertTrue("$mode produced a blank label", label.isNotBlank())
        }
    }
}

/**
 * A donut with twenty hairlines communicates nothing, so everything past the sixth
 * category is folded into one 「其他」 wedge -- and the wedges must still add up to
 * the period total.
 */
class DonutCollapseTest {

    private fun rank(id: Long, cents: Long) = CategoryRank(
        id = id,
        name = "c$id",
        colorArgb = 0xFF000000.toInt(),
        totalCents = cents,
        share = 0f,
    )

    @Test
    fun `a short ranking is returned untouched`() {
        val ranking = listOf(rank(1, 100), rank(2, 50))
        assertEquals(ranking, collapseTail(ranking, 150))
    }

    @Test
    fun `a long ranking folds the tail into one slice`() {
        val ranking = (1..10).map { rank(it.toLong(), (11 - it) * 100L) }
        val collapsed = collapseTail(ranking, ranking.sumOf { it.totalCents })

        assertEquals(6, collapsed.size)
        assertEquals("其他", collapsed.last().name)
        assertEquals(-1L, collapsed.last().id)
    }

    @Test
    fun `collapsed slices still sum to the period total`() {
        val ranking = (1..10).map { rank(it.toLong(), (11 - it) * 100L) }
        val total = ranking.sumOf { it.totalCents }
        val collapsed = collapseTail(ranking, total)

        assertEquals(total, collapsed.sumOf { it.totalCents })
    }

    @Test
    fun `the other slice carries a share that matches its amount`() {
        val ranking = (1..10).map { rank(it.toLong(), 100L) }
        val total = ranking.sumOf { it.totalCents }
        val collapsed = collapseTail(ranking, total)

        val other = collapsed.last()
        assertEquals(500L, other.totalCents)   // five categories of 100 folded together
        assertEquals(0.5f, other.share, 0.001f)
    }

    @Test
    fun `exactly six categories are not collapsed`() {
        val ranking = (1..6).map { rank(it.toLong(), 100L) }
        val collapsed = collapseTail(ranking, 600)
        assertEquals(6, collapsed.size)
        assertTrue(collapsed.none { it.name == "其他" })
    }
}
