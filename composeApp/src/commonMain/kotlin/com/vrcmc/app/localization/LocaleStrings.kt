package com.vrcmc.app

interface LocaleStrings {
    val quickStart: String get() = "Quick start"
    val quickStartQwen: String get() = "Configure Qwen"
    val quickStartVrc: String get() = "Configure VRC"
    val quickStartDevice: String get() = "Add computer"
    val quickStartQwenIntro: String get() = "Open Qianwen, top up your account, and create an API Key. The key is saved only after the connection test succeeds."
    val openQianwen: String get() = "Open Qianwen"
    val autoConfigure: String get() = "Test and configure automatically"
    val autoConfiguring: String get() = "Testing qwen-mt-plus..."
    val autoConfigureSuccess: String get() = "Qwen translation and voice input are ready."
    val skipQwenConfiguration: String get() = "Skip Qwen configuration"
    val continueToVrc: String get() = "Continue to VRC configuration"
    val vrcConfigured: String get() = "VRC is configured, continue"
    val computerIpGuide: String get() = "On the computer running VRChat, right-click the network icon in the system tray, open Network & Internet settings, select Properties, and find the IPv4 address. Then add it below."
    val computerIpPlaceholder: String get() = "For example: 192.168.1.100"
    val addAndFinish: String get() = "Add device and finish"
    val quickStartComplete: String get() = "Setup complete. You can now use VRCMC."
    val stepProgress: String get() = "Step %d of 3"
    val lineBreakOutput: String get() = "Line breaks"
    val enableLineBreakOutput: String get() = "Put each language on a new line"
    val lineBreakOutputHint: String get() = "When disabled, the second item in the display order is enclosed in parentheses."
    val chat: String
    val devices: String
    val api: String
    val settings: String
    val done: String
    val theme: String
    val preferences: String
    val disableDynamicInputLimit: String get() = "Disable dynamic input limit"
    val chatbox: String
    val addDevice: String
    val activeDevice: String
    val send: String
    val typeMessage: String
    val addIp: String
    val sendFailed: String
    val apiConfiguration: String
    val openMenu: String
    val selectDevice: String
    val deleteDevice: String
    val deviceAddress: String
    val defaultPortHint: String
    val invalidDeviceAddress: String
    val cancel: String
    val translationAssistant: String
    val translating: String

    fun translatingRetry(attempt: Int, limit: Int): String

    val deviceManagement: String
    val editDevice: String
    val ipAddress: String
    val receivePort: String
    val sendPort: String
    val save: String
    val noDevices: String
    val deviceIpHint: String
    val scanNetwork: String
    val scanningNetwork: String
    val scanResults: String
    val noScanResults: String
    val unknownDeviceName: String
    val alreadyAdded: String
    val copyMessage: String
    val resendMessage: String
    val appearance: String
    val language: String
    val systemTheme: String
    val lightTheme: String
    val darkTheme: String
    val configureVrc: String
    val configureVrcIntro: String
    val steamStep1: String
    val steamStep2: String
    val steamStep3: String
    val oscLaunchCommand: String
    val copyCommand: String
    val commandCopied: String
    val refreshIp: String
    val selectLanIp: String
    val currentLanIp: String
    val lanIpUnavailable: String
    val localhostOscNote: String
    val remoteOscNote: String
    val yesterday: String
    val dayBeforeYesterday: String
    val clearHistory: String
    val clearHistoryTitle: String
    val clearHistoryMessage: String
    val delete: String
    val messageTooLong: String
    val translationLlm: String
    val provider: String
    val customCompatible: String
    val apiKey: String
    val targetLanguage: String
    val translateBeforeSending: String
    val testConnection: String
    val testing: String
    val connectionFailed: String
    val connected: String
    val originalAndTranslationSent: String
    val apiSettingsSubtitle: String
    val translationService: String
    val credentialsAndEndpoint: String
    val modelAndLanguage: String
    val translationBehavior: String
    val apiKeyOptional: String
    val showApiKey: String
    val apiKeyRequired: String
    val customEndpoint: String
    val baseUrlHint: String
    val officialEndpointLocked: String
    val model: String
    val chooseModel: String
    val customModelPreserved: String
    val fallbackModel: String
    val enableFallbackModel: String
    val fallbackModelHint: String
    val fallbackRetryCount: String
    val fallbackRetryCountHint: String
    val translateBehaviorHint: String
    val advancedSettings: String
    val requestTimeout: String
    val timeoutHint: String
    val sendOriginalBeforeTranslation: String
    val sendOriginalBeforeTranslationHint: String
    val retryCount: String
    val retryCountHint: String
    val customHeaders: String
    val customHeadersHint: String
    val streamingResponse: String
    val streamingHint: String
    val connectionSuccessful: String
    val connectionFailedTitle: String
    val chooseProvider: String
    val providerPickerHint: String
    val searchProvider: String
    val recommended: String
    val highlyRecommended: String
    val chooseLanguage: String
    val twoLanguageHint: String
    val languageSettings: String
    val selectedLanguages: String
    val displayOrder: String
    val effectPreview: String
    val originalText: String
    val moveUp: String
    val moveDown: String
    val simultaneousInterpretation: String
    val enableSimultaneousInterpretation: String
    val simultaneousInterpretationInputHint: String
    val interpretationVoiceInput: String get() = "同步开启语音输入"
    val interpretationVoiceInputHint: String get() = "解释模式会自动使用应用内语音识别，不会改变输入法的语音输入。"
    val simultaneousInterpretationHint: String
    val listeningPort: String
    val listenerReady: String
    val listenerStopped: String
    val listenerFailed: String
    val interpreting: String
    val alwaysInterpretation: String
    val enableAlwaysInterpretation: String
    val alwaysInterpretationHint: String
    val startAlwaysInterpretation: String
    val stopAlwaysInterpretation: String
    val alwaysInterpretationDelay: String
    val alwaysInterpretationDelayHint: String

    val keepScreenOn: String get() = "防止熄屏"
    val keepScreenOnHint: String get() = "传译运行期间保持屏幕常亮"

    fun seconds(value: String): String

    val aboutApp: String
    val version: String
    val repository: String
    val checkForUpdates: String
    val checkingForUpdates: String
    val alreadyLatestVersion: String
    val updateCheckFailed: String
    val updateAvailable: String
    val updateNow: String
    val later: String
    val ignoreThisVersion: String
    val errorLogs: String
    val noErrorLogs: String
    val clearErrorLogs: String
    val clearErrorLogsMessage: String

    val voiceInputService: String get() = "语音输入服务"
    val enableVoiceInput: String get() = "启用语音输入"
    val voiceInputHint: String get() = "使用 Qwen3-ASR 将录音转换为聊天文字"
    val qwenApiKey: String get() = "Qwen API Key"
    val qwenRegion: String get() = "区域"
    val qwenLanguage: String get() = "识别语言"
    val qwenModel: String get() = "模型"
    val qwenSampleRate: String get() = "采样率"
    val qwenMaxSegment: String get() = "最长录音"
    val qwenTailSilence: String get() = "尾部静音"
    val qwenVadMinRms: String get() = "最低语音音量（RMS）"
    val qwenVadSpeechRatio: String get() = "语音帧比例"
    val qwenPartialInterval: String get() = "实时识别间隔"
    val qwenTimeout: String get() = "请求超时"
    val startVoiceInput: String get() = "开始语音输入"
    val stopVoiceInput: String get() = "停止语音输入"
    val transcribingVoiceInput: String get() = "正在识别语音..."
    val voiceWaitingForSpeech: String get() = "正在等待说话…"
    val voiceSpeechDetected: String get() = "检测到语音，正在实时识别…"

    fun providerHint(provider: TranslationProvider): String

    fun providerRegionLabel(providerId: String, region: ProviderRegion): String
}
