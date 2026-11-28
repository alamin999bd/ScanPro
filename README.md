# Document Scanner

An offline-first Android document scanning and PDF utility built with Kotlin and Jetpack Compose.

- **Package:** `com.scanpro.pdfdocscanner`
- **Current version:** `0.7.0 (versionCode 9)`
- **Android:** minSdk 26, targetSdk 35
- **Branch:** `document-scanner-premium`

The project includes camera and gallery scanning, page editing, PDF tools, Bangla/English OCR, searchable PDFs, and text-based DOCX/XLSX import.

## CI status

[Latest verified Build #264](https://github.com/alamin999bd/ScanPro/actions/runs/46122270245) passed unit tests, all 3 OfficeConverter instrumentation tests, APK builds/signature checks, and source ZIP integrity verification.

**Release signing is not yet finalized.** The successful CI run used the temporary Gradle debug-key fallback for the release APK. Configure the persistent release keystore secrets and run the workflow with `use_persistent_signing=true` before distributing a final updateable APK. Samsung Galaxy M12 physical testing is also pending.

See [PROJECT_DOCS.md](PROJECT_DOCS.md) and [BUILD_APK_GUIDE_BN.md](BUILD_APK_GUIDE_BN.md) for feature limitations, signing setup, build and test instructions.
