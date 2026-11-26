# Document Scanner — GitHub project status

Updated: 2026-11-27
Repository: https://github.com/alamin999bd/ScanPro
Working branch: `document-scanner-premium`
Package/applicationId: `com.scanpro.pdfdocscanner` (keep unchanged for app updates)
App label: **Document Scanner**
Source version: **0.6.0 (versionCode 8)**

## Target and design
- Samsung Galaxy M12, Android 13, 6 GB RAM.
- Offline-first: no paid API, account, cloud upload or unnecessary INTERNET permission.
- Camera-first flow: live camera → capture → edge detection → editable crop corners → perspective correction → document filters → multi-page review → searchable PDF.
- Process images with bounded previews and controlled export dimensions to reduce memory pressure.

## Existing source capabilities (code-level; not physical-device verified)
- CameraX capture, flash/focus, multi-page capture and gallery import.
- Page editor with rectangular crop handles, detected quadrilateral corner handles (manual drag), page presets, rotation, brightness and contrast. Drag gesture restart bug fixed by keeping pointer-input keys stable.
- Filter presets: Original, Enhance, B&W Document, Grayscale, Vivid, Auto, Lighten, Darken and Color.
- Automatic document boundary detection and automatic perspective straighten heuristic.
- PDF/image conversion and viewer; PDF merge, extract, compression, password lock/unlock, watermark and drawn signature.
- Document search, rename/move/delete, folders, sharing and print/open-with intents.
- Offline Tesseract OCR integration for Bengali and English; recognized text is stored for content search. GitHub Actions downloads `ben` and `eng` traineddata.

## Main gaps to implement or verify
1. Verify the new four-corner draggable perspective adjustment against varied real photos; ensure corner geometry stays valid and output orientation is correct.
2. Improve edge detection against shadows, patterned backgrounds, low contrast and partly hidden page corners. Always allow manual correction.
3. Camera finish flow: Retake / Crop / Add page / Finish, then multi-page review before export.
4. Newly added filter presets need visual comparison on printed text, colored pages, shadows and low-contrast pages.
5. Verify PDF split, page deletion/reorder/rotate, page numbers, text annotation, highlight, bookmarks, PDF text search, password-protected import, print and share. Add missing items only.
6. Validate Bengali/English OCR, searchable PDF text layer and recognized-text search; explain OCR limits on blur/handwriting.
7. Check touch targets, readability, clipping and memory use for Galaxy M12.

## Verification status
- Repository, working branch and uploaded ZIP variants inspected. The uploaded ZIPs are older than the current working branch; do not overwrite the branch with them.
- Source code and current GitHub Actions workflow reviewed.
- Added filter presets and draggable quadrilateral corner adjustment in source. GitHub Actions build verification is pending for these commits. No unit/instrumentation tests exist in the repository tree at this point.
- No physical Samsung Galaxy M12 test has been performed in this session.

## Resume in the next chat
Continue this Document Scanner project from this branch and this file. Check the latest commit and Actions run first, then work through the numbered gaps. Do not claim device testing unless it actually happens. Publish APK and source ZIP links only after a successful build/artifact is confirmed.

## Changelog
- 0.5.1: build workflow, version tracking and audit/resume notes.\n- 0.6.0: expanded offline document filter presets and added draggable perspective quadrilateral corners; fixed drag gesture restart; sequentialized Debug/Release Gradle build to avoid Room schema generation race. Build and functional verification pending.
