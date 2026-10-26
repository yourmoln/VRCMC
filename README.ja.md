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

<p align="center">
  <img src="image/Screenshot.png" width="100%" alt="VRCMC のチャット画面のスクリーンショット">
</p>

> [!NOTE]
> VRCMC は独立したオープンソースプロジェクトであり、VRChat Inc. とは関係ありません。VRChat は各権利所有者の商標です。

## 主な機能

- **VRChat Chatbox**：OSC で同じ PC またはローカルネットワーク上の VRChat にメッセージを送信します。複数のデバイスを保存して切り替えられ、デスクトップ版と Android ではローカルネットワークのスキャンも利用できます。IP だけを入力した場合は `9000:IP:9001` を使用し、送受信ポートも指定できます。入力欄の文字数上限は Chatbox の 144 文字・9 行制限と出力言語数に応じて計算され、設定で動的な文字数制限を無効にできます。
- **翻訳サービス**：API ページで Qwen、Microsoft Edge Web（Bing）、DeepSeek、OpenAI/GPT、カスタム互換エンドポイントを選択できます。サービスに応じて API Key、Base URL、モデル、タイムアウト、再試行、フォールバックモデル、カスタムヘッダー、ストリーミングを設定できます。
- **翻訳出力**：最大 2 つの翻訳先言語を選択し、原文の表示、改行、原文と訳文の順番を設定できます。原文を先に送信することもできます。
- **音声入力**：Qwen Fun-ASR のクラウド認識を利用できます。Windows x64 では SenseVoice Small INT8、Fun-ASR-Nano INT8、Qwen3-ASR 0.6B INT8 をダウンロードしてオフライン認識でき、不要になったモデルは削除できます。デスクトップ版ではマイクを選択でき、Android ではクラウド認識とマイク権限を使用します。
- **同時通訳**：システムの音声入力またはアプリ内認識と連携できます。有効にすると VRChat のマイク状態に応じて録音し、マイクを切った後に翻訳して送信します。「常時通訳」では入力が止まった後に自動送信し、短時間に続けて入力したメッセージを結合できます。
- **相手の声を聞く（Windows）**：チャットページ右上の耳ボタンで Windows の既定の再生デバイスを聞き取り、音声をローカルで検出して、常に手前に表示される字幕ウィンドウに原文と 1 つの訳文を文単位で表示します。聞き取り言語と翻訳先言語、ウィンドウのサイズ・最小化・不透明度を設定できます。
- **SteamVR オーバーレイ**：字幕設定で有効にし、左手・右手・画面中央から位置を選べます。手元のパネルはコントローラーに、画面中央のパネルはヘッドセットに追従します。サイズは 50%–200%、字幕の文字サイズは 75%–200% から個別に選べ、不透明度はデスクトップ字幕と共通の 30%–100% です。SteamVR と接続済みのヘッドセットが必要です。
- **メッセージ読み上げ**：Edge TTS を有効にすると、原文・訳文・最終送信テキストのどれを読み上げるか選べます。音声の試聴にも対応し、デスクトップ版では再生デバイスを選択できます。読み上げるテキストは Microsoft に送信されます。
- **ホットワード辞書**：送信前にキーワードを置換したり、文全体をブロックしたりできます。JSON のインポートとエクスポートに対応します。
- **ローカル設定**：ライト・ダーク・システムテーマを選べます。UI は簡体字中国語、繁体字中国語、英語、日本語に対応し、日本語のローマ字表示、チャット履歴、エラーログも利用できます。
- **アプリ内更新**：GitHub の最新リリースを確認し、設定で自動確認を無効にできます。Android と Windows はアプリからインストーラーをダウンロードして起動でき、iOS はリリースページを開きます。

## クイックスタート

1. 「クイックスタート」を開き、Qwen API Key をテストするか、API Key 不要の Edge Web 翻訳を選びます。
2. 「VRC を設定」を開き、生成された `--osc=9000:<VRCMC の LAN IP>:9001` を Steam の VRChat 起動オプションに入力して VRChat を再起動します。同じ PC で実行する場合は `127.0.0.1` を使用します。
3. 「デバイス管理」を開き、VRChat を実行している PC の IP を追加します。IP だけの場合はデフォルトの 9000/9001 ポートを使用します。
4. API、翻訳言語、音声入力、その他の設定を必要に応じて有効にします。

## 翻訳サービス

サービス選択画面には Qwen、Microsoft Edge Web（Bing）、DeepSeek、OpenAI/GPT、カスタム互換エンドポイントが表示されます。カスタムエンドポイントでは OpenAI-compatible または Anthropic-compatible プロトコルを選べます。

Edge Web は API Key 不要の公開ウェブエンドポイントで、Azure AI Translator API ではありません。レート制限、変更、停止が行われる場合があります。サードパーティーサービスに送信されるテキストの扱いは、各サービスの利用規約とプライバシーポリシーに従います。

## データとセキュリティ

- デバイス情報、チャット履歴、アプリ設定、ホットワード辞書、エラーログはデフォルトでローカルに保存されます。
- API Key とカスタム認証ヘッダーは通常の設定と分けて保存されます。Android は Android Keystore、Windows は DPAPI、iOS は Keychain を使用し、その他のデスクトップではこれらの機密情報を永続化しません。
- クラウド音声認識では録音を選択したサービスへ送信します。ローカル認識モデルは端末内で音声を処理し、クラウドへ切り替えません。翻訳テキストは設定した翻訳サービスへ、Edge TTS の読み上げテキストは Microsoft へ送信されます。
- 「相手の声を聞く」の字幕はウィンドウ内だけに表示され、チャット履歴への保存、VRChat への送信、読み上げは行いません。聞き取り開始時の Windows 既定の再生デバイスを使用するため、デバイスを切り替えた場合は聞き取りを再開してください。
- Android ではアプリデータのバックアップを無効にしています。

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
