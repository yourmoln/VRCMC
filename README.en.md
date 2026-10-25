<div align="center">
  <img src="image/Logo.png" width="128" alt="VRCMC Logo">
  <h1>VRCMC</h1>
  <p><strong>Send translated messages to VRChat with ease.</strong></p>
  <p>
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white">
    <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white">
    <img alt="Platforms" src="https://img.shields.io/badge/Platforms-Android%20%7C%20Desktop%20%7C%20iOS-34A853">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-F4B400.svg"></a>
  </p>
</div>

<div align="center">
  <a href="README.md">简体中文</a> ·
  <a href="README.zh-TW.md">繁體中文</a> ·
  <strong>English</strong> ·
  <a href="README.ja.md">日本語</a>
</div>

VRCMC is a VRChat Chatbox companion built with Kotlin Multiplatform and Compose Multiplatform. It connects to VRChat over OSC on your local network and can translate messages before sending them, making it useful for multilingual conversations, voice input, and real-time communication.

> [!NOTE]
> VRCMC is an independent open-source project and is not affiliated with VRChat Inc. VRChat is a trademark of its respective owner.

## Highlights

- **VRChat Chatbox**: Send messages over OSC to VRChat on the same computer or local network. Save multiple devices and switch between them; desktop and Android builds can also scan the local network. An IP alone defaults to `9000:IP:9001`, or you can specify both ports. The input limit follows Chatbox's 144-character and 9-line limits and the number of output languages; disable dynamic character limiting in Preferences.
- **Translation providers**: The API page exposes Qwen, Microsoft Edge Web (Bing), DeepSeek, OpenAI/GPT, and a custom compatible endpoint. Configure an API key, base URL, model, timeout, retries, fallback model, custom headers, and streaming where supported.
- **Translation output**: Select up to two target languages, choose whether to show the original, configure line breaks and output order, and optionally send the original before the translation.
- **Voice input**: Use Qwen Fun-ASR cloud recognition. On Windows x64, download SenseVoice Small INT8 or Fun-ASR-Nano INT8 for offline recognition. Desktop builds let you select a microphone; Android uses cloud recognition and requires microphone permission.
- **Interpretation**: Use system voice input or app recognition. Simultaneous interpretation follows the VRChat microphone state and sends after mute; Always interpretation sends after input pauses and can merge quick successive messages.
- **Listen to others (Windows)**: Click the ear button on the Chat page to capture the Windows default playback device. Speech is detected locally and shown sentence by sentence with the original and one translation in an always-on-top subtitle window. Choose listening and target languages, resize or minimize the window, and adjust opacity.
- **SteamVR overlay**: Enable it in subtitle settings and choose the left hand, right hand, or screen center. Hand overlays follow controllers and the center overlay follows the headset. Choose 50%–200% size and an independent 75%–200% font size; opacity is shared with desktop subtitles and ranges from 30%–100%. SteamVR and a connected headset are required.
- **Text-to-speech**: Enable Edge TTS, choose original, translation, or outgoing text, preview voices, and select a desktop playback device. Text is sent to Microsoft for synthesis.
- **Hotword dictionary**: Replace keywords or block complete messages before sending; import and export JSON.
- **Local settings**: Use light, dark, or system themes; the UI supports Simplified Chinese, Traditional Chinese, English, and Japanese. Japanese romaji display, local chat history, and error logs are also available.
- **In-app updates**: The app checks the latest GitHub release, and the check can be disabled in Preferences. Android and Windows can download and launch an installer; iOS opens the release page.

## Quick Start

1. Open **Quick Start**. Test a Qwen API key, or choose Edge Web for keyless public translation.
2. Open **Configure VRChat**, copy the generated `--osc=9000:<VRCMC LAN IP>:9001` command into VRChat's Steam launch options, and restart VRChat. Use `127.0.0.1` when both apps run on the same computer.
3. Open **Device Management** and add the IP address of the computer running VRChat. An IP alone uses the default 9000/9001 ports.
4. Enable and configure translation, languages, voice input, and other options as needed.

## Translation Services

The provider picker currently offers Qwen, Microsoft Edge Web (Bing), DeepSeek, OpenAI/GPT, and a custom compatible endpoint. The custom endpoint can use either the OpenAI-compatible or Anthropic-compatible protocol.

Edge Web is a public web endpoint that does not require an API key. It is not the Azure AI Translator API and may be rate-limited, changed, or discontinued. Text sent to a third-party provider is handled according to that provider's terms and privacy policy.

## Data and Security

- Device information, chat history, application settings, the hotword dictionary, and error logs are stored locally by default.
- API keys and custom authentication headers are stored separately from regular settings. Android uses Android Keystore, Windows uses DPAPI, and iOS uses Keychain; other desktop systems do not persist these sensitive values.
- Cloud speech recognition sends recordings to the selected service. Local recognition models process audio on the device and do not fall back to the cloud. Translation text is sent to the configured translation provider, and Edge TTS sends speech text to Microsoft.
- **Listen to others** keeps recognized subtitles in its window only: it does not write them to chat history, send them to VRChat, or read them aloud. It captures the Windows default playback device selected when listening starts; restart listening after switching devices.
- Android application data backup is disabled.

## Project Structure

```text
VRCMC/
├── composeApp/
│   └── src/
│       ├── commonMain/   # Shared UI, state, translation, and OSC interfaces
│       ├── androidMain/  # Android platform implementation
│       ├── desktopMain/  # JVM desktop platform implementation
│       ├── iosMain/      # iOS platform implementation
│       └── commonTest/   # Shared logic tests
├── gradle/               # Gradle Wrapper and version catalog
├── image/                # Project image assets
└── LICENSE
```

## Contributing

Issues and pull requests are welcome. Before submitting changes, keep platform implementations consistent where possible and run the tests relevant to your changes. Changes involving OSC, translation requests, or configuration storage should ideally include shared logic tests.

## License

This project is licensed under the [MIT License](LICENSE).
