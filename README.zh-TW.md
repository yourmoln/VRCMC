<div align="center">
  <img src="image/Logo.png" width="128" alt="VRCMC Logo">
  <h1>VRCMC</h1>
  <p><strong>把翻譯後的訊息，輕鬆送進 VRChat。</strong></p>
  <p>
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white">
    <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white">
    <img alt="Platforms" src="https://img.shields.io/badge/Platforms-Android%20%7C%20Desktop%20%7C%20iOS-34A853">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-F4B400.svg"></a>
  </p>
</div>

<div align="center">
  <a href="README.md">简体中文</a> ·
  <strong>繁體中文</strong> ·
  <a href="README.en.md">English</a> ·
  <a href="README.ja.md">日本語</a>
</div>

VRCMC 是一款使用 Kotlin Multiplatform 與 Compose Multiplatform 開發的 VRChat Chatbox 助手。它透過 OSC 在區域網路內連接 VRChat，並可在傳送訊息前呼叫翻譯服務，適合跨語言聊天、語音輸入與即時交流。

> [!NOTE]
> VRCMC 是獨立的開源專案，與 VRChat Inc. 沒有關聯。VRChat 是其各自所有者的商標。

## 功能亮點

- **VRChat Chatbox**：透過 OSC 向同一台電腦或區域網路中的 VRChat 傳送訊息。可儲存多個裝置並切換，桌面版和 Android 也可掃描區域網路裝置；只填 IP 時使用 `9000:IP:9001`，也可指定收發連接埠。輸入框會依 Chatbox 的 144 字元、9 行限制與輸出語言數量計算字元上限，可在偏好設定中關閉動態字元限制。
- **翻譯服務**：API 頁面可選擇 Qwen、Microsoft Edge Web（Bing）、DeepSeek、OpenAI/GPT 或自訂相容端點。可依服務支援情況設定 API Key、Base URL、模型、逾時、重試、備用模型、自訂請求標頭與串流回應。
- **翻譯輸出**：最多選擇兩種目標語言，設定是否顯示原文、換行方式與原文/譯文順序，也可以先傳送原文再傳送譯文。
- **語音輸入**：可使用 Qwen Fun-ASR 雲端辨識；Windows x64 裝置可下載 SenseVoice Small INT8 或 Fun-ASR-Nano INT8，在本機離線辨識。桌面版可選擇麥克風，Android 使用雲端辨識並需要麥克風權限。
- **同聲口譯**：可搭配系統語音輸入或應用程式內辨識。開啟後會依 VRChat 麥克風狀態收音，閉麥後翻譯並傳送；「始終口譯」會在輸入暫停後自動傳送，並可合併快速連續訊息。
- **聽別人說話（Windows）**：點擊聊天頁右上角的耳朵按鈕，監聽 Windows 預設播放裝置並在本機偵測人聲，在置頂字幕視窗中按句顯示原文與一種譯文。視窗可調整大小、最小化與不透明度，設定中可選擇聆聽語言和目標語言。
- **SteamVR 疊加介面**：可在字幕設定中開啟，位置支援左手、右手或螢幕中央；手部面板跟隨控制器，螢幕中央面板跟隨頭顯。大小可選 50%–200%，與桌面字幕共用 30%–100% 的不透明度，需要 SteamVR 和已連接的頭顯。
- **訊息朗讀**：啟用 Edge TTS 後，可選擇朗讀原文、譯文或最終傳送文字，支援音色試聽；桌面版可選擇播放裝置。朗讀文字會傳送至 Microsoft 進行語音合成。
- **熱詞字典**：在傳送前替換關鍵字或封鎖整句，支援 JSON 匯入與匯出。
- **本機設定**：支援淺色、深色與跟隨系統主題，介面提供簡體中文、繁體中文、English 和日本語；可選顯示日文羅馬字，聊天記錄和錯誤日誌儲存在本機。
- **應用程式內更新**：應用程式會檢查 GitHub 最新版本，可在偏好設定中關閉檢查。Android 和 Windows 可直接下載並啟動安裝程式；iOS 會開啟發行頁面。

## 快速開始

1. 開啟「快速上手」，測試 Qwen API Key，或直接選擇不需要 API Key 的 Edge Web 翻譯。
2. 開啟「設定 VRC」，將產生的 `--osc=9000:<VRCMC 的區域網路 IP>:9001` 填入 Steam 中 VRChat 的啟動選項，然後重新啟動 VRChat。同一台電腦執行兩者時使用 `127.0.0.1`。
3. 開啟「裝置管理」，加入執行 VRChat 的電腦 IP；只填 IP 時會使用預設連接埠 9000/9001。
4. 在 API、翻譯語言、語音輸入和偏好設定中依需求開啟功能。

## 翻譯服務

服務選擇器目前提供 Qwen、Microsoft Edge Web（Bing）、DeepSeek、OpenAI/GPT 和自訂相容端點。自訂端點可選擇 OpenAI-compatible 或 Anthropic-compatible 協定。

Edge Web 是不需要 API Key 的公共網頁端點，不是 Azure AI Translator API，可能受到限流、變更或停止服務。使用第三方服務時，待翻譯文字會傳送至所選服務，請自行查看其條款和隱私權政策。

## 資料與安全性

- 裝置資訊、聊天記錄、應用程式設定、熱詞字典與錯誤日誌預設儲存在本機。
- API Key 與自訂驗證請求標頭會與一般設定分開儲存；Android 使用 Android Keystore、Windows 使用 DPAPI、iOS 使用 Keychain，其他桌面系統不會持久儲存這些敏感設定。
- 雲端語音辨識會將錄音傳送至所選服務；本機辨識模型在裝置上處理音訊，不會回退到雲端。翻譯文字會傳送至已設定的翻譯服務，Edge TTS 會將朗讀文字傳送至 Microsoft。
- 「聽別人說話」只在字幕視窗顯示辨識結果，不會寫入聊天記錄、傳送至 VRChat 或觸發朗讀；它監聽啟動時的 Windows 預設播放裝置，切換裝置後需要重新開始。
- Android 已關閉應用程式資料備份。

## 專案結構

```text
VRCMC/
├── composeApp/
│   └── src/
│       ├── commonMain/   # 共用 UI、狀態、翻譯與 OSC 介面
│       ├── androidMain/  # Android 平台實作
│       ├── desktopMain/  # JVM 桌面平台實作
│       ├── iosMain/      # iOS 平台實作
│       └── commonTest/   # 共用邏輯測試
├── gradle/               # Gradle Wrapper 與版本目錄
├── image/                # 專案圖片資源
└── LICENSE
```

## 參與貢獻

歡迎提交 Issue 與 Pull Request。提交變更前，請盡量維持各平台實作的一致性，並執行與變更範圍相對應的測試。涉及 OSC、翻譯請求或設定儲存時，建議同時補充共用邏輯測試。

## 授權條款

本專案採用 [MIT License](LICENSE) 開源。
