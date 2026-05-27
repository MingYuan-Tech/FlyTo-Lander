@file:Suppress("DEPRECATION") // Criteria 在 API 30 deprecated，但 LocationManager.addTestProvider
// 在 minSdk 29 仍以 int power/accuracy 參數為 API；ProviderProperties 替代品需 API 31+。

package com.mingyuan.flyto.lander

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log

// 包裝 LocationManager test provider API：註冊 GPS test provider、注入座標、清除。
// 必須由「持有 ACCESS_MOCK_LOCATION 且被 Developer Options 選為 Mock Location App」的 App 呼叫，
// 否則 setTestProviderLocation 會 throw SecurityException。
object MockProvider {

    private const val TAG = "FlyToLander"
    private const val PROVIDER = LocationManager.GPS_PROVIDER

    @Volatile
    private var registered = false

    /**
     * 確保 test provider 已註冊；若未授權則 catch SecurityException 並回 false。
     */
    fun ensureRegistered(context: Context): Boolean {
        if (registered) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            // 先 remove 一次，避免 "Provider already exists" 錯誤
            try { lm.removeTestProvider(PROVIDER) } catch (_: IllegalArgumentException) { /* ignore */ }

            lm.addTestProvider(
                PROVIDER,
                /* requiresNetwork  */ false,
                /* requiresSatellite*/ false,
                /* requiresCell    */ false,
                /* hasMonetaryCost */ false,
                /* supportsAltitude*/ true,
                /* supportsSpeed   */ true,
                /* supportsBearing */ true,
                /* powerRequirement*/ Criteria.POWER_LOW,
                /* accuracy        */ Criteria.ACCURACY_FINE
            )
            lm.setTestProviderEnabled(PROVIDER, true)
            registered = true
            Log.i(TAG, "MockProvider registered ($PROVIDER)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "MockProvider not authorized: ${e.message}")
            false
        }
    }

    /**
     * 注入一筆座標到 GPS test provider。
     */
    fun update(
        context: Context,
        lat: Double,
        lng: Double,
        alt: Double = 0.0,
        speed: Float = 0f,
        bearing: Float = 0f,
        accuracy: Float = 5f,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        if (!ensureRegistered(context)) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = Location(PROVIDER).apply {
            latitude = lat
            longitude = lng
            altitude = alt
            this.speed = speed
            this.bearing = bearing
            this.accuracy = accuracy
            time = timestamp
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        return try {
            lm.setTestProviderLocation(PROVIDER, loc)
            Log.i(TAG, "MockProvider set: %.6f, %.6f".format(lat, lng))
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "MockProvider update failed: ${e.message}")
            false
        }
    }

    /**
     * 移除 test provider，停止模擬。
     */
    fun clear(context: Context) {
        if (!registered) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.removeTestProvider(PROVIDER)
            registered = false
            Log.i(TAG, "MockProvider cleared")
        } catch (e: Exception) {
            Log.w(TAG, "MockProvider clear failed: ${e.message}")
        }
    }
}
