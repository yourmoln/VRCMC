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

- **OSC Chatbox**：向 VRChat 傳送文字，並遵守 Chatbox 的 144 字元與 9 行限制。
- **多裝置管理**：儲存多組 IP 與收發連接埠，並可掃描相同區域網路內的裝置。
- **多服務翻譯**：支援 OpenAI-compatible、Anthropic、DeepL、Google Web、Microsoft Edge Web、MyMemory、LibreTranslate 等協定與服務。
- **雙語輸出**：可同時選擇一至兩種目標語言，自訂原文與譯文的顯示順序。
- **容錯能力**：支援請求逾時、自動重試、備用模型、自訂請求標頭與串流回應。
- **口譯模式**：搭配系統輸入法的語音輸入，依照 VRChat 麥克風狀態傳送內容。
- **本機體驗**：聊天記錄、裝置設定與錯誤日誌儲存在本機，支援淺色、深色主題與多語言介面。

## 翻譯服務

專案內建多種服務設定，包括 OpenAI、Anthropic / Claude、xAI / Grok、DeepSeek、Gemini、Qwen、GLM、Kimi、DeepL、Microsoft Edge Web、Ollama、LibreTranslate 等，也支援自訂 OpenAI-compatible 或 Anthropic-compatible 端點。

部分公共翻譯 API 不需要 API Key，但可用性、速率限制與隱私權政策由對應的服務提供者決定。使用第三方翻譯服務時，待翻譯文字會傳送至你所選擇的服務，請視需要閱讀其條款。

Microsoft Edge Web 使用未公開的 Edge 瀏覽器網頁端點，而非有服務保障的 Azure AI Translator API；該端點可能受到限流、變更或停止服務。

## 資料與安全性

- 裝置資訊、聊天記錄、應用程式設定與錯誤日誌預設儲存在本機。
- API Key 與自訂驗證請求標頭會與一般設定分開儲存。
- Android 使用 Android Keystore、Windows 使用 DPAPI、iOS 使用 Keychain 保護敏感設定。
- 目前 macOS / Linux 桌面版不會持久儲存 API Key 等敏感設定。
- Android 已停用應用程式資料備份，降低設定遭系統備份匯出的風險。

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
