# 簡單錄影（Simple Recorder）

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg)](https://kotlinlang.org)

簡單錄影是一款以 Kotlin 與 Jetpack Compose 開發的 Android 螢幕錄影工具。沒有帳號、沒有雲端、沒有廣告，所有影片只留在裝置本機。每段錄影最長 5 分鐘，可選擇是否收錄麥克風旁白，錄影前可設定 3／5／10 秒倒數。錄完可直接在 App 內預覽、全螢幕播放、重新命名、剪輯頭尾另存副本，或透過 Android 分享面板分享 MP4。

## 功能特色

- **螢幕錄影**：透過 `MediaProjection` + `MediaRecorder` 錄製 H.264／MP4，每段上限 5 分鐘，到時自動停止並儲存。
- **可選麥克風旁白**：開錄前開啟「麥克風錄音」即可收錄旁白與環境聲音（AAC 單聲道）；拒絕權限時仍可只錄畫面。
- **錄影前倒數**：可選擇無倒數或 3／5／10 秒倒數，倒數期間可點畫面或按取消鍵中止。
- **前景服務通知**：錄影中顯示常駐通知，可從通知停止錄影或回到 App；剩餘 30 秒時提醒。
- **影片庫**：依時間排序，顯示縮圖、名稱、日期與長度；支援下拉載入與空狀態提示。
- **預覽與全螢幕**：Media3／ExoPlayer 播放，支援音訊焦點、耳機拔除暫停、播放時螢幕恆亮與全螢幕模式。
- **重新命名**：自動全選名稱、顯示字數，阻擋空白、超長、開頭為點或含非法字元的名稱。
- **剪輯另存**：以 Media3 Transformer 保留指定片段並另存副本，原始影片不受影響；可取消匯出。
- **分享與刪除**：透過 `FileProvider` 分享 MP4；刪除前需二次確認。
- **顯示設定**：跟隨系統／淺色／深色模式；Android 12 以上可啟用桌布動態色彩。
- **隱私優先**：不需網路權限，錄影檔存放於 App 私有目錄，不上傳、不蒐集個人資料。

## 系統需求

| 項目 | 需求 |
| --- | --- |
| 執行環境 | Android 8.0（API 26）以上 |
| 建置環境 | JDK 17 以上、Android SDK 36 |
| 建置工具 | Gradle 8.13（Wrapper）、AGP 8.13.2、Kotlin 2.2.20 |
| 開發 IDE | Android Studio（建議）或命令列 |

> Android 14 以上可使用系統提供的「單一 App」螢幕擷取選項。

## 建置與安裝

### Debug 版本（開發與日常測試）

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Pre-release 版本（R8 壓縮、Debug 金鑰簽署）

`prerelease` Build Type 繼承 `release` 設定（啟用 R8 與資源縮減），但使用 Debug 金鑰簽署，適合內部測試，**不可用於正式上架**。

```powershell
.\gradlew.bat assemblePrerelease
```

輸出位於 `app\build\outputs\apk\prerelease\app-prerelease.apk`。本專案的 GitHub Pre-release 附件即為此檔案（版本 `1.0.0-prerelease`）。

### Release 版本

```powershell
.\gradlew.bat assembleRelease
```

正式版本需自行設定正式簽章金鑰；本倉庫不包含任何 keystore。

## 使用方式

1. 首次開啟後，點「開始錄影」。
2. 依需求開啟或關閉「麥克風錄音」。
3. （選用）在設定頁選擇倒數秒數。
4. 於系統對話框允許螢幕擷取；若開啟麥克風，另需允許錄音權限。
5. 錄影中可從 App 或通知按「停止錄影」。
6. 錄完後在「近期錄影」點選影片，即可預覽、分享、剪輯、重新命名或刪除。

完整操作說明請見 [使用指南](docs/USER_GUIDE.md)。

## 權限說明

| 權限 | 用途 | 請求時機 |
| --- | --- | --- |
| `RECORD_AUDIO` | 錄製麥克風旁白 | 開啟麥克風並開始錄影時 |
| `POST_NOTIFICATIONS` | 顯示錄影與儲存結果通知（Android 13+） | 首次開始錄影時 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 錄影期間維持前景服務 | 安裝時宣告 |
| `FOREGROUND_SERVICE_MICROPHONE` | 含麥克風錄影時的前景服務類型 | 安裝時宣告 |
| `ACCESS_NETWORK_STATE` | 由 Media3／ExoPlayer 依賴合併，僅用於檢查連線狀態 | 安裝時宣告 |

App **不請求 `INTERNET` 權限**，無法將影片或資料上傳到網際網路。

## 專案結構

```text
app/src/main/java/com/vibecode/simplerecorder/
├── MainActivity.kt            # 權限、倒數、螢幕擷取同意與分享入口
├── data/                      # 影片檔案、DataStore 設定
├── editing/                   # Media3 Transformer 剪輯匯出
├── recording/                 # 前景服務、MediaProjection、狀態機
└── ui/                        # Compose 畫面、ViewModel、主題
docs/                          # 使用、架構、設計、隱私與開發紀錄
```

## 文件

- [使用指南](docs/USER_GUIDE.md)
- [系統架構與技術文件](docs/ARCHITECTURE.md)
- [系統設計文件](docs/DESIGN.md)
- [隱私說明](docs/PRIVACY.md)
- [開發紀錄](docs/DEV_LOG.md)
- [版本異動](CHANGELOG.md)

## 已知限制

- 不擷取 App 內部播放音訊（Android 10 以下的系統限制；本版未啟用內部音訊錄製）。
- 輸出解析度於開錄時決定，錄影中旋轉裝置或改變擷取視窗大小可能產生黑邊。
- 錄影長度上限固定為 5 分鐘，無法在設定中調整。
- 影片儲存在 App 私有目錄，解除安裝 App 會一併移除所有錄影。
- 尚未在全部 Android 版本與各家 OEM 裝置完成實機驗證。

## 授權

本專案採用 [MIT License](LICENSE)，Copyright (c) 2026 mark216tw。

使用之 AndroidX、Jetpack Compose 與 Media3 依 Apache License 2.0 授權。
