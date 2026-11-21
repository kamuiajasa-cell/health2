package id.kamui.runsense

import id.kamui.runsense.domain.ActivityRecord
import id.kamui.runsense.domain.StrengthRecord
import id.kamui.runsense.domain.TrainingEngine
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
    strength: List<StrengthRecord>,
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
    val result = Snapshot(
        profile = p,
        acts = acts,
        wellness = well,
        nowMs = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
        settings = settings,
    ).processData()
    val load = TrainingEngine.summarize(activities, strength, wellness, profile)
    val guarded = when {
        load.qualitySessions7d >= 2 -> result.recommendation.copy(
            k = "wn", status = "train", title = "Easy saja", duration = "25–35 menit",
            hr = "Zone 1–2", pace = "Santai dan terkontrol",
            reason = "Sudah ada ${load.qualitySessions7d} sesi quality dalam 7 hari. Coach menahan intensitas tambahan agar ada ruang adaptasi.",
            steps = listOf("Pemanasan" to "5–8 menit jalan dan jog", "Inti" to "15–20 menit easy", "Pendinginan" to "5 menit jalan"),
            tag = "RECOVERY", wl = null,
        )
        load.legStrengthLoad48h >= 3.4 -> result.recommendation.copy(
            k = "wn", status = "train", title = "Easy setelah strength", duration = "20–30 menit",
            hr = "Zone 1–2", pace = "Sangat santai",
            reason = "Paparan strength kaki 48 jam terakhir cukup tinggi. Beri ruang pemulihan otot dan jaringan penunjang sebelum quality berikutnya.",
            steps = listOf("Inti" to "20–30 menit easy atau jalan cepat", "Batas" to "Hentikan bila nyeri atau langkah berubah"),
            tag = "RECOVERY", wl = null,
        )
        load.strengthLoad48h >= 4.0 -> result.recommendation.copy(
            k = "wn", status = "train", title = "Pemulihan setelah strength", duration = "20–30 menit",
            hr = "Zone 1", pace = "Santai",
            reason = "Beban strength keseluruhan 48 jam terakhir cukup besar. Hindari menambah intensitas hari ini.",
            steps = listOf("Inti" to "Jalan santai atau easy singkat", "Batas" to "Jangan mengejar pace"),
            tag = "RECOVERY", wl = null,
        )
        else -> result.recommendation
    }
    return result.copy(recommendation = guarded)
}
