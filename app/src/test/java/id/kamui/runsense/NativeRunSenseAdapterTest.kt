package id.kamui.runsense

import com.runnow.domain.Profile
import com.runnow.domain.Snapshot
import id.kamui.runsense.domain.ActivityRecord
import id.kamui.runsense.domain.AthleteProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeRunSenseAdapterTest {
    @Test fun significantPainCheckinBlocksRunningRecommendation() {
        val now = System.currentTimeMillis()
        val base = Snapshot(Profile(rest = 63.0, max = 185.0), emptyList(), emptyList(), nowMs = now)
        val run = ActivityRecord("pain", now - 60_000L, "run", 30.0, 4.0, 145, rpe = 6, feel = "normal", pain = 4)

        val result = nativeRunSenseAnalysis(
            activities = listOf(run),
            strength = emptyList(),
            wellness = emptyList(),
            profile = AthleteProfile(),
            baseSnapshot = base,
        )

        assertEquals("rest", result.recommendation.status)
        assertEquals("Pulihkan kaki", result.recommendation.title)
    }

    @Test fun repeatedHighEffortCheckinsSwitchRecommendationToEasy() {
        val now = System.currentTimeMillis()
        val base = Snapshot(Profile(rest = 63.0, max = 185.0), emptyList(), emptyList(), nowMs = now)
        val runs = listOf(
            ActivityRecord("r1", now - 60_000L, "run", 30.0, 4.0, 145, rpe = 9, feel = "sangat berat"),
            ActivityRecord("r2", now - 2 * 86_400_000L, "run", 28.0, 3.5, 142, rpe = 8, feel = "berat"),
        )

        val result = nativeRunSenseAnalysis(
            activities = runs,
            strength = emptyList(),
            wellness = emptyList(),
            profile = AthleteProfile(),
            baseSnapshot = base,
        )

        assertEquals("train", result.recommendation.status)
        assertEquals("Easy respons tubuh", result.recommendation.title)
    }
}
