# AniBrowser

A browser ecosystem focused on a personal home screen, persistent video playback speed, and control over website navigation.

## Platforms

| Platform | Implementation | Status |
| --- | --- | --- |
| Android | Kotlin, Mozilla Android Components, GeckoView | Source and local APK builds |
| Android Lite | Java, direct GeckoView | Separate minimal app; preview APKs available |
| iOS / iPadOS | Swift, UIKit, WebKit | Source and unsigned IPA cloud builds |
| Windows | Planned | No desktop application or installer yet |
| Linux | Under consideration | No application or package yet |

Android and iOS are separate native implementations with shared playback behavior. Features and website compatibility vary by platform.

## Features

- AniHome with saved website tiles.
- Persistent HTML5 video playback speed from 1× to 2×.
- Browser tabs and website sessions.
- Popup and redirect controls.
- Platform-specific fullscreen and playback controls.

Protected streaming, subtitles, sign-in and player behavior depend on each website and platform. AniBrowser does not bypass DRM. Current builds are personal prototypes, not a stable public release.

## Build and install

- **Android Lite:** [Features and build instructions](lite/README.md). [Download the Lite preview](https://github.com/akasumitlamba/AniBrowser/releases/tag/lite-v0.2.0-preview). Installs alongside the full Android app.

- **Android:** [Build instructions](android/README.md). Run `./build-android.ps1` on Windows or use the Gradle wrapper inside `android/`.
- **iPhone and iPad:** [Development](ios/README.md) and [free installation from Windows](ios/INSTALL-WINDOWS.md). Run **Actions → Build free iOS IPA**. Free Apple Account signing requires refreshing every seven days.
- **Windows and Linux:** desktop builds are not available yet.

The iOS workflow uses a standard GitHub-hosted runner in this public repository and needs no Apple credentials. Download temporary build artifacts from completed Actions runs.

## Source layout

```text
android/             Android app, assets, tests and Gradle configuration
ios/                 iPhone/iPad app, assets, tests and build configuration
.github/workflows/   Build and source-check workflows
build-android.ps1    Windows helper for building the Android app
```

## License and attribution

Source code is distributed under the [Mozilla Public License 2.0](LICENSE). Android derives from [Mozilla Reference Browser](https://github.com/mozilla-mobile/reference-browser), using Android Components and GeckoView. Existing source notices are retained. AniBrowser is independent and is not an official Mozilla product. Third-party libraries and media retain their respective licenses; product names and trademarks belong to their owners.
