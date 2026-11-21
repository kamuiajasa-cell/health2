package id.kamui.runsense.data

import android.content.Context
import id.kamui.runsense.domain.*
import id.kamui.runsense.domain.RawStreamPoint
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RunSenseRepository(context: Context) {
    private val prefs = context.getSharedPreferences("runsense_native_v1", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun activities(): List<ActivityRecord> = objects("activities").mapNotNull { o ->
        try { ActivityRecord(o.getString("id"), o.getLong("startedAt"), o.getString("type"), o.getDouble("durationMin"), o.getDouble("distanceKm"), o.optInt("averageHr"), o.optDouble("externalLoad"), o.optBoolean("quality")) } catch (_: Exception) { null }
    }.sortedByDescending { it.startedAt }

    fun strength(): List<StrengthRecord> = objects("strength").mapNotNull { o ->
        try { StrengthRecord(o.getString("id"), o.getString("date"), o.getString("category"), o.optInt("durationMin"), o.optInt("level"), o.optBoolean("completed")) } catch (_: Exception) { null }
    }.sortedByDescending { it.date }

    fun wellness(): List<WellnessRecord> = objects("wellness").mapNotNull { o ->
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



    /** Native weekly plan and race target; kept separately from imported watch snapshots. */
    fun planSettings(): com.runnow.domain.Settings {
        val raw = prefs.getString("plan_settings", null) ?: return com.runnow.domain.Settings()
        return try {
            val root = JSONObject(raw)
            val planJson = root.optJSONObject("plan") ?: JSONObject()
            val plan = LinkedHashMap<Int, com.runnow.domain.PlanDay>()
            for (key in planJson.keys()) {
                val day = key.toIntOrNull() ?: continue
                val item = planJson.optJSONObject(key) ?: continue
                val title = item.optString("title").trim()
                if (title.isNotBlank()) plan[day] = com.runnow.domain.PlanDay(title, item.optDouble("km", 0.0).coerceAtLeast(0.0))
            }
            com.runnow.domain.Settings(
                style = root.optString("style", "moderate"),
                plan = plan,
                race = root.optString("race", ""),
                raceDist = root.optDouble("raceDist", 0.0).coerceAtLeast(0.0),
                raceGoal = root.optString("raceGoal", ""),
                goal = root.optString("goal", "5K"),
            )
        } catch (_: Exception) { com.runnow.domain.Settings() }
    }

    fun savePlanSettings(settings: com.runnow.domain.Settings) {
        val plan = JSONObject()
        settings.plan.forEach { (day, item) ->
            plan.put(day.toString(), JSONObject().put("title", item.t).put("km", item.km))
        }
        val json = JSONObject()
            .put("style", settings.style)
            .put("plan", plan)
            .put("race", settings.race)
            .put("raceDist", settings.raceDist)
            .put("raceGoal", settings.raceGoal)
            .put("goal", settings.goal)
        prefs.edit().putString("plan_settings", json.toString()).apply()
    }

    fun streamPoints(activityId: String): List<RawStreamPoint> {
        val watchRaw = prefs.getString("watch_stream_$activityId", null)
        if (watchRaw != null) {
            val points = try { JSONArray(watchRaw) } catch (_: Exception) { JSONArray() }
            return (0 until points.length()).mapNotNull { i ->
                val p = points.optJSONArray(i) ?: return@mapNotNull null
                if (p.length() < 5) return@mapNotNull null
                RawStreamPoint(
                    timeSec = p.optDouble(0, Double.NaN),
                    heartRate = p.optDouble(1, 0.0),
                    speedMps = p.optDouble(2, 0.0),
                    cadenceRaw = p.optDouble(4, 0.0),
                )
            }
        }
        val raw = prefs.getString("icu_stream_$activityId", null) ?: return emptyList()
        return parseIntervalsStream(raw)
    }

    private fun parseIntervalsStream(raw: String): List<RawStreamPoint> {
        val rootArray = try { JSONArray(raw) } catch (_: Exception) { null }
        val rootObject = if (rootArray == null) try { JSONObject(raw) } catch (_: Exception) { null } else null
        fun series(type: String): JSONArray? {
            if (rootArray != null) {
                for (i in 0 until rootArray.length()) {
                    val item = rootArray.optJSONObject(i) ?: continue
                    if (item.optString("type") == type) return item.optJSONArray("data")
                }
            }
            val value = rootObject?.opt(type)
            return when (value) {
                is JSONArray -> value
                is JSONObject -> value.optJSONArray("data")
                else -> null
            }
        }
        val time = series("time") ?: return emptyList()
        val cadence = series("cadence")
        val hr = series("heartrate")
        val speed = series("velocity_smooth")
        val distance = series("distance")
        var previousTime = 0.0
        var previousDistance = 0.0
        return (0 until time.length()).map { i ->
            val t = time.optDouble(i, Double.NaN)
            val v = speed?.optDouble(i, 0.0) ?: run {
                val d = distance?.optDouble(i, 0.0) ?: previousDistance
                val dt = t - previousTime
                val derived = if (dt > 0) (d - previousDistance) / dt else 0.0
                previousDistance = d
                previousTime = t
                derived
            }
            RawStreamPoint(t, hr?.optDouble(i, 0.0) ?: 0.0, v, cadence?.optDouble(i, 0.0) ?: 0.0)
        }.filter { it.timeSec.isFinite() }
    }

    /** Merge Intervals.icu / native domain repository records without duplicating stable activity IDs. */
    fun importDomainRecords(
        incomingActivities: List<com.runnow.domain.Act>,
        incomingWellness: List<com.runnow.domain.Wellness>,
    ): Pair<Int, Int> {
        val old = readArray("activities")
        val byId = LinkedHashMap<String, JSONObject>()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isNotBlank()) byId[id] = item
        }
        var added = 0
        incomingActivities.forEach { a ->
            val id = a.icuId ?: a.lid
            val existed = byId.containsKey(id)
            val item = byId[id] ?: JSONObject()
            item.put("id", id).put("startedAt", a.start)
                .put("type", when (a.kind) { com.runnow.domain.ActKind.RUN -> "run"; com.runnow.domain.ActKind.WALK -> "walk"; else -> "other" })
                .put("durationMin", a.min).put("distanceKm", a.km).put("averageHr", a.avgHr.toInt())
                .put("externalLoad", a.load).put("quality", false)
            byId[id] = item
            if (!existed) added++
        }
        val merged = JSONArray()
        byId.values.sortedBy { it.optLong("startedAt") }.takeLast(1200).forEach { merged.put(it) }
        writeArray("activities", merged)

        val oldWell = readArray("wellness")
        val byDate = LinkedHashMap<String, JSONObject>()
        for (i in 0 until oldWell.length()) {
            val item = oldWell.optJSONObject(i) ?: continue
            val date = item.optString("date")
            if (date.isNotBlank()) byDate[date] = item
        }
        incomingWellness.forEach { w ->
            val item = byDate[w.id] ?: JSONObject().put("date", w.id)
            if (w.restingHR > 0) item.put("restingHr", w.restingHR.toInt())
            if (w.sleepSecs > 0) item.put("sleepHours", w.sleepSecs / 3600.0)
            if (w.hrv > 0) item.put("hrv", w.hrv)
            byDate[w.id] = item
        }
        val mergedWell = JSONArray()
        byDate.values.sortedBy { it.optString("date") }.takeLast(400).forEach { mergedWell.put(it) }
        writeArray("wellness", mergedWell)
        return added to incomingWellness.size
    }

    /** Merge Huawei WatchImport JSON into the same local history used by native Coach and Progress. */
    fun importWatchJson(json: String): Pair<Int, Int> {
        val root = JSONObject(json)
        val oldActs = readArray("activities")
        val byId = LinkedHashMap<String, JSONObject>()
        for (i in 0 until oldActs.length()) {
            val item = oldActs.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isNotBlank()) byId[id] = item
        }
        val workouts = root.optJSONArray("workouts") ?: JSONArray()
        for (i in 0 until workouts.length()) {
            val workout = workouts.optJSONObject(i) ?: continue
            val streamId = workout.optString("iid")
            val points = workout.optJSONArray("pts")
            if (streamId.isNotBlank() && points != null && points.length() > 0) {
                prefs.edit().putString("watch_stream_$streamId", points.toString()).apply()
            }
        }
        val incomingActs = root.optJSONArray("acts") ?: JSONArray()
        var added = 0
        for (i in 0 until incomingActs.length()) {
            val row = incomingActs.optJSONArray(i) ?: continue
            if (row.length() < 10) continue
            val start = row.optLong(0)
            val id = row.optString(9).ifBlank { "hw$start" }
            if (row.optString(1) == "weight") {
                val strengthItems = readArray("strength")
                val exists = (0 until strengthItems.length()).any { strengthItems.optJSONObject(it)?.optString("id") == id }
                if (!exists) {
                    strengthItems.put(JSONObject().put("id", id)
                        .put("date", dateFormat.format(Date(start)))
                        .put("category", "full")
                        .put("durationMin", row.optDouble(2).toInt())
                        .put("level", 1)
                        .put("completed", true))
                    writeArray("strength", strengthItems)
                }
                continue
            }
            val existed = byId.containsKey(id)
            val item = byId[id] ?: JSONObject()
            item.put("id", id)
                .put("startedAt", start)
                .put("type", row.optString(1, "run"))
                .put("durationMin", row.optDouble(2))
                .put("distanceKm", row.optDouble(3))
                .put("averageHr", row.optDouble(4).toInt())
                .put("externalLoad", row.optDouble(8))
                .put("quality", false)
            byId[id] = item
            if (!existed) added++
        }
        val mergedActs = JSONArray()
        byId.values.sortedBy { it.optLong("startedAt") }.takeLast(1200).forEach { mergedActs.put(it) }
        writeArray("activities", mergedActs)

        val oldWell = readArray("wellness")
        val byDate = LinkedHashMap<String, JSONObject>()
        for (i in 0 until oldWell.length()) {
            val item = oldWell.optJSONObject(i) ?: continue
            val date = item.optString("date")
            if (date.isNotBlank()) byDate[date] = item
        }
        val incomingWell = root.optJSONArray("wellness") ?: JSONArray()
        for (i in 0 until incomingWell.length()) {
            val item = incomingWell.optJSONObject(i) ?: continue
            val date = item.optString("id")
            if (date.isBlank()) continue
            val dest = byDate[date] ?: JSONObject().put("date", date)
            if (item.has("restingHR")) dest.put("restingHr", item.optDouble("restingHR").toInt())
            if (item.has("sleepSecs")) dest.put("sleepHours", item.optDouble("sleepSecs") / 3600.0)
            if (item.has("hrv")) dest.put("hrv", item.optDouble("hrv"))
            byDate[date] = dest
        }
        val mergedWell = JSONArray()
        byDate.values.sortedBy { it.optString("date") }.takeLast(400).forEach { mergedWell.put(it) }
        writeArray("wellness", mergedWell)
        return added to incomingWell.length()
    }

    private fun objects(key: String): List<JSONObject> { val a = readArray(key); return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
    private fun readArray(k: String): JSONArray = try { JSONArray(prefs.getString(k, "[]")) } catch (_: Exception) { JSONArray() }
    private fun writeArray(k: String, v: JSONArray) { prefs.edit().putString(k, v.toString()).apply() }
}
