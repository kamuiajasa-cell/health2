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

private val Ink = Color(0xFF090B09)
private val Panel = Color(0xFF1E211D)
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
    var showWellness by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showPlanRace by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val profile = remember(revision) { repository.profile() }
    val planSettings = remember(revision) { repository.planSettings() }
    val activities = remember(revision) { repository.activities() }
    val strength = remember(revision) { repository.strength() }
    val wellness = remember(revision) { repository.wellness() }
    val summary = remember(revision) { TrainingEngine.summarize(activities, strength, wellness, profile) }
    val baseSnapshot = remember(revision) { runCatching { intervals.snapshot() }.getOrNull() }
    val domainResult = remember(revision) { runCatching { nativeRunSenseAnalysis(activities, strength, wellness, profile, baseSnapshot, planSettings) }.getOrNull() }
    fun changed() { revision++ }

    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, background = Ink, surface = Panel, onPrimary = Ink, onBackground = White, onSurface = White)) {
        Scaffold(
            containerColor = Ink,
            topBar = { AppTopBar(selected) },
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
                RunTab.COACH -> CoachScreen(activities, summary, strength, domainResult, baseSnapshot, planSettings, Modifier.padding(padding))
                RunTab.WATCH -> WatchScreen(Modifier.padding(padding)) { json -> repository.importWatchJson(json); intervals.ingestWatchJson(json); changed() }
                RunTab.SETTINGS -> SettingsScreen(
                    profile, wellness, Modifier.padding(padding),
                    hasIntervalsKey = intervals.hasApiKey(), syncBusy = syncBusy, syncMessage = syncMessage,
                    onProfile = { showProfile = true }, onWellness = { showWellness = true }, onPlanRace = { showPlanRace = true },
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
        if (showActivity) ActivityDialog(onDismiss = { showActivity = false }, onSave = { type, duration, distance, hr, load, quality ->
            repository.addActivity(type, duration, distance, hr, load, quality); showActivity = false; changed()
        })
        if (showStrength) StrengthDialog(onDismiss = { showStrength = false }, onSave = { category, duration, level, done ->
            repository.addStrength(category, duration, level, done); showStrength = false; changed()
        })
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
private fun AppTopBar(selected: RunTab) {
    Surface(color = Ink, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Lime, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("R", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("RUNSENSE", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(selected.label, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(50)) {
                Text("NATIVE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun Page(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun HomeScreen(activities: List<ActivityRecord>, summary: TrainingSummary, domainResult: com.runnow.domain.ProcessResult?, modifier: Modifier, onAdd: () -> Unit, onStrength: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BrandHeader() }
        item { ReadinessCard(domainResult?.athlete?.score ?: summary.readiness, summary.recommendation) }
        item { CoachRecommendationCard(domainResult?.recommendation) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("BEBAN 7 HARI", fmt(summary.acuteLoad), Modifier.weight(1f))
            MetricCard("ACWR", summary.acwr?.let { "%.2f".format(Locale.US, it) } ?: "—", Modifier.weight(1f))
            MetricCard("STRENGTH 48J", "%.1f".format(Locale.US, summary.strengthLoad48h), Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("＋ Catat latihan") }
            OutlinedButton(onClick = onStrength, modifier = Modifier.weight(1f)) { Text("＋ Strength") }
        } }
        item { SectionTitle("Aktivitas terbaru", "TERSIMPAN DI PERANGKAT") }
        if (activities.isEmpty()) item { EmptyCard("Belum ada aktivitas. Tambahkan sesi pertama untuk mulai membangun analisis beban.") }
        items(activities.take(5), key = { it.id }) { ActivityCard(it) }
    }
}

@Composable
private fun TrainingScreen(activities: List<ActivityRecord>, strength: List<StrengthRecord>, modifier: Modifier, onAdd: () -> Unit, onStrength: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Latihan", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Catatan lari, jalan, dan strength", color = Muted, fontSize = 13.sp) }
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
            InfoCard(strengthLabel(s.category), "${s.date} · ${s.durationMin} menit · level ${s.level}", if (s.completed) "SELESAI" else "SKIP")
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
) {
    val now = System.currentTimeMillis()
    val last7 = activities.filter { now - it.startedAt in 0..604800000L }
    val last28 = activities.filter { now - it.startedAt in 0..2419200000L }
    val km7 = last7.sumOf { it.distanceKm }
    val mins7 = last7.sumOf { it.durationMin }
    val avgPace = if (km7 > 0) mins7 / km7 else 0.0
    Page(modifier) {
        Text("Progress", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Ringkasan berdasarkan aktivitas yang tersimpan", color = Muted, fontSize = 13.sp)
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
        SectionTitle("Review sesi", "MESIN RUNSENSE")
        val runs = domainResult?.activities?.filter { it.act.kind == com.runnow.domain.ActKind.RUN }?.take(5).orEmpty()
        if (runs.isEmpty()) EmptyCard("Review sesi akan muncul setelah aktivitas lari tersedia.")
        else runs.forEach { SessionReviewCard(it, snapshot, repository, onLoadStream) }
        SectionTitle("Aktivitas 28 hari", "TERBARU")
        if (last28.isEmpty()) EmptyCard("Data grafik dan tren akan terbentuk setelah aktivitas dicatat.")
        else last28.sortedByDescending { it.startedAt }.forEach { ActivityCard(it) }
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
    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
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
private fun CoachScreen(activities: List<ActivityRecord>, summary: TrainingSummary, strength: List<StrengthRecord>, domainResult: com.runnow.domain.ProcessResult?, snapshot: Snapshot?, planSettings: com.runnow.domain.Settings, modifier: Modifier) {
    Page(modifier) {
        Text("RunSense Coach", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Evaluasi beban, pemulihan, dan strength", color = Muted, fontSize = 13.sp)
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
        SectionTitle("Strength yang dibaca Coach", "${strength.size} CATATAN")
        if (strength.isEmpty()) {
            EmptyCard("Belum ada catatan strength. Setelah sesi disimpan sebagai selesai, bebannya masuk ke evaluasi Coach.")
        } else {
            strength.sortedByDescending { it.date }.take(5).forEach { item ->
                InfoCard(
                    strengthLabel(item.category),
                    "${item.date} · ${item.durationMin} menit · level ${item.level}",
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
            val today = java.time.LocalDate.parse(snapshot.ymd())
            val monday = today.minusDays((today.dayOfWeek.value + 6).toLong() % 7L)
            val plannedSessions = PLAN_DAYS.mapNotNull { day ->
                planSettings.plan[day]?.let { p ->
                    PlannedSession(monday.plusDays(if (day == 0) 6 else (day - 1).toLong()), p.t, p.km)
                }
            }
            val weekReview = WeeklyPlanReviewer.evaluate(today, plannedSessions, activities, strength, summary)
            if (planSettings.race.isNotBlank()) {
                val phase = snapshot?.copy(settings = planSettings)?.racePhase()
                InfoCard("Target race", "${planSettings.raceDist} km · ${planSettings.raceGoal.ifBlank { "waktu belum ditentukan" }}", phase?.let { "${it.k.uppercase(Locale.ROOT)} · ${it.days} HARI" } ?: "TANGGAL RACE")
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
    hasIntervalsKey: Boolean, syncBusy: Boolean, syncMessage: String,
    onProfile: () -> Unit, onWellness: () -> Unit, onPlanRace: () -> Unit, onApiKey: () -> Unit, onSync: () -> Unit
) {
    Page(modifier) {
        Text("Pengaturan", color = Lime, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Profil fisiologis, sinkronisasi, dan pemulihan", color = Muted, fontSize = 13.sp)
        InfoCard("Profil detak jantung", "RHR ${profile.restingHr} · HRmax ${profile.maxHr} · LTHR ${profile.thresholdHr}", "EDIT")
        Button(onClick = onProfile, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Atur profil") }
        SectionTitle("Rencana dan target race", "TERSIMPAN LOKAL")
        OutlinedButton(onClick = onPlanRace, modifier = Modifier.fillMaxWidth()) { Text("Atur rencana mingguan & race") }
        SectionTitle("Sinkronisasi Intervals.icu", if (hasIntervalsKey) "TERHUBUNG" else "BELUM TERHUBUNG")
        Text(if (hasIntervalsKey) "API key tersimpan secara lokal. Sinkronkan aktivitas dan wellness dari akun Intervals.icu." else "Masukkan API key Intervals.icu untuk mengambil riwayat aktivitas dan wellness.")
        Button(onClick = onApiKey, colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Lime)) { Text(if (hasIntervalsKey) "Ganti API key" else "Masukkan API key") }
        OutlinedButton(onClick = onSync, enabled = hasIntervalsKey && !syncBusy) { Text(if (syncBusy) "Menyinkronkan…" else "Sinkronkan sekarang") }
        if (syncMessage.isNotBlank()) Text(syncMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        SectionTitle("Wellness", "${wellness.size} HARI TERCATAT")
        wellness.takeLast(7).reversed().forEach { InfoCard(it.date, "RHR ${it.restingHr} bpm · tidur ${it.sleepHours} jam · HRV ${if (it.hrv > 0) it.hrv else 0.0}", "TERSIMPAN") }
        OutlinedButton(onClick = onWellness) { Text("＋ Catat wellness hari ini") }
        Text("Data aktivitas dan wellness dari Huawei serta Intervals digabung ke riwayat lokal. API key disimpan di penyimpanan privat aplikasi.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun BrandHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("RunSense", color = Lime, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Text("Latihan cerdas, bertahap, dan berkelanjutan", color = Muted, fontSize = 13.sp)
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
private fun ActivityDialog(onDismiss: () -> Unit, onSave: (String, Double, Double, Int, Double, Boolean) -> Unit) {
    var type by remember { mutableStateOf("run") }
    var duration by remember { mutableStateOf("30") }
    var distance by remember { mutableStateOf("3") }
    var hr by remember { mutableStateOf("") }
    var load by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(false) }
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
        } },
        confirmButton = { TextButton(onClick = {
            val d = duration.toDoubleOrNull() ?: 0.0
            val km = distance.toDoubleOrNull() ?: 0.0
            if (d > 0 && (type == "walk" || km > 0)) onSave(type, d, km, hr.toIntOrNull() ?: 0, load.toDoubleOrNull() ?: 0.0, quality)
        }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun StrengthDialog(onDismiss: () -> Unit, onSave: (String, Int, Int, Boolean) -> Unit) {
    var category by remember { mutableStateOf("leg") }
    var duration by remember { mutableStateOf("20") }
    var level by remember { mutableStateOf("1") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Catat strength") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kelompok otot", color = Muted)
            listOf("leg" to "Kaki", "hip" to "Pinggul", "calf" to "Betis", "core" to "Core", "drill" to "Drill").forEach { (key, label) ->
                FilterChip(category == key, { category = key }, label = { Text(label) })
            }
            NumberField("Durasi (menit)", duration) { duration = it }
            NumberField("Level 0–4", level) { level = it }
        } },
        confirmButton = { TextButton(onClick = { onSave(category, (duration.toIntOrNull() ?: 0).coerceAtLeast(0), (level.toIntOrNull() ?: 0).coerceIn(0,4), true) }) { Text("Sudah") } },
        dismissButton = { TextButton(onClick = { onSave(category, (duration.toIntOrNull() ?: 0), (level.toIntOrNull() ?: 0).coerceIn(0,4), false) }) { Text("Skip") } })
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
