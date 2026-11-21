package id.kamui.runsense.domain

import kotlin.math.floor
import kotlin.math.roundToInt

data class RawStreamPoint(
    val timeSec: Double,
    val heartRate: Double = 0.0,
    val speedMps: Double = 0.0,
    val cadenceRaw: Double = 0.0,
)

data class CadenceWindow(
    val startSec: Int,
    val endSec: Int,
    val cadenceSpm: Double?,
    val mode: String,
    val paceSecPerKm: Int?,
)

data class CadenceSummary(
    val windows: List<CadenceWindow>,
    val runningWindows: Int,
    val walkingWindows: Int,
    val averageRunningCadence: Int?,
)

object CadenceAnalyzer {
    fun analyze(points: List<RawStreamPoint>, windowSeconds: Int = 5): CadenceSummary {
        val window = windowSeconds.coerceAtLeast(1)
        val buckets = points.filter { it.timeSec.isFinite() && it.timeSec >= 0 }
            .groupBy { floor(it.timeSec / window).toInt() }
        val windows = buckets.toSortedMap().map { (bucket, samples) ->
            val cadence = samples.mapNotNull { p ->
                val raw = p.cadenceRaw
                if (!raw.isFinite() || raw <= 0) null else if (raw < 120) raw * 2.0 else raw
            }.takeIf { it.isNotEmpty() }?.average()
            val speed = samples.map { it.speedMps }.filter { it.isFinite() && it > 0 }.takeIf { it.isNotEmpty() }?.average()
            val pace = speed?.let { (1000.0 / it).roundToInt() }
            CadenceWindow(
                startSec = bucket * window,
                endSec = bucket * window + window - 1,
                cadenceSpm = cadence,
                mode = when { cadence == null -> "—"; cadence > 135.0 -> "Lari"; else -> "Jalan" },
                paceSecPerKm = pace,
            )
        }
        val running = windows.filter { it.mode == "Lari" }
        return CadenceSummary(
            windows = windows,
            runningWindows = running.size,
            walkingWindows = windows.count { it.mode == "Jalan" },
            averageRunningCadence = running.mapNotNull { it.cadenceSpm }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }

    fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    fun formatPace(secondsPerKm: Int?): String = secondsPerKm?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}/km" } ?: "—"
}
