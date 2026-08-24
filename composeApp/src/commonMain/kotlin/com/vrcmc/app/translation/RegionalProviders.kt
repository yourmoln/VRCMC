package com.vrcmc.app

import kotlinx.serialization.json.*

internal val regionalProviders =
    listOf(
        TranslationProvider(
            "google_web",
            "Google Web",
            ProviderProtocol.GOOGLE_WEB,
            "https://translate.googleapis.com/translate_a/single",
            "google-web",
            listOf("google-web"),
            keyRequired = false,
            hint = "免 Key 的公共网页翻译接口；部分网络环境可能无法访问。",
        ),
        TranslationProvider(
            "mymemory",
            "MyMemory",
            ProviderProtocol.MYMEMORY,
            "https://api.mymemory.translated.net/get",
            "mymemory",
            listOf("mymemory"),
            keyRequired = false,
            hint = "免 Key 的翻译记忆库，适合作为零配置备用服务。",
        ),
        TranslationProvider(
            "deepl",
            "DeepL",
            ProviderProtocol.DEEPL,
            "https://api-free.deepl.com/v2",
            "deepl-api",
            listOf("deepl-api"),
            editableBaseUrl = true,
            hint = "默认使用 DeepL API Free；付费账户请改为 https://api.deepl.com/v2。",
        ),
        TranslationProvider(
            "libretranslate",
            "LibreTranslate",
            ProviderProtocol.LIBRE,
            "http://127.0.0.1:5000",
            "libretranslate",
            listOf("libretranslate"),
            keyRequired = false,
            editableBaseUrl = true,
            hint = "自托管 LibreTranslate 通常无需 Key，公共实例可能需要。",
        ),
        TranslationProvider(
            "qianwen",
            "Qwen",
            ProviderProtocol.OPENAI,
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "qwen-mt-plus",
            listOf("qwen-mt-plus", "qwen-mt-flash"),
            editableBaseUrl = true,
            hint = "按 API Key 所属地域选择端点。日本区域需填写工作空间专属 URL。",
            regions =
                listOf(
                    ProviderRegion(
                        "china",
                        "中国大陆",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    ),
                    ProviderRegion(
                        "singapore",
                        "新加坡/国际",
                        "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                    ),
                    ProviderRegion("japan", "日本（自定义）", ""),
                ),
        ),
        TranslationProvider(
            "hunyuan",
            "腾讯混元",
            ProviderProtocol.OPENAI,
            "https://api.hunyuan.cloud.tencent.com/v1",
            "hunyuan-turbos-latest",
            listOf("hunyuan-turbos-latest", "hunyuan-turbo-latest"),
            editableBaseUrl = true,
            hint = "腾讯混元 OpenAI-compatible 接口。",
        ),
        TranslationProvider(
            "xiaomi",
            "Xiaomi MiMo",
            ProviderProtocol.OPENAI,
            "https://api.xiaomimimo.com/v1",
            "mimo-v2.5-pro",
            listOf("mimo-v2.5-pro", "mimo-v2.5", "mimo-v2-flash"),
            editableBaseUrl = true,
            hint = "按 Key 类型选择全球按量或 Token Plan 集群。",
            regions =
                listOf(
                    ProviderRegion("global", "全球按量", "https://api.xiaomimimo.com/v1"),
                    ProviderRegion(
                        "china",
                        "中国 Token Plan",
                        "https://token-plan-cn.xiaomimimo.com/v1",
                    ),
                    ProviderRegion(
                        "singapore",
                        "新加坡 Token Plan",
                        "https://token-plan-sgp.xiaomimimo.com/v1",
                    ),
                    ProviderRegion(
                        "europe",
                        "欧洲 Token Plan",
                        "https://token-plan-ams.xiaomimimo.com/v1",
                    ),
                ),
        ),
    )
