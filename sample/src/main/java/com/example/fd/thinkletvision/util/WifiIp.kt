package com.example.fd.thinkletvision.util

import android.content.Context
import android.net.wifi.WifiManager
import java.util.Locale

fun getWifiIPAddress(context: Context): String {
    try {
        val manager = context.getSystemService(WifiManager::class.java)
        val info = manager.connectionInfo
        val ipAddr = info.ipAddress
        val ipString = String.format(
            Locale.JAPAN,
            "%02d.%02d.%02d.%02d",
            (ipAddr shr 0) and 0xff,
            (ipAddr shr 8) and 0xff,
            (ipAddr shr 16) and 0xff,
            (ipAddr shr 24) and 0xff
        )
        return ipString
            .split(".").joinToString(".") { it.toInt().toString() }
    } catch (_: Exception) {
        return ""
    }
}