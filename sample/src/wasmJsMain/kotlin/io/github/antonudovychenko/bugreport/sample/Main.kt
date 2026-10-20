package io.github.antonudovychenko.bugreport.sample

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/** `./gradlew :sample:wasmJsBrowserDevelopmentRun` */
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    ComposeViewport(document.body!!) {
        SampleApp()
    }
}
