package com.mingyuan.flyto.lander

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

// Process-scoped 即時狀態，供 LocationBroadcastReceiver 寫入、MainActivity Compose UI 讀取。
// §7.4 不持久化：僅存於記憶體，App process 結束即釋放，不寫入 SharedPreferences / DB / 檔案。
object ReceiverState {

    data class LastReceived(
        val lat: Double,
        val lng: Double,
        val callerUid: Int,
        val timestampMs: Long
    )

    private val _lastReceived = mutableStateOf<LastReceived?>(null)
    val lastReceived: State<LastReceived?> = _lastReceived

    fun updateLast(lat: Double, lng: Double, callerUid: Int) {
        _lastReceived.value = LastReceived(
            lat = lat,
            lng = lng,
            callerUid = callerUid,
            timestampMs = System.currentTimeMillis()
        )
    }

    fun clearLast() {
        _lastReceived.value = null
    }
}
