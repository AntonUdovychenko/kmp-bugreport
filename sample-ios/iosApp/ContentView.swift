import SwiftUI
import UIKit
import Sample

/// Hosts the shared SampleApp() composable from :sample.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // The app is edge-to-edge; its Scaffold pads for the status bar, home indicator and keyboard.
        ComposeView()
            .ignoresSafeArea()
    }
}
