package com.vrcmc.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MessageBubbleDesktopTest {
    @get:Rule val compose = createComposeRule()

    private fun showMessage() {
        compose.setContent {
            MaterialTheme {
                MessageBubble(
                    message = ChatMessage("Desktop message", MessageRole.USER),
                    strings = LocaleStringsEn,
                    retryAttempt = 0,
                    retryLimit = 0,
                    resendEnabled = true,
                    showJapaneseRomaji = false,
                    onCopy = {},
                    onResend = {},
                )
            }
        }
    }

    @Test
    fun rightClickOpensMessageActions() {
        showMessage()

        compose.onNodeWithText("Desktop message").performMouseInput { rightClick() }

        compose.onNodeWithText(LocaleStringsEn.copyMessage).assertIsDisplayed()
        compose.onNodeWithText(LocaleStringsEn.resendMessage).assertIsDisplayed()
    }

    @Test
    fun primaryLongClickDoesNotOpenMessageActions() {
        showMessage()

        compose.onNodeWithText("Desktop message").performMouseInput { longClick() }

        compose.onNodeWithText(LocaleStringsEn.copyMessage).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsEn.resendMessage).assertDoesNotExist()
    }
}
