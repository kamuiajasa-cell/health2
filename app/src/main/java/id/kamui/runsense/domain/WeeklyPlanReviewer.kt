package id.kamui.runsense.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

data class PlannedSession(val date: LocalDate, val title: String, val targetKm: Double)
data class PlannedDayResult(
    val date: LocalDate,
    val title: String,
    val targetKm: Double,
    val actualKm: Double,
    val runCount: Int,
    val status: String,
)
data class WeeklyReview(
    val weekStart: LocalDate,
    val targetKm: Double,
    val actualKm: Double,
    val adherencePercent: Int?,
    val completedSessions: Int,
    val dueSessions: Int,
    val extraKm: Double,
    val nextWeekTargetKm: Double,
    val adjustment: String,
    val rationale: String,
    val days: List<PlannedDayResult>,
    val nextWeekSessions: List<PlannedSession>,
)

object WeeklyPlanReviewer {
    fun evaluate(
        today: LocalDate,
        plan: List<PlannedSession>,
        activities: List<ActivityRecord>,
        strength: List<StrengthRecord>,
        summary: TrainingSummary,
    ): WeeklyReview {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val sunday = monday.plusDays(6)
        val sessions = plan.filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
        val runPlans = sessions.filter { isRunSession(it.title, it.targetKm) }
        val actualRuns = activities.filter {
            it.type.equals("run", true) && !it.localDate().isBefore(monday) && !it.localDate().isAfter(sunday)
        }
        val actualByDate = actualRuns.groupBy { it.localDate() }
        val days = runPlans.map { session ->
            val dayRuns = actualByDate[session.date].orEmpty()
            val km = dayRuns.sumOf { it.distanceKm }
            val status = when {
                session.date.isAfter(today) -> "MENDATANG"
                session.date == today && dayRuns.isEmpty() -> "HARI INI"
                session.targetKm <= 0 && dayRuns.isNotEmpty() -> "TERCAPAI"
                session.targetKm > 0 && km >= session.targetKm * 0.8 -> "TERCAPAI"
                km > 0 -> "SEBAGIAN"
                else -> "TERLEWAT"
            }
            PlannedDayResult(session.date, session.title, session.targetKm, km, dayRuns.size, status)
        }
        val due = days.filter { it.date.isBefore(today) || (it.date == today && it.status != "HARI INI") }
        val completed = due.count { it.status == "TERCAPAI" }
        val adherence = if (due.isEmpty()) null else (completed * 100.0 / due.size).roundToInt()
        val target = due.sumOf { it.targetKm }
        val actual = actualRuns.filter { !it.localDate().isAfter(today) }.sumOf { it.distanceKm }
        val extra = (actual - target).coerceAtLeast(0.0)

        val recentFeelRuns = actualRuns.filter {
            !it.localDate().isBefore(today.minusDays(6)) && !it.localDate().isAfter(today) && it.rpe in 1..10
        }
        val averageRpe = recentFeelRuns.map { it.rpe }.average().takeIf { recentFeelRuns.size >= 2 }
        val repeatedPain = recentFeelRuns.count { it.pain >= 3 } >= 2
        val significantPain = recentFeelRuns.any { it.pain >= 4 }
        val subjectiveRisk = significantPain || repeatedPain || (averageRpe != null && averageRpe >= 8.0)
        val elevatedLoad = (summary.acwr ?: 0.0) > 1.3 ||
            (summary.readiness != null && summary.readiness < 45) ||
            summary.legStrengthLoad48h >= 3.4 || subjectiveRisk
        val factor: Double
        val adjustment: String
        val rationale: String
        when {
            elevatedLoad && target > 0 -> {
                factor = if (significantPain) 0.85 else 0.9
                adjustment = if (significantPain) "KURANGI 15% + PRIORITASKAN PEMULIHAN" else "KURANGI TARGET 10% + RINGANKAN QUALITY"
                val feelReason = when {
                    significantPain -> " Ada check-in nyeri >=4/10."
                    repeatedPain -> " Nyeri >=3/10 berulang pada beberapa sesi."
                    averageRpe != null && averageRpe >= 8.0 -> " RPE rata-rata ${averageRpe.roundToInt()}/10 pada minimal dua sesi."
                    else -> ""
                }
                rationale = "Beban/pemulihan memberi sinyal hati-hati.$feelReason Turunkan volume rencana minggu depan sekitar ${((1.0 - factor) * 100).roundToInt()}% dan ubah satu sesi quality menjadi easy ringan. Bila nyeri masih ada atau mengubah langkah, jangan jalankan sesi lari dan evaluasi lebih lanjut."
            }
            adherence != null && adherence < 60 -> {
                factor = 1.0
                adjustment = "PERTAHANKAN, JANGAN MENUMPUK"
                rationale = "Sebagian sesi belum terlaksana. Pertahankan target dasar dan jangan memindahkan seluruh kilometer yang terlewat ke minggu depan."
            }
            adherence != null && adherence >= 85 && !elevatedLoad &&
                (summary.acwr == null || summary.acwr <= 1.15) &&
                (summary.readiness == null || summary.readiness >= 60) -> {
                factor = 1.0
                adjustment = "PERTAHANKAN DULU"
                rationale = "Pelaksanaan cukup konsisten dan tidak ada sinyal beban tinggi. Pertahankan volume; kenaikan baru dipertimbangkan setelah tren beberapa minggu stabil."
            }
            else -> {
                factor = 1.0
                adjustment = "JAGA VOLUME"
                rationale = "Data belum cukup kuat untuk menaikkan atau memangkas volume. Pertahankan struktur, lalu evaluasi respons tubuh minggu depan."
            }
        }

        var qualityDowngraded = false
        var previousQualityDate: LocalDate? = null
        val nextWeekSessions = runPlans.sortedBy { it.date }.map { session ->
            val quality = isQualitySession(session.title)
            val tooClose = quality && previousQualityDate?.let {
                ChronoUnit.DAYS.between(it, session.date) < 3
            } == true
            val reduceQuality = quality && elevatedLoad && !qualityDowngraded
            val title = if (quality && (reduceQuality || tooClose)) {
                qualityDowngraded = true
                "Easy ringan (pengganti ${session.title})"
            } else session.title
            if (quality && title == session.title) previousQualityDate = session.date
            PlannedSession(session.date.plusDays(7), title, session.targetKm * factor)
        }
        val nextTarget = nextWeekSessions.sumOf { it.targetKm }
        return WeeklyReview(
            monday, target, actual, adherence, completed, due.size, extra,
            nextTarget, adjustment, rationale, days, nextWeekSessions
        )
    }

    private fun isQualitySession(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return listOf("interval", "tempo", "threshold", "quality", "fartlek", "speed", "vo2").any(t::contains)
    }

    private fun isRunSession(title: String, km: Double): Boolean {
        val t = title.lowercase(Locale.ROOT)
        if (listOf("rest", "off", "istirahat", "strength", "pemulihan", "libur").any(t::contains)) return false
        return km > 0 || listOf("run", "lari", "jog", "easy", "interval", "tempo", "long", "fartlek", "threshold").any(t::contains)
    }

    private fun ActivityRecord.localDate(): LocalDate =
        java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
}
