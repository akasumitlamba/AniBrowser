# AniBrave

Personal Android browser based on the official Brave 1.97.56 ARM64 APK (Chromium 155.0.8059.40). Install identity: `app.anibrave.main`. Launcher name: **AniBrave**. This installs alongside official Brave, AniBrowser Full and AniBrowser Lite. It has an independent profile: cookies are shared between AniBrave tabs and its shortcuts, not imported from other installed browsers.

The native engine and Shields libraries remain byte-for-byte identical to the verified upstream APK. This is a locally compiled Java/JavaScript customization and repackaging of that APK, not a full Chromium source build. It uses a separate personal signing key and needs manual updates. It is an experimental personal build; physical tablet startup and settings application have been checked, but universal streaming compatibility is not claimed.

## Included scope

- Per-site speed choices from 0.5× to 4×, saved immediately. `www` and mobile hostname aliases share settings. Embedded frames use the top website's setting.
- Event-driven speed recovery for replacement players, metadata, playing and rate changes, with bounded retries when a player rejects the requested rate.
- A native sliders icon beside the tab switcher opens Playback speed, Site view and Fullscreen shortcut. It belongs to the address-bar layout and scrolls with Brave's toolbar; there is no persistent floating button. Website seeking, subtitles and playback controls remain untouched.
- Android pinned website shortcuts launch Brave's existing FullScreenCustomTabActivity via an internal activity, using its normal browsing profile, zero-height browser toolbar and immersive Android system bars. Edge gestures can temporarily reveal system bars.
- Scroll or vertical touch gestures in immersive shortcuts reveal tools without consuming page input.
- Mobile, desktop identity with mobile viewport, and desktop viewport settings saved per site. Changes reload the page. Device rotation updates viewport dimensions.
- Website-provided launcher artwork, with an AniBrave icon fallback.
- Brave's ordinary address-bar scroll behavior, Shields and DNS settings remain in place.
- New-tab URLs are translated to `about:blank`; no AniHome/dashboard/wallpaper is added.

No extra playback/seek controls, AniBrowser redirect prompts, custom DNS interface or extension framework is added.

## Use

Open a site, tap the **sliders icon next to Tabs**, then choose **Playback speed**, **Site view**, or **Fullscreen shortcut**. Immersive shortcuts keep gesture-revealed temporary tools because their browser toolbar is hidden. Confirm the Android launcher prompt to create a shortcut. Create shortcuts within AniBrave: shortcuts made by another browser remain associated with that browser.

Website players may resist speed changes, and native/closed-shadow/DRM-controlled playback can restrict scripting. The controller does not bypass DRM or modify the website's fullscreen DOM. Website speed support, subtitles, login persistence and controls in fullscreen must be checked on the tablet.

## Build on Windows

```powershell
.\brave\build.ps1
# Controller checks only:
.\brave\build.ps1 -TestsOnly
```

Requires Java 17, Node, Python, `gh`, and the workspace Android SDK 37.1/build-tools 37.0.0. The script pins and checks upstream APK/tool hashes, compiles the feature module, assembles five narrowly scoped integration hooks, rewrites install-identity string constants through dexlib2, rebuilds Android resources, retains all original native libraries, aligns and signs the APK, and verifies the result. It uses the existing `brave/signing/anibrave.jks`; preserve that file for future updates. Signing files and downloaded binaries are ignored by Git.

Output: `output/anibrave/AniBrave-0.1.0-arm64-v8a.apk`, plus `verification.json` and `SHA256SUMS.txt`. Minimum Android version: Android 10. Only this ARM64 build is intended for delivery.

## Validation and limits

23 controller checks cover rate persistence/recovery, replacement videos, bounded rejection, independent frame contexts, preserved fullscreen controls, gestures, duplicate installation, site keys and persistent HTTP response parsing. APK signature, 16 KiB library alignment, APK integrity, all eight DEX checksums and all five original native-library hashes are checked.

The original page-control connection waited for EOF on Chromium's persistent HTTP connection, preventing any settings from being applied. The response reader now consumes exactly Content-Length bytes, including partial reads, with size and truncation checks. Browser-level events without a page session no longer trigger a null-key lookup and reconnect. Target URL changes update saved settings. Diagnostic logs report connection and protocol failures without logging page contents.

On 2026-10-09 the updated signed ARM64 APK installed on the connected 9080G Android 10 tablet. The address-bar sliders icon beside Tabs and its menu were visually checked. Using the native menu, 2× immediately reached a temporary hidden HTML media element, recovered after a rate reset, and persisted across a page reload. Mobile, desktop identity with mobile layout, and Desktop produced their expected user agents and viewport widths (960 / 960 / 1280 in the tablet's landscape orientation). Original site settings were restored and the temporary element removed. The final controller remained connected without the previous null-session errors. Screenshot and probe evidence is under `brave/qa-output`.

These are settings integration checks, not live streaming/DRM or fullscreen shortcut acceptance. An earlier private x86-64 emulator harness checked startup only; its local video launch was blocked by automatic approval review. Website playback and shortcut behavior still need physical user acceptance.

The internal controller uses Chromium's DevTools protocol through an abstract Unix socket named `anibrave_devtools_remote`. It opens no TCP port. Native authorization retains Chromium's ADB/root rules and additionally allows AniBrave's own UID; other ordinary apps are denied. The client and socket stop when the browser activity pauses. The socket is not a public Android service or JavaScript bridge exposed to website scripts; bindings live in a separate isolated world.

## Upstream and notices

- [Brave 1.97.56 release](https://github.com/brave/brave-browser/releases/tag/v1.97.56)
- [Corresponding Brave source](https://github.com/brave/brave-core/tree/v1.97.56), checked-out commit `b01cdf43be4b4d5559bf7e58229e666e24454f50`
- [Chromium source](https://chromium.googlesource.com/chromium/src/+/155.0.8059.40/)
- [Brave source license](https://github.com/brave/brave-core/blob/v1.97.56/LICENSE)

Existing native and browser notices are retained in the app. Customization sources in this folder are MPL-2.0. AniBrave is independent and is not an official Brave Software product. Brave, Chromium, apktool, smali/dexlib2 and their dependencies retain their respective licenses and trademarks.
