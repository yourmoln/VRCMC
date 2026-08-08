package com.vrcmc.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import com.vrcmc.app.generated.resources.Res
import com.vrcmc.app.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    val state =
        rememberWindowState(
            width = 1100.dp,
            height = 760.dp,
            position = WindowPosition.Aligned(Alignment.Center),
        )
    Window(
        state = state,
        onCloseRequest = ::exitApplication,
        title = "VRCMC",
        icon = painterResource(Res.drawable.logo),
        resizable = true,
    ) {
        VrcmcApp { dark -> setWindowsTitleBar(window, dark) }
    }
}

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(window: HWND, attribute: Int, value: Pointer, size: Int): Int
}

private fun setWindowsTitleBar(window: Window, dark: Boolean) {
    if (!Platform.isWindows()) return
    runCatching {
        val hwnd = HWND(Native.getComponentPointer(window))
        val api = Native.load("dwmapi", DwmApi::class.java)
        setDwmColor(api, hwnd, 35, if (dark) 0x00202020 else 0x00FFFFFF)
        setDwmColor(api, hwnd, 36, if (dark) 0x00F5F5F5 else 0x00111111)
    }
}

private fun setDwmColor(api: DwmApi, hwnd: HWND, attribute: Int, color: Int) {
    Memory(4).also {
        it.setInt(0, color)
        api.DwmSetWindowAttribute(hwnd, attribute, it, 4)
    }
}
