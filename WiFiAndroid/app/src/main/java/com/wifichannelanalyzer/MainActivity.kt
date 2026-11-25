package com.wifichannelanalyzer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifi: WifiManager
    private lateinit var scanButton: Button
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showResults(readScan())
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startScan() else status.text =
                "Location permission is required by Android to see nearby Wi-Fi networks. " +
                    "This app never stores or sends your location."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        scanButton = findViewById(R.id.scanButton)
        status = findViewById(R.id.status)
        results = findViewById(R.id.results)
        scanButton.setOnClickListener { onScanClicked() }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(scanReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun onScanClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startScan() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Suppress("DEPRECATION")
    private fun startScan() {
        if (!wifi.isWifiEnabled) {
            status.text = "Wi-Fi is turned off. Turn it on and scan again."
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            status.text = "Android needs Location switched on to list Wi-Fi networks. " +
                "Turn it on in quick settings and scan again."
            return
        }
        status.text = "Scanning nearby access points..."
        if (!wifi.startScan()) {
            // Android limits how often apps may scan; fall back to the latest cached results.
            status.text = "Android limited the scan rate, showing the most recent results. " +
                "Wait a minute and try again for a fresh scan."
            showResults(readScan())
        }
    }

    @Suppress("DEPRECATION")
    private fun readScan(): List<AccessPoint> {
        val raw: List<ScanResult> = try {
            wifi.scanResults
        } catch (e: SecurityException) {
            emptyList()
        }
        return raw.mapNotNull { r ->
            val ch = ChannelAnalyzer.channelFromFrequency(r.frequency) ?: return@mapNotNull null
            AccessPoint(
                ssid = r.SSID.ifBlank { "(hidden)" },
                bssid = r.BSSID ?: "",
                signalPct = ChannelAnalyzer.dbmToPercent(r.level),
                channel = ch,
                frequencyMhz = r.frequency
            )
        }
    }

    private fun showResults(aps: List<AccessPoint>) {
        results.removeAllViews()
        if (aps.isEmpty()) {
            status.text = "No networks found. Make sure Wi-Fi and Location are on, then scan again."
            return
        }
        status.text = "Found ${aps.size} access points."

        val r24 = ChannelAnalyzer.rank24(aps)
        val has24 = aps.any(ChannelAnalyzer::is24)
        addHeader("2.4 GHz")
        if (has24) {
            addBig("Best channel: ${r24[0].channel}")
            addText("Ranking (lower score = less crowded):")
            r24.forEach { addText("  Channel ${it.channel}   score ${it.score}") }
            addText("Tip: use a 20 MHz channel width on 2.4 GHz.")
        } else {
            addText("No 2.4 GHz networks detected.")
        }

        val r5 = ChannelAnalyzer.rank5(aps)
        val has5 = aps.any(ChannelAnalyzer::is5)
        addHeader("5 GHz")
        if (has5) {
            addBig("Best channel: ${r5[0].channel}")
            addText("Top 5 (lower score = less crowded):")
            r5.take(5).forEach {
                addText("  Channel ${it.channel}   score ${it.score}" + if (it.dfs) "   (DFS)" else "")
            }
            addText("Check that your router supports the channel and that it is allowed in your country. " +
                "DFS channels may be paused if radar is detected.")
        } else {
            addText("No 5 GHz networks detected (or this phone can't scan 5 GHz).")
        }

        addHeader("Detected networks")
        aps.sortedWith(compareBy({ it.channel }, { -it.signalPct })).forEach {
            addText("Ch ${it.channel}  |  ${it.signalPct}%  |  ${it.ssid}")
        }

        addText(
            "\nThis is an estimate from visible Wi-Fi networks and their signal strength. " +
                "It can't see non-Wi-Fi interference, hidden traffic or real airtime use. " +
                "Scan from the spot where your router is, and re-scan after changing its channel.",
            italic = true
        )
    }

    private fun addHeader(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(0, dp(20), 0, dp(4))
        results.addView(tv)
    }

    private fun addBig(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(0xFF2E7D32.toInt())
        tv.setPadding(0, dp(4), 0, dp(4))
        results.addView(tv)
    }

    private fun addText(text: String, italic: Boolean = false) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        if (italic) tv.setTypeface(null, Typeface.ITALIC)
        tv.setPadding(0, dp(2), 0, dp(2))
        results.addView(tv)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
