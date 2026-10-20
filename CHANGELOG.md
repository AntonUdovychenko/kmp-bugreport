# Changelog

## 0.1.0 — unreleased

First release, extracted from the CarGuy app's in-app feedback tool.

- `BugReportHost`: captures the app's content when the report opens; opens on a system screenshot
  on iOS and Android 14+.
- Markdown description with a formatting toolbar and keyboard shortcuts.
- `ImageMarkupEditor`: freehand markup and crop, with undo and reset.
- Images from the platform picker on Android, iOS, desktop and web.
- `DiagnosticLogBuffer`: bounded, redacting log history with a per-report opt-out.
- `bugreport-ktor`: multipart/form-data sender for a Ktor `HttpClient`.
- English, Czech, German, Polish and Ukrainian.
