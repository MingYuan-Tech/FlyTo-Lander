# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案概述

**FlyTo Lander** 是 macOS GPS 模擬工具 [FlyTo](https://github.com/Jaofeng/FlyTo)（Private repo）的 Android 端 helper App。

### 角色

接收 macOS 端透過 adb broadcast 發來的座標，呼叫 `LocationManager.setTestProviderLocation` 灌入系統 Mock Location Provider，讓 Android 裝置上所有 location-aware App 都收到模擬座標。

### 為什麼存在這個 repo

Android 系統設計上，mock location 必須由「持有 `ACCESS_MOCK_LOCATION` 且被 Developer Options 選為 Mock Location App」的 App 呼叫；`adb shell` 的 uid 2000 不對應任何 package，無法直接灌座標（會丟 `SecurityException`）。因此 FlyTo macOS App 必須搭配這支 Android helper App 才能完成端對端 GPS 模擬。

獨立為 **Public repo**（FlyTo macOS App 主 repo 仍為 Private）讓使用者：
- 可審計 APK 程式碼來源
- 可驗證 APK SHA-256
- 可比對 Reproducible Build 結果
- 對「裝在我手機上的 App 到底會做什麼」有完全透明性

## 技術棧

- **語言**：Kotlin 2.0.20
- **UI 框架**：Jetpack Compose（Material 3）
- **建置工具**：Gradle 8.9 + AGP 8.5.2
- **JDK**：Android Studio 內建 JBR（OpenJDK 21）
- **目標系統**：minSdk 29（Android 10）、targetSdk 34（Android 14）、compileSdk 34
- **架構模式**：極簡 — process-scoped singleton + Compose State

### 編程硬性限制

呼應 FlyTo 主 repo「不使用任何第三方函式庫」精神：

- **零第三方依賴**：`build.gradle.kts` 的 dependencies 只允許 Kotlin stdlib + AndroidX core / activity-compose / material3；不可加入 Firebase、Crashlytics、Sentry、App Center、Retrofit、OkHttp、Glide 等任何第三方 SDK
- **嚴禁 `INTERNET` permission**：本 App 不聯網是核心承諾（[`docs/android-platform.md` §7.3](docs/android-platform.md#73-第二層最小權限defensive-manifest)），README 一級標題下方有橫幅保證、`AndroidManifest.xml` 可驗證
- **不持久化座標**：每筆 broadcast 處理完即釋放，禁止寫入 `SharedPreferences` / Room / 任何檔案 / 任何 DB
- **零 analytics / 零 crash report 上傳**：即使 Crash 也只走系統內建 Logcat

## 架構

```
adb broadcast (macOS)
   ↓
LocationBroadcastReceiver
   ├── Binder.getCallingUid() 白名單檢查（uid 2000 / 0 / self）
   ├── 解析 lat / lng / alt / speed / bearing / accuracy / ts extras
   └── 呼叫 MockProvider.update()
   ↓
MockProvider（包裝 LocationManager test provider API）
   ├── ensureRegistered() → addTestProvider()
   ├── update() → setTestProviderLocation()
   └── clear() → removeTestProvider()
   ↓
系統 GPS Provider（mock）→ Location-aware Apps
```

### 核心元件

| 元件 | 職責 |
|------|------|
| `MockProvider` (object) | 包裝 `LocationManager` test provider API；ensureRegistered / update / clear |
| `LocationBroadcastReceiver` | 監聽 `SET_LOCATION` / `CLEAR_LOCATION`，含 UID 白名單檢查 |
| `MainActivity` (Compose) | 三段透明聲明 + 即時狀態 + 開發者選項引導 |
| `BootCompletedReceiver` | 開機預註冊 Test Provider（選配） |
| `ReceiverState` (object) | Process-scoped Compose State，Receiver 寫入、UI 讀取 |

### 套件結構

```
app/src/main/
├── AndroidManifest.xml
├── java/com/mingyuan/flyto/lander/
│   ├── BootCompletedReceiver.kt
│   ├── LocationBroadcastReceiver.kt
│   ├── MainActivity.kt
│   ├── MockProvider.kt
│   └── ReceiverState.kt
└── res/
    ├── drawable/ic_launcher_foreground.xml
    ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
    ├── values/{colors,strings,themes}.xml
    └── xml/data_extraction_rules.xml
```

## 設計約束（Phase 1 開工前硬性 Gate）

[`docs/android-platform.md` §7](docs/android-platform.md#7-安全性與風險揭露phase-1-開工前約束) 為**硬性約束**，違反者視同 bug，需修正後才能 release：

1. **零網路權限**：不請求 `INTERNET` / `ACCESS_NETWORK_STATE` 等
2. **可審計**：完全開源、Reproducible Build、GPG 簽署 release
3. **最小權限**：僅 `ACCESS_MOCK_LOCATION` / `ACCESS_FINE_LOCATION` / `RECEIVE_BOOT_COMPLETED`
4. **技術硬隔離**：不持久化、零 SDK、Receiver UID 白名單
5. **UI 透明**：MainActivity 顯眼處三段聲明
6. **使用者文件揭露**：README 5 條風險揭露

每次 PR 必須通過 [§7.8 驗收 checklist 10 條](docs/android-platform.md#78-驗收-checklistphase-1-結束前必須通過)，詳見 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

## 建置與執行

```bash
# 環境變數
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

# 建置 debug APK
./gradlew assembleDebug

# 建置 release APK（需 keystore，CI 觸發）
./gradlew assembleRelease

# 跑 Lint
./gradlew lint

# 清理
./gradlew clean

# APK 位置
ls app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
```

### Commit Message 規範

繁體中文 `[類型]` 開頭，與 FlyTo 主 repo 一致：

- `[新增]` 新功能 / 新檔案
- `[修正]` bug fix
- `[更新]` 既有功能改進
- `[文件]` 純文件
- `[重構]` 不改變行為的內部重構
- `[初始]` initial commit
- `[發布]` release tag

範例：`[修正] LocationBroadcastReceiver：補上 callerUid 為 ROOT 時的判斷`

## 開發與測試環境

| 類型 | 配置 | 備註 |
|------|------|------|
| macOS 開發機 | MacBook Pro 16" (Intel) | 與 FlyTo 主 repo 同一台 |
| Android 模擬器 | `flyto_pixel6_api29` / `flyto_pixel6_api33` / `flyto_pixel7_api34` | x86_64 ABI（Intel Mac），詳見 [`docs/android-platform.md` §3.3](docs/android-platform.md#33-多版本矩陣建議) |
| Android 實機 | （尚無） | Phase 3 採購二手 Pixel + Samsung A 系列 |

### 啟動測試流程

```bash
# 1. 啟動 emulator（背景）
nohup "$ANDROID_HOME/emulator/emulator" -avd flyto_pixel6_api33 -no-snapshot > /tmp/emu.log 2>&1 &

# 2. 等 boot 完成
"$ANDROID_HOME/platform-tools/adb" wait-for-device
until [ "$($ANDROID_HOME/platform-tools/adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done

# 3. install APK
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk

# 4. 授權 Mock Location（跳過 GUI 開發者選項）
"$ANDROID_HOME/platform-tools/adb" shell appops set com.mingyuan.flyto.lander android:mock_location allow

# 5. 啟動 App
"$ANDROID_HOME/platform-tools/adb" shell am start -n com.mingyuan.flyto.lander/.MainActivity

# 6. 發送 broadcast（必須用 -n + --ed）
"$ANDROID_HOME/platform-tools/adb" shell am broadcast \
  -n com.mingyuan.flyto.lander/.LocationBroadcastReceiver \
  -a com.mingyuan.flyto.lander.SET_LOCATION \
  --ed lat 25.033964 --ed lng 121.564468

# 7. 看 Logcat
"$ANDROID_HOME/platform-tools/adb" logcat -d | grep FlyToLander

# 8. 關閉 emulator
"$ANDROID_HOME/platform-tools/adb" emu kill
```

### 兩個容易踩的 Pitfall

1. **broadcast 必須用 `-n <component>` 顯式指定 receiver**，否則 Android 8.0+ Background Execution Limits 會 reject 並 log `Background execution not allowed`
2. **`--ed`（double）vs `--ef`（float）**：座標必須用 `--ed`，否則 `getDoubleExtra` 拿不到（回 NaN）

詳見 [`docs/android-platform.md` §2.3](docs/android-platform.md#23-intent-格式) / [§5.2](docs/android-platform.md#52-模擬位置)。

## 跨專案關係

| 配套專案 | URL | 角色 |
|---------|-----|------|
| **FlyTo**（macOS App 主 repo，Private） | [`Jaofeng/FlyTo`](https://github.com/Jaofeng/FlyTo) | 桌面端 GPS 模擬工具；Phase 2 在該 repo 實作 `Core/Platform/AndroidPlatform` 對接本 helper |

兩 repo 的 Intent 格式 / Permission 約束 / Background Execution Limits 等規格以 **本 repo `docs/android-platform.md` 為單一真實來源**；macOS 端對應為 §8、§5.2 / §2.3。

## 文件索引

| 檔案 | 內容 |
|------|------|
| [README.md](README.md) | 使用者導覽、安裝步驟、5 條風險揭露 |
| [docs/android-platform.md](docs/android-platform.md) | **完整設計文件**（11 章、§7 安全約束、§7.8 驗收 checklist、Phase 0/1/2/3/4 路線） |
| [SECURITY.md](SECURITY.md) | 資安回報流程 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 貢獻流程、commit message 規範、PR §7.8 checklist |
| [LICENSE](LICENSE) / [NOTICE](NOTICE) | Apache-2.0 |

## 已知限制

- 實機行為 / 廠商 ROM 客製化（MIUI / OneUI / ColorOS）尚未驗證（Phase 3 採購實機後補）
- Wi-Fi adb 在不同網路環境穩定性未驗證（Phase 4）
- 部分強防詐 App（銀行、行動支付）會偵測 `Location.isFromMockProvider()` 拒絕運行 —— 系統限制，非 bug
- 無法上 Google Play（政策禁止 mock location 類 App）—— 計畫內，走 GitHub Release 散佈
