package io.github.dwite.voiceglow.sample

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "voice-glow", state = rememberWindowState(width = 980.dp, height = 760.dp)) {
        Sample()
    }
}
