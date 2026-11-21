package id.kamui.runsense.data

import android.content.Context
import id.kamui.runsense.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RunSenseRepository(context: Context) {
    private val prefs = context.getSharedPreferences("runsense_native_v1", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun activities(): List<ActivityRecord> = readArray("activities").mapNotNull { o ->
        try { ActivityRecord(o.getString("id"), o.getLong("startedAt"), o.getString("type"), o.getDouble("durationMin"), o.getDouble("distanceKm"), o.optInt("averageHr"), o.optDouble("externalLoad"), o.optBoolean("quality")) } catch (_: Exception) { null }
    }.sortedByDescending { it.startedAt }

    fun strength(): List<StrengthRecord> = readArray("strength").mapNotNull { o ->
        try { StrengthRecord(o.getString("id"), o.getString("date"), o.getString("category"), o.optInt("durationMin"), o.optInt("level"), o.optBoolean("completed")) } catch (_: Exception) { null }
    }.sortedByDescending { it.date }

    fun wellness(): List<WellnessRecord> = readArray("wellness").mapNotNull { o ->
        try { WellnessRecord(o.getString("date"), o.optInt("restingHr"), o.optDouble("sleepHours"), o.optDouble("hrv")) } catch (_: Exception) { null }
    }.sortedBy { it.date }

    fun profile(): AthleteProfile = AthleteProfile(prefs.getInt("rhr", 60), prefs.getInt("maxHr", 185), prefs.getInt("lthr", 160), prefs.getInt("ltPace", 390))
    fun saveProfile(p: AthleteProfile) { prefs.edit().putInt("rhr", p.restingHr).putInt("maxHr", p.maxHr).putInt("lthr", p.thresholdHr).putInt("ltPace", p.thresholdPaceSecPerKm).apply() }

    fun addActivity(type: String, duration: Double, distance: Double, hr: Int, load: Double, quality: Boolean) {
        val a = readArray("activities")
        a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("startedAt", System.currentTimeMillis()).put("type", type).put("durationMin", duration).put("distanceKm", distance).put("averageHr", hr).put("externalLoad", load).put("quality", quality))
        writeArray("activities", a)
    }

    fun addStrength(category: String, duration: Int, level: Int, completed: Boolean) {
        val a = readArray("strength")
        a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("date", dateFormat.format(Date())).put("category", category).put("durationMin", duration).put("level", level).put("completed", completed))
        writeArray("strength", a)
    }

    fun addWellness(rhr: Int, sleep: Double, hrv: Double) {
        val date = dateFormat.format(Date()); val old = readArray("wellness"); val next = JSONArray()
        for (i in 0 until old.length()) { val o = old.optJSONObject(i) ?: continue; if (o.optString("date") != date) next.put(o) }
        next.put(JSONObject().put("date", date).put("restingHr", rhr).put("sleepHours", sleep).put("hrv", hrv))
        writeArray("wellness", next)
    }

    private fun readArray(k: String): JSONArray = try { JSONArray(prefs.getString(k, "[]")) } catch (_: Exception) { JSONArray() }
    private fun writeArray(k: String, v: JSONArray) { prefs.edit().putString(k, v.toString()).apply() }
}
