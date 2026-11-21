package com.runnow.data

import com.runnow.domain.ActKind
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class WatchImportCodecTest {
    @Test fun watchImportPreservesRunStreamAndWellness() {
        val points = (0 until 45).joinToString(",") { i ->
            "[$i,150,2.8,12,160,-7.80,112.00]"
        }
        val json = """
            {
              "wellness":[{"id":"2026-11-21","restingHR":61,"hrv":48,"sleepSecs":25200,"steps":6200}],
              "workouts":[{
                "start":1795220000000,
                "kind":"run",
                "durSec":2400,
                "distM":5000,
                "avgHr":150,
                "maxHr":171,
                "elevUp":22,
                "name":"Huawei outdoor run",
                "iid":"hw-run-001",
                "pts":[$points]
              }]
            }
        """.trimIndent()

        val parsed = Codec.parseWatchImport(json)
        assertNotNull(parsed)
        val imported = Ingest.ingestWatch(
            AppData(), parsed!!, 1795220000000L, ZoneId.of("Asia/Jakarta")
        ) { false }

        assertEquals(1, imported.wellness)
        assertEquals(1, imported.workouts)
        assertEquals(ActKind.RUN, imported.data.acts.single().kind)
        assertEquals(5.0, imported.data.acts.single().km, 0.001)
        assertEquals(61.0, imported.data.wellness.single().restingHR, 0.001)
        assertEquals(48.0, imported.data.wellness.single().hrv, 0.001)
        assertTrue(imported.data.sx.containsKey("hw-run-001"))
        assertNotNull(imported.data.sx["hw-run-001"])
    }
}
