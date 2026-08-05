package com.vrcmc.app

object LocaleStringsEn : LocaleStrings {
    override val chat = "Chat"
    override val devices = "Devices"
    override val api = "API"
    override val settings = "Settings"
    override val done = "Done"
    override val theme = "Theme"
    override val preferences = "Preferences"
    override val chatbox = "Chatbox"
    override val addDevice = "Add device"
    override val activeDevice = "Active device"
    override val send = "Send"
    override val typeMessage = "Type a message..."
    override val addIp = "Add a computer IP first"
    override val sendFailed = "Send failed"
    override val apiConfiguration = "API configuration"
    override val openMenu = "Open menu"
    override val selectDevice = "Select device"
    override val deleteDevice = "Delete device"
    override val deviceAddress = "Receive port:IP:send port"
    override val defaultPortHint = "IP only defaults to 9000:IP:9001"
    override val invalidDeviceAddress = "Enter a valid IP and ports"
    override val cancel = "Cancel"
    override val translationAssistant = "Translation"
    override val translating = "Translating..."

    override fun translatingRetry(attempt: Int, limit: Int) = "Translating... Retry $attempt/$limit"

    override val deviceManagement = "Device management"
    override val editDevice = "Edit device"
    override val ipAddress = "IP address"
    override val receivePort = "Receive port"
    override val sendPort = "Send port"
    override val save = "Save"
    override val noDevices = "No devices added"
    override val deviceIpHint = "Enter the LAN IP of the device running VRChat."
    override val scanNetwork = "Scan local network"
    override val scanningNetwork = "Scanning local network..."
    override val scanResults = "Devices found"
    override val noScanResults =
        "No devices responded. Check that both devices are on the same local network and try again."
    override val unknownDeviceName = "Unknown device"
    override val alreadyAdded = "Added"
    override val copyMessage = "Copy"
    override val resendMessage = "Send again"
    override val appearance = "Appearance"
    override val language = "Language"
    override val systemTheme = "System"
    override val lightTheme = "Light"
    override val darkTheme = "Dark"
    override val configureVrc = "Configure VRC"
    override val configureVrcIntro =
        "Add the generated OSC argument to VRChat's Steam launch options so VRChat can exchange data with this device."
    override val steamStep1 = "Open Steam Library, right-click VRChat, and select Properties."
    override val steamStep2 = "Under General, find Launch Options."
    override val steamStep3 = "Paste the command below into the field, then restart VRChat."
    override val oscLaunchCommand = "OSC launch command"
    override val copyCommand = "Copy command"
    override val commandCopied = "Copied"
    override val refreshIp = "Refresh IP"
    override val selectLanIp = "Select this device's LAN IP"
    override val currentLanIp = "This device's LAN IP: %s"
    override val lanIpUnavailable =
        "No usable LAN IPv4 address was found. Connect this device to the same local network as the computer running VRChat, then refresh."
    override val localhostOscNote =
        "When this app and VRChat run on the same computer, you may use localhost or 127.0.0.1 instead of the detected address."
    override val remoteOscNote =
        "For another device on your network, the middle value must be that device's IP, for example: --osc=9000:192.168.1.42:9001"
    override val yesterday = "Yesterday"
    override val dayBeforeYesterday = "The day before yesterday"
    override val clearHistory = "Clear chat history"
    override val clearHistoryTitle = "Clear chat history?"
    override val clearHistoryMessage = "All saved messages will be permanently deleted."
    override val delete = "Delete"
    override val messageTooLong =
        "Message exceeds the VRChat Chatbox limit (144 characters or 9 lines)"
    override val translationLlm = "Translation & LLM"
    override val provider = "Provider"
    override val customCompatible = "Custom compatible"
    override val apiKey = "API Key (optional for Ollama)"
    override val targetLanguage = "Target language"
    override val translateBeforeSending = "Translate before sending"
    override val testConnection = "Test connection"
    override val testing = "Testing..."
    override val connectionFailed = "Connection failed; check URL, key and model"
    override val connected = "Connected: %s"
    override val originalAndTranslationSent = "Original and translation sent"
    override val apiSettingsSubtitle =
        "Independent provider profiles, protocol-aware requests, and connection diagnostics"
    override val translationService = "Translation service"
    override val credentialsAndEndpoint = "Credentials & endpoint"
    override val modelAndLanguage = "Model & target language"
    override val translationBehavior = "Translation behavior"
    override val apiKeyOptional = "API Key (optional)"
    override val showApiKey = "Show API Key"
    override val apiKeyRequired = "API Key is required for this provider"
    override val customEndpoint = "Custom endpoint"
    override val baseUrlHint = "Enter the endpoint supplied by the provider or relay"
    override val officialEndpointLocked =
        "Official endpoint is locked to prevent accidental changes"
    override val model = "Model"
    override val chooseModel = "Choose model"
    override val customModelPreserved = "Custom model IDs are preserved exactly as entered"
    override val fallbackModel = "Fallback model"
    override val enableFallbackModel = "Use fallback model"
    override val fallbackModelHint =
        "Used only after the primary model exhausts all retryable attempts"
    override val fallbackRetryCount = "Fallback retry attempts"
    override val fallbackRetryCountHint = "0–10 retries; defaults to 3"
    override val translateBehaviorHint =
        "Send the original text together with its translation to VRChat"
    override val advancedSettings = "Advanced request settings"
    override val requestTimeout = "Request timeout"
    override val timeoutHint = "3–300 seconds; live chat works best with a short timeout"
    override val sendOriginalBeforeTranslation = "Send original before translation"
    override val sendOriginalBeforeTranslationHint =
        "Show the original text in VRChat while the translation is being generated"
    override val retryCount = "Retry attempts"
    override val retryCountHint = "0–10 retries when no usable translation is returned"
    override val customHeaders = "Custom request headers"
    override val customHeadersHint =
        "One Name: value header per line. Secrets are hidden with the rest of this provider profile."
    override val streamingResponse = "Streaming response"
    override val streamingHint = "Assemble server-sent chunks; useful for compatible relays"
    override val connectionSuccessful = "Connection successful"
    override val connectionFailedTitle = "Connection failed"
    override val chooseProvider = "Choose provider"
    override val providerPickerHint = "Search a compact list without covering the page"
    override val searchProvider = "Search provider or protocol"
    override val recommended = "Recommended"
    override val chooseLanguage = "Choose target language"
    override val twoLanguageHint = "Choose one or two languages. Two translations run in parallel."
    override val languageSettings = "Translation languages"
    override val selectedLanguages = "Target languages"
    override val displayOrder = "Display order"
    override val effectPreview = "Preview"
    override val originalText = "Original"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val simultaneousInterpretation = "Simultaneous interpretation"
    override val enableSimultaneousInterpretation = "Enable simultaneous interpretation"
    override val simultaneousInterpretationInputHint =
        "This feature must be used with your keyboard's voice input function."
    override val simultaneousInterpretationHint =
        "Listen for VRChat MuteSelf at the active device address and send port. Opening the microphone starts live original-text messages; muting or Send performs the normal send flow."
    override val listeningPort = "Listening endpoint"
    override val listenerReady = "Waiting for VRChat microphone events"
    override val listenerStopped = "Listener is off"
    override val listenerFailed = "Could not listen on this endpoint: %s"
    override val interpreting = "Microphone open"
    override val alwaysInterpretation = "Always interpret"
    override val enableAlwaysInterpretation = "Enable always interpret"
    override val alwaysInterpretationHint =
        "Replaces Send with Start. Once started, non-empty text is sent automatically after input has been idle for the configured time."
    override val startAlwaysInterpretation = "Start always interpret"
    override val stopAlwaysInterpretation = "Stop always interpret"
    override val alwaysInterpretationDelay = "Automatic send delay"
    override val alwaysInterpretationDelayHint =
        "Send after this much time without another input event"

    override fun seconds(value: String) = "$value seconds"

    override val aboutApp = "About app"
    override val version = "Version"
    override val errorLogs = "Error logs"
    override val noErrorLogs = "No error logs"
    override val clearErrorLogs = "Clear error logs"
    override val clearErrorLogsMessage = "Delete all saved error logs?"

    override fun providerHint(provider: TranslationProvider): String =
        when (provider.id) {
            "openai" ->
                "Official OpenAI API. Base URL is fixed; select a model ID from the presets."
            "openai_compatible" ->
                "For OpenAI-compatible proxies, relays, and self-hosted APIs. Custom model IDs are preserved exactly."
            "anthropic" -> "Official Anthropic Messages API."
            "anthropic_compatible" ->
                "For proxies and relays compatible with the Anthropic Messages API."
            "xai" -> "Official xAI OpenAI-compatible API."
            "grok_compatible" -> "For Grok-compatible relays. Custom request headers are supported."
            "local_ai" ->
                "OpenAI-compatible service on this computer or LAN. API Key may be left blank."
            "google_web" ->
                "Public web translation API with no API Key required; it may be unavailable on some networks."
            "mymemory" ->
                "Translation memory with no API Key required, suitable as a zero-configuration fallback."
            "deepl" ->
                "Uses DeepL API Free by default. Paid accounts should use https://api.deepl.com/v2."
            "libretranslate" ->
                "Self-hosted LibreTranslate usually requires no API Key; public instances may require one."
            "qianwen" ->
                "Select the endpoint for the API Key region. Japan requires a workspace-specific URL."
            "hunyuan" -> "Tencent Hunyuan OpenAI-compatible API."
            "xiaomi" ->
                "Select the global pay-as-you-go or Token Plan cluster for the API Key type."
            "deepseek" ->
                "Official DeepSeek API. Enter the corresponding Base URL when using a relay API Key."
            "zhipu" -> "Zhipu GLM OpenAI-compatible API."
            "gemini" -> "Official Gemini OpenAI-compatible API."
            "kimi" -> "Official Moonshot OpenAI-compatible API."
            "mistral" -> "Official Mistral API."
            "doubao" ->
                "Enter the current model or inference endpoint ID from the Volcengine Ark console."
            "nvidia" -> "Supports NVIDIA API Catalog, self-hosted NIM, and compatible proxies."
            else -> provider.hint
        }

    override fun providerRegionLabel(providerId: String, region: ProviderRegion): String =
        when (providerId to region.id) {
            "qianwen" to "china" -> "Mainland China"
            "qianwen" to "singapore" -> "Singapore / International"
            "qianwen" to "japan" -> "Japan (custom)"
            "xiaomi" to "global" -> "Global pay-as-you-go"
            "xiaomi" to "china" -> "China Token Plan"
            "xiaomi" to "singapore" -> "Singapore Token Plan"
            "xiaomi" to "europe" -> "Europe Token Plan"
            else -> region.label
        }
}
