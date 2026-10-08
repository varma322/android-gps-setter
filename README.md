# GPS Setter

[![release](https://img.shields.io/github/v/release/varma322/android-gps-setter)](https://github.com/varma322/android-gps-setter/releases)
[![build](https://img.shields.io/github/actions/workflow/status/varma322/android-gps-setter/apk.yml)](https://github.com/varma322/android-gps-setter/actions/workflows/apk.yml)
[![license](https://img.shields.io/github/license/varma322/android-gps-setter)](https://github.com/varma322/android-gps-setter/blob/main/LICENSE)
[![issues](https://img.shields.io/github/issues/varma322/android-gps-setter)](https://github.com/varma322/android-gps-setter/issues)

An Xposed module that sets your device's location, built on the modern [libxposed](https://github.com/libxposed/api) API for LSPosed 2.x / Vector.

This is a fork of [jqssun/android-gps-setter](https://github.com/jqssun/android-gps-setter), which builds on [Android1500/GpsSetter](https://github.com/Android1500/GpsSetter).

## What's different in this fork

- **Modern Xposed API (libxposed 101).** No "deprecated feature" warning in LSPosed 2.x. Settings reach the hook through LSPosed's remote preferences instead of a world-readable file, so the module keeps working after LSPosed 2.3.0 removes that file-based sharing.
- **System-wide spoofing on Android 12+.** Every location fix (GPS, network, fused, passive) is rewritten inside the system server, so apps don't have to be scoped one by one.
- **Real location during emergencies.** While an emergency call or SMS is active (Android 14+), real fixes pass through untouched.
- **System hooks fixed for Android 11–13.** The location service moved packages in Android 11, so the system hook never ran there.
- **Start, stop and location changes apply immediately**, without a reboot.
- **Working maps in the FOSS build.** It uses keyless [OpenFreeMap](https://openfreemap.org) tiles, because the previously bundled Mapbox token was revoked.
- **Fixes:** saving favorites and map taps in the FOSS build, crashes when offline or without a geocoder, and the update checker picking the other build's APK. Debug builds no longer need a keystore.

## Downloads

Grab an APK from [Releases](https://github.com/varma322/android-gps-setter/releases). There are two builds:

|                | `app-full-*.apk`                     | `app-foss-*.apk`                        |
|----------------|--------------------------------------|-----------------------------------------|
| Maps           | Google Maps (`play-services-maps`)   | MapLibre + OpenFreeMap                  |
| Fused location | Google Play services                 | microG client (`org.microg.gms`)        |

This fork installs as `io.github.varma322.gpssetter`, alongside upstream (`io.github.jqssun.gpssetter`) rather than over it, and its builds are signed with a different key. It is not on F-Droid.

## Requirements

- A rooted device with **LSPosed 2.x or Vector** (libxposed API 101+).
  - LSPosed 1.x can't load libxposed 101 modules; use upstream v0.0.6 there.
  - LSPatch (unrooted) isn't supported, because embedded frameworks don't provide remote preferences.
- **Android 8.1+.** System-wide spoofing of live location updates needs Android 12+. Tested on Android 16.

## Setup

1. Install the APK and enable **GPS Setter** in LSPosed.
2. In its scope, tick **System Framework** for system-wide spoofing. Optionally also tick individual apps (see [Tips](#tips-and-limitations)).
3. Reboot. System Framework hooks load at boot; per-app hooks only need that app restarted.
4. Open GPS Setter, pick a location on the map and press ▶.

## How it works

- **System Framework.** Rewrites fixes in `LocationProviderManager.onReportLocation` (Android 12+), `LocationManagerService.getLastLocation` and `injectLocation`. While spoofing, it also refuses raw GNSS measurement, navigation and batching listeners.
- **Scoped apps.** Inside the app's own process, hooks `Location.getLatitude` / `getLongitude` / `getAccuracy` / `set` and `LocationManager.getLastKnownLocation`. This catches every location the app reads, wherever it came from.

## Tips and limitations

- **Apps using Google Play services location** can work out your position from Wi-Fi and cell towers inside Play services, which the system hook can't see. Either:
  - turn off *Settings → Location → Location services → Google Location Accuracy*, plus Wi-Fi and Bluetooth scanning, or
  - add the app to the module's scope.
- **No GPS signal (e.g. indoors) with network location off** means there are no fixes to rewrite, so Play-services apps may show a cached location. Scope those apps.
- **Apps protected by Google Play's anti-tamper** (look for `libpairipcore.so` in the APK) crash when scoped. Don't scope them; rely on the system hook.
- **Only enable one location-spoofing module.** If two hook the same calls, whichever runs last wins.
- **Spoofing is system-wide while it's on.** That includes other system services; only emergencies are exempt (Android 14+).

## Building

- Debug builds need JDK 17–21 and Android SDK platform 36, but no keystore:

  ```sh
  ./gradlew assembleFossDebug   # or assembleFullDebug
  ```

- Release builds read `keyAlias`, `keyPassword`, `storeFile` and `storePassword` from `local.properties`.
- CI ([`apk.yml`](.github/workflows/apk.yml)) builds both flavors on `v*.*.*` tags and publishes a GitHub release. It needs these repository secrets:
  - `STORE`: base64 of the keystore
  - `LOCAL`: base64 of a `local.properties` containing the signing keys
- `./release.sh v1.2.3` bumps the version, then tags and pushes. It uses `gsed` (GNU sed).

## Credits

- [jqssun/android-gps-setter](https://github.com/jqssun/android-gps-setter), the project this fork is based on
- [Android1500/GpsSetter](https://github.com/Android1500/GpsSetter), the original GpsSetter for Android 8.1–13
- [libxposed](https://github.com/libxposed) and [LSPosed](https://github.com/LSPosed/LSPosed) / [Vector](https://github.com/JingMatrix/Vector)
- [MapLibre](https://github.com/maplibre/maplibre-native) for the mapping library; [OpenFreeMap](https://openfreemap.org) for tiles; map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors
- [microG](https://github.com/microg/GmsCore) for the FOSS implementation of Google Mobile Services

## License

[GPL-3.0](LICENSE)
