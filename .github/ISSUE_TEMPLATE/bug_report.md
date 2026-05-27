---
name: Bug 回報
about: 回報 FlyTo Lander 的功能異常（非安全性問題）
title: "[Bug] "
labels: bug
assignees: ''
---

> 安全性問題請勿開 public issue，請改走 [GitHub Security Advisory](https://github.com/MingYuan-Tech/FlyTo-Lander/security/advisories/new)。詳見 [SECURITY.md](../../SECURITY.md)。

## 環境資訊

| 項目 | 內容 |
|------|------|
| Android 版本 | 例：Android 14 (API 34) |
| 裝置型號 | 例：Pixel 6 / Galaxy S22 / AVD pixel6_api33 |
| 是否為 AVD | 是 / 否 |
| FlyTo Lander 版本 | 例：v1.0.0（畫面下方或 `adb shell dumpsys package com.mingyuan.flyto.lander \| grep versionName`） |
| FlyTo macOS App 版本 | 例：1.0.36 (build 90) |
| macOS 版本 | 例：macOS 14.5 |
| adb 版本 | `adb --version` 第一行 |

## 復現步驟

1.
2.
3.

## 預期行為

<!-- 你預期應該發生什麼？ -->

## 實際行為

<!-- 實際發生了什麼？ -->

## Logcat 輸出（若有）

```
adb -s <serial> logcat -s FlyToLander:V *:S
```

```
（貼上相關 log，請先移除任何個人資料）
```

## 截圖（若適用）

<!-- 拖曳圖片到這裡 -->

## 其他補充

<!-- 任何你覺得對排查有幫助的資訊 -->
