# AniBrowser Lite

A separate Android browser with a small native interface, built directly on Mozilla's open-source GeckoView engine. It installs alongside AniBrowser as `app.anibrowser.lite` and keeps its own cookies and settings.

## Included

- Playback speed saved automatically for each website, including its embedded players. Mobile/www aliases share the setting. Speed is restored on media loading, playing and rate-change events, without a polling loop or document-wide mutation observer.
- Cross-site navigation and pop-up confirmation for each attempt. Common search engines are exempt. An Allow decision applies only to that request. Embedded video frames and media requests are not treated as page redirects.
- Three saved viewing modes: Mobile; Desktop identity with mobile layout; Desktop. These select user-agent identity and viewport independently. Websites still decide which layout/content to return.
- Home-screen shortcuts open with the browser address bar, status bar and navigation bar hidden. Android edge gestures can temporarily reveal system bars. A vertical swipe or the small controls button reveals the playback/site toolbar. Its space is reserved below the page, keeping bottom player controls accessible.
- Play/pause, seek backward/forward 10 seconds, speed and site settings. Website player containers and their controls are left intact. Lite's native speed/controls button stays reachable above fullscreen web content. A tap or vertical swipe reveals the full toolbar. Bare-video fullscreen enables native video controls and restores the original setting on exit.
- Optional Mozilla-signed WebExtensions: install via an HTTPS XPI download link, review permissions, open extension actions/options, enable, disable, update on demand and remove.

There is no home dashboard, wallpaper, ad blocker, sync/account framework, background playback service, picture-in-picture, tab thumbnail cache, add-on catalog fetcher or periodic app maintenance worker. The main browser uses one page session. One temporary popup is permitted when needed for login flows and is closed when returning to the parent. Extension UI uses a temporary session only while open.

## Use

Launch the app and enter a website or search. Use **Site** for the three viewing modes and playback speed; **Menu** provides Reload, Forward, Add to home screen and Extensions. Speed changes are saved immediately.

For a fullscreen shortcut, open the site and select **Menu → Add to home screen**, then accept the Android launcher prompt. Create shortcuts from Lite itself; existing full-app shortcuts still belong to the full app.

## Build

From the parent AniBrowser workspace on Windows:

```powershell
.\build-lite.ps1
```

This runs JavaScript playback/redirect tests, Java navigation-policy tests, Android lint, and produces signed development APKs for ARM64, ARMv7 and x86-64 under `output/lite/`. Use ARM64 for most current devices and ARMv7 for 32-bit Android installations. These are development builds, not store releases.

For a standalone checkout, configure an Android SDK in `local.properties` and run Gradle 9.7.1 with JDK 17:

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
node --test app/src/test/playback.test.cjs
```

The engine is pinned to `org.mozilla.geckoview:geckoview-nightly-omni:157.0.20260906203723`. It should receive regular security updates before long-term distribution. App sources are MPL-2.0; see LICENSE and THIRD-PARTY-NOTICES.md.

## Scope and validation

This is an Android application. Desktop identity is a website setting, not a Windows application.

The smaller app framework does not remove the browser engine's or website's own memory/CPU requirements. Smooth video on a particular 3 GB device, codec/DRM compatibility, subscription playback and site-specific extension compatibility require physical-device testing. Some players reject playback-rate changes; writes are bounded to avoid a CPU loop. Closed shadow DOM/native/DRM players can restrict injected controls. A native controls button remains available in fullscreen.

Camera, microphone, location, push notifications, browser sync, a download manager and password storage are outside this minimal version. Standard website forms, HTML video controls and sign-in cookies remain available. Extension APIs requiring a full tabs/downloads ecosystem may not be supported.
