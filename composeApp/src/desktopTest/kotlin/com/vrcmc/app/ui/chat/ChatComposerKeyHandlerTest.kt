package com.vrcmc.app

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import java.awt.event.InputEvent
import kotlin.test.*

@OptIn(InternalComposeUiApi::class)
class ChatComposerKeyHandlerTest {
    private fun enter(
        release: Boolean = false,
        modifiers: Int = 0,
        numpad: Boolean = false,
    ) = KeyEvent(
        key = if (numpad) Key.NumPadEnter else Key.Enter,
        type = if (release) KeyEventType.KeyUp else KeyEventType.KeyDown,
        isShiftPressed = modifiers and InputEvent.SHIFT_DOWN_MASK != 0,
        isCtrlPressed = modifiers and InputEvent.CTRL_DOWN_MASK != 0,
        isAltPressed = modifiers and InputEvent.ALT_DOWN_MASK != 0,
        isMetaPressed = modifiers and InputEvent.META_DOWN_MASK != 0,
    )

    @Test fun enterSendsOnceAndConsumesPressAndRelease() {
        val handler = ChatComposerKeyHandler()
        var sends = 0
        assertTrue(handler.onKeyEvent(enter(), true, true, false) { sends++ })
        assertTrue(handler.onKeyEvent(enter(), true, true, false) { sends++ })
        assertTrue(handler.onKeyEvent(enter(release = true), true, true, false) { sends++ })
        assertEquals(1, sends)
        assertTrue(handler.onKeyEvent(enter(), true, true, false) { sends++ })
        assertEquals(2, sends)
    }

    @Test fun shiftEnterInsertsExactlyOneNewlineAndConsumesBothEvents() {
        val handler = ChatComposerKeyHandler()
        var newlines = 0
        repeat(2) {
            assertTrue(handler.onKeyEvent(enter(modifiers = InputEvent.SHIFT_DOWN_MASK), true, true, false,
                onNewline = { newlines++ }) { fail() })
        }
        assertTrue(handler.onKeyEvent(enter(release = true), true, true, false,
            onNewline = { newlines++ }) { fail() })
        assertEquals(1, newlines)
    }

    @Test fun nonDesktopEnterAndShiftEnterAreNotConsumed() {
        val handler = ChatComposerKeyHandler()
        for (modifiers in listOf(0, InputEvent.SHIFT_DOWN_MASK)) {
            assertFalse(handler.onKeyEvent(enter(modifiers = modifiers), false, true, false) { fail() })
            assertFalse(handler.onKeyEvent(enter(release = true, modifiers = modifiers), false, true, false) { fail() })
        }
    }

    @Test fun inputMethodConfirmationDoesNotSendEvenIfCompositionEndsBeforeKeyRelease() {
        val handler = ChatComposerKeyHandler()
        assertFalse(handler.onKeyEvent(enter(), true, true, true) { fail() })
        assertFalse(handler.onKeyEvent(enter(), true, true, false) { fail() })
        assertFalse(handler.onKeyEvent(enter(release = true), true, true, false) { fail() })
        var sends = 0
        assertTrue(handler.onKeyEvent(enter(), true, true, false) { sends++ })
        assertEquals(1, sends)
    }

    @Test fun emptyOrDisabledInputDoesNotSendOrInsertNewline() {
        val handler = ChatComposerKeyHandler()
        assertTrue(handler.onKeyEvent(enter(), true, false, false) { fail() })
        assertTrue(handler.onKeyEvent(enter(release = true), true, false, false) { fail() })
    }

    @Test fun numpadEnterSendsAndFocusLossResetsHeldKey() {
        val handler = ChatComposerKeyHandler()
        var sends = 0
        assertTrue(handler.onKeyEvent(enter(numpad = true), true, true, false) { sends++ })
        handler.reset()
        assertTrue(handler.onKeyEvent(enter(), true, true, false) { sends++ })
        assertEquals(2, sends)
    }

    @Test fun otherModifierCombinationsDoNotSend() {
        for (modifiers in listOf(InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK)) {
            val handler = ChatComposerKeyHandler()
            assertFalse(handler.onKeyEvent(enter(modifiers = modifiers), true, true, false) { fail() })
        }
    }
}
