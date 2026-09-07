package com.vrcmc.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class ChatComposerInputTest {
    @get:Rule val compose = createComposeRule()
    private val sent = mutableListOf<String>()

    private fun showComposer() {
        compose.setContent {
            var input by remember { mutableStateOf("") }
            MaterialTheme {
                ChatComposer(
                    input = input,
                    sending = false,
                    enabled = true,
                    interpreting = false,
                    alwaysInterpretationEnabled = false,
                    alwaysInterpretationActive = false,
                    voiceInputEnabled = false,
                    voiceRecording = false,
                    voiceSpeaking = false,
                    voiceTranscribing = false,
                    maxInputCharacters = 144,
                    strings = LocaleStringsEn,
                    onInputChange = { input = it },
                    onSend = { sent += input; input = "" },
                    onToggleAlwaysInterpretation = {},
                    onToggleVoiceInput = {},
                )
            }
        }
    }

    @Test fun shiftEnterInsertsAtCursorThenEnterSendsMultilineText() {
        showComposer()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("firstsecond")
        field.performTextInputSelection(TextRange(5))
        field.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        field.assertTextEquals("first\nsecond")
        field.assertIsFocused()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
        field.performTextInput("middle")
        field.assertTextEquals("first\nmiddlesecond")
        field.performKeyInput { pressKey(Key.Enter) }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        field.assertIsFocused()
        compose.runOnIdle { assertEquals(listOf("first\nmiddlesecond"), sent) }
    }

    @Test fun shiftEnterReplacesSelectionWithNewline() {
        showComposer()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("first REPLACE second")
        field.performTextInputSelection(TextRange(14, 5))
        field.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        field.assertTextEquals("first\nsecond")
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }

    @Test fun shiftNumpadEnterInsertsNewlineInEmptyInput() {
        showComposer()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.NumPadEnter)
            keyUp(Key.ShiftLeft)
        }
        field.assertTextEquals("\n")
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }
}
