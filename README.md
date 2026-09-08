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
- **热词字典**：自定义关键词替换与整句屏蔽，在翻译和 OSC 发送前生效，支持 JSON 导入导出。
- **消息朗读**：在偏好设置中启用 Edge TTS，默认关闭、默认朗读原文，可切换为仅译文或最终发送文本，选择音色并试听；Windows 支持选择播放设备。
- **听别人说话（Windows）**：点击聊天界面右上角、删除按钮左侧的耳朵按钮，在屏幕左上角留出 24 像素间距后打开置顶字幕窗口，采用 Windows 风格圆角。本地检测到有效人声后识别，按完整句子一起显示原文和译文，长句不会因 6 秒音频分块而拆成多条字幕。窗口默认宽高比为 3:4（360×480），隐藏系统标题栏，顶部显示“正在听 / 正在等待说话”的状态，以及设置、最小化和关闭按钮，可拖动顶部空白处移动、拖动边缘调整大小；最小化后继续监听，再次点击耳朵按钮或关闭窗口即可停止。设置中可调节窗口不透明度（30%–100%，默认 80%），保存后应用并记住，不会重启监听。
- **本地体验**：聊天记录、设备配置与错误日志保存在本机，支持深浅色主题与多语言界面。

“听别人说话”的设置中可开启 **SteamVR 叠加界面**（默认关闭），预设位置为 **左手、右手、屏幕中间**，默认左手。左右手位置随对应控制器移动，面板底边中心对齐对应控制器，默认宽 14 厘米、高约 18.7 厘米；屏幕中间固定在头显视角前方，默认宽 30 厘米、高 40 厘米。叠加大小可选 50%–200%（每档 25%），上述尺寸为 100% 默认值，旧配置也沿用此默认值；调整大小时字号保持不变，手部面板继续以底边中心对齐手柄。显示最新字幕的原文、译文和监听状态。叠加界面与桌面字幕窗口共用“窗口不透明度”（30%–100%，默认 80%），点击保存后一起生效并记住设置。修改叠加开关、位置、大小或不透明度不会重启监听或清空字幕；桌面窗口最小化时仍显示 VR 字幕，停止监听或关闭窗口时会释放叠加界面。需要本机安装 SteamVR 并连接头显；暂不可用时显示提示并自动重连，所选手柄断开时隐藏叠加界面，连接后恢复。

**本地语音识别（Windows）**：在 **API → 语音输入服务** 中启用语音输入，将“识别服务”切换为 **本地 Whisper**。点击“下载模型”后才会下载 Whisper Small Q5_1 多语言模型（约 190 MB），选择服务或启用语音输入不会自动下载；页面显示下载进度，可手动取消，失败后可重试；下载完成并校验后即可离线识别，无需语音识别 API Key。模型缓存在 `%LOCALAPPDATA%\VRCMC\models\whisper-small`，再次启用复用缓存。下载在切换页面、切换识别服务或关闭语音输入后继续，只有点击“取消下载”才会主动停止，并清理未完成的临时文件；切回设置仍可查看进度和取消。关闭语音输入或切回 Qwen 只释放模型内存，已完成的模型和 Qwen 配置会保留。下载完成时若本地服务已启用则自动加载，否则仅保留缓存。本地识别同时用于麦克风输入、解释模式和“听别人说话”，支持中文、日语、英语等语言及自动检测；麦克风按句识别，不进行云端式高频中间结果更新。当前支持 Windows x64、具备 AVX2 的处理器，使用 CPU 推理，指定识别语言可降低延迟；速度和准确率取决于硬件、语言与录音质量。声音过小时需要提高播放/麦克风音量或降低高级设置中的最低语音音量门限。


## 翻译服务

项目内置多种服务配置，包括 OpenAI、Anthropic / Claude、xAI / Grok、DeepSeek、Gemini、Qwen、GLM、Kimi、DeepL、Microsoft Edge Web、Ollama、LibreTranslate 等，也支持自定义 OpenAI-compatible 或 Anthropic-compatible 端点。

部分公共翻译接口无需 API Key，但可用性、速率限制及隐私政策由对应服务提供方决定。使用第三方翻译时，待翻译文本会发送到你所选择的服务，请按需阅读其条款。

Microsoft Edge Web 使用未公开的 Edge 浏览器网页接口，而不是有服务保障的 Azure AI Translator API；该接口可能被限流、变更或停止服务。

## 数据与安全

“听别人说话”复用 API 页面配置的语音识别与翻译服务，语言由字幕窗口顶部的设置按钮单独配置：听的语言默认为自动，目标语言默认为简体中文。点击保存后应用，并在重新打开窗口或应用后保留；不影响麦克风输入语言、聊天目标语言或聊天发送的翻译开关。内置 Silero VAD 在本地判断人声，并结合音量、连续人声时长过滤静音、低音量底噪和短促杂音。Windows 播放设备静音或音量为零时不发送识别请求。连续语音在后台分块识别，每次音频不超过 6 秒，相邻块保留约 640 毫秒重叠以衔接边界词语；到达时长上限不会结束字幕句子。合并识别文字并去除重叠后，根据后续文字确认的句中标点输出已完成的句子，保留末尾待续内容；检测到配置的句尾停顿后再输出最后一句。音频块末尾的标点不会单独作为断句依据，避免识别服务给半句话加上句号后直接显示。每句按目标语言各翻译一次，原文与译文一起显示。仅有重叠音频和静音的结尾不会重复调用识别。服务过慢或某块识别失败时丢弃尚未完成的字幕并提示，不将缺失内容的剩余半句当作完整字幕显示。关闭监听会丢弃未说完的结尾。

字幕仅保留在窗口中，不写入聊天记录，不发送到 VRChat，也不触发朗读。捕获范围为启动时 Windows 默认播放设备上的全部声音（包括其他应用和本应用朗读），不按应用或说话人区分；切换播放设备后请重新开启监听。字幕延迟取决于句尾静音设置和服务响应速度。本地人声检测无需额外配置或联网下载；云端识别仅发送通过检测的语音片段，选择本地 Whisper 后音频在本机处理，不会回退到云端识别。模型下载优先使用 Hugging Face，连接失败时使用 HF Mirror，并验证固定版本的文件大小与 SHA-256；翻译仍使用已配置的翻译服务，译前文本会发送到该服务。

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
