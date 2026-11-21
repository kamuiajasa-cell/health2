package id.kamui.runsense.domain

import kotlin.math.roundToInt

data class AthleteProfile(val restingHr: Int = 60, val maxHr: Int = 185, val thresholdHr: Int = 160, val thresholdPaceSecPerKm: Int = 390)
data class ActivityRecord(val id: String, val startedAt: Long, val type: String, val durationMin: Double, val distanceKm: Double, val averageHr: Int, val externalLoad: Double = 0.0, val quality: Boolean = false)
data class StrengthRecord(val id: String, val date: String, val category: String, val durationMin: Int, val level: Int, val completed: Boolean)
data class WellnessRecord(val date: String, val restingHr: Int = 0, val sleepHours: Double = 0.0, val hrv: Double = 0.0)
data class TrainingSummary(val acuteLoad: Double, val chronicLoad: Double, val acwr: Double?, val readiness: Int?, val strengthLoad48h: Double, val legStrengthLoad48h: Double, val qualitySessions7d: Int, val recommendation: String, val rationale: List<String>)

object TrainingEngine {
    private const val DAY = 86_400_000L

    fun activityLoad(a: ActivityRecord, profile: AthleteProfile): Double {
        if (a.externalLoad > 0) return a.externalLoad
        if (a.type == "walk") return a.durationMin * 0.35
        if (a.type == "strength") return a.durationMin * 0.8
        val range = (profile.maxHr - profile.restingHr).coerceAtLeast(40)
        val intensity = if (a.averageHr > 0) ((a.averageHr - profile.restingHr).toDouble() / range) / 0.88 else 0.72
        val factor = intensity.coerceIn(0.45, 1.15)
        return (a.durationMin / 60.0 * 100.0 * factor * factor).roundToInt().toDouble()
    }

    fun strengthLoad48h(strength: List<StrengthRecord>, now: Long = System.currentTimeMillis()): Pair<Double, Double> {
        val weights = mapOf("hip" to 1.0, "calf" to 1.2, "leg" to 1.25, "core" to 0.1, "drill" to 0.55)
        var total = 0.0; var legs = 0.0
        strength.filter { it.completed }.forEach { item ->
            val stamp = parseDate(item.date) ?: return@forEach
            val age = now - stamp
            if (age < -12 * 60 * 60 * 1000L || age > 48 * 60 * 60 * 1000L) return@forEach
            val dose = (weights[item.category] ?: 0.5) * (1.0 + item.level.coerceIn(0, 4) * 0.45)
            total += dose
            if (item.category in setOf("hip", "calf", "leg", "drill")) legs += dose
        }
        return total to legs
    }

    fun summarize(activities: List<ActivityRecord>, strength: List<StrengthRecord>, wellness: List<WellnessRecord>, profile: AthleteProfile, now: Long = System.currentTimeMillis()): TrainingSummary {
        val acute = activities.filter { now - it.startedAt in 0..(7 * DAY) }.sumOf { activityLoad(it, profile) }
        val chronic = activities.filter { now - it.startedAt in (7 * DAY)..(28 * DAY) }.sumOf { activityLoad(it, profile) } / 3.0
        val ratio = if (chronic >= 25.0) acute / chronic else null
        val (strengthTotal, legLoad) = strengthLoad48h(strength, now)
        val hard = activities.count { now - it.startedAt in 0..(7 * DAY) && it.quality && it.type == "run" }
        val reasons = mutableListOf<String>()
        var score = 70; var inputs = 0
        val recent = wellness.sortedBy { it.date }.takeLast(3)
        val baseline = wellness.filter { it.restingHr > 30 }.dropLast(3).map { it.restingHr }.sorted()
        if (recent.any { it.restingHr > 30 } && baseline.size >= 5) {
            val current = recent.filter { it.restingHr > 30 }.map { it.restingHr }.average()
            val delta = current - baseline[baseline.size / 2]
            score += (-delta * 1.3).toInt().coerceIn(-14, 12); inputs++
            if (delta >= 3) reasons += "RHR naik ${delta.roundToInt()} bpm dari baseline"
        }
        val sleep = recent.lastOrNull { it.sleepHours > 0 }?.sleepHours
        if (sleep != null) { inputs++; if (sleep < 5.5) { score -= 8; reasons += "Tidur kurang dari 5,5 jam" } else if (sleep < 6.5) { score -= 3; reasons += "Durasi tidur masih terbatas" } }
        if (ratio != null) {
            inputs++
            when { ratio > 1.5 -> { score -= 20; reasons += "Beban akut jauh di atas kebiasaan" }; ratio > 1.3 -> { score -= 12; reasons += "Beban akut meningkat tajam" }; ratio > 1.15 -> { score -= 5; reasons += "Beban akut sedikit meningkat" }; ratio in 0.8..1.1 -> score += 5; ratio < 0.6 -> { score -= 6; reasons += "Beban latihan jauh di bawah kebiasaan" } }
        }
        if (legLoad >= 3.4) { score -= 5; reasons += "Paparan strength kaki 48 jam terakhir cukup tinggi" }
        val yesterday = activities.filter { now - it.startedAt in DAY..(2 * DAY) }.sumOf { activityLoad(it, profile) }
        if (yesterday > 90) { score -= 10; reasons += "Ada sesi berbeban tinggi kemarin" }
        val hardDays = (0..2).takeWhile { offset ->
            val end = now - offset * DAY
            activities.filter { end - DAY < it.startedAt && it.startedAt <= end }.sumOf { activityLoad(it, profile) } > 70
        }.size
        if (hardDays >= 3) score -= 12 else if (hardDays == 2) score -= 5
        val readiness = if (inputs > 0) score.coerceIn(22, 98) else null
        val recommendation = when {
            hard >= 2 -> "Hindari quality tambahan; pilih easy ringan atau pemulihan."
            legLoad >= 3.4 -> "Beri jarak untuk otot kaki setelah strength; pilih easy ringan atau istirahat."
            readiness != null && readiness < 45 -> "Prioritaskan pemulihan. Jangan mengejar target intensitas."
            ratio != null && ratio > 1.3 -> "Tahan kenaikan beban. Sesi berikutnya sebaiknya easy."
            else -> "Ikuti sesi terencana dengan kontrol effort dan evaluasi respons tubuh."
        }
        if (hard >= 2) reasons += "Sudah ada $hard sesi quality dalam 7 hari"
        if (reasons.isEmpty()) reasons += "Belum ada sinyal beban atau pemulihan yang kuat; tetap pantau respons tubuh"
        return TrainingSummary(acute, chronic, ratio, readiness, strengthTotal, legLoad, hard, recommendation, reasons)
    }

    fun cadenceSpm(raw: Double): Double? = when { !raw.isFinite() || raw <= 0 -> null; raw < 120 -> raw * 2.0; else -> raw }
    fun classifyCadenceWindow(cadenceSpm: Double?): String = if (cadenceSpm != null && cadenceSpm > 135.0) "Lari" else "Jalan"

    private fun parseDate(date: String): Long? = try {
        val p = date.split("-").map { it.toInt() }
        java.util.Calendar.getInstance().apply { set(p[0], p[1] - 1, p[2], 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    } catch (_: Exception) { null }
}
