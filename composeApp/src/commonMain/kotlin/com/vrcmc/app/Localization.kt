package com.vrcmc.app

enum class AppLanguage(val label: String) { ZH_HANS("\u7b80\u4f53\u4e2d\u6587"), EN("English"), ZH_HANT("\u7e41\u9ad4\u4e2d\u6587"), JA("\u65e5\u672c\u8a9e") }

open class LocaleStrings {
    open val chat = "Chat"; open val devices = "Devices"; open val api = "API"; open val settings = "Settings"; open val done = "Done"; open val theme = "Theme"; open val preferences = "Preferences"
    open val chatbox = "Chatbox"; open val addDevice = "Add device"; open val activeDevice = "Active device"; open val send = "Send"; open val typeMessage = "Type a message..."; open val addIp = "Add a computer IP first"; open val sendFailed = "Send failed"
    open val apiConfiguration = "API configuration"; open val openMenu = "Open menu"; open val selectDevice = "Select device"; open val deleteDevice = "Delete device"; open val deviceAddress = "Receive port:IP:send port"
    open val defaultPortHint = "IP only defaults to 9000:IP:9001"; open val invalidDeviceAddress = "Enter a valid IP and ports"; open val cancel = "Cancel"; open val translationAssistant = "Translation"; open val translating = "Translating..."; open fun translatingRetry(attempt: Int, limit: Int) = "Translating... Retry $attempt/$limit"
    open val deviceManagement = "Device management"; open val editDevice = "Edit device"; open val ipAddress = "IP address"; open val receivePort = "Receive port"; open val sendPort = "Send port"; open val save = "Save"; open val noDevices = "No devices added"
    open val copyMessage = "Copy"; open val resendMessage = "Send again"
    open val appearance = "Appearance"; open val language = "Language"; open val systemTheme = "System"; open val lightTheme = "Light"; open val darkTheme = "Dark"
    open val yesterday = "Yesterday"; open val dayBeforeYesterday = "The day before yesterday"
    open val clearHistory = "Clear chat history"; open val clearHistoryTitle = "Clear chat history?"; open val clearHistoryMessage = "All saved messages will be permanently deleted."; open val delete = "Delete"; open val messageTooLong = "Message exceeds the VRChat Chatbox limit (144 characters or 9 lines)"
    open val translationLlm = "Translation & LLM"; open val provider = "Provider"; open val customCompatible = "Custom compatible"; open val apiKey = "API Key (optional for Ollama)"; open val targetLanguage = "Target language"; open val translateBeforeSending = "Translate before sending"; open val testConnection = "Test connection"; open val testing = "Testing..."; open val connectionFailed = "Connection failed; check URL, key and model"; open val connected = "Connected: %s"; open val originalAndTranslationSent = "Original and translation sent"
    open val apiSettingsSubtitle = "Independent provider profiles, protocol-aware requests, and connection diagnostics"
    open val translationService = "Translation service"; open val credentialsAndEndpoint = "Credentials & endpoint"; open val modelAndLanguage = "Model & target language"; open val translationBehavior = "Translation behavior"
    open val apiKeyOptional = "API Key (optional)"; open val showApiKey = "Show API Key"; open val apiKeyRequired = "API Key is required for this provider"
    open val customEndpoint = "Custom endpoint"; open val baseUrlHint = "Enter the endpoint supplied by the provider or relay"; open val officialEndpointLocked = "Official endpoint is locked to prevent accidental changes"
    open val model = "Model"; open val chooseModel = "Choose model"; open val customModelPreserved = "Custom model IDs are preserved exactly as entered"
    open val translateBehaviorHint = "Send the original text together with its translation to VRChat"; open val advancedSettings = "Advanced request settings"; open val requestTimeout = "Request timeout"; open val timeoutHint = "3–300 seconds; live chat works best with a short timeout"
    open val retryCount = "Retry attempts"; open val retryCountHint = "0–10 retries when no usable translation is returned"
    open val customHeaders = "Custom request headers"; open val customHeadersHint = "One Name: value header per line. Secrets are hidden with the rest of this provider profile."
    open val streamingResponse = "Streaming response"; open val streamingHint = "Assemble server-sent chunks; useful for compatible relays"
    open val connectionSuccessful = "Connection successful"; open val connectionFailedTitle = "Connection failed"
    open val chooseProvider = "Choose provider"; open val providerPickerHint = "Search a compact list without covering the page"; open val searchProvider = "Search provider or protocol"; open val recommended = "Recommended"
    open val chooseLanguage = "Choose target language"; open val twoLanguageHint = "Choose one or two languages. Two translations run in parallel."
    open val languageSettings = "Translation languages"; open val selectedLanguages = "Target languages"; open val displayOrder = "Display order"; open val effectPreview = "Preview"; open val originalText = "Original"; open val moveUp = "Move up"; open val moveDown = "Move down"
    open val simultaneousInterpretation = "Simultaneous interpretation"; open val enableSimultaneousInterpretation = "Enable simultaneous interpretation"; open val simultaneousInterpretationHint = "Listen for VRChat MuteSelf at the active device address and send port. Opening the microphone starts live original-text messages; muting or Send performs the normal send flow."; open val listeningPort = "Listening endpoint"; open val listenerReady = "Waiting for VRChat microphone events"; open val listenerStopped = "Listener is off"; open val listenerFailed = "Could not listen on this endpoint: %s"; open val interpreting = "Microphone open"
}
object LocaleStringsEn : LocaleStrings()
object LocaleStringsZhHans : LocaleStrings() {
    override val deviceManagement = "\u8bbe\u5907\u7ba1\u7406"; override val editDevice = "\u7f16\u8f91\u8bbe\u5907"; override val ipAddress = "IP \u5730\u5740"; override val receivePort = "\u63a5\u6536\u7aef\u53e3"; override val sendPort = "\u53d1\u9001\u7aef\u53e3"; override val save = "\u4fdd\u5b58"; override val noDevices = "\u5c1a\u672a\u6dfb\u52a0\u8bbe\u5907"
    override val deviceAddress = "\u63a5\u6536\u7aef\u53e3:IP:\u53d1\u9001\u7aef\u53e3"; override val defaultPortHint = "\u4ec5\u8f93\u5165 IP \u65f6\u9ed8\u8ba4\u4e3a 9000:IP:9001"; override val invalidDeviceAddress = "\u8bf7\u8f93\u5165\u6709\u6548\u7684 IP \u548c\u7aef\u53e3"
    override val yesterday = "\u6628\u5929"; override val dayBeforeYesterday = "\u524d\u5929"
    override val chat = "聊天"; override val devices = "设备"; override val api = "接口"; override val settings = "设置"; override val done = "完成"; override val theme = "主题"; override val preferences = "偏好设置"
    override val chatbox = "Chatbox 消息"; override val addDevice = "添加设备"; override val activeDevice = "当前设备"; override val send = "发送"; override val typeMessage = "输入消息..."; override val addIp = "请先在顶部添加电脑 IP"; override val sendFailed = "发送失败"
    override val apiConfiguration = "API 配置"; override val openMenu = "打开菜单"; override val selectDevice = "选择设备"; override val deleteDevice = "删除设备"
    override val cancel = "取消"; override val translationAssistant = "翻译"; override val translating = "翻译中..."; override fun translatingRetry(attempt: Int, limit: Int) = "翻译中...（重试 $attempt/$limit）"
    override val copyMessage = "复制"; override val resendMessage = "重新发送"
    override val appearance = "外观"; override val language = "语言"; override val systemTheme = "跟随系统"; override val lightTheme = "浅色"; override val darkTheme = "深色"
    override val clearHistory = "清空聊天记录"; override val clearHistoryTitle = "确认清空聊天记录？"; override val clearHistoryMessage = "所有已保存的聊天消息都将被永久删除。"; override val delete = "删除"; override val messageTooLong = "消息超过 VRChat Chatbox 限制（144 字符或 9 行）"
    override val translationLlm = "翻译接口"; override val provider = "服务商"; override val customCompatible = "自定义兼容接口"; override val apiKey = "API Key"; override val targetLanguage = "目标语言"; override val translateBeforeSending = "发送前翻译"; override val testConnection = "测试连接"; override val testing = "测试中..."; override val connectionFailed = "连接失败，请检查 URL、Key 和模型"; override val connected = "连接成功：%s"; override val originalAndTranslationSent = "已发送原文和译文"
    override val apiSettingsSubtitle = "服务商配置独立保存，并根据接口协议发送请求和诊断连接"
    override val translationService = "翻译服务"; override val credentialsAndEndpoint = "凭据与端点"; override val modelAndLanguage = "模型与目标语言"; override val translationBehavior = "翻译行为"
    override val apiKeyOptional = "API Key（可选）"; override val showApiKey = "显示 API Key"; override val apiKeyRequired = "该服务商需要 API Key"
    override val customEndpoint = "自定义端点"; override val baseUrlHint = "填写服务商或中转站提供的接口地址"; override val officialEndpointLocked = "官方端点已锁定，避免意外修改"
    override val model = "模型"; override val chooseModel = "选择模型"; override val customModelPreserved = "自定义模型 ID 将按输入内容原样保存"
    override val translateBehaviorHint = "将原文和译文一起发送至 VRChat"; override val advancedSettings = "高级请求设置"; override val requestTimeout = "请求超时"; override val timeoutHint = "范围 3–300 秒；实时聊天建议使用较短超时"
    override val retryCount = "重试次数"; override val retryCountHint = "未返回可用翻译时重试 0–10 次"
    override val customHeaders = "自定义请求头"; override val customHeadersHint = "每行填写一个 Name: value；内容随当前服务商配置一同保存"
    override val streamingResponse = "流式响应"; override val streamingHint = "拼接服务器推送的数据块，适用于兼容中转接口"
    override val connectionSuccessful = "连接成功"; override val connectionFailedTitle = "连接失败"
    override val chooseProvider = "选择服务商"; override val providerPickerHint = "紧凑显示服务商，可按名称或协议搜索"; override val searchProvider = "搜索服务商或协议"; override val recommended = "推荐"
    override val chooseLanguage = "选择目标语言"; override val twoLanguageHint = "可选择 1–2 种语言；双语翻译会并行执行"
    override val languageSettings = "翻译语言"; override val selectedLanguages = "目标语言"; override val displayOrder = "显示顺序"; override val effectPreview = "效果预览"; override val originalText = "原文"; override val moveUp = "上移"; override val moveDown = "下移"
    override val simultaneousInterpretation = "同声传译"; override val enableSimultaneousInterpretation = "开启同声传译"; override val simultaneousInterpretationHint = "监听当前设备 IP 和发送端口。游戏内开麦后每次输入都发送一条原文消息，闭麦或点击发送时执行一次正常发送。"; override val listeningPort = "监听端点"; override val listenerReady = "正在等待 VRChat 麦克风事件"; override val listenerStopped = "监听已关闭"; override val listenerFailed = "无法监听此端点：%s"; override val interpreting = "游戏麦克风已打开"
}
object LocaleStringsZhHant : LocaleStrings() { override val yesterday = "\u6628\u5929"; override val dayBeforeYesterday = "\u524d\u5929"; override val copyMessage = "複製"; override val resendMessage = "重新傳送"; override val languageSettings = "翻譯語言"; override val selectedLanguages = "目標語言"; override val displayOrder = "顯示順序"; override val effectPreview = "效果預覽"; override val originalText = "原文"; override val moveUp = "上移"; override val moveDown = "下移" }
object LocaleStringsJa : LocaleStrings() { override val yesterday = "\u6628\u65e5"; override val dayBeforeYesterday = "\u4e00\u6628\u65e5"; override val copyMessage = "コピー"; override val resendMessage = "再送信"; override val languageSettings = "翻訳言語"; override val selectedLanguages = "翻訳先言語"; override val displayOrder = "表示順序"; override val effectPreview = "プレビュー"; override val originalText = "原文"; override val moveUp = "上へ"; override val moveDown = "下へ" }

fun localeStrings(language: AppLanguage): LocaleStrings = when (language) { AppLanguage.ZH_HANS -> LocaleStringsZhHans; AppLanguage.ZH_HANT -> LocaleStringsZhHant; AppLanguage.JA -> LocaleStringsJa; AppLanguage.EN -> LocaleStringsEn }
