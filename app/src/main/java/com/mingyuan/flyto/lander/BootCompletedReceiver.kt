package com.mingyuan.flyto.lander

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// 開機後預註冊 MockProvider，避免第一筆 SET_LOCATION broadcast 因 cold start 延遲失敗。
// 若使用者尚未在 Developer Options 授權，註冊會失敗（log 提示），不影響 App 正常運作。
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("FlyToLander", "Boot completed; pre-registering MockProvider")
        MockProvider.ensureRegistered(context)
    }
}
