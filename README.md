<div align="center">
  <img src="image/Logo.png" width="128" alt="VRCMC Logo">
  <h1>VRCMC</h1>
  <p><strong>把翻译后的消息，轻松送进 VRChat。</strong></p>
  <p>
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white">
    <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white">
    <img alt="Platforms" src="https://img.shields.io/badge/Platforms-Android%20%7C%20Desktop%20%7C%20iOS-34A853">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-F4B400.svg"></a>
  </p>
</div>

<div align="center">
  <strong>简体中文</strong> ·
  <a href="README.zh-TW.md">繁體中文</a> ·
  <a href="README.en.md">English</a> ·
  <a href="README.ja.md">日本語</a>
</div>

VRCMC 是一款基于 Kotlin Multiplatform 与 Compose Multiplatform 开发的 VRChat Chatbox 助手。它通过 OSC 在局域网内连接 VRChat，并可在发送消息前调用翻译服务，适合跨语言聊天、语音输入与实时交流。

> [!NOTE]
> VRCMC 是独立的开源项目，与 VRChat Inc. 没有关联。VRChat 是其各自所有者的商标。

## 功能亮点

- **OSC Chatbox**：向 VRChat 发送文本，遵守 Chatbox 的 144 字符与 9 行限制。
- **多设备管理**：保存多组 IP 与收发端口，并可扫描同一局域网内的设备。
- **多服务翻译**：支持 OpenAI-compatible、Anthropic、DeepL、Google Web、Microsoft Edge Web、MyMemory、LibreTranslate 等协议与服务。
- **双语输出**：可同时选择一至两种目标语言，自定义原文和译文的显示顺序。
- **容错能力**：支持请求超时、自动重试、备用模型、自定义请求头与流式响应。
- **传译模式**：配合系统输入法的语音输入，依据 VRChat 麦克风状态发送内容。
- **本地体验**：聊天记录、设备配置与错误日志保存在本机，支持深浅色主题与多语言界面。


## 翻译服务

项目内置多种服务配置，包括 OpenAI、Anthropic / Claude、xAI / Grok、DeepSeek、Gemini、Qwen、GLM、Kimi、DeepL、Microsoft Edge Web、Ollama、LibreTranslate 等，也支持自定义 OpenAI-compatible 或 Anthropic-compatible 端点。

部分公共翻译接口无需 API Key，但可用性、速率限制及隐私政策由对应服务提供方决定。使用第三方翻译时，待翻译文本会发送到你所选择的服务，请按需阅读其条款。

Microsoft Edge Web 使用未公开的 Edge 浏览器网页接口，而不是有服务保障的 Azure AI Translator API；该接口可能被限流、变更或停止服务。

## 数据与安全

- 设备信息、聊天记录、应用设置和错误日志默认保存在本地。
- API Key 与自定义鉴权请求头会从普通设置中分离存储。
- Android 使用 Android Keystore，Windows 使用 DPAPI，iOS 使用 Keychain 保护敏感配置。
- 当前 macOS / Linux 桌面端不会持久化 API Key 等敏感配置。
- Android 已禁用应用数据备份，减少配置被系统备份导出的风险。


## 项目结构

```text
VRCMC/
├── composeApp/
│   └── src/
│       ├── commonMain/   # 共享 UI、状态、翻译与 OSC 接口
│       ├── androidMain/  # Android 平台实现
│       ├── desktopMain/  # JVM 桌面平台实现
│       ├── iosMain/      # iOS 平台实现
│       └── commonTest/   # 共享逻辑测试
├── gradle/               # Gradle Wrapper 与版本目录
├── image/                # 项目图片资源
└── LICENSE
```

## 参与贡献

欢迎提交 Issue 与 Pull Request。提交改动前，请尽量保持平台实现的一致性，并运行与改动范围对应的测试。涉及 OSC、翻译请求或配置存储时，建议同时补充共享逻辑测试。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
