# 貢獻指南

感謝你對 FlyTo Lander 的興趣！本專案是 macOS 工具 [FlyTo](https://flyto.mytechs.com.tw/) 的 Android 端 helper App，因為直接觸碰系統 Mock Location 能力，**任何貢獻都必須符合 [docs/android-platform.md §7 安全性與風險揭露](docs/android-platform.md#7-安全性與風險揭露phase-1-開工前約束)的約束**。

本文件說明提交貢獻的流程。請在送 PR 前讀完。

---

## 目錄

- [貢獻指南](#貢獻指南)
  - [目錄](#目錄)
  - [行為準則](#行為準則)
  - [提交流程](#提交流程)
  - [分支與 commit message 慣例](#分支與-commit-message-慣例)
    - [分支命名](#分支命名)
    - [Commit message 格式](#commit-message-格式)
  - [PR 驗收 Checklist](#pr-驗收-checklist)
  - [安全性問題不走這條路](#安全性問題不走這條路)

---

## 行為準則

- 以**繁體中文**或英文溝通皆可，文件預設繁體中文。
- 對事不對人，避免人身攻擊。
- 重視使用者隱私與資安：任何貢獻不得削弱本專案的「零網路、零追蹤、零持久化」承諾。

---

## 提交流程

1. **先開 issue 討論**（除非是修正 typo 或 1–2 行的明顯 bug fix）
   - Bug：用 [Bug 回報](.github/ISSUE_TEMPLATE/bug_report.md) 模板
   - 功能：用 [功能建議](.github/ISSUE_TEMPLATE/feature_request.md) 模板
   - 取得維護者方向認可後再動手，避免白工。
2. **Fork → 建立分支** （見 [分支與 commit message 慣例](#分支與-commit-message-慣例)）
3. **在本地實作與測試**
   - 至少跑過 `./gradlew assembleDebug` 確認可建置
   - 在 AVD 上手動驗證 broadcast 行為
   - 若改動觸及安全性敏感區（Manifest、Receiver、build.gradle 依賴），請在 PR 描述特別標註
4. **送 PR**
   - PR 標題沿用 commit message 風格
   - PR 描述至少包含：解決什麼問題、做了什麼變更、如何驗證
   - 勾選下方 [PR 驗收 Checklist](#pr-驗收-checklist)
5. **回應 review**
   - 維護者可能會請你補測試 / 調整實作 / 更新文件
   - PR 通過 CI 並由維護者 approve 後合併

---

## 分支與 commit message 慣例

### 分支命名

- `feat/<short-desc>`：新功能
- `fix/<short-desc>`：bug 修正
- `docs/<short-desc>`：文件
- `chore/<short-desc>`：CI、build script、依賴升級等

### Commit message 格式

沿用 FlyTo 的繁體中文慣例：**`[類型]` 開頭** + 一行描述。例：

```
[新增] LocationBroadcastReceiver：解析 lat/lng extras 並呼叫 MockProvider
[修正] Compose UI 在 Android 13 上的權限狀態顯示
[文件] README：補充 GPG 簽章驗證步驟
[清理] 移除未使用的 Coil 依賴
```

常用類型：

| 類型 | 用途 |
|------|------|
| `[新增]` | 新功能、新檔案 |
| `[修正]` | bug 修正 |
| `[優化]` | 效能、UX、可讀性改善（無新功能） |
| `[文件]` | README / CONTRIBUTING / 註解等 |
| `[清理]` | 刪除死碼、移除依賴 |
| `[CI]` | GitHub Actions workflow 變動 |
| `[發布]` | 版本號 bump、release 相關 |

**不要**：

- 不要在 commit message 內加上 `Generated with Claude` 或類似自動產生標記
- 不要寫籠統訊息如 `update`、`fix bug`、`改一下`

---

## PR 驗收 Checklist

FlyTo Lander 因為直接持有系統 Mock Location 權限，所有 PR 都必須通過以下 10 條（沿用 FlyTo 規範 `docs/android-platform.md §7.8`）：

- [ ] 1. `AndroidManifest.xml` 不含 `INTERNET` / `ACCESS_NETWORK_STATE`，也不含其他禁止的 permission（`READ_PHONE_STATE` / 外部儲存讀寫 / `ACCESS_BACKGROUND_LOCATION` / `QUERY_ALL_PACKAGES`）
- [ ] 2. `build.gradle`（含 catalog / dependencies block）不含 Firebase / Crashlytics / Sentry / App Center / 任何 analytics SDK
- [ ] 3. `LocationBroadcastReceiver` 內有 `Binder.getCallingUid()` 來源檢查（只接受 uid 2000 shell 與本 App 自身）
- [ ] 4. 所有 Receiver 在 Manifest 標 `android:exported="false"` 或加 signature-level permission
- [ ] 5. `MainActivity` UI 包含三段「不聯網 / 不蒐集 / 開源」聲明 + 即時狀態面板（若 UI 有變動，請附截圖）
- [ ] 6. README 一級標題下方仍保留 `This app requests NO INTERNET permission` 橫幅
- [ ] 7. Release tag 有 GPG 簽章（CI release workflow 自動處理；若手改 workflow 請說明）
- [ ] 8. Release artifact 附 APK SHA-256（CI release workflow 自動處理）
- [ ] 9. CI 驗證 reproducible build：同一 commit 重複建置產生同 SHA-256 APK
- [ ] 10. README 列出「使用前風險揭露」5 條完整保留

> 若你的 PR 僅涉及文件 / CI / 非程式碼變動，部分條目（如 3、4、5）可標為不適用，但請在 PR 描述說明。

---

## 安全性問題不走這條路

如果你發現的是 **資安弱點**（例如可以繞過 uid 檢查、可以遠端注入座標、Manifest 意外暴露元件），請 **不要** 開 public issue 或 PR。請依 [SECURITY.md](SECURITY.md) 走 [GitHub Security Advisory](https://github.com/MingYuan-Tech/FlyTo-Lander/security/advisories/new) 私下回報，等修補方案備妥後再公開揭露。

---

銘源科技工作室 / MingYuan Tech Studio
