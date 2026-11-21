package id.kamui.runsense.data

import android.content.Context
import com.runnow.data.Clock
import com.runnow.data.Codec
import com.runnow.data.snapshot
import com.runnow.domain.Snapshot
import com.runnow.data.HttpClient
import com.runnow.data.HttpResponse
import com.runnow.data.IcuHttp
import com.runnow.data.KeyValueStore
import com.runnow.data.RunSenseRepository as DomainRepository
import com.runnow.data.WeatherApi
import com.runnow.domain.GeoLoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId

/** Android adapters for the original RunSense data module. */
class NativeIntervalsRuntime(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("runsense_native_v1", Context.MODE_PRIVATE)
    private val local = RunSenseRepository(app)
    private lateinit var domain: DomainRepository

    init {
        val store = object : KeyValueStore {
            override fun read(key: String): String? = prefs.getString(key, null)
            override fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
            override fun remove(key: String) { prefs.edit().remove(key).apply() }
        }
        val http = object : HttpClient {
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
                withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 30_000
                        connection.useCaches = false
                        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                        val status = connection.responseCode
                        val stream = if (status in 200..399) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        HttpResponse(status, body)
                    } finally {
                        connection.disconnect()
                    }
                }
        }
        val weather = object : WeatherApi {
            override suspend fun forecast(lat: Double, lon: Double) = null
            override suspend fun archive(lat: Double, lon: Double, day: String) = null
            override suspend fun geocode(name: String): GeoLoc? = null
            override suspend fun currentTemp(lat: Double, lon: Double): Double? = null
        }
        val icu = IcuHttp(http) { domain.apiKey }
        domain = DomainRepository(store, icu, weather, null, null, Clock { System.currentTimeMillis() }, ZoneId.systemDefault())
        domain.restore()
    }

    fun hasApiKey(): Boolean = !domain.apiKey.isNullOrBlank()

    fun snapshot(): Snapshot = domain.data.snapshot(System.currentTimeMillis(), ZoneId.systemDefault())

    fun ingestWatchJson(json: String) {
        val parsed = Codec.parseWatchImport(json) ?: return
        domain.ingestWatch(parsed)
    }

    suspend fun sync(apiKey: String? = null): String {
        if (!apiKey.isNullOrBlank()) domain.login(apiKey.trim())
        val result = domain.sync(silent = false)
        val imported = local.importDomainRecords(domain.data.acts, domain.data.wellness)
        return buildString {
            append(result.message.ifBlank { if (result.ok) "Sinkron selesai" else "Belum ada hasil sinkron" })
            if (result.ok) append(" · ${imported.first} aktivitas baru · ${imported.second} hari wellness diperbarui")
        }
    }
}
