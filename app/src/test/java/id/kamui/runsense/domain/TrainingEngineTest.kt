package id.kamui.runsense.domain

import org.junit.Assert.*
import org.junit.Test

class TrainingEngineTest {
    @Test fun cadenceStreamIsNormalizedBeforeClassification() {
        assertEquals(160.0, TrainingEngine.cadenceSpm(80.0)!!, 0.001)
        assertEquals("Lari", TrainingEngine.classifyCadenceWindow(160.0))
        assertEquals("Jalan", TrainingEngine.classifyCadenceWindow(130.0))
        assertNull(TrainingEngine.cadenceSpm(0.0))
    }

    @Test fun externalTrainingLoadTakesPriority() {
        val a = ActivityRecord("a", 1L, "run", 30.0, 4.0, 150, 72.0)
        assertEquals(72.0, TrainingEngine.activityLoad(a, AthleteProfile()), 0.001)
    }

    @Test fun runLoadUsesHeartRateReserveWhenNoExternalLoadExists() {
        val a = ActivityRecord("a", 1L, "run", 60.0, 8.0, 150)
        val load = TrainingEngine.activityLoad(a, AthleteProfile(60, 180))
        assertTrue(load > 0.0)
        assertTrue(load < 130.0)
    }

    @Test fun twoQualityRunsLockAdditionalQualityRecommendation() {
        val now = System.currentTimeMillis()
        val runs = listOf(
            ActivityRecord("1", now - 86_400_000L, "run", 35.0, 5.0, 160, quality = true),
            ActivityRecord("2", now - 3 * 86_400_000L, "run", 30.0, 4.0, 158, quality = true)
        )
        val summary = TrainingEngine.summarize(runs, emptyList(), emptyList(), AthleteProfile(), now)
        assertEquals(2, summary.qualitySessions7d)
        assertTrue(summary.recommendation.contains("Hindari quality"))
    }

    @Test fun completedLegStrengthChangesCoachRecommendation() {
        val now = System.currentTimeMillis()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        val strength = listOf(StrengthRecord("s", date, "leg", 30, 4, true))
        val summary = TrainingEngine.summarize(emptyList(), strength, emptyList(), AthleteProfile(), now)
        assertTrue(summary.legStrengthLoad48h >= 3.4)
        assertTrue(summary.recommendation.contains("strength"))
    }

    @Test fun unfinishedStrengthDoesNotAffectCoachLoad() {
        val now = System.currentTimeMillis()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        val strength = listOf(StrengthRecord("s", date, "leg", 30, 4, false))
        val load = TrainingEngine.strengthLoad48h(strength, now)
        assertEquals(0.0, load.first, 0.001)
        assertEquals(0.0, load.second, 0.001)
    }

    @Test fun completedLegStrengthIsIncludedIn48HourLoad() {
        val now = System.currentTimeMillis()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        val strength = listOf(StrengthRecord("s", date, "leg", 20, 2, true))
        val load = TrainingEngine.strengthLoad48h(strength, now)
        assertTrue(load.first > 0.0)
        assertEquals(load.first, load.second, 0.001)
    }
}
