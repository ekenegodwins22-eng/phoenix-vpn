# PhoenixTunnel

PhoenixTunnel is a personal Android VPN client project. The Android application uses Kotlin, plain Android views, min SDK 26, target SDK 34, compile SDK 35, and the WireGuard Android tunnel library.

## Features

- WireGuard configuration parsing, file-based profiles, and VPN service foundation.
- Tweak-profile JSON storage and `vmess://` / `vless://` URI inspection.
- Protocol selector scaffolding for SSH, V2Ray, and Hysteria. Those non-WireGuard engines are stubs and log `engine not yet implemented`.
- Purple dark UI, profile import/paste, logs, Android 12 splash screen, and Phoenix flame launcher artwork.
- GitHub Actions debug APK build on pushes to `main`.

## Build

Install JDK 17 and Android SDK (platform 35, build tools), then run:

```sh
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. The landing page is `landing/index.html`; its download button targets `apk/PhoenixTunnel.apk`, which can be populated with a release artifact separately.

## Profiles

Sample profiles are in `configs/samples/`. All keys and endpoints are placeholders. Replace them with real credentials before use. The Create Config screen checks parsing and does a TCP reachability check for WireGuard endpoints before saving. This check does not prove that the WireGuard peer is correctly configured.

## Scope and security

Only WireGuard has an implemented engine path. Do not treat the SSH, V2Ray, or Hysteria protocol options as working tunnels. The sample carrier tweak data is a template and is not a guarantee of carrier access or free data. No infrastructure is provisioned by this repository build workflow.
