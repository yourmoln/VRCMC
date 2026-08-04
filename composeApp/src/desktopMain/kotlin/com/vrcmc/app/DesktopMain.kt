package com.vrcmc.app
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.vrcmc.app.generated.resources.Res
import com.vrcmc.app.generated.resources.logo
import org.jetbrains.compose.resources.painterResource
fun main() = application {
    val state = rememberWindowState(width = 400.dp, height = 800.dp, position = WindowPosition.Aligned(Alignment.Center))
    Window(state = state, onCloseRequest = ::exitApplication, title = "VRCMC", icon = painterResource(Res.drawable.logo), resizable = false) { VrcmcApp() }
}
