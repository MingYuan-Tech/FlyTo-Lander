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

### 6.4 Phase 1.3 — Public repo 隔離 + launcher icon 升級 ✅ 2026-05-28 傍晚

User 提出兩條硬性規則並修補相關違規、同時把 launcher icon 從翅膀+pin 草版升級為 `docs/AppLogo.png` 完整版。

**Public repo 隔離修補**（commit `991839c`）：
- [x] SECURITY.md / README.md / docs/android-platform.md §1.3 + §6 / `.github/ISSUE_TEMPLATE/config.yml` 共 5 處違規修補
- [x] FlyTo URL 全部指向 https://flyto.mytechs.com.tw/（不再指 GitHub）
- [x] 嚴禁將桌面端技術 / 資料記錄到本 repo

**Launcher icon 升級**（commit `efad72f`）：
- [x] `docs/AppLogo.png` 5 個密度 foreground PNG 全換新（mdpi / hdpi / xhdpi / xxhdpi / xxxhdpi）
- [x] 中途加 Bugdroid 元素評估後因 CC-BY attribution 缺失 + mock location 工具用 Google 商標風險高 + App icon 是高曝光點 → 還原為翅膀+pin 完整版（零商標風險）
- [x] 3 種 launcher mask（circle / squircle / square）預覽下視覺都 OK

> Phase 1.3 收尾 commit：`efad72f`

### 6.5 Phase 1.4 — CI 體質補完 + AGP 9 升級失敗 revert 🟡 半完成 2026-05-28 傍晚

延續 1.1 / 1.2 / 1.3「體質補完」主題、共 6 commit（含 1 個 revert + 1 個 fix attempt）。AGP 9 升級踩坑後 revert、後續配套 fix 保留。

**G1 工具鏈全升嘗試 → revert**（commit `06cecd7` / `f756da9` / `74a82cc` revert）：
- [x] AGP 8.13.2 → 9.2.1、Gradle 8.14.5 → 9.4.1（本機 + CI build/lint 全綠）
- [x] AGP 9 plugin set 變動：kotlin.android plugin 不再需要、改 KotlinCompile DSL
- [ ] H1 跨版 emulator 測試發現所有 emulator 上 App 啟動 FATAL `ClassNotFoundException MainActivity` — AGP 9「內建 Kotlin」未自動 enable、Kotlin classes 沒 dex 進 APK
- [x] 時間有限不深入研究 opt-in、revert 回 Phase 1.2 中庸路線（AGP 8.13.2 / Gradle 8.14.5 / kotlin.android plugin / kotlinOptions DSL）
- [x] 保留 `MainActivity unsafeCheckOpRawNoThrow` 改進（與 AGP 無關）

**G2 release / reproducible workflow 補 lint**（commit `301cd4b`）：
- [x] Phase 1.1 早上漏修 release-side lint、本次補（跟 build.yml 對齊）

**G3 build.gradle signingConfig wire**（commit `a74197f`）：
- [x] release.yml 用的 `-PRELEASE_STORE_FILE` 等 properties 沒接上、補上
- [x] 寬容設計：missing properties 時 build unsigned APK 而非 fail（Phase 1.6 用此設計做 reproducible 比對）

**新 debug APK SHA-256**（revert 後）：`913ca798e610e7e09e36e169f13eb77305825fff0465fac5aa67f73a135a40c9`

> 半完成原因：
> 1. G1 revert 後 AGP 9 升級暫掛起（要時間研究內建 Kotlin opt-in）
> 2. G3 cut tag 需 GitHub Actions secrets（8 條 secret 都沒設）、留 Phase 1.5 處理
>
> Phase 1.4 收尾 commit：`74a82cc`

### 6.6 Phase 1.5 — Release secret 設定 + 首次 cut release tag ✅ 完成 2026-05-28 晚（reproducible PASS 條件由 Phase 1.6 達成）

引導 user 一步步走完 keystore + GPG + secret + cut tag 全流程、共 8 commit + 1 tag + 1 issue。

**Keystore + GPG + Secrets 設定**：
- [x] I1 產 release keystore（`~/.flyto-lander-secrets/release.jks`）
- [x] I2 產工作室 GPG keypair（KEY_ID `DCD89190E52012AE`、`release@mytechs.com.tw`、fingerprint `2DC1ED20...12AE`）
- [x] I3 設 6 條 GitHub Actions secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` / `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE`）
- [x] I4 commit `6dc2455`：README「驗證 GPG 簽章」段從 placeholder 改實作版 + `docs/release-pubkey.asc` 入 repo

**第一次 cut tag**（commit `15678e4` / tag `v0.1.1-rc1`）：
- [x] bump versionCode 1 → 2、versionName 0.1.0 → 0.1.1-rc1
- [x] release.yml 第一次 fail（keystore 密碼記錯、本機 keytool 也跑不過）
- [x] 改用 `openssl rand -base64` + 變數 pipeline 重產 keystore 與 3 條密碼 secret → 重 trigger pass → GitHub Release `v0.1.1-rc1` publish 3 artifact（signed APK 22 MB / .sha256 / .asc）

**Cascade trigger 缺口發現**：
- [ ] reproducible-build.yml 未自動觸發（GitHub `github.token` 防 infinite loop 設計不 cascade trigger）
- [x] 手動 `gh workflow run` 觸發 → SHA-256 mismatch fail → issue #1 紀錄

**Cascade trigger fix**（commit `8feb865`、隔天 2026-05-29 端到端驗證）：
- [x] release.yml 結尾加 step 主動 `gh workflow run reproducible-build.yml`
- [x] org / repo Workflow permissions 改 write 後端到端驗證通過（cascade 真的 dispatch reproducible-build.yml）

> Phase 1.5 收尾：tag `v0.1.1-rc1` + cascade trigger 在 commit `8feb865` 修補完成。reproducible PASS 條件由 Phase 1.6 達成。

### 6.7 Phase 1.6 — Reproducible build best-effort ✅ 2026-05-29（commit `e063163` / tag `v0.1.1-rc2`）

issue #1 真實 root cause 鎖定為 **apksig 函式庫 APK Signing Block 內 ~5KB 非確定性**（非原 hypothesis 的 timestamp / commit drift / signing key drift）。採 F-Droid 同樣 best-effort 路徑、改用 unsigned APK 作為 reproducible 比對標的。

**前置：本機環境對齊 CI**：
- [x] SDKMAN 裝 temurin 17.0.19-tem（對齊 CI `Java_Temurin-Hotspot_jdk/17.0.19-10`）
- [x] 為了裝 SDKMAN 順帶 `brew install bash`（macOS 預設 bash 3.2、SDKMAN 要 bash 4+、brew 裝 5.3.9）
- [x] system Java（`/usr/bin/java` Oracle 1.8）與 Android Studio JBR 21 都不動

**Root cause 鎖定**：
- [x] 本機跑兩次 unsigned `assembleRelease` → SHA 完全一致 `18e79a8e...`（build pipeline 已 deterministic）
- [x] 本機跑兩次 signed `assembleRelease` → SHA 不同、差異 5331 bytes 集中於 ZIP central directory 前的 APK Signing Block（兩 APK 同 size、ZIP entry CRC 全一致）
- [x] `apksigner` 0.9 唯一 deterministic flag 是 `--deterministic-dsa-signing`、無 RSA 對應 flag
- [x] AGP 8.13.2 內部用 `com.android.tools.build:apksig:8.13.2` 函式庫、無設定可消除

**Best-effort 三件套**（commit `e063163`）：
- [x] `release.yml`：修 dispatch checkout bug（`ref: ${{ github.event.inputs.tag || github.ref }}`）+ 新增 Build unsigned release APK step + Locate unsigned APK + clean before signed + artifact 多上傳 unsigned APK + .sha256 + release notes 拆 signed/unsigned 兩段
- [x] `reproducible-build.yml`：移 keystore 相關 step（unsigned 不需 secrets）+ 改 Rebuild unsigned + 比對 unsigned APK SHA + failure 時 issue body 措辭更新
- [x] `README.md` §Reproducible Build：移 Phase 1.5 RC 期間 callout、重寫為 best-effort（signed vs unsigned APK 用途表 + 本機驗證指令）

**端到端驗證 PASS**（cut `v0.1.1-rc2` commit `ed93185`）：

| Run | 時間 | 結果 |
|---|---|---|
| [release.yml 26615776642](https://github.com/MingYuan-Tech/FlyTo-Lander/actions/runs/26615776642) | 4m26s | ✓ publish 5 artifact |
| [reproducible-build.yml 26615905355](https://github.com/MingYuan-Tech/FlyTo-Lander/actions/runs/26615905355) | 3m44s | ✓ Rebuilt 與 Published byte-by-byte 一致 |

**Unsigned APK SHA-256**（v0.1.1-rc2）：`195217302d37b7dfcf60576103fbab06b7d00ef779fb15ab90b4151a3fb2d997`

> §4.8 #9「CI 驗證 reproducible build」正式達成（best-effort 標準）、issue #1 close 並附完整證據。

### 6.8 Phase 1.6 完成後雜項升級 ✅ 2026-05-29（同日連續）

Phase 1.6 PASS 後當日連續做兩件 GHA Node 24 transition 升級、用 reproducible 機制作為**升級安全網**驗證每次升級不破壞 build content。

**v0.1.1-rc3：GHA actions 升 Node 24 v5**（commit `32b17a2` + `61fc6fb`）：
- [x] `actions/checkout@v4` → `@v5`（已 Node 24）
- [x] `actions/setup-java@v4` → `@v5`（已 Node 24）
- [x] `gradle/actions/setup-gradle@v3` → `@v4`（**v4 仍 Node 20**、upstream v5 未釋出）
- [x] `actions/upload-artifact@v4` 不動（已 Node 24）
- [x] `softprops/action-gh-release@v2` 不動（floating tag latest patch 仍 Node 20）

**Unsigned APK SHA-256**（v0.1.1-rc3）：`cb1f0205e1a600fd5c1237297a1bd5ca24cc6ab3fbbf332c3ddda71b166313bf` — reproducible PASS

**v0.1.1-rc4：FORCE_JAVASCRIPT_ACTIONS_TO_NODE24 env workaround**（commit `011a23c`）：
- [x] 三 workflow workflow-level 加 `env: FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'`（GitHub 官方 workaround）
- [x] 殘餘 Node 20 actions（`setup-gradle@v4` + `softprops/action-gh-release@v2`）改為「target Node 20 but forced to Node 24」informational note
- [x] 2026-09-16 Node 20 強制移除時這些 actions 仍跑 Node 24、**不會 fail**
- [x] 副效益：reproducible-build.yml 跑時間 3m48s（v0.1.1-rc2）→ 1m58s（v0.1.1-rc4）、Node 24 perf gain ~50%

**Unsigned APK SHA-256**（v0.1.1-rc4）：`ea1532369fadc1f4de0586866a2f05ec8c50d900c615949a7448f92eee3f18e8` — reproducible PASS

> 2026-05-29 連續 cut 3 個 rc tag、reproducible 連續 PASS、reproducible 機制完美承擔「升級安全網」角色。
>
> Future follow-up：上游 `gradle/actions` v5 / `softprops/action-gh-release` v3 釋出後可拿掉 `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` env workaround。

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
