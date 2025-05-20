package ai.fd.thinklet.camerax.vision.util

import android.content.Context
import android.net.wifi.WifiManager
import java.util.Locale

/**
 * Wi-Fi経由で割り当てられているデバイスのIPアドレスを取得します。
 *
 * 注意: この関数は内部で WifiManager.connectionInfo および WifiInfo.ipAddress を使用しており、
 * これらはAPIレベル31 (Android S) で非推奨となっています。
 * THINKLETのOSバージョン(APIレベル27)上で動作させることを前提しているためこの実装としています。
 *
 * @param context アプリケーションコンテキスト。
 * @return 取得できたIPアドレスの文字列。取得に失敗した場合は空文字列。
 */
@Suppress("DEPRECATION")
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
