package io.github.antonudovychenko.bugreport.sample

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** `./gradlew :sample:run` */
public fun main(): Unit = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KMP Bug Report sample",
        state = rememberWindowState(width = 440.dp, height = 880.dp),
    ) {
        SampleApp()
    }
}
