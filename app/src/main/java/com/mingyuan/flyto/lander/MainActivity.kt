package com.mingyuan.flyto.lander

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// FlyTo Lander 唯一 Activity；§7.5 In-app trust signals：
// 三段透明聲明（不聯網 / 不蒐集 / 開源）+ 即時狀態（授權、最後座標、最後 UID）。
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LanderApp()
                }
            }
        }
    }
}

@Composable
private fun LanderApp() {
    val context = LocalContext.current
    var authorized by remember { mutableStateOf(isMockLocationAuthorized(context)) }
    val lastReceived by ReceiverState.lastReceived

    // 每 2 秒自動 refresh 一次授權狀態（使用者從設定切回 App 時自動感知）
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000L)
            authorized = isMockLocationAuthorized(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // targetSdk 35+ 預設 edge-to-edge，需避開 status bar / nav bar / cutout
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "FlyTo Lander",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "FlyTo Mac 端的 Android 端 helper",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 三段透明聲明（§7.5）
        ClaimCard(
            symbol = "✓",
            title = stringResource(R.string.claim_no_internet_title),
            body = stringResource(R.string.claim_no_internet_body)
        )
        ClaimCard(
            symbol = "✓",
            title = stringResource(R.string.claim_no_data_title),
            body = stringResource(R.string.claim_no_data_body)
        )
        ClaimCard(
            symbol = "✓",
            title = stringResource(R.string.claim_open_source_title),
            body = stringResource(R.string.claim_open_source_body)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 即時狀態
        StatusCard(authorized = authorized, lastReceived = lastReceived)

        if (!authorized) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "尚未授權 Mock Location。請開啟開發者選項並選 FlyTo Lander 為 Mock Location App：",
                fontSize = 13.sp
            )
            Button(
                onClick = { openDeveloperOptions(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_open_developer_options))
            }
            Text(
                text = stringResource(R.string.hint_authorize_steps),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { authorized = isMockLocationAuthorized(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重新整理授權狀態")
        }
    }
}

@Composable
private fun ClaimCard(symbol: String, title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$symbol  $title",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = body,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(authorized: Boolean, lastReceived: ReceiverState.LastReceived?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "── ${stringResource(R.string.status_section_title)} ──",
                fontWeight = FontWeight.SemiBold
            )

            Text(stringResource(
                if (authorized) R.string.status_mock_authorized
                else R.string.status_mock_unauthorized
            ))

            if (lastReceived != null) {
                // HH:mm:ss 24h 格式 locale-neutral，用 ROOT 避免 Compose NonObservableLocale lint
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
                val time = timeFmt.format(Date(lastReceived.timestampMs))
                Text(
                    text = "最後一次收到的座標：",
                    fontSize = 13.sp
                )
                Text(
                    text = "  %.6f, %.6f  @ %s".format(lastReceived.lat, lastReceived.lng, time),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                val uidLabel = when (lastReceived.callerUid) {
                    Process.SHELL_UID -> "uid=${lastReceived.callerUid} (shell) ✓"
                    Process.ROOT_UID -> "uid=${lastReceived.callerUid} (root)"
                    Process.myUid() -> "uid=${lastReceived.callerUid} (self)"
                    else -> "uid=${lastReceived.callerUid}"
                }
                Text("最後 broadcast 來源：$uidLabel", fontSize = 13.sp)
            } else {
                Text(stringResource(R.string.status_last_location_none), fontSize = 13.sp)
                Text(stringResource(R.string.status_last_uid_none), fontSize = 13.sp)
            }
        }
    }
}

private fun isMockLocationAuthorized(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    return try {
        // unsafeCheckOpRawNoThrow：取代 API 30 deprecated 的 unsafeCheckOpNoThrow，
        // 行為等價但回傳的 mode 可能包含 MODE_FOREGROUND（前台可用），都視為已授權。
        val mode = appOps.unsafeCheckOpRawNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND
    } catch (e: Exception) {
        false
    }
}

private fun openDeveloperOptions(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback：開啟 App 詳細頁，讓使用者手動切到設定
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
    }
}
