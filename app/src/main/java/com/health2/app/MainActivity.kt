package com.health2.app

import android.content.Intent
import android.webkit.WebView
import com.joyodigitama.huaweiwear.WatchDataSignal

/** Health2 memakai dashboard lokal dan hanya meminta data historis dari jam. */
class MainActivity : BridgeActivity() {
    override fun startUrl(): String = "https://$ORIGIN_HOST/index.html"

    override fun openRoute(route: String): Boolean {
        if (route != "/watch" && route != "/settings/koneksi") return false
        runOnUiThread { startActivity(Intent(this, PairingActivity::class.java)) }
        return true
    }

    override fun onWebCreated(web: WebView) {
        bridge.pushesWatchData = true
    }

    override fun onPageLoaded() {
        bridge.pushWatchData()
    }

    override fun onResume() {
        super.onResume()
        if (WatchDataSignal.take()) bridge.pushWatchData()
    }

    override fun wantsLiveHr(): Boolean = false
}
