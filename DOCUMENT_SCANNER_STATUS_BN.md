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
