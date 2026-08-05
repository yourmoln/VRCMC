package com.vrcmc.app

fun localeStrings(language: AppLanguage): LocaleStrings =
    when (language) {
        AppLanguage.ZH_HANS -> LocaleStringsZhHans
        AppLanguage.ZH_HANT -> LocaleStringsZhHant
        AppLanguage.JA -> LocaleStringsJa
        AppLanguage.EN -> LocaleStringsEn
    }
