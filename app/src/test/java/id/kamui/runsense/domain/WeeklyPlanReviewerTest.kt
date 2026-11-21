package id.kamui.runsense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeeklyPlanReviewerTest {
    private fun summary(
        acwr: Double? = 1.0,
        readiness: Int? = 70,
        legLoad: Double = 0.0,
    ) = TrainingSummary(100.0, 100.0, acwr, readiness, legLoad, legLoad, 0, "OK", emptyList())

    @Test fun comparesPlannedRunsWithActualRunsByCalendarDay() {
        val monday = LocalDate.of(2026, 11, 16)
        val plan = listOf(
            PlannedSession(monday, "Easy run", 4.0),
            PlannedSession(monday.plusDays(2), "Interval", 5.0),
            PlannedSession(monday.plusDays(5), "Long run", 6.0),
        )
        val actual = listOf(
            ActivityRecord("a", monday.atTime(7, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "run", 30.0, 4.2, 145),
            ActivityRecord("b", monday.plusDays(2).atTime(7, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "run", 35.0, 2.0, 155),
        )
        val review = WeeklyPlanReviewer.evaluate(monday.plusDays(3), plan, actual, emptyList(), summary())
        assertEquals(9.0, review.targetKm, 0.001)
        assertEquals(6.2, review.actualKm, 0.001)
        assertEquals("TERCAPAI", review.days[0].status)
        assertEquals("SEBAGIAN", review.days[1].status)
        assertEquals("MENDATANG", review.days[2].status)
        assertEquals(1, review.completedSessions)
        assertEquals(2, review.dueSessions)
    }

    @Test fun elevatedLoadSuggestsSmallNextWeekVolumeReduction() {
        val monday = LocalDate.of(2026, 11, 16)
        val plan = listOf(PlannedSession(monday, "Easy run", 5.0), PlannedSession(monday.plusDays(3), "Tempo", 5.0))
        val runs = plan.mapIndexed { i, p ->
            ActivityRecord("a$i", p.date.atTime(7, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "run", 35.0, 5.0, 150)
        }
        val review = WeeklyPlanReviewer.evaluate(monday.plusDays(4), plan, runs, emptyList(), summary(acwr = 1.4, readiness = 42))
        assertEquals("KURANGI TARGET 10%", review.adjustment)
        assertEquals(9.0, review.nextWeekTargetKm, 0.001)
    }

    @Test fun missedSessionsAreNotAddedToNextWeekTarget() {
        val monday = LocalDate.of(2026, 11, 16)
        val plan = listOf(PlannedSession(monday, "Easy run", 5.0), PlannedSession(monday.plusDays(2), "Interval", 5.0))
        val review = WeeklyPlanReviewer.evaluate(monday.plusDays(3), plan, emptyList(), emptyList(), summary())
        assertEquals("PERTAHANKAN, JANGAN MENUMPUK", review.adjustment)
        assertEquals(10.0, review.nextWeekTargetKm, 0.001)
        assertEquals(0.0, review.actualKm, 0.001)
    }

    @Test fun elevatedLoadReplacesOneQualitySessionWithEasyNextWeek() {
        val monday = LocalDate.of(2026, 11, 16)
        val plan = listOf(
            PlannedSession(monday.plusDays(1), "Easy run", 4.0),
            PlannedSession(monday.plusDays(2), "Interval", 5.0),
            PlannedSession(monday.plusDays(5), "Tempo", 4.0),
        )
        val review = WeeklyPlanReviewer.evaluate(monday.plusDays(4), plan, emptyList(), emptyList(), summary(acwr = 1.45, readiness = 40))
        assertTrue(review.nextWeekSessions.any { it.title.startsWith("Easy ringan (pengganti Interval)") })
        assertEquals(11.7, review.nextWeekTargetKm, 0.001)
    }

    @Test fun closelySpacedQualitySessionsAreSeparatedInSuggestedWeek() {
        val monday = LocalDate.of(2026, 11, 16)
        val plan = listOf(
            PlannedSession(monday.plusDays(1), "Interval", 4.0),
            PlannedSession(monday.plusDays(3), "Tempo", 4.0),
        )
        val review = WeeklyPlanReviewer.evaluate(monday.plusDays(4), plan, emptyList(), emptyList(), summary())
        assertTrue(review.nextWeekSessions[1].title.startsWith("Easy ringan (pengganti Tempo)"))
    }


    @Test fun todaysUnfinishedSessionIsNotCountedAsMissedBeforeDayEnds() {
        val today = LocalDate.of(2026, 11, 19)
        val plan = listOf(PlannedSession(today, "Easy run", 4.0))
        val review = WeeklyPlanReviewer.evaluate(today, plan, emptyList(), emptyList(), summary())
        assertEquals("HARI INI", review.days.single().status)
        assertEquals(0, review.dueSessions)
        assertNull(review.adherencePercent)
        assertEquals(0.0, review.targetKm, 0.001)
    }

}
