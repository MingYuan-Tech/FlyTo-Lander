# FlyTo Lander

> **本 App 不請求網路權限 / This app requests NO `INTERNET` permission.**
> 
> Verify in [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml).

FlyTo Lander 是 macOS 工具 **[FlyTo](https://flyto.mytechs.com.tw/)** 在 Android 端的 helper App。FlyTo 在 macOS 端模擬 GPS 座標，透過 `adb` 對 Android 裝置發送 broadcast；FlyTo Lander 在裝置上接收 broadcast，呼叫系統 `LocationManager.setTestProviderLocation` 把座標灌入 Android Location 框架，讓裝置上的其他 App 看到模擬後的位置。

- **套件名稱**：`com.mingyuan.flyto.lander`
- **散佈方式**：本 repo 的 GitHub Release（**不上 Google Play**）
- **授權**：[Apache-2.0](LICENSE)
- **零網路、零追蹤、零第三方 SDK**

---

## 目錄

- [FlyTo Lander](#flyto-lander)
  - [目錄](#目錄)
  - [為什麼需要這個 App](#為什麼需要這個-app)
  - [安裝步驟](#安裝步驟)
  - [使用方式](#使用方式)
    - [設定模擬座標](#設定模擬座標)
    - [清除模擬座標](#清除模擬座標)
    - [查看執行日誌](#查看執行日誌)
  - [移除步驟](#移除步驟)
  - [風險揭露](#風險揭露)
    - [我們做了哪些事降低你的疑慮](#我們做了哪些事降低你的疑慮)
  - [驗證 APK 完整性](#驗證-apk-完整性)
    - [驗證 SHA-256](#驗證-sha-256)
    - [驗證 GPG 簽章](#驗證-gpg-簽章)
  - [Reproducible Build](#reproducible-build)
  - [回報資安問題](#回報資安問題)
  - [授權](#授權)
  - [相關專案](#相關專案)

---

## 為什麼需要這個 App

Android 9 (API 28) 之後，AOSP 的安全模型不允許「外部程序直接注入 mock location」。`LocationManagerService.checkMockLocation()` 規定：

- 呼叫 `setTestProviderLocation` 必須來自一個 **持有 `ACCESS_MOCK_LOCATION` 的 App**
- 該 App 還必須被使用者在 **Developer Options → Select mock location app** 明確指定
- `adb shell` 的 uid (`2000 / shell`) 不對應任何 package，`AppOpsManager.checkOp(OP_MOCK_LOCATION)` 永遠 reject

因此 macOS 端不可能跳過 App 直接灌位置，**一定要有一個常駐的 helper App 接 broadcast、再以 App 自身身分呼叫 LocationManager**。FlyTo Lander 就是這個角色。

業界自動化測試平台（Appium 的 `io.appium.settings`、BrowserStack、Sauce Labs、HeadSpin、Bitbar）採用的是完全相同的雙端架構，這是 AOSP 安全模型下的唯一合法路徑，不是 FlyTo 的設計選擇。

---

## 安裝步驟

> 建議使用 **測試專用裝置**，不要裝在主力手機上（見[風險揭露](#風險揭露)）。

1. 在 Android 裝置上開啟開發者選項
   - **設定 → 關於手機 → 連點 7 次 Build number**
2. 啟用 USB Debugging
   - **設定 → 系統 → 開發者選項 → USB 偵錯**
3. 從本 repo 的 [Releases](https://github.com/MingYuan-Tech/FlyTo-Lander/releases) 下載最新 `flyto-lander.apk`
4. 連接電腦並透過 adb 安裝
   ```bash
   adb install flyto-lander.apk
   ```
5. 將 FlyTo Lander 指定為 Mock Location App
   - **設定 → 系統 → 開發者選項 → Select mock location app → FlyTo Lander**
6. 驗證授權狀態
   ```bash
   adb shell appops get com.mingyuan.flyto.lander android:mock_location
   # 預期輸出包含：android:mock_location: allow
   ```

完成後即可從 FlyTo macOS App 控制此裝置。

---

## 使用方式

FlyTo macOS App 會自動代發以下 broadcast；本節指令僅供除錯使用。

### 設定模擬座標

```bash
adb -s <serial> shell am broadcast \
  -a com.mingyuan.flyto.lander.SET_LOCATION \
  --ef lat 25.033964 --ef lng 121.564468
```

可選的 extras：

| key | 型別 | 說明 |
|-----|------|------|
| `lat` | double | 緯度（必填） |
| `lng` | double | 經度（必填） |
| `alt` | double | 海拔，單位公尺 |
| `speed` | float | 速度，單位 m/s |
| `bearing` | float | 方向，單位度（0–360） |
| `accuracy` | float | 精度半徑，單位公尺 |
| `ts` | long | timestamp（Unix epoch ms） |

> 座標精度沿用 FlyTo 規範：小數點後 6 位。

### 清除模擬座標

```bash
adb -s <serial> shell am broadcast \
  -a com.mingyuan.flyto.lander.CLEAR_LOCATION
```

### 查看執行日誌

```bash
adb -s <serial> logcat -s FlyToLander:V *:S
```

---

## 移除步驟

完整解除流程（4 步缺一不可）：

1. **Uninstall APK**
   ```bash
   adb uninstall com.mingyuan.flyto.lander
   ```
2. **取消 Mock Location App 指定**
   - **設定 → 系統 → 開發者選項 → Select mock location app → None**
3. **關閉 USB Debugging**
   - **設定 → 系統 → 開發者選項 → USB 偵錯（關閉）**
4. **關閉開發者選項**
   - **設定 → 系統 → 開發者選項 → 主開關（關閉）**

---

## 風險揭露

啟用 FlyTo Lander 牽涉系統等級的權限變更，使用前請務必理解以下 5 條風險：

1. **建議使用測試專用裝置**，不要裝在主力手機上。
2. **USB Debugging 啟用後**，任何能接上 USB 的人都能透過 `adb shell` 操作這台裝置；不要把手機接到不信任的電腦或公共充電站。
3. **Mock Location 啟用後**，部分金融、防作弊類 App（如 Line Pay、行動銀行、神魔之塔）會檢測 `Location.isFromMockProvider()` 並拒絕運行 —— 這是系統行為，不是 bug；用完關閉 Mock Location 即恢復。
4. **完整移除步驟**：uninstall APK → Developer Options → Select mock location app → None → 關閉 USB Debugging → 關閉開發者選項（見上方[移除步驟](#移除步驟)）。
5. **回報資安問題**：請透過 [GitHub Security Advisory](https://github.com/MingYuan-Tech/FlyTo-Lander/security/advisories/new) 私下回報，請勿開 public issue（詳見 [SECURITY.md](SECURITY.md)）。

### 我們做了哪些事降低你的疑慮

| 層級 | 措施 |
|------|------|
| 程式碼 | 完全開源（本 repo 為 Public），任何人可審計 |
| Manifest | **絕不**請求 `INTERNET` / `ACCESS_NETWORK_STATE` / `READ_PHONE_STATE` / 外部儲存 / 背景定位 / `QUERY_ALL_PACKAGES` |
| 依賴 | 僅 Kotlin stdlib + AndroidX core/appcompat + Compose Material；無 Firebase / Crashlytics / Sentry / App Center 等任何 analytics / crash SDK |
| 資料 | 不持久化座標；每筆 broadcast 處理完即丟，不寫入 SharedPreferences / Room / 任何檔案 |
| Broadcast | `LocationBroadcastReceiver.onReceive` 內以 `Binder.getCallingUid()` 限制只接受 uid 2000（shell）與本 App 自身 |
| Release | GPG 簽署的 git tag、附 APK SHA-256、Reproducible Build |

---

## 驗證 APK 完整性

每個 Release 都會同時提供：

| 檔案 | 用途 |
|------|------|
| `flyto-lander-<version>.apk` | App 本體 |
| `flyto-lander-<version>.apk.sha256` | APK 的 SHA-256 雜湊值 |
| `flyto-lander-<version>.apk.asc` | 工作室 GPG 簽章 |

### 驗證 SHA-256

```bash
shasum -a 256 flyto-lander-<version>.apk
# 對照 flyto-lander-<version>.apk.sha256 內的雜湊值
```

### 驗證 GPG 簽章

工作室 GPG public key：

| 項目 | 值 |
|------|----|
| Key ID | `DCD89190E52012AE` |
| Fingerprint | `2DC1 ED20 2ED5 34C7 9DF0  EF18 DCD8 9190 E520 12AE` |
| Email | `release@mytechs.com.tw` |
| Public key 檔案 | [`docs/release-pubkey.asc`](docs/release-pubkey.asc) |

匯入工作室公鑰（首次驗證需要，二擇一）：

```bash
# 方法 A：從本 repo 的 public key 檔案匯入（推薦，無外部依賴）
curl -O https://raw.githubusercontent.com/MingYuan-Tech/FlyTo-Lander/main/docs/release-pubkey.asc
gpg --import release-pubkey.asc

# 方法 B：從公開 keyserver 拉（如已上傳）
gpg --keyserver keys.openpgp.org --recv-keys DCD89190E52012AE
```

匯入後**務必**驗證 fingerprint 是否與上方一致（防止假冒同 email 的 key）：

```bash
gpg --fingerprint DCD89190E52012AE
# 期望輸出含：2DC1 ED20 2ED5 34C7 9DF0  EF18 DCD8 9190 E520 12AE
```

fingerprint 正確後驗證簽章：

```bash
gpg --verify flyto-lander-<version>.apk.asc flyto-lander-<version>.apk
# 期望輸出含 "Good signature from MingYuan Tech Studio <release@mytechs.com.tw>"
```

---

## Reproducible Build

本專案承諾 **build content（classes.dex、resources、assets 等）任何人從同一個 git commit 重建出的 APK 必須產生相同 SHA-256**。

> ℹ️ **APK Signing Block 已知例外**：APK 的 signing 部分（用工作室 release keystore 簽章）因 [`apksig` 函式庫的內部行為](https://issuetracker.google.com/issues/?q=apksig%20deterministic) 而每次 build 不同（`apksigner` 0.9 也無 RSA 對應 deterministic flag），是業界 known limitation（F-Droid 等 reproducible 專案多年同樣議題）。**Build content 本身（所有 ZIP entry CRC）完全 deterministic、可逐 byte 驗證**。

每次 release 提供兩種 APK，各司其職：

| Asset | 用途 | reproducibility |
|---|---|---|
| `flyto-lander-<version>.apk` | 實際安裝、GPG 簽署 | signing block 約 5KB 非確定性；其餘 build content 一致 |
| `flyto-lander-<version>-unsigned.apk` | reproducible 比對驗證 | **完全 deterministic** |

每次 release 的 release note 會記載：

- Git commit hash
- Gradle 版本
- Android Gradle Plugin (AGP) 版本
- JDK 版本（temurin）

### 本機驗證 reproducible build

```bash
git clone https://github.com/MingYuan-Tech/FlyTo-Lander.git
cd FlyTo-Lander
git checkout <release-tag>          # 例如 v0.1.1-rc2
./gradlew assembleRelease           # 不傳 -PRELEASE_STORE_FILE → 自動產 unsigned APK
shasum -a 256 app/build/outputs/apk/release/app-release-unsigned.apk
# 期望輸出 = release 頁面 flyto-lander-<version>-unsigned.apk.sha256 的內容
```

若 SHA 一致，代表你本機 build 出來的 APK 內容（classes.dex / resources / assets / native libs）與 release 發行的完全相同。發行 APK 真實性由 GPG 簽章驗證（見上方「驗證 GPG 簽章」），不靠 signed APK 的 SHA。

CI 中亦有 [reproducible-build workflow](.github/workflows/reproducible-build.yml) 自動驗證 unsigned APK SHA 一致性，不一致時自動開 issue。

---

## 回報資安問題

請 **不要** 開 public issue 揭露安全性問題。請透過：

- [GitHub Security Advisory](https://github.com/MingYuan-Tech/FlyTo-Lander/security/advisories/new)

完整政策見 [SECURITY.md](SECURITY.md)。

---

## 授權

[Apache License 2.0](LICENSE)

Copyright 2026 MingYuan Tech Studio.

---

## 相關專案

| 專案 | 說明 |
|------|------|
| [FlyTo](https://flyto.mytechs.com.tw/) | macOS 端主程式 |
| [Appium io.appium.settings](https://github.com/appium/io.appium.settings) | 業界對照組，相同雙端架構 |

---

<sub>銘源科技工作室 / MingYuan Tech Studio</sub>
