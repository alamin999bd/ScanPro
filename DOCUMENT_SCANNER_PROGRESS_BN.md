# Document Scanner — Upgrade Progress (Bangla)

Last updated: 2026-11-27

## Project identity
- GitHub repository: https://github.com/alamin999bd/ScanPro
- Working branch: document-scanner-premium
- Base branch: ocr
- Base commit: dbb0164b74db2380a73bca9d4a60b8da3f38b91f
- Existing package/applicationId: com.scanpro.pdfdocscanner (preserve for in-place app updates)
- Existing OCR branch version: 0.4.1 (versionCode 5)
- User-facing app name requested: Document Scanner
- Target test device: Samsung Galaxy M12, Android 13, 6 GB RAM
- App should remain offline-first; do not introduce paid APIs, server dependencies, or INTERNET permission without explicit necessity and approval.

## User's requested outcome
A premium, reliable, mostly offline document scanner and PDF toolkit. Prioritize the camera-to-PDF flow shown in the supplied reference: live camera capture -> edge detection -> crop handles -> perspective correction -> document enhancement/filter preview -> multi-page review/reorder -> readable PDF export. Provide a polished launcher icon/logo. Optimize memory and responsiveness for Samsung Galaxy M12.

## Uploaded inputs
- Latest supplied source archive: ScanPro-source (5).zip
- Earlier source archives: ScanPro-source.zip and ScanPro-source (1) through (4).zip
- Supplied APK archive: ScanPro-APK (1).zip (contains debug/app-debug.apk)
- Supplied current-app video: 1000069494.mp4
- Supplied scanner UI reference image: 1000069492.jpg

## Baseline audit (source inspection only; not device-verified)
The GitHub `ocr` branch is the newer baseline than the latest uploaded ZIP: its Gradle config is version 0.4.1 / versionCode 5, while the uploaded ScanPro-source (5).zip is version 0.3.0 / versionCode 3. Preserve the newer GitHub baseline and compare useful code changes from uploaded archives before merging anything.

Existing code areas include:
- CameraX camera capture, flash, tap-to-focus, multiple captured pages, gallery page import, page thumbnails, page reordering/removal.
- Page editor with manual crop handles, rotation, brightness/contrast, preset filters, automatic page detection and perspective straighten attempt.
- Image-to-PDF, ID scan, PDF/image import, document library, rename/search/sort, folders.
- PDF tools: merge, extract pages, PDF-to-JPEG, compression, password lock/unlock, text watermark, drawn signature.
- PDF viewer with page rendering and password prompt.
- Tesseract OCR integration and GitHub Actions workflow that downloads Bengali (ben) and English (eng) language data; OCR/search is documented in the project docs.
- Adaptive launcher icon resources already exist, but need review/redesign for the requested Document Scanner brand.

Important limitations already recorded in existing project docs:
- Current auto-edge detection and perspective straighten are heuristic/limited; they are not a robust guided four-corner detector. Manual crop exists.
- OCR is on-demand per document/page; language data is fetched at build time by Actions and OCR accuracy varies with print quality, blur, layout, and handwriting. Must verify OCR is actually wired and searchable in the current source before claiming complete.
- PDF compression rasterizes pages and can remove selectable/searchable text.
- DOCX/XLSX import is text extraction rather than layout-faithful conversion.
- Password-protected PDF support is limited to what the current PDF library can read/write.
- Existing docs explicitly say the prior build was not verified on a physical device. A successful GitHub Actions build is not equivalent to on-device testing.

## Feature checklist and implementation policy
Do not add duplicate features. For each item, inspect current code and run a test before marking it complete.

### Scanner capture and image quality
- [ ] Camera Scan flow polish and reliability
- [ ] Gallery Import
- [ ] Reliable Auto Edge Detection with visible corner handles
- [ ] Manual Crop
- [ ] Perspective Correction from four adjustable corners
- [ ] Auto Enhance
- [ ] Black & White document filter
- [ ] Grayscale filter
- [ ] Additional useful document filters (e.g. Original, Lighten, Magic Color/contrast, sharpen) with previews
- [ ] Multi-page scan, reorder, delete, edit, retake
- [ ] High-resolution PDF/JPG export with readable text and controllable quality/size
- [ ] Camera-to-PDF reference flow reproduced in app

### OCR and searchable content
- [ ] English OCR
- [ ] Bengali OCR using supported Tesseract language model; report limitations honestly
- [ ] OCR text persistence and re-index/search
- [ ] Search recognized text
- [ ] Searchable PDF export with invisible text layer, if library and rendering pipeline support it without damaging page appearance

### Document and PDF management
- [ ] JPG export
- [ ] Document rename
- [ ] Folder create/rename/delete/move
- [ ] PDF split
- [ ] Extract pages
- [ ] Delete pages
- [ ] Reorder pages
- [ ] Rotate pages
- [ ] Compress PDF (offer clear quality/text-search trade-off)
- [ ] Add watermark
- [ ] Add page numbers
- [ ] Add text annotation
- [ ] Highlight text (only when text layer exists; otherwise provide a clearly labeled image annotation mode)
- [ ] Bookmark pages
- [ ] Search PDF text
- [ ] Print PDF
- [ ] Share PDF
- [ ] Password-protected PDF support where authorized and supported by the selected library; never bypass unknown passwords

### Brand, quality, compatibility
- [ ] Rename visible branding to Document Scanner
- [ ] Create a polished, legible launcher icon/logo
- [ ] Premium and consistent UI/UX
- [ ] Samsung Galaxy M12 memory/performance optimization
- [ ] Preserve existing package ID and user data compatibility
- [ ] Update Room schema only with explicit migration
- [ ] Keep project updateable and document dependencies/build/versioning

## Build and test gates
1. Static source audit and feature-to-code mapping.
2. Implement only missing/incomplete capabilities.
3. Run GitHub Actions build; inspect complete logs and resolve compile/build failures.
4. Run automated/unit checks available in repository.
5. Test APK on Samsung Galaxy M12 if device access is actually available. Otherwise provide a focused manual test checklist and explicitly state that physical-device testing remains pending.
6. Test camera capture, gallery import, crop/perspective, filters, multi-page ordering, PDF export/readability, OCR Bengali/English, PDF tools, share/print, permissions, low-memory behavior, and reopen/restore.
7. Publish APK and complete source archive from GitHub only after the relevant build/test gates pass. Do not claim untested functionality as complete.

## Current progress
- Repository access verified: account has Admin and Push permissions.
- Created this isolated working branch from the newer `ocr` branch to preserve the previous baseline.
- GitHub Actions workflow exists and last recorded run #8 (2026-11-25) succeeded, but that does not prove physical-device functionality.
- No app source changes have been made yet in this upgrade branch beyond this progress record.
