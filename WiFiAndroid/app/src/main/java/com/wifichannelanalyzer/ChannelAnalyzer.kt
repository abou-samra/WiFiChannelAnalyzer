package com.wifichannelanalyzer

import kotlin.math.abs
import kotlin.math.max

data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val signalPct: Int,   // 0-100, same idea as the Windows "Signal" percentage
    val channel: Int,
    val frequencyMhz: Int
)

data class ChannelScore(val channel: Int, val score: Double, val dfs: Boolean = false)

/**
 * Same scoring model as the original PowerShell script.
 * It is an estimate from visible networks, not an RF measurement.
 */
object ChannelAnalyzer {

    private val CANDIDATES_24 = listOf(1, 6, 11)
    private val CANDIDATES_5 = listOf(
        36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120,
        124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165
    )

    fun channelFromFrequency(mhz: Int): Int? = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 -> (mhz - 2407) / 5
        mhz in 5170..5895 -> (mhz - 5000) / 5
        else -> null // 6 GHz and anything unusual is ignored
    }

    /** Android reports dBm; convert to a 0-100 quality like Windows does. */
    fun dbmToPercent(dbm: Int): Int = (2 * (dbm + 100)).coerceIn(0, 100)

    fun is24(ap: AccessPoint) = ap.channel in 1..14
    fun is5(ap: AccessPoint) = ap.channel > 14

    private fun round3(x: Double) = Math.round(x * 1000.0) / 1000.0

    private fun score24(target: Int, nets: List<AccessPoint>): Double {
        var score = 0.0
        for (n in nets) {
            val distanceMhz = abs(target - n.channel) * 5
            val weight = max(0.0, 1.0 - distanceMhz / 25.0)
            score += (n.signalPct / 100.0) * weight
        }
        return round3(score)
    }

    private fun score5(target: Int, nets: List<AccessPoint>): Double {
        var score = 0.0
        for (n in nets) {
            val d = abs(target - n.channel)
            val weight = when {
                d == 0 -> 1.0
                d <= 4 -> 0.15
                else -> 0.0
            }
            score += (n.signalPct / 100.0) * weight
        }
        return round3(score)
    }

    fun rank24(all: List<AccessPoint>): List<ChannelScore> {
        val nets = all.filter(::is24)
        return CANDIDATES_24
            .map { ChannelScore(it, score24(it, nets)) }
            .sortedWith(compareBy({ it.score }, { it.channel }))
    }

    fun rank5(all: List<AccessPoint>): List<ChannelScore> {
        val nets = all.filter(::is5)
        return CANDIDATES_5
            .map { ChannelScore(it, score5(it, nets), dfs = it in 52..144) }
            .sortedWith(compareBy({ it.score }, { it.channel }))
    }
}
