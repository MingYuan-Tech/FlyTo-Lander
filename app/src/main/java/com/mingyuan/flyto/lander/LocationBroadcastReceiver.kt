package com.mingyuan.flyto.lander

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process
import android.util.Log

// 監聽 macOS 端透過 adb broadcast 發來的 Intent，呼叫 MockProvider 灌入座標。
// §7.4 來源檢查：只允許 shell (uid 2000) / root (uid 0) / 本 App 自己，其餘 reject 並 log。
class LocationBroadcastReceiver : BroadcastReceiver() {

    private val tag = "FlyToLander"

    override fun onReceive(context: Context, intent: Intent) {
        val callerUid = Binder.getCallingUid()
        val callerPid = Binder.getCallingPid()

        if (!isAllowedCaller(callerUid)) {
            Log.w(
                tag,
                "Rejected broadcast from uid=$callerUid pid=$callerPid " +
                    "(only shell/root/self allowed); action=${intent.action}"
            )
            return
        }

        when (intent.action) {
            ACTION_SET -> handleSet(context, intent, callerUid)
            ACTION_CLEAR -> handleClear(context)
            else -> Log.w(tag, "Unknown action: ${intent.action}")
        }
    }

    private fun isAllowedCaller(uid: Int): Boolean =
        uid == Process.SHELL_UID ||  // 2000，adb shell
            uid == Process.ROOT_UID || // 0，極少數系統情境
            uid == Process.myUid()     // 本 App 自送

    private fun handleSet(context: Context, intent: Intent, callerUid: Int) {
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) {
            Log.w(tag, "SET_LOCATION missing required extras (lat, lng)")
            return
        }
        val alt = intent.getDoubleExtra(EXTRA_ALT, 0.0)
        val speed = intent.getFloatExtra(EXTRA_SPEED, 0f)
        val bearing = intent.getFloatExtra(EXTRA_BEARING, 0f)
        val accuracy = intent.getFloatExtra(EXTRA_ACCURACY, 5f)
        val ts = intent.getLongExtra(EXTRA_TS, System.currentTimeMillis())

        val ok = MockProvider.update(
            context = context,
            lat = lat, lng = lng, alt = alt,
            speed = speed, bearing = bearing, accuracy = accuracy,
            timestamp = ts
        )
        if (ok) {
            ReceiverState.updateLast(lat, lng, callerUid)
        }
    }

    private fun handleClear(context: Context) {
        MockProvider.clear(context)
        ReceiverState.clearLast()
    }

    companion object {
        const val ACTION_SET = "com.mingyuan.flyto.lander.SET_LOCATION"
        const val ACTION_CLEAR = "com.mingyuan.flyto.lander.CLEAR_LOCATION"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ALT = "alt"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_BEARING = "bearing"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_TS = "ts"
    }
}
