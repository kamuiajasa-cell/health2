package id.kamui.runsense

import id.kamui.runsense.domain.ActivityRecord
import id.kamui.runsense.domain.AthleteProfile
import id.kamui.runsense.domain.WellnessRecord
import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.AthleteSettings
import com.runnow.domain.Profile
import com.runnow.domain.Settings
import com.runnow.domain.Snapshot
import com.runnow.domain.Wellness
import com.runnow.domain.processData
import java.time.ZoneId

/** Adapter from local native records to the original RunSense Kotlin domain model. */
fun nativeRunSenseAnalysis(
    activities: List<ActivityRecord>,
    wellness: List<WellnessRecord>,
    profile: AthleteProfile,
): com.runnow.domain.ProcessResult {
    val acts = activities.map { a ->
        Act(
            start = a.startedAt,
            kind = when (a.type) { "run" -> ActKind.RUN; "walk" -> ActKind.WALK; else -> ActKind.OTHER },
            min = a.durationMin,
            km = a.distanceKm,
            avgHr = a.averageHr.toDouble(),
            lid = a.id,
            name = a.type,
            load = a.externalLoad,
        )
    }
    val well = wellness.map { w ->
        Wellness(
            id = w.date,
            restingHR = w.restingHr.toDouble(),
            hrv = w.hrv,
            sleepSecs = w.sleepHours * 3600.0,
        )
    }
    val p = Profile(
        rest = profile.restingHr.toDouble(),
        max = profile.maxHr.toDouble(),
        restManual = true,
        lthrC = profile.thresholdHr,
        ltpC = profile.thresholdPaceSecPerKm,
    )
    val settings = Settings(
        style = "moderate",
        athlete = AthleteSettings(lthr = profile.thresholdHr, ltPace = ""),
        goal = "5K",
    )
    return Snapshot(
        profile = p,
        acts = acts,
        wellness = well,
        nowMs = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
        settings = settings,
    ).processData()
}
