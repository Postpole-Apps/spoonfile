# Spoonfile

Spoonfile is a recipe organizer you pay for once. Your recipes stay on your device. There is no account and there are no ads.

Package / applicationId: `com.postpole.spoonfile`

## Tech
- Kotlin, Jetpack Compose (Material 3, light / dark / system theme, edge-to-edge)
- Single activity, Navigation Compose, Hilt, Room (schemas exported to `app/schemas`), DataStore Preferences
- Brand palette (Direction 4 Expressive Pastel, seed `#8E6BD1`); Material You dynamic colour is not used
- Theme setting (Settings > Appearance): System default / Light / Dark, applied immediately
- minSdk 26, compileSdk/targetSdk 36, version catalog in `gradle/libs.versions.toml`
- Google Analytics for Firebase (GA4) and Crashlytics; Ad ID collection stays on

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```
./gradlew assembleDebug testDebugUnitTest lint
./gradlew assembleRelease
```

`app/google-services.json` is git-ignored and is never committed; the Firebase plugins are applied when it exists.

## Release and signing
`app/build.gradle.kts` signs the release build from `KEYSTORE_BASE64` (or `KEYSTORE_FILE`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (environment or `local.properties`); the build is unsigned if absent. Workflows live in `ci-templates/` (`ci.yml`, `release.yml`); a maintainer copies them to `.github/workflows/`.

## Origin
This app was started from the Postpole Apps Android template (`Postpole-Apps/android-app-template`); see [docs/NEW_APP.md](docs/NEW_APP.md).
