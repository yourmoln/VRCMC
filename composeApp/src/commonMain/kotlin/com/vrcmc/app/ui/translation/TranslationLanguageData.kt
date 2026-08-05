package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

internal val availableTargetLanguages =
    listOf("English", "简体中文", "繁體中文", "日本語", "한국어", "Español", "Français", "Deutsch", "Русский")

internal fun previewForLanguage(language: String): String =
    when (language) {
        "English" -> "Want to play together tonight?"
        "简体中文" -> "今晚一起玩吗？"
        "繁體中文" -> "今晚一起玩嗎？"
        "日本語" -> "今夜一緒に遊びませんか？"
        "한국어" -> "오늘 밤 같이 놀래요?"
        "Español" -> "¿Jugamos juntos esta noche?"
        "Français" -> "On joue ensemble ce soir ?"
        "Deutsch" -> "Spielen wir heute Abend zusammen?"
        "Русский" -> "Поиграем вместе сегодня вечером?"
        else -> language
    }

internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }
