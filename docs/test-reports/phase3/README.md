# Phase 3 實機測試報告

本目錄收集 **Phase 3：實機行為 / 廠商 ROM 客製化驗證** 的測試紀錄。Phase 3 啟動條件、測試範圍與 exit criteria 在主 [phase-progress 記錄](https://github.com/MingYuan-Tech/FlyTo-Lander/issues?q=label%3Aphase-3)（或本 repo 開發者 memory）定義；本目錄僅承擔**測試紀錄**。

## 為何需要實機測試

Lander Phase 1.x 全程僅在 Android emulator (AVD) 上跑（API 33 / 34 / 35 / 36）。對 GPS mock 這類 App，emulator 不足以涵蓋：

- 各廠商 ROM 對 `LocationManager.addTestProvider` 的細節差異（MIUI / OneUI / ColorOS 都有 customized location stack）
- 銀行 / 行動支付 App 用 `Location.isFromMockProvider()` 等 API 偵測 mock 的實機行為
- `BootCompletedReceiver` 在不同 ROM 開機流程下的觸發可靠性
- 廠商 power management / background restriction 對 BroadcastReceiver 的影響

## 測試矩陣總覽

| 廠商 / ROM | 機型 | Android | 採購狀態 | 報告 |
|---|---|---|---|---|
| Xiaomi MIUI / HyperOS | _待採購_ | 13+ | ⏳ | _尚未填寫_ |
| Samsung OneUI | _待採購_ | 13+ | ⏳ | _尚未填寫_ |
| OPPO ColorOS 或 Vivo OriginOS | _待採購_ | 13+ | ⏳ | _尚未填寫_ |

## 報告命名規則

每台實機一份報告，命名 `<vendor>-<model>-<rom-version>.md`，譬如：

- `xiaomi-redmi-note-13-hyperos-1.0.md`
- `samsung-galaxy-a55-oneui-6.1.md`
- `oppo-find-x7-coloros-14.md`

採購到實機後，從 [template.md](template.md) copy 一份，依機型重命名後逐項填寫。

## 各項測試的「PASS」定義

詳見 [template.md](template.md) 內每節「期望」段落。最終 exit criteria（見 [phase-progress 紀錄](https://github.com/MingYuan-Tech/FlyTo-Lander/issues?q=label%3Aphase-3)）：

- 至少 3 個廠商 ROM 上 mock 注入成功率 ≥ 95%
- 5 個常用 App 拿到 mock 座標的清單已記錄
- 銀行 / 支付 App 風控行為清單已記錄（哪些 App 拒運行屬預期、不是 bug）
- README 補入「已驗證實機列表」表格
- docs/android-platform.md 「已知限制」表更新

## 與 emulator 測試的差異

emulator 已驗的「跨版功能正常」屬 sanity check、不重複；本目錄聚焦**只有實機才能驗的行為**：

- ROM customization 差異
- 真實 location-aware App 整合（Google Maps、Uber 等都不在 emulator default image 內）
- 銀行 App / 行動支付（emulator Play Protect 通常擋）
- 開機流程 timing（emulator 開機行為跟實機差很大）
