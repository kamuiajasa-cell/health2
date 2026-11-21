package id.kamui.runsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kamui.runsense.data.RunSenseRepository
import id.kamui.runsense.data.NativeIntervalsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.kamui.runsense.domain.*
import com.runnow.domain.ActivityItem
import com.runnow.domain.Snapshot
import com.runnow.domain.reviewRun
import com.runnow.domain.PLAN_DAYS
import com.runnow.domain.hasPlan
import com.runnow.domain.planOf
import com.runnow.domain.ymd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFF0A0A0A)
private val Panel = Color(0xFF1E1E1E)
private val Lime = Color(0xFFC6FF38)
private val Muted = Color(0xFF9E9E9E)
private val White = Color(0xFFFAFAFA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 9)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 9)
        val repository = RunSenseRepository(applicationContext)
        val intervals = NativeIntervalsRuntime(applicationContext)
        setContent { RunSenseNativeApp(repository, intervals) }
    }
}

private enum class RunTab(val label: String, val mark: String) {
    HOME("Beranda", "⌂"), TRAINING("Latihan", "◷"), PROGRESS("Progress", "↗"),
    COACH("Coach", "✦"), WATCH("Jam", "⌚"), SETTINGS("Setelan", "⚙")
}

@Composable
private fun RunSenseNativeApp(repository: RunSenseRepository, intervals: NativeIntervalsRuntime) {
    var selected by remember { mutableStateOf(RunTab.HOME) }
    var revision by remember { mutableIntStateOf(0) }
    var showActivity by remember { mutableStateOf(false) }
    var showStrength by remember { mutableStateOf(false) }
    var showStrengthTarget by remember { mutableStateOf(false) }
    var showParq by remember { mutableStateOf(false) }
    var showWellness by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showPlanRace by remember { mutableStateOf(false) }
    var checkinActivity by remember { mutableStateOf<ActivityRecord?>(null) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val profile = remember(revision) { repository.profile() }
    val planSettings = remember(revision) { repository.planSettings() }
    val activities = remember(revision) { repository.activities() }
    val strength = remember(revision) { repository.strength() }
    val wellness = remember(revision) { repository.wellness() }
    val strengthTarget = remember(revision) { repository.weeklyStrengthTarget() }
    val parqAnswers = remember(revision) { repository.parqAnswers() }
    val summary = remember(revision) { TrainingEngine.summarize(activities, strength, wellness, profile) }
    val baseSnapshot = remember(revision) { runCatching { intervals.snapshot() }.getOrNull() }
    val domainResult = remember(revision) { runCatching { nativeRunSenseAnalysis(activities, strength, wellness, profile, baseSnapshot, planSettings) }.getOrNull() }
    fun changed() { revision++ }

    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, background = Ink, surface = Panel, onPrimary = Ink, onBackground = White, onSurface = White)) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111410), contentColor = White, tonalElevation = 0.dp) {
                    RunTab.entries.forEach { tab ->
                        NavigationBarItem(selected = selected == tab, onClick = { selected = tab },
                            icon = { Text(tab.mark, fontSize = 17.sp) }, label = { Text(tab.label, fontSize = 9.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Lime, selectedTextColor = Lime,
                                indicatorColor = Lime.copy(alpha = .13f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }
        ) { padding ->
            when (selected) {
                RunTab.HOME -> HomeScreen(activities, summary, domainResult, Modifier.padding(padding), onAdd = { showActivity = true }, onStrength = { showStrength = true })
                RunTab.TRAINING -> TrainingScreen(activities, strength, Modifier.padding(padding), onAdd = { showActivity = true }, onStrength = { showStrength = true })
                RunTab.PROGRESS -> ProgressScreen(
                    activities, summary, domainResult, baseSnapshot, repository, Modifier.padding(padding),
                    onCheckin = { checkinActivity = it },
                    onLoadStream = { id ->
                        scope.launch {
                            syncBusy = true
                            try {
                                val loaded = withContext(Dispatchers.IO) { intervals.ensureStreamFor(id) }
                                syncMessage = if (loaded) "Stream sesi berhasil dimuat." else "Stream tidak tersedia atau belum ada API key."
                                changed()
                            } catch (e: Exception) {
                                syncMessage = "Gagal memuat stream: ${e.message}"
                            } finally { syncBusy = false }
                        }
                    }
                )
                RunTab.COACH -> CoachScreen(activities, summary, strength, strengthTarget, parqAnswers, domainResult, baseSnapshot, planSettings, Modifier.padding(padding))
                RunTab.WATCH -> WatchScreen(Modifier.padding(padding)) { json -> repository.importWatchJson(json); intervals.ingestWatchJson(json); changed() }
                RunTab.SETTINGS -> SettingsScreen(
                    profile, wellness, Modifier.padding(padding),
                    hasIntervalsKey = intervals.hasApiKey(), syncBusy = syncBusy, syncMessage = syncMessage,
                    onProfile = { showProfile = true }, onWellness = { showWellness = true }, onPlanRace = { showPlanRace = true },
                    strengthTarget = strengthTarget, parqAnswers = parqAnswers,
                    onStrengthTarget = { showStrengthTarget = true }, onParq = { showParq = true },
                    onApiKey = { showApiKey = true },
                    onSync = {
                        scope.launch {
                            syncBusy = true
                            syncMessage = "Menyinkronkan Intervals.icu…"
                            try {
                                syncMessage = withContext(Dispatchers.IO) { intervals.sync() }
                                changed()
                            } catch (e: Exception) {
                                syncMessage = "Gagal sinkron: ${e.message}"
                            } finally { syncBusy = false }
                        }
                    }
                )
            }
        }
        checkinActivity?.let { activity ->
            SessionCheckinDialog(activity, onDismiss = { checkinActivity = null }, onSave = { rpe, feel, pain ->
                repository.updateActivityCheckin(activity.id, rpe, feel, pain)
                checkinActivity = null
                changed()
            })
        }
        if (showActivity) ActivityDialog(onDismiss = { showActivity = false }, onSave = { type, duration, distance, hr, load, quality, rpe, feel, pain ->
            repository.addActivity(type, duration, distance, hr, load, quality, rpe, feel, pain); showActivity = false; changed()
        })
        if (showStrength) StrengthDialog(onDismiss = { showStrength = false }, onSave = { category, duration, level, done, exercise, sets, reps, load ->
            repository.addStrength(category, duration, level, done, exercise, sets, reps, load); showStrength = false; changed()
        })
        if (showStrengthTarget) StrengthTargetDialog(strengthTarget, onDismiss = { showStrengthTarget = false }, onSave = { days -> repository.saveWeeklyStrengthTarget(days); showStrengthTarget = false; changed() })
        if (showParq) ParqDialog(parqAnswers, onDismiss = { showParq = false }, onSave = { answers -> repository.saveParqAnswers(answers); showParq = false; changed() })
        if (showWellness) WellnessDialog(onDismiss = { showWellness = false }, onSave = { rhr, sleep, hrv ->
            repository.addWellness(rhr, sleep, hrv); showWellness = false; changed()
        })
        if (showProfile) ProfileDialog(profile, onDismiss = { showProfile = false }, onSave = {
            repository.saveProfile(it); showProfile = false; changed()
        })
        if (showPlanRace) PlanRaceDialog(planSettings, onDismiss = { showPlanRace = false }, onSave = { settings ->
            repository.savePlanSettings(settings); showPlanRace = false; changed()
        })
        if (showApiKey) ApiKeyDialog(onDismiss = { showApiKey = false }, onSave = { key ->
            showApiKey = false
            scope.launch {
                syncBusy = true
                syncMessage = "Menyinkronkan Intervals.icu…"
                try {
                    syncMessage = withContext(Dispatchers.IO) { intervals.sync(key) }
                    changed()
                } catch (e: Exception) {
                    syncMessage = "Gagal sinkron: ${e.message}"
                } finally { syncBusy = false }
            }
        })
    }
}

@Composable
@Composable
private fun Page(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun HomeScreen(activities: List<ActivityRecord>, summary: TrainingSummary, domainResult: com.runnow.domain.ProcessResult?, modifier: Modifier, onAdd: () -> Unit, onStrength: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HomePageHero() }
        item { CoachRecommendationCard(domainResult?.recommendation) }
        item { ReadinessCard(domainResult?.athlete?.score ?: summary.readiness, summary.recommendation) }
        item { SectionKicker("YOUR TRAINING", "01") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("BEBAN 7 HARI", fmt(summary.acuteLoad), Modifier.weight(1f))
            MetricCard("ACWR", summary.acwr?.let { "%.2f".format(Locale.US, it) } ?: "—", Modifier.weight(1f))
            MetricCard("STRENGTH 48J", "%.1f".format(Locale.US, summary.strengthLoad48h), Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("＋ Catat latihan") }
            OutlinedButton(onClick = onStrength, modifier = Modifier.weight(1f)) { Text("＋ Strength") }
        } }
        item { SectionKicker("RECENT ACTIVITY", "02") }
        item { SectionTitle("Aktivitas terbaru", "TERSIMPAN DI PERANGKAT") }
        if (activities.isEmpty()) item { EmptyCard("Belum ada aktivitas. Tambahkan sesi pertama untuk mulai membangun analisis beban.") }
        items(activities.take(5), key = { it.id }) { ActivityCard(it) }
    }
}

@Composable
private fun TrainingScreen(activities: List<ActivityRecord>, strength: List<StrengthRecord>, modifier: Modifier, onAdd: () -> Unit, onStrength: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHero("YOUR ACTIVITY LOG", "Every session", "has a story.", "Catatan lari, jalan, dan strength.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("＋ Aktivitas") }
            OutlinedButton(onClick = onStrength, modifier = Modifier.weight(1f)) { Text("＋ Strength") }
        } }
        item { SectionTitle("Riwayat aktivitas", "${activities.size} SESI") }
        if (activities.isEmpty()) item { EmptyCard("Belum ada aktivitas yang dicatat.") }
        items(activities, key = { it.id }) { ActivityCard(it) }
        item { SectionTitle("Riwayat strength", "${strength.size} CATATAN") }
        if (strength.isEmpty()) item { EmptyCard("Belum ada sesi strength tersimpan.") }
        items(strength.take(30), key = { it.id }) { s ->
            InfoCard(strengthLabel(s.category), "${s.date} · ${s.durationMin} menit · level ${s.level}" + (if (s.exercise.isNotBlank()) " · ${s.exercise}: ${s.sets}×${s.reps}" + (if (s.resistanceKg > 0) " @ ${s.resistanceKg} kg" else "") else ""), if (s.completed) "SELESAI" else "SKIP")
        }
    }
}

@Composable
private fun ProgressScreen(
    activities: List<ActivityRecord>,
    summary: TrainingSummary,
    domainResult: com.runnow.domain.ProcessResult?,
    snapshot: Snapshot?,
    repository: RunSenseRepository,
    modifier: Modifier,
    onLoadStream: (String) -> Unit,
    onCheckin: (ActivityRecord) -> Unit,
) {
    val now = System.currentTimeMillis()
    val last7 = activities.filter { now - it.startedAt in 0..604800000L }
    val last28 = activities.filter { now - it.startedAt in 0..2419200000L }
    val km7 = last7.sumOf { it.distanceKm }
    val mins7 = last7.sumOf { it.durationMin }
    val avgPace = if (km7 > 0) mins7 / km7 else 0.0
    Page(modifier) {
        PageHero("PERFORMANCE INTELLIGENCE", "See how far", "you've grown.", "Ringkasan berdasarkan aktivitas yang tersimpan.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("JARAK 7 HARI", "%.1f km".format(Locale.US, km7), Modifier.weight(1f))
            MetricCard("DURASI", "${mins7.roundToInt()} mnt", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("PACE RATA-RATA", if (avgPace > 0) pace(avgPace) else "—", Modifier.weight(1f))
            MetricCard("SESI 28 HARI", last28.size.toString(), Modifier.weight(1f))
        }
        MetricCard("BEBAN 7 HARI", fmt(summary.acuteLoad), Modifier.fillMaxWidth())
        SectionTitle("Volume harian", "7 HARI")
        WeeklyDistanceChart(activities)
        TrainingTrendChart(activities)
        SectionTitle("Review sesi", "MESIN RUNSENSE")
        val runs = domainResult?.activities?.filter { it.act.kind == com.runnow.domain.ActKind.RUN }?.take(5).orEmpty()
        if (runs.isEmpty()) EmptyCard("Review sesi akan muncul setelah aktivitas lari tersedia.")
        else runs.forEach { SessionReviewCard(it, snapshot, repository, onLoadStream) }
        SectionTitle("Aktivitas 28 hari", "TERBARU")
        if (last28.isEmpty()) EmptyCard("Data grafik dan tren akan terbentuk setelah aktivitas dicatat.")
        else last28.sortedByDescending { it.startedAt }.forEach { CheckinActivityCard(it, onCheckin) }
    }
}

@Composable
private fun WeeklyDistanceChart(activities: List<ActivityRecord>) {
    val today = java.time.LocalDate.now()
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = dates.map { date ->
        activities.filter { a ->
            a.type.equals("run", true) &&
                java.time.Instant.ofEpochMilli(a.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == date
        }.sumOf { it.distanceKm }
    }
    val maxKm = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Jarak lari", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("%.1f km total".format(Locale.US, values.sum()), color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(Modifier.fillMaxWidth().height(104.dp)) {
                val slot = size.width / 7f
                val barWidth = slot * .46f
                values.forEachIndexed { index, value ->
                    val height = (value / maxKm * size.height * .86).toFloat()
                    val left = index * slot + (slot - barWidth) / 2f
                    drawRoundRect(
                        color = if (index == 6) Lime else Lime.copy(alpha = .38f),
                        topLeft = Offset(left, size.height - height),
                        size = Size(barWidth, height.coerceAtLeast(3f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dates.forEach { date ->
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id", "ID")),
                        color = if (date == today) Lime else Muted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun TrainingTrendChart(activities: List<ActivityRecord>) {
    var metric by remember { mutableStateOf("pace") }
    val today = java.time.LocalDate.now()
    val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weeks = (3 downTo 0).map { offset ->
        val start = currentMonday.minusWeeks(offset.toLong())
        val end = start.plusWeeks(1)
        val runs = activities.filter { activity ->
            if (!activity.type.equals("run", true)) return@filter false
            val date = java.time.Instant.ofEpochMilli(activity.startedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            !date.isBefore(start) && date.isBefore(end)
        }
        val km = runs.sumOf { it.distanceKm }
        val paceValue = if (km > 0) runs.sumOf { it.durationMin } / km else null
        val hrValues = runs.filter { it.averageHr > 0 }.map { it.averageHr }
        val hrValue = hrValues.takeIf { it.isNotEmpty() }?.average()
        Triple(start, paceValue, hrValue)
    }
    val values = weeks.map { if (metric == "pace") it.second else it.third }
    val valid = values.filterNotNull()
    val minValue = valid.minOrNull() ?: 0.0
    val maxValue = valid.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).coerceAtLeast(if (metric == "pace") 0.15 else 3.0)
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Tren 4 minggu", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (metric == "pace") "Pace rata-rata lari (menit/km)" else "Detak jantung rata-rata (bpm)", color = Muted, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = metric == "pace", onClick = { metric = "pace" }, label = { Text("Pace") })
                    FilterChip(selected = metric == "hr", onClick = { metric = "hr" }, label = { Text("HR") })
                }
            }
            if (valid.isEmpty()) {
                Text("Belum cukup data lari untuk membentuk tren.", color = Muted, fontSize = 11.sp)
            } else {
                Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                    val slot = size.width / 4f
                    val barWidth = slot * .42f
                    values.forEachIndexed { index, value ->
                        if (value != null) {
                            val normalized = ((value - minValue) / range).toFloat().coerceIn(0f, 1f)
                            val height = (0.28f + normalized * .68f) * size.height
                            val left = index * slot + (slot - barWidth) / 2f
                            drawRoundRect(
                                color = if (index == 3) Lime else Lime.copy(alpha = .38f),
                                topLeft = Offset(left, size.height - height),
                                size = Size(barWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weeks.forEachIndexed { index, week ->
                        val value = values[index]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("M${index + 1}", color = Muted, fontSize = 9.sp)
                            Text(
                                value?.let { if (metric == "pace") pace(it) else "${it.roundToInt()} bpm" } ?: "—",
                                color = if (index == 3) Lime else White, fontSize = 9.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionReviewCard(item: ActivityItem, snapshot: Snapshot?, repository: RunSenseRepository, onLoadStream: (String) -> Unit) {
    val review = remember(item.act, snapshot) { snapshot?.reviewRun(item.act) }
    val streamKey = item.act.icuId ?: item.act.lid
    val hasStream = snapshot?.streams?.containsKey(streamKey) == true
    val rawPoints = remember(item.act.lid, snapshot) { repository.streamPoints(streamKey) }
    val cadence = remember(rawPoints) { CadenceAnalyzer.analyze(rawPoints, 5) }
    var cadenceExpanded by remember(item.act.lid) { mutableStateOf(false) }
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(item.meta, color = Muted, fontSize = 11.sp)
            Text(review?.summary?.ifBlank { item.why } ?: item.why, color = White, fontSize = 12.sp, lineHeight = 17.sp)
            review?.sections?.take(3)?.forEach { section ->
                Text(section.title, color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                section.notes.take(2).forEach { note ->
                    Text("• ${note.text}", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            if (rawPoints.isNotEmpty()) {
                Divider(color = Color.White.copy(alpha = .08f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Kadens per 5 detik", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${cadence.windows.size} segmen · ${cadence.runningWindows} lari · ${cadence.walkingWindows} jalan", color = Muted, fontSize = 10.sp)
                    }
                    TextButton(onClick = { cadenceExpanded = !cadenceExpanded }) {
                        Text(if (cadenceExpanded) "Tutup" else "Lihat")
                    }
                }
                Text("Rata-rata kadens saat lari: ${cadence.averageRunningCadence?.let { "$it spm" } ?: "—"}", color = White, fontSize = 11.sp)
                if (cadenceExpanded) {
                    cadence.windows.forEach { w ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(CadenceAnalyzer.formatTime(w.startSec), color = Muted, fontSize = 10.sp)
                            Text(w.cadenceSpm?.let { "${it.roundToInt()} spm" } ?: "—", color = White, fontSize = 10.sp)
                            Text(w.mode, color = if (w.mode == "Lari") Lime else Muted, fontSize = 10.sp)
                            Text(CadenceAnalyzer.formatPace(w.paceSecPerKm), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                Text("Data kadens mentah belum tersedia. Sinkronkan jam atau muat stream Intervals.icu.", color = Muted, fontSize = 10.sp)
            }
            if (item.act.icuId != null && !hasStream) {
                OutlinedButton(onClick = { onLoadStream(streamKey) }) { Text("Muat stream Intervals.icu") }
            }
        }
    }
}

@Composable
private fun WeeklyReviewCard(review: WeeklyReview) {
    val ratio = if (review.targetKm > 0) (review.actualKm / review.targetKm).coerceIn(0.0, 1.0).toFloat() else 0f
    Surface(color = Panel, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("RINGKASAN PELAKSANAAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("%.1f km".format(Locale.US, review.actualKm), color = Lime, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("realisasi hingga hari ini", color = Muted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("%.1f km".format(Locale.US, review.targetKm), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("target yang jatuh tempo", color = Muted, fontSize = 10.sp)
                }
            }
            LinearProgressIndicator(progress = ratio, modifier = Modifier.fillMaxWidth().height(7.dp), color = Lime, trackColor = Color.White.copy(alpha = .10f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kepatuhan sesi: ${review.adherencePercent?.let { "$it%" } ?: "belum ada sesi jatuh tempo"}", color = White, fontSize = 11.sp)
                Text("${review.completedSessions}/${review.dueSessions} selesai", color = Muted, fontSize = 10.sp)
            }
            Divider(color = Color.White.copy(alpha = .08f))
            Text("ARAH MINGGU DEPAN", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(review.adjustment, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(review.rationale, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Target rencana berikutnya", color = Muted, fontSize = 10.sp)
                Text("%.1f km".format(Locale.US, review.nextWeekTargetKm), color = Lime, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (review.extraKm > 0.2) {
                Text("Ada %.1f km di luar target harian. Pastikan penambahan spontan ini tidak menumpuk dengan sesi berat.".format(Locale.US, review.extraKm), color = Color(0xFFFFC16B), fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun CoachScreen(activities: List<ActivityRecord>, summary: TrainingSummary, strength: List<StrengthRecord>, strengthTarget: Int, parqAnswers: List<Boolean?>, domainResult: com.runnow.domain.ProcessResult?, snapshot: Snapshot?, planSettings: com.runnow.domain.Settings, modifier: Modifier) {
    Page(modifier) {
        PageHero("ADAPTIVE TRAINING", "Train with", "better insight.", "Evaluasi beban, pemulihan, dan strength.")
        val parqYes = parqAnswers.count { it == true }
        val parqAnswered = parqAnswers.count { it != null }
        if (parqYes > 0) {
            Surface(color = Color(0xFF3A2A1B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PERHATIAN · PAR-Q: $parqYes JAWABAN YA", color = Color(0xFFFFC36A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Coach tidak menganggap Anda otomatis mendapat izin untuk berlatih. Konsultasikan jawaban ini dengan tenaga kesehatan sebelum memulai atau meningkatkan latihan, terutama latihan intensitas tinggi.", color = White, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        } else if (parqAnswered < 7) {
            InfoCard("Skrining PAR-Q belum lengkap", "$parqAnswered dari 7 pertanyaan terisi. Lengkapi di Setelan agar Coach dapat menampilkan status skrining.", "BELUM LENGKAP")
        } else {
            InfoCard("Skrining PAR-Q", "Tidak ada jawaban YA pada skrining terakhir. Ini bukan jaminan bebas risiko atau izin medis.", "TERISI")
        }
        ReadinessCard(domainResult?.athlete?.score ?: summary.readiness, summary.recommendation)
        CoachRecommendationCard(domainResult?.recommendation)
        domainResult?.athlete?.let { athlete ->
            InfoCard("Tahap kapasitas", athlete.stageName, "STAGE ${athlete.stage}")
            InfoCard("Ambang laktat", "LTHR ${athlete.lthr ?: "—"} bpm · LT pace ${athlete.ltPace ?: "—"}", "AUTO / PROFIL")
            InfoCard("Estimasi 5K", domainResult.goalEst.current, domainResult.goalEst.projection)
        }
        MetricCard("ACWR", summary.acwr?.let { "%.2f".format(Locale.US, it) } ?: "Belum cukup data beban kronis", Modifier.fillMaxWidth())
        InfoCard("Sesi quality 7 hari", "${summary.qualitySessions7d} sesi tercatat", if (summary.qualitySessions7d >= 2) "KUNCI QUALITY" else "TERPANTAU")
        InfoCard("Strength kaki 48 jam", "%.2f unit beban".format(Locale.US, summary.legStrengthLoad48h), if (summary.legStrengthLoad48h >= 3.4) "WASPADA BEBAN" else "NORMAL")
        InfoCard("Total strength 48 jam", "%.2f unit beban".format(Locale.US, summary.strengthLoad48h), if (summary.strengthLoad48h >= 4.0) "BEBAN TINGGI" else "TERPANTAU")
        val recentCheckins = activities.filter {
            it.type.equals("run", true) && (it.rpe > 0 || it.pain > 0 || it.feel.isNotBlank())
        }.take(5)
        SectionTitle("Respons tubuh setelah lari", "${recentCheckins.size} CHECK-IN")
        if (recentCheckins.isEmpty()) {
            EmptyCard("Belum ada check-in RPE/feel/nyeri. Saat mencatat lari, isi seberapa berat sesi terasa dan apakah ada nyeri. Data ini membantu Coach menyesuaikan beban mingguan.")
        } else {
            recentCheckins.forEach { item ->
                val date = java.time.Instant.ofEpochMilli(item.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val detail = "RPE ${item.rpe}/10 · feel ${item.feel.ifBlank { "—" }} · nyeri ${item.pain}/10"
                val tag = when {
                    item.pain >= 4 -> "NYERI TINGGI"
                    item.pain >= 3 || item.rpe >= 8 || item.feel == "sangat berat" -> "PERLU HATI-HATI"
                    else -> "TERCATAT"
                }
                InfoCard(date.toString(), detail, tag)
            }
            Text("RPE tinggi berulang atau nyeri yang bermakna akan menurunkan target minggu depan. Check-in tidak menggantikan pemeriksaan medis.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        val weekStart = java.time.LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        val strengthDaysThisWeek = strength.filter { it.completed && runCatching { java.time.LocalDate.parse(it.date) }.getOrNull()?.let { date -> !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6)) } == true }.map { it.date }.distinct().size
        InfoCard("Target strength mingguan", "$strengthDaysThisWeek / $strengthTarget hari tercapai", if (strengthDaysThisWeek >= strengthTarget && strengthTarget > 0) "TARGET TERCAPAI" else "PROGRES")
        SectionTitle("Strength yang dibaca Coach", "${strength.size} CATATAN")
        if (strength.isEmpty()) {
            EmptyCard("Belum ada catatan strength. Setelah sesi disimpan sebagai selesai, bebannya masuk ke evaluasi Coach.")
        } else {
            strength.sortedByDescending { it.date }.take(5).forEach { item ->
                InfoCard(
                    strengthLabel(item.category),
                    "${item.date} · ${item.durationMin} menit · level ${item.level}" + (if (item.exercise.isNotBlank()) " · ${item.exercise} ${item.sets}×${item.reps}" + (if (item.resistanceKg > 0) " @ ${item.resistanceKg} kg" else "") else ""),
                    if (item.completed) "DIPERHITUNGKAN" else "BELUM SELESAI"
                )
            }
            Text(
                "Hanya sesi yang ditandai selesai yang masuk ke beban strength. Beban kaki dan total strength 48 jam terakhir ikut memengaruhi readiness serta rekomendasi sesi berikutnya.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
            )
        }
        SectionTitle("Rencana vs realisasi", "MINGGU INI")
        if (planSettings.plan.isEmpty()) {
            EmptyCard("Belum ada rencana mingguan tersimpan di profil RunSense. Setelah rencana tersedia, Coach akan membandingkan target harian dengan kilometer aktual dan strength yang selesai.")
        } else {
            val today = snapshot?.ymd()?.let { java.time.LocalDate.parse(it) } ?: java.time.LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value + 6).toLong() % 7L)
            val plannedSessions = PLAN_DAYS.mapNotNull { day ->
                planSettings.plan[day]?.let { p ->
                    PlannedSession(monday.plusDays(if (day == 0) 6 else (day - 1).toLong()), p.t, p.km)
                }
            }
            val weekReview = WeeklyPlanReviewer.evaluate(today, plannedSessions, activities, strength, summary)
            if (planSettings.race.isNotBlank()) {
                val daysToRace = runCatching {
                    java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(planSettings.race)).toInt()
                }.getOrNull()
                val raceTag = when {
                    daysToRace == null -> "TANGGAL"
                    daysToRace < 0 -> "SELESAI"
                    daysToRace == 0 -> "HARI INI"
                    else -> "H-$daysToRace"
                }
                InfoCard("Target race", "${planSettings.raceDist} km · ${planSettings.raceGoal.ifBlank { "waktu belum ditentukan" }} · ${planSettings.race}", raceTag)
            }
            WeeklyReviewCard(weekReview)
            SectionTitle("Usulan jadwal minggu depan", "ADAPTIF")
            weekReview.nextWeekSessions.forEach { suggested ->
                val dayName = suggested.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id", "ID"))
                val km = if (suggested.targetKm > 0) " · %.1f km".format(Locale.US, suggested.targetKm) else ""
                InfoCard("$dayName · ${suggested.date.dayOfMonth}/${suggested.date.monthValue}", suggested.title + km,
                    if (suggested.title.startsWith("Easy ringan")) "DIUBAH COACH" else "DIPERTAHANKAN")
            }
            SectionTitle("Rincian per hari", "TARGET · AKTUAL")
            weekReview.days.forEach { day ->
                val dayName = day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id", "ID"))
                val target = if (day.targetKm > 0) "target %.1f km".format(Locale.US, day.targetKm) else "target sesi"
                val actual = if (day.actualKm > 0) "aktual %.1f km".format(Locale.US, day.actualKm) else "belum ada lari"
                val dayStrength = strength.filter { it.date == day.date.toString() && it.completed }
                val extra = if (dayStrength.isNotEmpty()) " · strength: " + dayStrength.joinToString { strengthLabel(it.category) } else ""
                InfoCard("$dayName · ${day.date.dayOfMonth}/${day.date.monthValue}", "${day.title} · $target · $actual$extra", day.status)
                if (!day.date.isBefore(today)) {
                    val advice = TrainingEngine.advisePlannedSession(day.title, day.date, activities, strength, summary)
                    if (advice != null) InfoCard("Catatan Coach · $dayName", advice.detail, advice.status)
                }
            }
            SectionTitle("Strength per hari", "RENCANA · AKTUAL")
            (0L..6L).forEach { offset ->
                val date = monday.plusDays(offset)
                val planKey = if (offset == 6L) 0 else (offset + 1).toInt()
                val plannedTitle = planSettings.plan[planKey]?.t.orEmpty()
                val isStrengthPlanned = listOf("strength", "gym", "kekuatan", "beban").any { plannedTitle.contains(it, ignoreCase = true) }
                val dayRecords = strength.filter { it.date == date.toString() }
                val completedRecords = dayRecords.filter { it.completed }
                if (isStrengthPlanned || dayRecords.isNotEmpty()) {
                    val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id", "ID"))
                    val plannedText = if (isStrengthPlanned) "Target: $plannedTitle" else "Strength tambahan"
                    val actualText = when {
                        completedRecords.isNotEmpty() -> completedRecords.joinToString { record ->
                            strengthLabel(record.category) + if (record.exercise.isNotBlank()) " · ${record.exercise} ${record.sets}×${record.reps}" else ""
                        }
                        dayRecords.isNotEmpty() -> "Ada catatan, tetapi belum ditandai selesai"
                        else -> "Belum ada catatan strength"
                    }
                    InfoCard("$dayName · ${date.dayOfMonth}/${date.monthValue}", "$plannedText · Aktual: $actualText",
                        if (completedRecords.isNotEmpty()) "TERLAKSANA" else if (isStrengthPlanned) "BELUM TERCATAT" else "BELUM SELESAI")
                }
            }
            Text("Coach membaca sesi lari yang tersimpan dan strength yang ditandai selesai. Kilometer yang terlewat tidak dipindahkan otomatis; saran minggu depan mempertimbangkan konsistensi, ACWR, readiness, dan beban kaki.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        SectionTitle("Alasan rekomendasi", "TRANSPARAN")
        summary.rationale.forEachIndexed { index, reason -> InfoCard("Sinyal ${index + 1}", reason, "DATA") }
        Text("Readiness adalah heuristik latihan, bukan diagnosis medis atau prediksi cedera. Nilai dapat belum tersedia jika data pemulihan dan beban belum cukup.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun SettingsScreen(
    profile: AthleteProfile, wellness: List<WellnessRecord>, modifier: Modifier,
    strengthTarget: Int, parqAnswers: List<Boolean?>, onStrengthTarget: () -> Unit, onParq: () -> Unit,
    hasIntervalsKey: Boolean, syncBusy: Boolean, syncMessage: String,
    onProfile: () -> Unit, onWellness: () -> Unit, onPlanRace: () -> Unit, onApiKey: () -> Unit, onSync: () -> Unit
) {
    Page(modifier) {
        PageHero("ATHLETE SETTINGS", "Your", "profile.", "Atur data tubuh, target, jadwal latihan, dan koneksi data.")
        InfoCard("Profil detak jantung", "RHR ${profile.restingHr} · HRmax ${profile.maxHr} · LTHR ${profile.thresholdHr}", "EDIT")
        Button(onClick = onProfile, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Atur profil") }
        SectionTitle("Rencana dan target race", "TERSIMPAN LOKAL")
        OutlinedButton(onClick = onPlanRace, modifier = Modifier.fillMaxWidth()) { Text("Atur rencana mingguan & race") }
        SectionTitle("Sinkronisasi Intervals.icu", if (hasIntervalsKey) "TERHUBUNG" else "BELUM TERHUBUNG")
        Text(if (hasIntervalsKey) "API key tersimpan secara lokal. Sinkronkan aktivitas dan wellness dari akun Intervals.icu." else "Masukkan API key Intervals.icu untuk mengambil riwayat aktivitas dan wellness.")
        Button(onClick = onApiKey, colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Lime)) { Text(if (hasIntervalsKey) "Ganti API key" else "Masukkan API key") }
        OutlinedButton(onClick = onSync, enabled = hasIntervalsKey && !syncBusy) { Text(if (syncBusy) "Menyinkronkan…" else "Sinkronkan sekarang") }
        if (syncMessage.isNotBlank()) Text(syncMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        SectionTitle("Strength", "TARGET MINGGUAN")
        InfoCard("Target strength", "$strengthTarget hari per minggu", "COACH")
        OutlinedButton(onClick = onStrengthTarget, modifier = Modifier.fillMaxWidth()) { Text("Atur target strength mingguan") }
        SectionTitle("Skrining aktivitas", "PAR-Q · 7 PERTANYAAN")
        val yesCount = parqAnswers.count { it == true }
        val answered = parqAnswers.count { it != null }
        InfoCard("Status PAR-Q", if (answered < 7) "$answered/7 pertanyaan terisi" else if (yesCount > 0) "$yesCount jawaban YA — perlu tindak lanjut" else "Semua jawaban TIDAK", if (yesCount > 0) "PERHATIAN" else "SKRINING")
        OutlinedButton(onClick = onParq, modifier = Modifier.fillMaxWidth()) { Text("Isi / perbarui PAR-Q") }
        if (yesCount > 0) Text("Ada jawaban YA. Konsultasikan dengan tenaga kesehatan sebelum memulai atau meningkatkan latihan.", color = Color(0xFFFFC36A), fontSize = 11.sp, lineHeight = 16.sp)
        SectionTitle("Wellness", "${wellness.size} HARI TERCATAT")
        wellness.takeLast(7).reversed().forEach { InfoCard(it.date, "RHR ${it.restingHr} bpm · tidur ${it.sleepHours} jam · HRV ${if (it.hrv > 0) it.hrv else 0.0}", "TERSIMPAN") }
        OutlinedButton(onClick = onWellness) { Text("＋ Catat wellness hari ini") }
        Text("Data aktivitas dan wellness dari Huawei serta Intervals digabung ke riwayat lokal. API key disimpan di penyimpanan privat aplikasi.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun HomePageHero() {
    val hour = java.time.LocalTime.now().hour
    val greeting = when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..17 -> "Selamat sore"
        else -> "Selamat malam"
    }
    val date = SimpleDateFormat("EEEE, dd MMMM", Locale("id", "ID")).format(Date())
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR DAILY RUNNING BRIEF", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Surface(color = Lime, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("R", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy((-3).dp)) {
            Text("Run with", color = White, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.4).sp)
            Text("your rhythm.", color = Lime, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.4).sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(greeting, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("·", color = Muted, fontSize = 12.sp)
            Text(date, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PageHero(eyebrow: String, title: String, accent: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.45.sp)
        Column(verticalArrangement = Arrangement.spacedBy((-3).dp)) {
            Text(title, color = White, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp)
            Text(accent, color = Lime, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp)
        }
        Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SectionKicker(label: String, index: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.35.sp)
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = .10f)))
        Text(index, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CoachRecommendationCard(recommendation: com.runnow.domain.Recommendation?) {
    if (recommendation == null) return
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("REKOMENDASI COACH · ${recommendation.tag}", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(recommendation.title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(listOf(recommendation.duration, recommendation.hr, recommendation.pace).filter { it.isNotBlank() && it != "—" }.joinToString(" · "), color = Lime, fontSize = 11.sp)
            Text(recommendation.reason, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            recommendation.steps.forEach { step ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(step.first, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(step.second, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(score: Int?, recommendation: String) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("READINESS HARI INI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(if (score == null) "DATA TERBATAS" else if (score < 45) "PEMULIHAN" else if (score < 65) "KONTROL" else "SIAP TERPANTAU",
                    color = if (score != null && score < 45) Color(0xFFFFB65C) else Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(82.dp)) {
                    CircularProgressIndicator(progress = ((score ?: 0).coerceIn(0, 100) / 100f), modifier = Modifier.fillMaxSize(),
                        color = if (score != null && score < 45) Color(0xFFFFB65C) else Lime, trackColor = Color.White.copy(alpha = .10f), strokeWidth = 7.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(score?.toString() ?: "—", color = White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                        Text("/100", color = Muted, fontSize = 9.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (score == null) "Menunggu data" else if (score < 45) "Fokus pulih" else if (score < 65) "Jaga intensitas" else "Ikuti rencana dengan kontrol effort",
                        color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(recommendation, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(action, color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CheckinActivityCard(activity: ActivityRecord, onCheckin: (ActivityRecord) -> Unit) {
    val stamp = SimpleDateFormat("dd MMM · HH:mm", Locale("id", "ID")).format(Date(activity.startedAt))
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Lari · $stamp", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${activity.durationMin} menit · ${"%.2f".format(Locale.US, activity.distanceKm)} km · HR ${if (activity.averageHr > 0) activity.averageHr else "—"}", color = Muted, fontSize = 11.sp)
                }
                Text(if (activity.quality) "QUALITY" else "RUN", color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            if (activity.rpe > 0 || activity.feel.isNotBlank() || activity.pain > 0) {
                Text("RPE ${activity.rpe}/10 · ${activity.feel.ifBlank { "feel belum diisi" }} · nyeri ${activity.pain}/10", color = Muted, fontSize = 10.sp)
            }
            OutlinedButton(onClick = { onCheckin(activity) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (activity.rpe > 0 || activity.feel.isNotBlank() || activity.pain > 0) "Ubah check-in" else "Isi check-in RPE / nyeri")
            }
        }
    }
}

@Composable
private fun ActivityCard(a: ActivityRecord) {
    val stamp = SimpleDateFormat("dd MMM · HH:mm", Locale("id", "ID")).format(Date(a.startedAt))
    InfoCard("${a.type.replaceFirstChar { it.uppercase() }} · $stamp",
        "${a.durationMin} menit · ${"%.2f".format(Locale.US, a.distanceKm)} km · HR ${if (a.averageHr > 0) a.averageHr else "—"}",
        if (a.quality) "QUALITY" else "LOAD ${TrainingEngine.activityLoad(a, AthleteProfile()).roundToInt()}")
}

@Composable
private fun InfoCard(title: String, detail: String, tag: String) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 11.sp)
            }
            Text(tag, color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ActivityDialog(onDismiss: () -> Unit, onSave: (String, Double, Double, Int, Double, Boolean, Int, String, Int) -> Unit) {
    var type by remember { mutableStateOf("run") }
    var duration by remember { mutableStateOf("30") }
    var distance by remember { mutableStateOf("3") }
    var hr by remember { mutableStateOf("") }
    var load by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(false) }
    var rpe by remember { mutableFloatStateOf(0f) }
    var feel by remember { mutableStateOf("") }
    var pain by remember { mutableFloatStateOf(0f) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Catat aktivitas") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == "run", { type = "run" }, label = { Text("Lari") })
                FilterChip(type == "walk", { type = "walk" }, label = { Text("Jalan") })
            }
            NumberField("Durasi (menit)", duration) { duration = it }
            NumberField("Jarak (km)", distance) { distance = it }
            NumberField("Rata-rata HR (opsional)", hr) { hr = it }
            NumberField("Training load eksternal (opsional)", load) { load = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(quality, { quality = it }); Text("Sesi quality / intensitas") }
            if (type == "run") {
                Divider(color = Color.White.copy(alpha = .12f))
                Text("Check-in setelah lari", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("RPE · seberapa berat sesi terasa?  ${rpe.roundToInt()}/10", color = White, fontSize = 11.sp)
                Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 0f..10f, steps = 9)
                Text("Bagaimana rasanya dibanding perkiraan?", color = White, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("lebih ringan", "sesuai").forEach { item ->
                        FilterChip(selected = feel == item, onClick = { feel = item }, label = { Text(item, fontSize = 9.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("lebih berat", "sangat berat").forEach { item ->
                        FilterChip(selected = feel == item, onClick = { feel = item }, label = { Text(item, fontSize = 9.sp) })
                    }
                }
                Text("Nyeri saat/ setelah lari: ${pain.roundToInt()}/10", color = White, fontSize = 11.sp)
                Slider(value = pain, onValueChange = { pain = it }, valueRange = 0f..10f, steps = 9)
                Text("0 = tidak nyeri · 10 = nyeri sangat berat. Hentikan latihan bila nyeri mengganggu gerak.", color = Muted, fontSize = 10.sp)
            }
        } },
        confirmButton = { TextButton(onClick = {
            val d = duration.toDoubleOrNull() ?: 0.0
            val km = distance.toDoubleOrNull() ?: 0.0
            if (d > 0 && (type == "walk" || km > 0)) onSave(type, d, km, hr.toIntOrNull() ?: 0, load.toDoubleOrNull() ?: 0.0, quality, if (type == "run") rpe.roundToInt() else 0, if (type == "run") feel else "", if (type == "run") pain.roundToInt() else 0)
        }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun SessionCheckinDialog(
    activity: ActivityRecord,
    onDismiss: () -> Unit,
    onSave: (Int, String, Int) -> Unit,
) {
    var rpe by remember(activity.id) { mutableFloatStateOf(activity.rpe.toFloat()) }
    var feel by remember(activity.id) { mutableStateOf(activity.feel) }
    var pain by remember(activity.id) { mutableFloatStateOf(activity.pain.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check-in setelah lari") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seberapa berat sesi terasa? RPE ${rpe.roundToInt()}/10", color = White, fontSize = 12.sp)
                Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 0f..10f, steps = 9)
                Text("Dibanding perkiraan awal", color = White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("lebih ringan", "sesuai").forEach { item ->
                        FilterChip(selected = feel == item, onClick = { feel = item }, label = { Text(item, fontSize = 9.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("lebih berat", "sangat berat").forEach { item ->
                        FilterChip(selected = feel == item, onClick = { feel = item }, label = { Text(item, fontSize = 9.sp) })
                    }
                }
                Text("Nyeri saat / setelah lari: ${pain.roundToInt()}/10", color = White, fontSize = 12.sp)
                Slider(value = pain, onValueChange = { pain = it }, valueRange = 0f..10f, steps = 9)
                Text("Jika nyeri mengubah langkah atau bertambah saat berlari, jangan memaksakan sesi berikutnya.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rpe.roundToInt(), feel, pain.roundToInt()) }) { Text("Simpan check-in") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun StrengthDialog(onDismiss: () -> Unit, onSave: (String, Int, Int, Boolean, String, Int, Int, Double) -> Unit) {
    var category by remember { mutableStateOf("leg") }
    var exercise by remember { mutableStateOf("Squat") }
    var duration by remember { mutableStateOf("20") }
    var level by remember { mutableStateOf("1") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var load by remember { mutableStateOf("0") }
    val exercises = when (category) {
        "leg" -> listOf("Squat", "Lunge", "Step-up", "Wall sit")
        "hip" -> listOf("Glute bridge", "Clamshell", "Hip thrust", "Single-leg bridge")
        "calf" -> listOf("Calf raise", "Single-leg calf raise", "Tibialis raise")
        "core" -> listOf("Plank", "Side plank", "Dead bug", "Bird dog")
        else -> listOf("A-skip", "High knees", "Butt kicks", "Drill teknik")
    }
    if (exercise !in exercises) exercise = exercises.first()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Catat strength") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kelompok otot", color = Muted)
            listOf("leg" to "Kaki", "hip" to "Pinggul", "calf" to "Betis", "core" to "Core", "drill" to "Drill").forEach { (key, label) ->
                FilterChip(category == key, { category = key }, label = { Text(label) })
            }
            Text("Gerakan", color = Muted)
            exercises.forEach { name -> FilterChip(exercise == name, { exercise = name }, label = { Text(name) }) }
            NumberField("Durasi (menit)", duration) { duration = it }
            NumberField("Set", sets) { sets = it }
            NumberField("Repetisi per set", reps) { reps = it }
            NumberField("Beban eksternal (kg, opsional)", load) { load = it }
            NumberField("Level effort 0–4", level) { level = it }
        } },
        confirmButton = { TextButton(onClick = { onSave(category, (duration.toIntOrNull() ?: 0).coerceAtLeast(0), (level.toIntOrNull() ?: 0).coerceIn(0,4), true, exercise, (sets.toIntOrNull() ?: 0).coerceAtLeast(0), (reps.toIntOrNull() ?: 0).coerceAtLeast(0), (load.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) }) { Text("Simpan sesi") } },
        dismissButton = { TextButton(onClick = { onSave(category, (duration.toIntOrNull() ?: 0), (level.toIntOrNull() ?: 0).coerceIn(0,4), false, exercise, 0, 0, 0.0) }) { Text("Skip") } })
}

@Composable
private fun StrengthTargetDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var days by remember(current) { mutableIntStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Target strength mingguan") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$days hari per minggu", color = Lime, fontWeight = FontWeight.Bold)
            Slider(value = days.toFloat(), onValueChange = { days = it.roundToInt() }, valueRange = 0f..7f, steps = 6)
        } },
        confirmButton = { TextButton(onClick = { onSave(days) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun ParqDialog(current: List<Boolean?>, onDismiss: () -> Unit, onSave: (List<Boolean?>) -> Unit) {
    val questions = listOf(
        "Dokter pernah menyatakan Anda memiliki kondisi jantung dan hanya boleh berolahraga sesuai rekomendasi tenaga kesehatan?",
        "Apakah Anda merasakan nyeri dada saat melakukan aktivitas fisik?",
        "Dalam sebulan terakhir, pernahkah nyeri dada muncul saat tidak beraktivitas fisik?",
        "Pernahkah Anda kehilangan keseimbangan karena pusing atau kehilangan kesadaran?",
        "Apakah Anda memiliki masalah tulang atau sendi yang dapat memburuk akibat perubahan aktivitas fisik?",
        "Apakah dokter saat ini meresepkan obat untuk tekanan darah atau kondisi jantung?",
        "Adakah alasan kesehatan lain yang membuat aktivitas fisik tidak aman bagi Anda?"
    )
    val answers = remember(current) { mutableStateListOf<Boolean?>().apply { addAll(current) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("PAR-Q · skrining awal") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Skrining awal, bukan diagnosis atau izin medis. Jawab semua pertanyaan.", color = Muted, fontSize = 11.sp)
            questions.forEachIndexed { index, question ->
                Text("${index + 1}. $question", color = White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(answers[index] == true, { answers[index] = true }, label = { Text("YA") })
                    FilterChip(answers[index] == false, { answers[index] = false }, label = { Text("TIDAK") })
                }
            }
            if (answers.any { it == true }) Text("Ada jawaban YA. Konsultasikan dengan tenaga kesehatan sebelum memulai atau meningkatkan latihan. Nyeri dada akut, pingsan, atau sesak berat memerlukan pertolongan segera.", color = Color(0xFFFFC36A), fontSize = 11.sp)
        } },
        confirmButton = { TextButton(onClick = { onSave(answers.toList()) }, enabled = answers.all { it != null }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun PlanRaceDialog(
    current: com.runnow.domain.Settings,
    onDismiss: () -> Unit,
    onSave: (com.runnow.domain.Settings) -> Unit,
) {
    val dayKeys = listOf(1, 2, 3, 4, 5, 6, 0)
    val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val titles = remember(current) {
        mutableStateListOf<String>().apply {
            addAll(dayKeys.map { current.plan[it]?.t ?: "" })
        }
    }
    val distances = remember(current) {
        mutableStateListOf<String>().apply {
            addAll(dayKeys.map { current.plan[it]?.km?.takeIf { km -> km > 0 }?.toString() ?: "" })
        }
    }
    var raceDate by remember(current) { mutableStateOf(current.race) }
    var raceDistance by remember(current) { mutableStateOf(if (current.raceDist > 0) current.raceDist.toString() else "") }
    var raceGoal by remember(current) { mutableStateOf(current.raceGoal) }
    var goal by remember(current) { mutableStateOf(current.goal) }
    var style by remember(current) { mutableStateOf(current.style) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rencana mingguan & race") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Rencana latihan", color = Lime, fontWeight = FontWeight.Bold)
                Text("Isi nama sesi dan target kilometer. Kosongkan hari istirahat.", color = Muted, fontSize = 11.sp)
                dayKeys.forEachIndexed { index, day ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(dayNames[index], color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = titles[index], onValueChange = { titles[index] = it },
                                modifier = Modifier.weight(1f), singleLine = true,
                                label = { Text("Sesi (Easy/Interval/Rest)") }
                            )
                            OutlinedTextField(
                                value = distances[index], onValueChange = { distances[index] = it },
                                modifier = Modifier.width(92.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                label = { Text("Km") }
                            )
                        }
                    }
                }
                Divider(color = Color.White.copy(alpha = .12f))
                Text("Target race", color = Lime, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = raceDate, onValueChange = { raceDate = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Tanggal (YYYY-MM-DD)") }
                )
                OutlinedTextField(
                    value = raceDistance, onValueChange = { raceDistance = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Jarak race (km)") }
                )
                OutlinedTextField(
                    value = raceGoal, onValueChange = { raceGoal = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Target waktu (mm:ss atau h:mm:ss)") }
                )
                Text("Fokus latihan", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5K", "10K", "Half", "Marathon", "Umum").forEach { item ->
                        FilterChip(selected = goal == item, onClick = { goal = item }, label = { Text(item) })
                    }
                }
                Text("Gaya penyesuaian", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("conservative" to "Konservatif", "moderate" to "Moderat", "aggressive" to "Agresif").forEach { item ->
                        FilterChip(selected = style == item.first, onClick = { style = item.first }, label = { Text(item.second) })
                    }
                }
                Text("Coach menggunakan rencana ini untuk membandingkan target dengan aktivitas yang benar-benar tersimpan. Menyimpan tidak mengubah riwayat latihan.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val plan = LinkedHashMap<Int, com.runnow.domain.PlanDay>()
                dayKeys.forEachIndexed { index, day ->
                    val title = titles[index].trim()
                    if (title.isNotBlank()) {
                        plan[day] = com.runnow.domain.PlanDay(title, (distances[index].toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0))
                    }
                }
                val validRaceDate = raceDate.trim().takeIf {
                    it.isBlank() || runCatching { java.time.LocalDate.parse(it) }.isSuccess
                } ?: current.race
                onSave(current.copy(
                    style = style,
                    plan = plan,
                    race = validRaceDate,
                    raceDist = (raceDistance.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    raceGoal = raceGoal.trim(),
                    goal = goal,
                ))
            }) { Text("Simpan rencana") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun WellnessDialog(onDismiss: () -> Unit, onSave: (Int, Double, Double) -> Unit) {
    var rhr by remember { mutableStateOf("60") }
    var sleep by remember { mutableStateOf("7") }
    var hrv by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Wellness hari ini") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Resting HR (bpm)", rhr) { rhr = it }
            NumberField("Tidur (jam)", sleep) { sleep = it }
            NumberField("HRV (opsional)", hrv) { hrv = it }
        } },
        confirmButton = { TextButton(onClick = { onSave(rhr.toIntOrNull() ?: 0, sleep.toDoubleOrNull() ?: 0.0, hrv.toDoubleOrNull() ?: 0.0) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun ProfileDialog(profile: AthleteProfile, onDismiss: () -> Unit, onSave: (AthleteProfile) -> Unit) {
    var rhr by remember { mutableStateOf(profile.restingHr.toString()) }
    var max by remember { mutableStateOf(profile.maxHr.toString()) }
    var lt by remember { mutableStateOf(profile.thresholdHr.toString()) }
    var pace by remember { mutableStateOf(profile.thresholdPaceSecPerKm.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Profil fisiologis") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("RHR (bpm)", rhr) { rhr = it }
            NumberField("HRmax (bpm)", max) { max = it }
            NumberField("LTHR (bpm)", lt) { lt = it }
            NumberField("LT pace (detik/km)", pace) { pace = it }
        } },
        confirmButton = { TextButton(onClick = { onSave(AthleteProfile(rhr.toIntOrNull() ?: 60, max.toIntOrNull() ?: 185, lt.toIntOrNull() ?: 160, pace.toIntOrNull() ?: 390)) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API key Intervals.icu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Masukkan API key pribadi dari pengaturan akun Intervals.icu. Kunci disimpan di penyimpanan privat aplikasi.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("API key") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (key.isNotBlank()) onSave(key.trim()) }) { Text("Simpan & sinkron") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

private fun strengthLabel(value: String) = when(value) { "hip" -> "Pinggul"; "calf" -> "Betis"; "leg" -> "Kaki"; "core" -> "Core"; "drill" -> "Drill"; else -> "Strength" }
private fun fmt(value: Double) = value.roundToInt().toString()
private fun pace(minutesPerKm: Double): String {
    val seconds = (minutesPerKm * 60).roundToInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}/km"
}
