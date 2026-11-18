# 開發紀錄

最新紀錄放最上面。每次開發工作都要更新，不要事後補寫。

---

## 2026-11-18 - v1.0.0-prerelease（versionCode 7）

### 目標
將專案整理為第一個可公開測試的版本：補齊繁體中文文件與 MIT License，建立 R8 + Debug 簽署的 `prerelease` 發行檔，發布到 GitHub 公開倉庫與 Pre-release。

### 變更
- `gradle/libs.versions.toml`：`appName` 改為 `1.0.0`、`appCode` 改為 `7`；prerelease 的 versionName 為 `1.0.0-prerelease`。
- `LICENSE`（新增）：MIT License，Copyright (c) 2026 mark216tw。
- `README.md`：改寫為專案說明、功能、建置、權限、文件索引與已知限制。
- `docs/USER_GUIDE.md`（新增）：安裝、權限、錄影、影片庫、剪輯、設定與疑難排解。
- `docs/ARCHITECTURE.md`（新增）：技術基線、分層架構、錄影時序、狀態機、執行緒模型、建置變體。
- `docs/DESIGN.md`（新增）：需求、畫面流程、互動設計、資料設計、錯誤處理、驗收清單與風險。
- `docs/PRIVACY.md`（新增）：權限、儲存、分享與資料刪除說明。
- `CHANGELOG.md`、`RELEASE_NOTES.md`：新增 1.0.0-prerelease 說明，標示為 Pre-release。
- 建置 `app-prerelease.apk` 並發布至 GitHub Release。

### 問題與解法
- Media3／ExoPlayer 會合併 `ACCESS_NETWORK_STATE` 權限，首次以 `aapt2 dump badging` 檢查時發現；文件補上說明，並強調 App 未宣告 `INTERNET`，無法上傳資料。
- 以 `apksigner verify --print-certs` 確認 prerelease 確實使用 Android Debug 金鑰簽署。
- R8 壓縮後 APK 約 2.4 MB（Debug 約 14 MB），確認 `minifyPrereleaseWithR8` 與資源縮減皆執行。

### 待辦／已知限制
- `assemblePrerelease` 通過；尚未在實機完整跑完 12 項驗收清單。
- GitHub Actions 自動化建置與正式簽章尚未設定。
- Pre-release 使用 Debug 金鑰，不可上架；正式版需另設 release keystore。

---

## 2026-11-18 - v0.1.5（versionCode 6）

### 目標
讓設定頁更緊湊、減少捲動，並把播放器改回上一版（0.1.3）的固定 4:3 顯示。

### 變更
- `ui/RecorderApp.kt`：設定頁改用 `CompactRadioRow`／`CompactSwitchRow`，縮小標題與間距；倒數選項改為單列 `SegmentedButton`；「關於」改為單列顯示版本。
- `ui/RecorderApp.kt`：播放器由依影片比例改回固定 4:3 播放框，保留全螢幕與螢幕恆亮。
- 版本升為 0.1.5（versionCode 6），同步更新 README、CHANGELOG、RELEASE_NOTES。

### 問題與解法
- 倒數選項由四個直式列改為橫排分段按鈕，需使用 Material3 1.3.1 既有的 `SingleChoiceSegmentedButtonRow`，未新增依賴。

### 待辦／已知限制
- `assembleDebug`、`lintDebug` 通過。
- `Recording` 的寬高與 `aspectRatio` 仍保留，目前 UI 未使用。
- 尚未實機確認分段按鈕在窄螢幕的顯示與設定頁整體密度。

---

## 2026-11-18 - v0.1.4（versionCode 5）

### 目標
依介面檢視結果做低風險、高效益的調整：播放比例、倒數取消、錄影進度、清單資訊、About、主題一致性與可用性細節，並清除 DataExtractionRules 警告。

### 變更
- `data/Recordings.kt`：`Recording` 新增寬高與 `aspectRatio`；以單次 `MediaMetadataRetriever` 讀取長度、寬高與 rotation（90/270 交換寬高）。
- `ui/RecorderApp.kt`：播放器依實際比例顯示；倒數加入取消按鈕與提示；錄影中加入進度條；近期清單日期／長度合併一行；空狀態與縮圖佔位圖示；API 26 縮圖後備；重新命名自動全選、IME 完成鍵與字數；About 對話框；動態色彩；狀態列圖示外觀；播放時螢幕恆亮。
- `data/AppPreferences.kt`、`ui/RecorderViewModel.kt`：新增 `dynamicColor` 設定與刪除成功提示。
- `res/values/themes.xml`、`res/values-night/`、`colors.xml`、`xml/backup_rules.xml`、`AndroidManifest.xml`：啟動背景、深色主題與舊版備份規則。
- `app/build.gradle.kts`：啟用 `buildConfig`。
- 版本升為 0.1.4（versionCode 5），同步更新 README、CHANGELOG、RELEASE_NOTES。

### 問題與解法
- 固定 4:3 播放框造成直式影片黑邊 → 由影片 metadata 取得寬高與 rotation 後換算比例。
- Lint 的 `UseKtx` 警告（`Bitmap.createScaledBitmap`）→ 改用 `Bitmap.scale`。
- `DataExtractionRules` 警告 → Manifest 補 `android:fullBackupContent` 與 `backup_rules.xml`。

### 待辦／已知限制
- `assembleDebug`、`lintDebug` 通過（0 錯誤；剩餘 17 項為依賴版本、targetSdk 與 `mipmap-anydpi-v26` 提示）。
- 尚未實機驗證：直／橫式播放比例、API 26 縮圖、動態色彩、狀態列對比與啟動背景。
- 手動強制深色而系統為淺色時，啟動瞬間仍可能看到淺色背景。
- 動態色彩預設關閉，僅 Android 12 以上顯示設定項目。

---

## 2026-11-18 - v0.1.3（versionCode 4）

### 目標
讓倒數在系統螢幕擷取同意後才開始、倒數中可取消，並放大倒數數字、統一返回圖示。

### 變更
- `MainActivity.kt`：倒數移入 `projectionConsent` 成功回呼，同意後才倒數並啟動服務；新增 `startCapture`、`cancelCountdown`。
- `ui/RecorderApp.kt`：新增 `cancelCountdown` 參數與倒數期間的 `BackHandler`；TopAppBar 返回改箭頭；`RecorderTheme` 套用自訂字型。
- `ui/theme/Type.kt`（新增）：`displayLarge` 改為 114sp（原 57sp）。
- `res/drawable/ic_arrow_back.xml`（新增）、`strings.xml` 新增「已取消錄影」。

### 問題與解法
- 倒數改到同意後，`requestProjection()` 不再負責倒數；音訊權限與略過麥克風流程改直接呼叫 `launchProjection()`。

### 待辦／已知限制
- `assembleDebug`、`lintDebug` 通過；尚未實機驗證倒數取消、倒數字級與返回箭頭。
- 倒數期間若 App 進入背景，`onStop` 仍會取消倒數且不顯示提示。

---

## 2026-11-18 - v0.1.2（versionCode 3）

### 目標
修正 App 圖示空白，補齊預覽全螢幕、影片重新命名、近期清單資訊，並加入設定頁與錄影前倒數。

### 變更
- `res/`、`AndroidManifest.xml`：新增 adaptive launcher icon（前景、背景、monochrome），改用 `@mipmap/ic_launcher`。
- `ui/RecorderApp.kt`：預覽新增全螢幕 Dialog（隱藏系統列、返回鍵退出）；新增齒輪入口與設定頁；近期清單改為名稱、日期時間、長度三行；「裁剪」改「剪輯」。
- `data/Recordings.kt`：新錄影改用時間戳命名，提供 `rename` 與顯示名稱；舊 UUID 檔名以錄影時間產生預設名稱。
- `data/AppPreferences.kt`：以 DataStore 保存顯示模式與倒數秒數。
- `ui/RecorderViewModel.kt`：載入設定、提供顯示模式／倒數設定與重新命名操作。
- `MainActivity.kt`：倒數期間顯示大型數字，倒數結束後才進入系統擷取授權；離開畫面會取消倒數。
- 版本升為 0.1.2（versionCode 3），同步更新 README、CHANGELOG、RELEASE_NOTES。

### 問題與解法
- adaptive-icon 放在 `mipmap-anydpi` 會讓 AAPT 找不到 `@mipmap/ic_launcher`；改放 `mipmap-anydpi-v26` 後可正常連結，Lint 僅留下 `ObsoleteSdkInt` 警告。
- 全螢幕 Dialog 的視窗不一定能從 `LocalView.parent` 取得，改為同時嘗試 `parent` 與 `context` 的 `DialogWindowProvider`。
- 倒數中途若 App 進入背景，協程取消可能停在最後一個數字；在 `onStop` 主動清除倒數狀態。

### 待辦／已知限制
- `assembleDebug`、`lintDebug` 通過（0 錯誤、17 警告，主要為依賴版本與 targetSdk 提示）；尚未實機驗證全螢幕、重新命名、倒數與三種顯示模式。
- 倒數結束到系統擷取授權之間仍有系統對話框延遲，實際錄影起點會略晚於倒數結束。

---

## 2026-11-18 - v0.1.1（versionCode 2）

### 目標
補齊錄影停止與收尾的時序，降低通知開片成本，並改善播放音訊與暫存管理。

### 變更
- `recording/`、`ui/`：準備中可取消；等前景服務清理並銷毀後再發布錄影結果。
- `data/`、`ui/`：通知按檔名查找影片、首頁按需載入列表；預覽交由 Media3 管理音訊焦點及耳機拔除。
- `data/`：每次程序啟動後首次載入列表或建立暫存前，清理超過 24 小時的未完成暫存檔。
- `assembleDebug`、`lintDebug` 通過，產出 `app/build/outputs/apk/debug/app-debug.apk`；Lint 為 0 錯誤、7 警告。

### 問題與解法
- 準備中取消可能碰到正在建立的暫存檔；收尾先阻止開錄，再等待準備工作清理其暫存檔。

### 待辦／已知限制
- `testDebugUnitTest` 無測試來源；ADB 未偵測到裝置，實機驗收依 README 的錄影、通知、音訊及暫存測試步驟進行。

---

## 2026-11-18 - v0.1.0（versionCode 1）

### 目標
建立「簡單錄影」的第一版：螢幕錄影、選用麥克風、每段五分鐘、裁剪與分享。

### 變更
- 建立 Android 專案與 Debug 建置設定。
- `recording/`：前景服務錄製螢幕與選用 AAC 音軌，處理到時停止、系統中止與成品長度驗證。
- `ui/`、`editing/`、`data/`：近期列表、播放、裁剪另存、分享與刪除；通知權限提示記錄於 DataStore。
- 新增建置指令、實機驗收步驟與版本說明。
- 驗證 `assembleDebug` 與 `lintDebug` 通過，產出 `app/build/outputs/apk/debug/app-debug.apk`。

### 問題與解法
- 初次執行 Lint 超過工具時限，待依賴下載後以 `lintDebug --offline` 重新執行，通過無錯誤。

### 待辦／已知限制
- 目前沒有已連線裝置或已設定的模擬器，需依 README 實機驗收權限、錄影、裁剪和分享。
- 輸出尺寸固定於開錄時設定，錄製中旋轉或改變視窗大小時可能留黑邊。

---

## YYYY-MM-DD - vX.Y.Z（versionCode N）

### 目標
（這次要做什麼、為什麼）

### 變更
- （檔案／功能）

### 問題與解法
- （錯誤訊息 → 最後怎麼解）

### 待辦／已知限制
- （尚未完成或未驗證的部分）
