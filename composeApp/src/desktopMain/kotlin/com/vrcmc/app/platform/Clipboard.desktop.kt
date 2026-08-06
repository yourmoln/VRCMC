package com.vrcmc.app

import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

actual fun textClipEntry(text: String): ClipEntry = ClipEntry(StringSelection(text))
