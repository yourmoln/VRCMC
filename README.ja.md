<div align="center">
  <img src="image/Logo.png" width="128" alt="VRCMC Logo">
  <h1>VRCMC</h1>
  <p><strong>翻訳したメッセージを、手軽に VRChat へ。</strong></p>
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
  <a href="README.en.md">English</a> ·
  <strong>日本語</strong>
</div>

VRCMC は、Kotlin Multiplatform と Compose Multiplatform で開発された VRChat Chatbox 支援アプリです。ローカルネットワーク上で OSC を使用して VRChat に接続し、メッセージを送信する前に翻訳できます。多言語での会話、音声入力、リアルタイムコミュニケーションに適しています。

> [!NOTE]
> VRCMC は独立したオープンソースプロジェクトであり、VRChat Inc. とは関係ありません。VRChat は各権利所有者の商標です。

## 主な機能

- **OSC Chatbox**：Chatbox の上限である 144 文字・9 行を守りながら、VRChat にテキストを送信します。
- **複数デバイス管理**：複数の IP アドレスと送受信ポートを保存し、同じローカルネットワーク上のデバイスを検索できます。
- **複数の翻訳サービス**：OpenAI-compatible、Anthropic、DeepL、Google Web、MyMemory、LibreTranslate などのプロトコルやサービスに対応しています。
- **二言語出力**：1 つまたは 2 つの翻訳先言語を選択し、原文と訳文の表示順を自由に設定できます。
- **安定したリクエスト処理**：タイムアウト、自動再試行、フォールバックモデル、カスタムヘッダー、ストリーミングレスポンスに対応しています。
- **通訳モード**：システムキーボードの音声入力と連携し、VRChat のマイク状態に応じて内容を送信します。
- **ローカル中心の設計**：チャット履歴、デバイス設定、エラーログをローカルに保存し、ライト・ダークテーマと多言語 UI に対応しています。

## 翻訳サービス

OpenAI、Anthropic / Claude、xAI / Grok、DeepSeek、Gemini、Qwen、GLM、Kimi、DeepL、Ollama、LibreTranslate などの設定を内蔵しています。独自の OpenAI-compatible または Anthropic-compatible エンドポイントも利用できます。

一部の公開翻訳 API は API Key なしで利用できますが、可用性、レート制限、プライバシーポリシーは各サービス提供者によって管理されています。サードパーティーの翻訳サービスを使用すると、翻訳対象のテキストが選択したサービスへ送信されます。必要に応じて各サービスの利用規約を確認してください。

## データとセキュリティ

- デバイス情報、チャット履歴、アプリ設定、エラーログは、デフォルトでローカルに保存されます。
- API Key とカスタム認証ヘッダーは、通常の設定とは分離して保存されます。
- 機密設定は Android では Android Keystore、Windows では DPAPI、iOS では Keychain によって保護されます。
- 現在の macOS / Linux デスクトップ版では、API Key などの機密情報は永続化されません。
- Android ではアプリデータのバックアップを無効にし、システムバックアップによる設定の持ち出しリスクを軽減しています。

## プロジェクト構成

```text
VRCMC/
├── composeApp/
│   └── src/
│       ├── commonMain/   # 共通 UI、状態、翻訳、OSC インターフェース
│       ├── androidMain/  # Android プラットフォーム実装
│       ├── desktopMain/  # JVM デスクトップ実装
│       ├── iosMain/      # iOS プラットフォーム実装
│       └── commonTest/   # 共通ロジックのテスト
├── gradle/               # Gradle Wrapper とバージョンカタログ
├── image/                # プロジェクト画像
└── LICENSE
```

## コントリビューション

Issue と Pull Request を歓迎します。変更を提出する前に、可能な限り各プラットフォームの実装を揃え、変更範囲に応じたテストを実行してください。OSC、翻訳リクエスト、設定の保存に関する変更では、共通ロジックのテストも追加することを推奨します。

## ライセンス

本プロジェクトは [MIT License](LICENSE) の下で公開されています。
