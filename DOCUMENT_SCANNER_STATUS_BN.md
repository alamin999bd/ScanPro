# Document Scanner — GitHub project status

Updated: 2026-11-27
Repository: https://github.com/alamin999bd/ScanPro
Working branch: `document-scanner-premium`
Package/applicationId: `com.scanpro.pdfdocscanner` (keep unchanged for app updates)
App label: **Document Scanner**
Source version: **0.6.0 (versionCode 8)**
Target device: Samsung Galaxy M12, Android 13, 6 GB RAM.

## Existing source capabilities (code-level; not physical-device verified)
- CameraX live preview, autofocus tap, flash, capture, multi-page capture and gallery import.
- Page editor: rectangular crop, aspect presets, rotation, brightness/contrast, Original/Enhance/B&W/Grayscale/Vivid/Auto/Lighten/Darken/Color filters.
- Manual quadrilateral perspective correction handles; automatic document boundary detection is present in ImageProcessor.
- PDF/JPG conversion, PDF viewer, merge, extract pages, PDF-to-images, rasterizing compression, password lock/unlock, watermark and drawn signature.
- Document rename/move/delete, folders, sharing/print intents.
- Offline Tesseract OCR code configured for Bengali + English; recognized text saved to Room and used for document-content search.

## Audit findings — 2026-11-27
### Confirmed in source (not yet feature-tested)
- Scan UI: `ui/scan/ScanScreen.kt`; CameraX uses MAXIMIZE_QUALITY, tap-to-focus, flash and gallery multi-picker.
- Scan flow: `ScanViewModel.kt` supports multi-page captures, gallery import, reorder/remove, editor, PDF save.
- Image pipeline: `ImageProcessor.kt` applies warp → rotate → crop → look/filter; warp uses Android Matrix.setPolyToPoly (not OpenCV).
- OCR: `OcrEngine.kt` expects bundled `ben.traineddata` and `eng.traineddata`, processes PDFs/images offline; `DocumentsViewModel.kt` stores extracted text; document search matches names and OCR text.
- PDF tools exposed in `PdfToolsScreen.kt`: Merge, Split/Extract, PDF-to-Images, Compress, Lock, Unlock, Watermark, Sign.
- `PdfEngine.compress()` rasterizes each page to JPEG. It may reduce file size but removes selectable/searchable text from the compressed copy; UI must disclose this limitation.
- PDF page delete/reorder/rotate, page numbering, text annotation, highlight, bookmarks and in-PDF text search are not exposed by current PDF tools/viewer implementation; these are outstanding, not complete.
- OCR text is stored in the app database for document search; current code does not add an invisible searchable text layer to the exported scanned PDF. This distinction must be addressed/documented.
- Release signing falls back to debug signing if no `SCANPRO_KEYSTORE_PATH` is configured. Do not describe it as Play Store production-signed without checking secrets/signature.

## CI evidence
- Latest workflow run at this update: **#21**, ID `45877343244`, commit `d98de944e225e4b3951fb47fdebd0fdd077ce0d7`.
- Status: completed / success; build job succeeded.
- Artifact: `Document-Scanner-APK`, artifact ID `15145796196`, 68,382,545 bytes, not expired.
- This proves CI build/package success only. It does not prove functional correctness or physical Samsung Galaxy M12 testing.
- Workflow: https://github.com/alamin999bd/ScanPro/actions/runs/45877343244

## Remaining work — priority order
1. Verify and improve auto-edge detection and perspective-corner geometry on diverse page photos; preserve manual correction.
2. Verify camera capture → editor → add/reorder/remove pages → save flow and gallery import; add tests.
3. Validate filters on text, shadows, colored paper and low contrast; address bitmap lifecycle/memory.
4. Implement/verify PDF page operations (delete/reorder/rotate) and add page numbers, annotations, highlight, bookmarks, PDF text search where technically feasible.
5. Decide and implement an honest searchable-PDF workflow: OCR currently stores text for in-app document search, but no PDF text layer is written. Bengali font embedding/text extraction needs careful validation.
6. Validate Bangla/English OCR traineddata presence, recognition accuracy, progress/cancellation and M12 memory behavior.
7. Test PDF tools, password-protected import with correct password, print/share and error states.
8. Premium UI polish, icon, accessibility, M12 performance, unit/instrumentation tests.
9. Final CI build from latest commit, release signing verification, source ZIP. No interim APK links.

## Verification boundaries
- No physical Samsung Galaxy M12 test has been performed in this session.
- No automated unit/instrumentation test suite was found in the repository tree during this audit.
- A successful GitHub Actions build is not a substitute for device testing.
- Do not distribute current artifacts as final.

## Resume protocol
Read `docs/PROJECT_MASTER_RECORD_BN.md` and this file, verify latest branch HEAD and Actions run, then continue from the remaining-work list. Update both records after meaningful changes.


## 2026-11-27 audit continuation
- Latest branch workflow run #21 (ID `45877343244`) completed successfully on commit `d98de944e225e4b3951fb47fdebd0fdd077ce0d7`; artifact `Document-Scanner-APK` (ID `15145796196`, 68,382,545 bytes) is present and not expired. This remains an interim CI artifact, not final delivery.
- Confirmed by source inspection: CameraX capture, multi-page capture, gallery import, page removal/reorder during capture, offline Tesseract `ben+eng` OCR, recognized-text database search, PDF merge/extract/compress/lock/unlock/watermark/signature, viewer/share/export, document rename/folder operations.
- Confirmed important limitations: current OCR persists extracted text for app-level document search and displays text, but the current PDF generation path does not embed a positioned invisible text layer; therefore scanned PDFs are not yet truly searchable/selectable internally in a standard PDF viewer. PDF compression rasterizes pages and removes selectable/searchable text. `PdfToolsScreen` currently exposes only Merge, Extract, PDF-to-Images, Compress, Lock, Unlock, Watermark, and Sign. Page delete/reorder/rotate, page numbers, text annotation, highlight, bookmarks, and PDF-internal text search are not exposed/implemented in the current tools UI.
- Camera flow has a capture preview and page list; automatic edge detection is invoked from the editor path (must continue checking integration and geometry). Perspective correction is currently a custom four-point homography, not OpenCV; needs coordinate validation and robustness tests.
- No repository unit/instrumentation tests found in the inspected tree; no physical Samsung Galaxy M12 test performed.
- Next implementation priority: (1) verify/correct perspective quad mapping and editor interaction; (2) add PDF page management operations (delete/reorder/rotate) safely using PDFBox; (3) add page numbers and text/highlight annotations with font support; (4) add bookmarks and PDF text search where text exists; (5) design true OCR searchable-PDF overlay only if Bangla font and word-position extraction can be reliably bundled offline. Never claim internal PDF searchability from app-level OCR database search.
- Latest audit branch head was `d98de944e225e4b3951fb47fdebd0fdd077ce0d7` before the next documentation commit.


## 2026-11-27 implementation update — PDF page management
- Added `PdfEngine.deletePages`, `reorderPages`, and `rotatePages` (copy-based operations; original PDF remains unchanged).
- Added matching ViewModel operations with input validation, progress/error messages and new-document registration.
- Added `Delete pages`, `Reorder pages`, and `Rotate pages` options to PDF Tools UI. Reorder requires every page exactly once; rotation supports 90/180/270 degrees clockwise.
- GitHub Actions builds triggered by these commits are currently in progress at the time of this note; compile success is not yet confirmed. Verify the newest run against the latest branch HEAD before continuing.


## 2026-11-27 CI failure diagnosis and fix
- User screenshot showed runs #28–#31 red. Inspected GitHub job logs: all four failed at Kotlin parsing in `PdfToolsScreen.kt`, line 424 (run #31; earlier line 423). Root cause was literal `\n` characters accidentally inserted between `when(tool)` branches after `Tool.SIGN`, not Gradle/KSP or PDFBox.
- Corrected the malformed line breaks and committed fix `a46f486fafdcc39491adc6a327ade9c06ec50754` (`fix(pdf): correct malformed PDF tools action labels`).
- A fresh build for this exact HEAD still needs confirmation. Do not mark the fix as verified until GitHub Actions passes.


## 2026-11-27 verified continuation — Build #154
- Latest branch HEAD: 181ab3df369da6ad3b2b5a84d767df15f1ce6297; latest matching workflow Build #154, run 45981830547, SUCCESS.
- Unit Tests, Debug APK, Release APK, APK output verification and artifact upload passed. Artifact Document-Scanner-APK, ID 15190061888; Debug 45 MB, Release 37 MB.
- Serial 6 code/CI milestones: PDF outline bookmarks with in-viewer page navigation; selectable PDF text search with page navigation; PDF text-note and highlight annotation overlays; CropBox/rotation geometry normalization and tests. Highlight overlay currently paints the annotation bounding rectangle rather than each text quad; UI is coordinate-based, not text-selection based.
- Serial 7 started: search/bookmark header height is measured dynamically; PDF render width capped at 1600 px; duplicate render requests recheck cache under lock; failed render bitmaps are recycled.
- Serial 1–5 are treated as code/CI-complete only; actual scanner, camera, gallery, OCR accuracy, PDF operations and filters still need sample-file and physical-device QA.
- Serial 6 CI verified; actual annotation appearance, bookmark behavior and search usability still need physical device/external viewer QA.
- Serial 7 is in progress. Next: inspect remaining memory/rendering paths, then Serial 8 regression tests, Serial 9 release-signing verification, Serial 10 final APK + full Source ZIP.
- No physical Samsung Galaxy M12 (Android 13) test has been performed in this session. Do not label the app device-tested or final yet.


## 2026-11-27 — Serial 7/8 verified; Serial 9 signing gate
- Latest pre-signing branch HEAD 2a198bbd586ddd73efbf146d6b3ecb1598ec2530; Build #164 run 45983573696 SUCCESS. All steps passed: OCR/font provisioning, Bengali/Latin glyph coverage, Unit Tests, Debug/Release APK builds, APK output verification and artifact upload.
- Serial 7 includes dynamic viewer header measurement, capped PDF render width/cache safeguards and 250 ms document-content search debounce. Build #160 verified the debounce change.
- Serial 8 automated regression expansion includes invalid/non-finite OCR PDF dimensions and invalid perspective geometry threshold tests. Nine core JVM test files exist; Builds #161–#164 passed.
- Serial 9 workflow update adds apksigner validation for both APKs and records SHA-256 signing certificate fingerprints. If no SCANPRO_KEYSTORE_PATH is configured, Release is signed with Gradle's debug-key fallback; this is not a production Play Store signing identity and may not support updates across fresh CI runners.
- Serial 10: after the signing-verification workflow passes on latest HEAD, create the final APK artifact and complete source ZIP. Physical Samsung Galaxy M12 testing remains for the user after delivery.


## 2026-11-27 — Serial 9 verified; Serial 10 final packaging
- Build #167 — run ID `45985668334`, commit `6205d86a06319e8cba4a5cc940617a14f9452631`: SUCCESS. Latest branch HEAD matched this exact commit at verification time.
- All workflow steps passed: Bengali/English OCR data and font provisioning, glyph coverage, Unit Tests, Debug/Release APK builds, APK output checks, Android `apksigner` signature verification and artifact upload.
- Artifact: `Document-Scanner-APK`, ID `15190593542`, size 69,293,299 bytes; not expired at verification. This is an interim APK artifact, not final delivery.
- Signature reports are now printed into CI logs and fingerprints are extracted when the tool output exposes them. Both APK signatures passed `apksigner verify`. With no `SCANPRO_KEYSTORE_PATH`, Release uses Gradle debug-key fallback; it is not Play Store production-signed and update compatibility across clean CI runners is not guaranteed.
- Serial 9 CI signature-verification gate is complete. A persistent release keystore must be user-controlled and stored only in GitHub Secrets before production distribution; do not commit keystore/private passwords.
- Serial 10 packaging change: add `git archive` ZIP of tracked source at the exact checked-out commit, validate ZIP integrity, and include it in the same final GitHub Actions artifact with Debug/Release APKs and signing reports. CI-provisioned OCR traineddata and font binaries are downloaded during workflow and are intentionally not committed as source; build workflow recreates them.
- Next gate: commit packaging/docs update, run a fresh workflow on that exact new HEAD, verify every job step and artifact contains both APKs, signing reports and `Document-Scanner-Source.zip`. If any step fails, inspect logs and rebuild latest HEAD. No final links until this gate passes.
- No physical Samsung Galaxy M12 testing has been performed in this session; device QA remains a separate user-side step after final delivery.


## 2026-11-27 — User video feedback: PDF save recovery + theme refresh
- Video review: camera scan and page editor were reachable; the editor showed Crop & Rotate, Filters, Adjust; after selecting Save, the UI remained on “Creating PDF…” through the end of the 121-second recording. This confirms a user-visible stuck-save symptom, not its exact device-side root cause.
- Code change on `document-scanner-premium`: the normal scan save path now exports the image-based PDF first using `PdfEngine.imagesToPdf`, validates that the output file is non-empty, registers it in the document library, and returns the success screen without waiting for Tesseract OCR.
- OCR text extraction is now a best-effort background task after successful PDF registration; OCR failure does not delete or invalidate the saved PDF. App-level OCR text search remains available when recognition succeeds. The primary scan-save path no longer embeds the invisible OCR text layer in the exported PDF; do not claim that these newly saved PDFs are searchable in third-party PDF readers until that path is separately restored and tested.
- Theme palette refreshed from indigo/purple to teal + warm coral with updated light/dark color schemes. The editor top-right action now displays “Apply” alongside the check icon for clearer affordance.
- App version bumped to 0.7.0 / versionCode 9; applicationId remains unchanged; minSdk 26 and targetSdk 35 remain compatible in principle with Android 13 (API 33).
- Latest code commit: `471ce385368d24036f222e0c2ca8006b058bfd74`. GitHub combined status currently reports no status entries; CI for this new HEAD has not yet been verified. Do not distribute a new APK until a successful build for the latest HEAD is confirmed.
- Samsung Galaxy M12 physical-device QA remains pending. No claim of device testing is made.


## 2026-11-27 — Latest verified PDF Tools status
- Build #198 — run 46004432065, commit `99cfa549480f53a3c606918a97b2cf6e65969f44`: SUCCESS; PDF-to-images Library registration is batched atomically.
- Build #199 — run 46005228410, commit `7acbfa8966ab2945d1d6151b7fc67785fe474444`: SUCCESS; memory exhaustion handling and partial-output cleanup added across PDF Tools.
- Build #199 passed Unit Tests, Debug/Release APK builds, APK output/signature verification and source ZIP packaging. CI does not prove physical device behavior.
- Next gate: sample PDF regression tests and Samsung Galaxy M12 Android 13 hands-on QA. Release signing still uses debug-key fallback unless a persistent user-owned keystore is configured securely.
