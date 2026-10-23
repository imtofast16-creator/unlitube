# UnliTube

Unli watching, no interruptions.

A fast, polished Android YouTube client with a fully custom player, plus a
Vercel website for downloads, install help, and update checks. The full
product plan lives in PLAN.md.

## Layout
- `app/` Android app. Native Kotlin, Jetpack Compose, Media3 ExoPlayer.
  Package ID: app.unlitube.android
- `web/` Next.js site for Vercel (unlitube.vercel.app target slug).
  Includes `/api/version`, the update check the app polls.
- `.github/workflows/build.yml` CI: builds the debug APK on every push, and a
  signed release APK when the keystore secrets are set. See SIGNING.md.
- `brand/` Logo and brand assets.

## Status
Phase 0 scaffold (v0.1.0). The home screen in the app is a design system
preview with sample data. Phase 1 wires the Triple Engine feed and playback
(PLAN.md Sections 3, 4D, 4E).

## Build
CI does the building: the workflow installs Gradle 8.7 and runs
assembleDebug, then assembleRelease when signing secrets exist.
Locally, with JDK 17, Gradle 8.7 and the Android SDK installed:

    gradle assembleDebug

A gradle wrapper can be generated later with `gradle wrapper` for
conventional ./gradlew use. It is intentionally not faked in this scaffold.
