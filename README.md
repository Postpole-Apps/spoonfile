# Spoonfile

Spoonfile is a recipe organizer you pay for once. Your data is stored on your device: no account needed.

Package: `com.postpole.spoonfile`. Free up to 75 recipes; Pro is a one-time purchase.

This app was started from the Postpole Apps Android template (see [docs/NEW_APP.md](docs/NEW_APP.md)).

## What's inside
- Kotlin, Jetpack Compose (Material 3, Expressive Pastel palette, light/dark/system theme, edge-to-edge)
- Single activity, Navigation Compose, Hilt, Room (schemas exported to `app/schemas`), DataStore Preferences
- minSdk 26, compileSdk/targetSdk 36, version catalog in `gradle/libs.versions.toml`
- Only cost rule: no paid APIs, servers, AI fees or paid databases. GA4 + Crashlytics are required; AD_ID / Ad ID collection stays ON (Google Ads)

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```
./gradlew assembleDebug testDebugUnitTest lint
./gradlew assembleRelease
```

## Theme
The brand palette (seed `#8E6BD1`) is in `ui/theme/Theme.kt`; Material You dynamic colour is not used. Settings > Appearance > Theme stores `themeMode` (System default, Light or Dark) in DataStore and applies immediately.

## Release and signing
`app/build.gradle.kts` signs the release build from `KEYSTORE_BASE64` (or `KEYSTORE_FILE`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (environment or `local.properties`); the build is unsigned if absent. The release workflow lives in `ci-templates/release.yml`.

A maintainer must copy `ci-templates/ci.yml` and `ci-templates/release.yml` to `.github/workflows/` (the bot cannot push there).

## Analytics and crash reporting (required)
Google Analytics for Firebase (GA4) and Crashlytics are wired through the `Analytics` interface. Without `app/google-services.json` (git-ignored) a no-op `Analytics` is used; with it, the google-services / crashlytics plugins are applied automatically. Ad ID collection is intentionally left enabled for Google Ads conversion tracking. `privacy_policy_url` (done: https://pocket-ledger-postpole.web.app/spoonfile/privacy) and `support_email` live in `res/values/app_config.xml`.

## Backup
Settings > Back up to file / Restore from backup file use the Storage Access Framework (no storage permissions) and a versioned JSON format (`BackupRepository`). Android Auto Backup is off (`allowBackup=false` plus explicit `data_extraction_rules.xml`).
