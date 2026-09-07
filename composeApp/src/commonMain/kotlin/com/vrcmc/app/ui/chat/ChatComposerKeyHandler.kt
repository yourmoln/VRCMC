package com.vrcmc.app

import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal class ChatComposerKeyHandler {
    private var enterPressed = false
    private var consumeEnterRelease = false

    fun reset() {
        enterPressed = false
        consumeEnterRelease = false
    }

    fun onKeyEvent(
        event: KeyEvent,
        desktop: Boolean,
        canSend: Boolean,
        composing: Boolean,
        onNewline: () -> Unit = {},
        onSend: () -> Unit,
    ): Boolean {
        if (!desktop || (event.key != Key.Enter && event.key != Key.NumPadEnter)) return false
        if (event.type == KeyEventType.KeyUp) {
            val consumed = consumeEnterRelease
            reset()
            return consumed
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (enterPressed) return consumeEnterRelease
        enterPressed = true
        if (composing || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return false
        }
        consumeEnterRelease = true
        if (event.isShiftPressed) onNewline()
        else if (canSend) onSend()
        return true
    }
}

internal fun insertChatComposerNewline(value: TextFieldValue): TextFieldValue = value.copy(
    text = value.text.replaceRange(value.selection.min, value.selection.max, "\n"),
    selection = TextRange(value.selection.min + 1),
    composition = null,
)
