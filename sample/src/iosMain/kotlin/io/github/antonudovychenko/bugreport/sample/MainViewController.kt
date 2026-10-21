package io.github.antonudovychenko.bugreport.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Hosted by sample-ios/ — open `sample-ios/iosApp.xcodeproj` in Xcode and run. */
public fun MainViewController(): UIViewController = ComposeUIViewController { SampleApp() }
