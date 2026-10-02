# AniBrowser

Android browsers focused on remembered video playback speed, fullscreen viewing, and control over redirects. Choose **Full** for the broader feature set or **Lite** for a smaller native interface with less background work. Both apps can be installed together.

## Download for Android

The [current preview release](https://github.com/akasumitlamba/AniBrowser/releases/tag/android-2026.09.25-preview) contains exactly **two APKs**, both for **ARM64 Android devices** running Android 8.0 or later:

| App | Version | Download | APK size |
| --- | --- | --- | --- |
| Full | 1.0.2640 | [Full APK](https://github.com/akasumitlamba/AniBrowser/releases/download/android-2026.09.25-preview/AniBrowser-Full-1.0.2640-arm64-v8a-debug.apk) | About 249 MiB |
| Lite | 0.3.2 | [Lite APK](https://github.com/akasumitlamba/AniBrowser/releases/download/android-2026.09.25-preview/AniBrowser-Lite-0.3.2-arm64-v8a-debug.apk) | About 196 MiB |

Download one app or both, open the APK on Android, and allow installation from your chosen browser or file manager when Android prompts you. These release downloads do not support 32-bit ARM or x86 devices. SHA-256 checksums are included in the release notes.

Both apps bundle Mozilla's GeckoView browser engine. **Lite reduces interface complexity and background work; it is not a tiny-download browser.** APK size is not a measurement of runtime RAM use. These are debug-signed preview builds, not stable store releases.

## Full and Lite

| Capability | Full | Lite |
| --- | --- | --- |
| Home screen | AniHome with saved website tiles | Native search panel, bookmark and history cards |
| Playback speed | Remembered playback settings, 1×–2× | Per-site speed, 0.5×–4× choices |
| Browser navigation | Tabs and website sessions | Up to four tabs, bookmarks, history, Find in page, sharing and Copy link |
| Fullscreen | Site controls preserved, native fallback where needed | Immersive home-screen shortcuts and accessible playback tools |
| Website controls | Popup/redirect controls and DNS provider settings | Per-attempt cross-site prompts; common search engines exempt |
| Viewing modes | Site settings | Mobile, desktop identity with mobile layout, or full desktop layout |
| Extensions | Add-on support | Optional Mozilla-signed extensions via HTTPS XPI links |
| Extra features | Ad blocking, background-playback settings and picture-in-picture | No ad blocker, background playback or picture-in-picture |

Lite uses Google by default, with remembered DuckDuckGo and Bing options. Its top address bar and grouped menu keep page tools accessible without a permanent bottom playback bar. Bookmarks can be renamed or removed from their menu. See the [Lite guide](lite/README.md) for details and supported extension limitations.

## Recent refinements

- Full stops recurring playback recovery checks while media is paused or ended, then restarts one timer on resume.
- Lite combines bursts of media events into one layout measurement and state message, and avoids temporary arrays when selecting a player.
- Lite includes a redesigned native home screen, grouped menu, failed-page retry, Stop loading, bookmark editing, and a white icon with blue Lite lettering.

The performance changes reduce work verified by regression tests. They have **not** been benchmarked for speed, RAM or battery life on a physical 3 GB device. The latest builds passed 35 Full script tests, 15 Full Android unit tests, 16 Lite script tests and 6 Lite Android unit tests. Lite lint has zero errors; Full lint cleanup remains outstanding.

Website playback controls, subtitles, sign-in, DRM, and extension compatibility vary by site. AniBrowser does not bypass DRM. Physical-device streaming acceptance remains unverified for these previews.

## Build from source

- **Full Android:** [Build instructions](android/README.md). Run `./build-android.ps1` on Windows or use the Gradle wrapper inside `android/`.
- **Lite Android:** [Features and build instructions](lite/README.md). Run `./build-lite.ps1` on Windows. Local builds can produce additional processor variants; the public preview offers only the two ARM64 APKs above.
- **iPhone and iPad:** [Development](ios/README.md) and [installation from Windows](ios/INSTALL-WINDOWS.md). Source and an unsigned IPA workflow are available; no iOS package is included in this Android release.
- **Windows and Linux:** no desktop application or installer is available. A website's desktop viewing mode does not make the Android app a desktop application.

## Source layout

```text
android/             Full Android app, assets, tests and Gradle configuration
lite/                Lightweight Android app, assets, tests and Gradle configuration
ios/                 iPhone/iPad app, assets, tests and build configuration
.github/workflows/   Build and source-check workflows
build-android.ps1    Windows helper for building Full
build-lite.ps1       Windows helper for building Lite
```

## License and attribution

Source code is distributed under the [Mozilla Public License 2.0](LICENSE). Full Android derives from [Mozilla Reference Browser](https://github.com/mozilla-mobile/reference-browser), using Android Components and GeckoView. Lite uses GeckoView directly. Existing source notices are retained. AniBrowser is independent and is not an official Mozilla product. Third-party libraries and media retain their respective licenses; product names and trademarks belong to their owners.
