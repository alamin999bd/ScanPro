# Document Scanner — Project Documentation

## Project identity

| Item | Current value |
|---|---|
| App name | Document Scanner |
| Application ID (release) | `com.scanpro.pdfdocscanner` |
| Application ID (debug) | `com.scanpro.pdfdocscanner.debug` |
| Version name | `0.7.0` |
| Version code | `9` |
| minSdk / targetSdk / compileSdk | `26 / 35 / 35` |
| Language / UI | Kotlin, Jetpack Compose |
| Repository branch | `document-scanner-premium` |

The app is designed to work offline. The Android manifest requests Camera permission only; OCR language files and fonts are bundled into the build by GitHub Actions. No app backend or API key is required.

## Build and test status

Latest verified CI run: [Build #264](https://github.com/alamin999bd/ScanPro/actions/runs/46122270245), commit `3a7dd896f9c7a4df8408da52aa2f3e0c38ccc64c`.

- Unit tests: passed.
- OfficeConverter Android instrumentation tests: 3/3 passed (DOCX conversion, XLSX shared/sparse cell conversion, Excel column limit rejection).
- Debug APK build: passed.
- Release APK build: passed.
- APK signature verification: passed.
- Full source ZIP packaging and ZIP integrity check: passed.
- Physical Samsung Galaxy M12 testing: not yet performed.

**Signing warning:** Build #264's Release APK used Gradle's temporary debug-key fallback because no persistent release keystore was configured. That APK is a CI validation artifact, not the final updateable release. A permanent keystore must be created and stored in GitHub Actions repository secrets before producing the final release APK.

## Feature inventory and known limits

The codebase includes:
- Camera scanning with multi-page capture, flash/focus controls, page ordering and deletion.
- Gallery image import and PDF import.
- Page editing: crop, rotate, brightness/contrast and filters.
- Automatic page boundary detection and perspective correction (Kotlin image processing; no OpenCV/ML dependency). Detection can be less reliable with low contrast, shadows, busy backgrounds or obscured page edges.
- Image-to-PDF and multi-page PDF creation.
- PDF viewer, page navigation, merge/extract, image export, compression, password lock/unlock, text watermark and drawn-signature placement.
- Bangla + English OCR and searchable text layer; OCR accuracy depends on image clarity and print quality, and handwriting may be inaccurate.
- DOCX and XLSX import to text-based PDF. Original formatting, images, charts and exact layout are not preserved. Legacy DOC/XLS are not supported.
- Document list/search, folders, rename, move, share, save and delete.
- Light/dark/system theme and app settings.

These are code-level feature descriptions. CI tests do not replace hands-on testing of every screen and workflow on the target phone.

## Main technology

- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle wrapper 8.9, JDK 17.
- Jetpack Compose / Material 3, CameraX, Room, DataStore, PDFBox-Android, Tesseract4Android and Coil.
- Dependencies are declared in `gradle/libs.versions.toml`.
- Unit tests use JUnit and Robolectric; OfficeConverter tests use AndroidX instrumentation runner.

## Release signing setup

Do not commit a keystore or passwords to the repository. Create one long-term keystore and keep a secure offline backup. Configure these four GitHub Actions repository secrets:

1. `SCANPRO_KEYSTORE_BASE64` — Base64 text of the `.jks` keystore file.
2. `SCANPRO_KEYSTORE_PASSWORD` — keystore password.
3. `SCANPRO_KEY_ALIAS` — key alias.
4. `SCANPRO_KEY_PASSWORD` — key password.

The workflow's manual **Actions → Build Document Scanner APK → Run workflow** option includes `use_persistent_signing`. Leave it off for ordinary CI validation. Turn it on only after all four secrets are configured; the workflow then fails closed if any secret is missing and verifies the release certificate differs from the debug certificate.

Never share the keystore, passwords, or Base64 value in chat, source files, commits, issues, or logs. Keep a backup: losing the keystore means future APKs cannot update an installation signed with it.

## Updating the project

- Keep the release application ID unchanged.
- Increment `versionCode` for each distributed update and update `versionName`.
- Preserve Room database migrations and schema files when changing stored data.
- Run the full CI workflow after source changes.
- Only distribute a release APK after persistent signing is enabled and verified, followed by target-device testing.
- Keep the complete source and documentation in this repository; do not recreate the app from scratch for updates.

## Output artifacts

A successful workflow uploads:
- `app-debug.apk`
- `app-release.apk`
- Debug and release signing reports
- `Document-Scanner-Source.zip`

Until persistent signing is configured, the release APK in the artifact is for build validation only.
