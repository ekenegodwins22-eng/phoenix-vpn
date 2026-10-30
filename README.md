# PhoenixTunnel

PhoenixTunnel is a personal Android VPN client project. The Android application uses Kotlin, plain Android views, min SDK 26, target SDK 34, compile SDK 35, and the WireGuard Android tunnel library.

## Features

- WireGuard configuration parsing, file-based profiles, and VPN service foundation.
- SSH DIRECT, SSH SSL/TLS, and SSH SSL/PAYLOAD use sshj password authentication, a local SOCKS5 TCP forwarder, and the hev TUN-to-SOCKS bridge. SSH host keys use first-use pinning, so verify the displayed fingerprint with the server operator before relying on it.
- SSH TUN routing is IPv4 and SOCKS TCP only. UDP/QUIC and IPv6 are not tunneled by this configuration.
- DNSTT, V2Ray, and Hysteria remain stubs and are not working tunnel modes.
- Tweak-profile JSON storage and `vmess://` / `vless://` URI inspection.
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

Only the SSH modes listed above and WireGuard have an implemented engine path. The sample carrier tweak data is a template and is not a guarantee of carrier access or free data. No infrastructure is provisioned by this repository build workflow.
