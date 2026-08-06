package com.vrcmc.app

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun textClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("VRCMC", text))
