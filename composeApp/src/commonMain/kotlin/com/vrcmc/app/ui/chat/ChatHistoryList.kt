package com.vrcmc.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun ChatHistoryList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    emptyContent: @Composable LazyItemScope.() -> Unit,
    messageContent: @Composable (Int, ChatMessage) -> Unit,
) {
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) listState.requestScrollToItem(0)
    }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            listState.requestScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        // Anchor the latest item to the bottom even when insets or message heights change.
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
    ) {
        if (messages.isEmpty()) {
            item(content = emptyContent)
        } else {
            itemsIndexed(
                items = messages.asReversed(),
                key = { index, _ -> messages.lastIndex - index },
            ) { index, message ->
                messageContent(messages.lastIndex - index, message)
            }
        }
    }
}
