# FlyTo Lander 設計文件

> 本文件描述 **FlyTo Lander 自己的設計**（Android 端 helper App）。
> 文件版本：2026-05-27

---

## 目錄

1. [目的與範疇](#1-目的與範疇)
2. [對外接口（Intent 格式）](#2-對外接口intent-格式)
3. [設計骨架](#3-設計骨架)
4. [安全性與風險揭露（Phase 1 開工前約束）](#4-安全性與風險揭露phase-1-開工前約束)
5. [已知限制](#5-已知限制)
6. [Phase 1 已完成狀態](#6-phase-1-已完成狀態)
7. [參考資料](#7-參考資料)

---

## 1. 目的與範疇

### 1.1 角色

FlyTo Lander 是 [FlyTo](https://flyto.mytechs.com.tw/)（macOS GPS 模擬工具）的 Android 端 helper App，職責唯一：

**接收 macOS 端透過 adb broadcast 發來的座標，呼叫 `LocationManager.setTestProviderLocation` 灌入系統 Mock Location Provider。**

### 1.2 為什麼必須是 App

Android 9+ 起，`setTestProviderLocation` 必須由「持有 `ACCESS_MOCK_LOCATION` 且被 Developer Options 選為 Mock Location App」的 App 在 process 內呼叫。`adb shell` 的 uid 2000 不對應任何 package，`AppOpsManager.checkOp(OP_MOCK_LOCATION)` 永遠 reject。

業界自動化測試平台（Appium `io.appium.settings`、BrowserStack、Sauce Labs、HeadSpin、Bitbar）皆採用相同雙端架構。

### 1.3 範疇

| 包含 | 不包含 |
|------|--------|
| Android helper App 自身的元件設計 | macOS 端如何發送 broadcast |
| 對外 Intent 接口契約 | macOS 端 AVD 設置流程 |
| 安全約束（permission 黑名單、開源、零 SDK） | macOS 端內部實作細節 |
| Phase 1 開工前的硬性 gate | macOS 端 broadcast 發送的 pitfall 細節 |

> 桌面端相關問題請洽 FlyTo 官方網站 https://flyto.mytechs.com.tw/

---

## 2. 對外接口（Intent 格式）

Lander 對 macOS 端的**唯一接口契約**。任何修改本節都會破壞 macOS 端對接 — 改動前必須同步通知 macOS 端開發者，並在 macOS 端完成對應修改後才能合併。

### 2.1 Action

| Action | 用途 |
|--------|------|
| `com.mingyuan.flyto.lander.SET_LOCATION` | 注入座標 |
| `com.mingyuan.flyto.lander.CLEAR_LOCATION` | 移除 Test Provider，停止模擬 |

### 2.2 Receiver Component

`com.mingyuan.flyto.lander/.LocationBroadcastReceiver`

> Manifest 為 `exported="true"`，但 `onReceive()` 內以 `Binder.getCallingUid()` 限制只接受 uid 2000（shell）、uid 0（root）、本 App 自身。其他 UID 一律 reject 並 log。

### 2.3 Extras（SET_LOCATION）

| Key | 型別 | 必要 | 說明 |
|-----|------|------|------|
| `lat` | double | ✓ | 緯度，小數點 6 位精度（約 0.11 公尺） |
| `lng` | double | ✓ | 經度，小數點 6 位精度 |
| `alt` | double | ✗ | 海拔（公尺） |
| `speed` | float | ✗ | 速度（m/s） |
| `bearing` | float | ✗ | 方位角（度） |
| `accuracy` | float | ✗ | 精度（公尺，預設 5） |
| `ts` | long | ✗ | timestamp（ms，預設當下） |

`CLEAR_LOCATION` 無 extras。

---

## 3. 設計骨架

### 3.1 必要元件

| 元件 | 職責 |
|------|------|
| `LocationBroadcastReceiver` | 監聽 `SET_LOCATION` / `CLEAR_LOCATION`，UID 白名單檢查，解析 extras，呼叫 `MockProvider` |
| `MockProvider` (object) | 包裝 `LocationManager.addTestProvider` / `setTestProviderLocation` / `removeTestProvider` |
| `MainActivity` (Compose) | 三段透明聲明 + 即時狀態 + 引導 Developer Options |
| `BootCompletedReceiver` | 開機後預註冊 Test Provider（選配） |
| `ReceiverState` (object) | Process-scoped Compose State，Receiver 寫入、UI 讀取 |

### 3.2 必要 Permission

| Permission | 用途 |
|-----------|------|
| `android.permission.ACCESS_MOCK_LOCATION` | 註冊 Test Provider（由 Developer Options 控制，不必 runtime request） |
| `android.permission.ACCESS_FINE_LOCATION` | 部分 OEM ROM 要求 |
| `android.permission.ACCESS_COARSE_LOCATION` | Android 12+ 起 FINE 必須同時宣告 COARSE，否則使用者授權對話框可能無法授予 FINE。非權限擴張：FINE 本就涵蓋 COARSE 能力 |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 開機預註冊（選配） |

§4.3 列出**禁止**請求的 permission 黑名單。

### 3.3 套件結構

```
app/src/main/
├── AndroidManifest.xml
├── java/com/mingyuan/flyto/lander/
│   ├── BootCompletedReceiver.kt
│   ├── LocationBroadcastReceiver.kt
│   ├── MainActivity.kt
│   ├── MockProvider.kt
│   └── ReceiverState.kt
└── res/...
```

### 3.4 散佈策略

- 套件名：`com.mingyuan.flyto.lander`
- APK：本 repo GitHub Release（**不上 Google Play**，Google 政策禁止 mock location 類 App）
- 簽章：GPG release tag + APK SHA-256（CI 自動產出，見 `.github/workflows/release.yml`）

---

## 4. 安全性與風險揭露（開工前約束）

> 本節為 **開工前硬性設計約束**，不是建議。任何 implementation 違反者視同 bug。
>
> 動機：Android 系統權限模型較寬鬆（USB Debugging、sideload APK、Mock Location 都可由使用者開啟），FlyTo Lander 直接觸碰這些敏感能力，使用者有合理的安全疑慮。本節以「設計＋文件」化解疑慮，不犧牲功能。

### 4.1 使用者面對的攻擊面

| 步驟 | 風險 |
|------|------|
| 開啟 Developer Options | 系統設定被改的入口 |
| 啟用 USB Debugging | 任何接得到 USB 的人都能 `adb shell` 該裝置 |
| sideload `flyto-lander.apk` | App 可能藏惡意行為 |
| 授予 `ACCESS_MOCK_LOCATION` | App 可以欺騙系統位置 |

### 4.2 第一層：可審計

- **完全開源**：Public repo + Apache-2.0
- **Reproducible Build**：每次 release 公開「git commit + Gradle / AGP / JDK 版本」，使用者可重建驗證同 SHA-256
- **GPG 簽署 release tag**：使用者可驗證 release 確實來自工作室
- **APK signing key 公開指紋**：README 公布 keystore SHA-256 指紋

### 4.3 第二層：最小權限（Defensive Manifest）

`AndroidManifest.xml` **絕對不得**請求以下 permission：

| Permission | 不請求理由 |
|-----------|----------|
| `android.permission.INTERNET` | **核心承諾**。無網路即無法外洩任何資料 |
| `android.permission.ACCESS_NETWORK_STATE` | 同上 |
| `android.permission.READ_PHONE_STATE` | 不需 IMEI / 電話狀態 |
| `android.permission.READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | 不存任何檔案 |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | 不需背景讀取真實位置 |
| `android.permission.QUERY_ALL_PACKAGES` | 不需查詢其他 App |

僅請求 [§3.2](#32-必要-permission) 列出的四個必要 permission。

> README 一級顯眼處必須有橫幅：「**This app requests NO `INTERNET` permission. Verify in `AndroidManifest.xml`.**」

### 4.4 第三層：技術層硬隔離

- **不持久化座標**：每筆 broadcast 處理完即釋放，禁止寫入 `SharedPreferences` / Room / 任何檔案 / 任何 DB
- **零 analytics / 零 crash report 上傳**：不引入 Firebase、Crashlytics、App Center、Sentry 等 SDK
- **零第三方依賴**：`build.gradle` dependencies 只允許 Kotlin stdlib + AndroidX core/appcompat + Compose Material；任何新增依賴需單獨評估
- **限制 broadcast 來源**：`LocationBroadcastReceiver.onReceive` 內 `Binder.getCallingUid()` 檢查白名單（uid 2000 / 0 / self）；其他丟棄並寫 Logcat
- **Receiver 暴露策略**：所有 Receiver 在 Manifest 明確宣告 `android:exported`：
  - `LocationBroadcastReceiver`：必須 `exported="true"`（要接 adb shell），搭配上述 UID 白名單檢查
  - `BootCompletedReceiver`：`exported="false"`

### 4.5 第四層：UI 透明（In-app trust signals）

`MainActivity`（Compose）必須顯眼顯示：

```
┌──────────────────────────────────┐
│  FlyTo Lander                    │
├──────────────────────────────────┤
│  ✓ 本 App 不請求網路權限          │
│  ✓ 本 App 不蒐集任何資料          │
│  ✓ 程式碼公開                     │
├─── 即時狀態 ────────────────────┤
│  Mock Location 授權  ✓ 已授權     │
│  最後一次收到的座標  25.033964,    │
│                     121.564468    │
│                     @ 14:23:45    │
│  最後 broadcast 來源 uid=2000     │
└──────────────────────────────────┘
```

UI 不放品牌動畫，保持工具感。

### 4.6 第五層：使用者文件化風險揭露

README 必須明確列出：

1. **建議使用測試專用裝置**，不要裝在主力手機上
2. **USB Debugging 啟用後**，不要把手機接到不信任的電腦 / 公共充電站
3. **Mock Location 啟用後**，部分金融、防作弊類 App 會檢測 `Location.isFromMockProvider()` 拒絕運行 —— 系統行為非 bug
4. **完整移除步驟**：uninstall APK → Developer Options → Select mock location app → None → 關閉 USB Debugging → 關閉 Developer Options
5. **回報資安問題**：GitHub Security Advisory

### 4.7 第六層：定位為「業界一致做法」

此設計與 Appium、BrowserStack、Sauce Labs 等業界自動化測試平台採用相同雙端架構，是 AOSP `LocationManagerService.checkMockLocation()` 設計下的唯一合法路徑。

### 4.8 驗收 Checklist（Phase 1 結束前必須通過）

- [x] `AndroidManifest.xml` 不含 §4.3 表中禁止 permission
- [x] `build.gradle` dependencies 無 Firebase / Crashlytics / Sentry / App Center / 任何 analytics SDK
- [x] `LocationBroadcastReceiver` 含 `Binder.getCallingUid()` 白名單檢查
- [x] Receiver 在 Manifest 明確標 `android:exported`；對外暴露者含 UID 白名單
- [x] `MainActivity` UI 含三段聲明 + 即時狀態面板
- [x] README 有 `This app requests NO INTERNET permission` 橫幅
- [x] Release tag 有 GPG 簽章（CI 設定就緒，待第一個 `v*` tag）
- [x] Release artifact 附 APK SHA-256（CI workflow `release.yml`）
- [x] CI 驗證 reproducible build（`reproducible-build.yml`）
- [x] README 列出風險揭露 5 條（[§4.6](#46-第五層使用者文件化風險揭露)）

---

## 5. 已知限制

| 項目 | 影響 | 緩解 |
|------|------|------|
| 無實機，廠商客製 ROM 行為未驗證 | MIUI / OneUI / ColorOS 環境的 Mock Location 開關位置與 Test Provider 行為差異 | Phase 3 採購二手實機驗證 |
| Mock Location 在 Google Play Protect 風控下可能觸發警示 | 部分強防詐 App（銀行、行動支付）會偵測並拒絕運行 | 文件揭露此為 Mock Location 機制本身限制 |
| 無法在 Google Play 上架 | Google 政策禁止 mock location 類 App | 計畫內，APK 走 GitHub Release 私下散佈 |

---

## 6. 歷史 Phase 紀錄

### 6.1 Phase 1 — 雛形 ✅ 2026-05-27

- [x] 建立 [`MingYuan-Tech/FlyTo-Lander`](https://github.com/MingYuan-Tech/FlyTo-Lander) Public repo（Apache-2.0）
- [x] Android Studio 專案：Kotlin + Compose、minSdk 29、targetSdk 34
- [x] 5 個 Kotlin 元件實作
- [x] AndroidManifest 通過 [§4.3](#43-第二層最小權限defensive-manifest) permission 約束
- [x] CI workflows：build / release / reproducible-build
- [x] 通過 [§4.8 驗收 checklist](#48-驗收-checklistphase-1-結束前必須通過) 全部 10 條
- [x] 端對端驗證：macOS adb → BroadcastReceiver → MockProvider.setTestProviderLocation（log 確認 6 位小數注入）
- [x] README、SECURITY.md、CONTRIBUTING.md、LICENSE、NOTICE

> 首次 commit：[`d8d4cbb`](https://github.com/MingYuan-Tech/FlyTo-Lander/commit/d8d4cbb)（2026-05-27）
> 桌面端 Phase 規劃屬桌面端範疇，不收錄於本 repo；相關資訊請洽 FlyTo 官方網站 https://flyto.mytechs.com.tw/。

### 6.2 Phase 1.1 — lint 體質補完 + Gradle 升級 ✅ 2026-05-28 上午

Phase 1 §4.8 第 4 條雖寫 lint pass，但實際 CI 從未跑 lint —— 跑了發現 4 errors。本次補完：

- [x] 4 個既存 lint errors 全修（MockProvider WrongConstant suppress、AndroidManifest 補 COARSE_LOCATION、ExportedReceiver suppress 並註明設計理由）
- [x] `build.gradle.kts` 加 `lint { abortOnError = true; disable += "MockLocation" }`，未來 lint error 會擋 build
- [x] CI `build.yml` 加 `lintDebug` step + failure 時上傳報告 artifact
- [x] Gradle 8.9 → 8.13、AGP 8.5.2 → 8.13.2（對齊 Android Studio 新版內建 AGP）
- [x] §6.2 必要 permission 從 3 個 → 4 個（加 COARSE_LOCATION）

> 收尾 commit：[`e42a109`](https://github.com/MingYuan-Tech/FlyTo-Lander/commit/e42a109)

### 6.3 Phase 1.2 — Android 13+ 升級 + 工具鏈中庸升級 + lint 全清 + 跨版測試 ✅ 2026-05-28 下午

支援目標從 Android 10+ 收斂為 Android 13+，工具鏈升級至中庸版本（避開 AGP/Kotlin major bump 風險），lint 從 30 warnings + 4 errors 收斂為 1 warning（targetSdk 35 < 36 的保守決策）。

**SDK 升級**：
- [x] minSdk 29 → 33（Android 10 → Android 13）
- [x] targetSdk 34 → 35（Android 14 → Android 15；目前最新是 36，刻意保守避開 Android 16 行為變化）
- [x] compileSdk 34 → 36（編譯期用最新，支援所有新 lint）
- [x] `mipmap-anydpi-v26/` → `mipmap-anydpi/`（minSdk 33 後 v26 配置限定符不需要）
- [x] `MainActivity.isMockLocationAuthorized()` 化簡（移除 `SDK_INT >= Q` 條件分支，minSdk 33 後恆真）

**工具鏈中庸升級**（避開 AGP 9.x / Kotlin 2.3.x 的 major bump 風險）：
- [x] Gradle 8.13 → 8.14.5（patch）
- [x] Kotlin 2.0.20 → 2.2.20
- [x] AGP 8.13.2（不動，刻意避開 9.x major）
- [x] androidx.core:core-ktx 1.13.1 → 1.18.0
- [x] androidx.lifecycle:lifecycle-runtime-ktx 2.8.5 → 2.10.0
- [x] androidx.activity:activity-compose 1.9.2 → 1.13.0
- [x] androidx.compose:compose-bom 2024.09.02 → 2026.05.01

**Lint 全清**（30 warnings + 4 errors → 1 warning）：
- [x] `strings.xml` 11 條未使用字串改 `stringResource()` 引用（i18n 鋪路，原本 Compose 直寫）
- [x] 刪 `mipmap-anydpi/ic_launcher_round.xml`（adaptive icon 系統自動衍生 round 變體）
- [x] `ic_launcher.xml` 補 `<monochrome>` tag（Android 13+ Material You themed icon）
- [x] AndroidManifest 拿掉 RedundantLabel（activity label 與 application 重複）
- [x] `MainActivity` `Uri.parse()` → `String.toUri()` KTX
- [x] `Compose NonObservableLocale`：`SimpleDateFormat` `Locale.getDefault()` → `Locale.ROOT`（HH:mm:ss 24h locale-neutral）
- [x] `build.gradle.kts` lint disable += `AndroidGradlePluginVersion`、`NewerVersionAvailable`（工具版本保守策略，由人類掌握）

**跨版 emulator 驗證**（API 33 / 34 / 35 / 36）：

| API 33 | API 34 | API 35 | API 36 |
|--------|--------|--------|--------|
| Pixel 6 (x86_64) | Pixel 7 (x86_64) | Pixel 6 (x86_64) | Pixel 6 (x86_64) |
| ✅ UI 正常 | ✅ UI 正常 | ✅ (edge-to-edge fix 後) | ✅ (edge-to-edge fix 後) |
| ✓ 已授權 | ❌ 未授權 + 引導 | ❌ 未授權 + 引導 | ❌ 未授權 + 引導 |
| 三段聲明顯示完整 | stringResource 重構驗證通過 | targetSdk 35 edge-to-edge 行為變化 | 同 API 35 |

**API 35+ edge-to-edge 修補**：
- [x] `LanderApp` Column 加 `.safeDrawingPadding()`，避開 status bar / nav bar / cutout

**新 debug APK SHA-256**：`4615ced104ed719076e2e3a037a78bad3980e943508e45c3479261ef3f6d09c4`

> Phase 1.2 起始 commit：[`6938074`](https://github.com/MingYuan-Tech/FlyTo-Lander/commit/6938074)；
> 收尾 commit：`e34bdb6`

---

## 7. 參考資料

| 主題 | 連結 |
|------|------|
| LocationManager Test Provider API | https://developer.android.com/reference/android/location/LocationManager#addTestProvider |
| Mock Location 與 App 風控 | https://developer.android.com/training/location/permissions#mock-location |
| AOSP `LocationManagerService` 原始碼 | https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/location/LocationManagerService.java |
| Appium io.appium.settings（業界對照） | https://github.com/appium/io.appium.settings |
| Reproducible Builds for Android | https://developer.android.com/about/versions/14/reproducible-builds |

### 本 repo 相關文件

- [`CLAUDE.md`](../CLAUDE.md) — Claude Code 指引、技術棧、§4 約束摘要
- [`README.md`](../README.md) — 使用者導覽、安裝步驟、5 條風險揭露
- [`SECURITY.md`](../SECURITY.md) — 資安回報流程
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — 貢獻流程、PR §4.8 checklist
