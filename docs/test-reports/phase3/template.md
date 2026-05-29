# 實機測試報告 — _vendor-model-rom-version_

> 從本 template copy 一份、依實機資訊重命名後填寫。詳細命名規則見 [README.md](README.md#報告命名規則)。

## 機台資訊

| 項目 | 值 |
|---|---|
| 廠商 / 品牌 | _譬如 Xiaomi_ |
| 機型 | _譬如 Redmi Note 13 Pro_ |
| Android version | _譬如 Android 14_ |
| ROM version | _譬如 HyperOS 1.0.5.0_ |
| 採購來源 | _譬如 二手露天 / 蝦皮、購入日期_ |
| 測試 Lander 版本 | _譬如 v0.1.1-rc2_ |
| 測試 APK SHA-256 | _release artifact 的 SHA_ |
| 測試日期 | _YYYY-MM-DD_ |

## 1. Mock 注入驗證

**驗證內容**：座標寫入後系統能立刻讀回。

### 操作步驟

```bash
# 1. 開發者選項把 Lander 設為「Mock Location App」
# 2. 桌面端 FlyTo 發 SET_LOCATION broadcast
# 3. 用 adb 跑 location 讀取驗證
adb shell dumpsys location | grep "last location" | head -3
```

### 結果

| 項目 | 期望 | 實際 |
|---|---|---|
| `LocationManager.getLastKnownLocation()` 能立刻讀回 broadcast 內座標 | ✅ | _填寫_ |
| `Location.isFromMockProvider()` 回 `true` | ✅ | _填寫_ |
| 座標精度 / accuracy 欄位正確對應 | ✅ | _填寫_ |
| 連續 broadcast 100 次成功率 | ≥ 95% | _填寫_ |

### Logcat 摘錄

```
（貼相關 logcat 行、刪敏感資訊）
```

## 2. App 互通驗證

**驗證內容**：至少 3 個常用 location-aware App 收到 mock 座標。

### 測試 App 清單

| App | 期望 | 實際 |
|---|---|---|
| Google Maps | 地圖中心移到 mock 座標 | _填寫_ |
| Uber / Lyft / 計程車 App | 起點顯示 mock 座標 | _填寫_ |
| 氣象 App (譬如 Weather.com) | 顯示 mock 座標當地氣象 | _填寫_ |
| _自選 1_ | _填寫_ | _填寫_ |
| _自選 2_ | _填寫_ | _填寫_ |

### 螢幕截圖

```
（檔名連結至 screenshots/ 子目錄）
```

## 3. 風控行為驗證

**驗證內容**：銀行 / 行動支付 App 在 mock 模式下行為。**拒絕運行屬預期、不是 bug**，但要文件化清單給使用者知情。

### 測試 App 清單

| App | 行為 | 備註 |
|---|---|---|
| 中國信託 Home Bank | _拒運行 / 提示警告 / 正常運行_ | _填寫_ |
| 玉山 Wallet | _填寫_ | _填寫_ |
| 街口支付 | _填寫_ | _填寫_ |
| LINE Pay | _填寫_ | _填寫_ |
| 悠遊付 | _填寫_ | _填寫_ |

> 預期多數會偵測 mock 並拒絕運行（用 `Location.isFromMockProvider()` API），請文件化此清單給使用者參考。

## 4. 開機自動 mock 驗證

**驗證內容**：實機重開機後、Lander 的 `BootCompletedReceiver` 是否正常觸發、test provider 是否預註冊成功。

### 操作步驟

```bash
# 1. 確認 Lander 已被選為 Mock Location App
# 2. adb shell reboot
# 3. 開機完成後不開 Lander、直接讀 dumpsys location 看 test provider 是否已註冊
adb shell dumpsys location | grep "Test Providers" -A 5
```

### 結果

| 項目 | 期望 | 實際 |
|---|---|---|
| BootCompletedReceiver 被觸發（logcat 可見） | ✅ | _填寫_ |
| Test provider 已預註冊 | ✅ | _填寫_ |
| 廠商 power management 是否擋住 RECEIVE_BOOT_COMPLETED | ❌ 不擋 | _填寫_ |
| 重開機 3 次成功率 | ≥ 95% | _填寫_ |

### Logcat 摘錄

```
（貼開機相關 logcat 行）
```

## 5. 移除流程驗證

**驗證內容**：uninstall Lander 後系統 location stack 立即恢復、無殘留 test provider。

### 操作步驟

```bash
adb uninstall com.mingyuan.flyto.lander
adb shell dumpsys location | grep "Test Providers" -A 5
# 期望輸出：Test Providers: （無）或 GPS_PROVIDER 不再標 mock
```

### 結果

| 項目 | 期望 | 實際 |
|---|---|---|
| Test provider 完全移除 | ✅ | _填寫_ |
| `LocationManager.getLastKnownLocation()` 回真實 GPS 座標（或 null） | ✅ | _填寫_ |
| 系統 location stack 無 stale entry | ✅ | _填寫_ |

## 總結

| 項目 | 結果 |
|---|---|
| Mock 注入 | ✅ / ⚠️ / ❌ |
| App 互通（測試 N 個成功 M 個） | _N/M_ |
| 風控偵測（拒運行 X 個 / 總 Y 個） | _X/Y_ |
| 開機自動 mock | ✅ / ⚠️ / ❌ |
| 移除流程 | ✅ / ⚠️ / ❌ |

### 已知問題

_列舉此 ROM 上的 quirk / workaround / 不影響功能但需文件化的小問題_

### 給使用者的建議

_譬如「本機型風控偵測較嚴、建議避免在 mock 模式下打開銀行 App」_
