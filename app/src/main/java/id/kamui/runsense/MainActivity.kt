package id.kamui.runsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        setContent { RunSenseNativeApp() }
    }
}

private enum class RunTab(val label: String, val mark: String) {
    HOME("Beranda", "⌂"), TRAINING("Latihan", "◷"), PROGRESS("Progress", "↗"),
    COACH("Coach", "✦"), SETTINGS("Setelan", "⚙")
}

@Composable
private fun RunSenseNativeApp() {
    var selected by remember { mutableStateOf(RunTab.HOME) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime, background = Ink, surface = Panel,
            onPrimary = Ink, onBackground = White, onSurface = White
        )
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111410), contentColor = White) {
                    RunTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = { Text(tab.mark, fontSize = 19.sp) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Lime, selectedTextColor = Lime,
                                indicatorColor = Lime.copy(alpha = .13f),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                RunTab.HOME -> HomeScreen(Modifier.padding(padding))
                RunTab.TRAINING -> SimpleScreen("Latihan", "Rencana dan riwayat latihan", "Belum ada sesi yang ditampilkan", Modifier.padding(padding))
                RunTab.PROGRESS -> SimpleScreen("Progress", "Perkembangan latihan", "Data progres akan muncul setelah data latihan dimasukkan", Modifier.padding(padding))
                RunTab.COACH -> SimpleScreen("RunSense Coach", "Keputusan latihan adaptif", "Coach membutuhkan data latihan, pemulihan, dan strength yang tersimpan", Modifier.padding(padding))
                RunTab.SETTINGS -> SimpleScreen("Pengaturan", "Profil, zona, dan preferensi", "Pengaturan profil dan zona latihan", Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("RunSense", color = Lime, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text("Latihan cerdas, bertahap, dan berkelanjutan", color = Muted, fontSize = 13.sp)
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RINGKASAN HARI INI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Belum ada data hari ini", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Sinkronkan atau masukkan aktivitas untuk melihat kondisi latihan.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { MetricRow() }
        item { SectionTitle("Menu utama", "Akses cepat") }
        item { FeatureCard("Rencana latihan", "Sesi berikutnya dan struktur interval", "LATIHAN") }
        item { FeatureCard("Analisis progres", "Jarak, pace, detak jantung, dan beban", "PROGRESS") }
        item { FeatureCard("Adaptive Coach", "Rekomendasi berdasarkan beban dan pemulihan", "COACH") }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("PRINSIP RUNSENSE", color = Lime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Konsistensi lebih penting daripada memaksakan intensitas. Beban latihan harus berkembang sesuai kesiapan tubuh.", color = White, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("JARAK", "RATA-RATA HR", "READINESS").forEach { label ->
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("—", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Belum ada data", color = Muted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(action, color = Lime, fontSize = 11.sp)
    }
}

@Composable
private fun FeatureCard(title: String, description: String, tag: String) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Lime.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                Text("›", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 11.sp)
            }
            Text(tag, color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SimpleScreen(title: String, subtitle: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Lime, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Text(message, color = White, fontSize = 14.sp, modifier = Modifier.padding(18.dp))
        }
    }
}
