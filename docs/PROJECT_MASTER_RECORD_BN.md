# Document Scanner — সম্পূর্ণ Project Master Record

> এই ফাইলটি প্রকল্পের স্থায়ী হ্যান্ডওভার/মাস্টার রেকর্ড। ভবিষ্যতে যে AI/ডেভেলপার কাজ করবে, সে প্রথমে এই ফাইল এবং `DOCUMENT_SCANNER_STATUS_BN.md` পড়বে। আগের কাজ যাচাই না করে নতুন করে শুরু করবে না।

## 1. প্রকল্প পরিচয়
- App name: **Document Scanner**
- Android applicationId: `com.scanpro.pdfdocscanner`
- GitHub repository: https://github.com/alamin999bd/ScanPro
- Working branch: `document-scanner-premium`
- Branch URL: https://github.com/alamin999bd/ScanPro/tree/document-scanner-premium
- Default branch: `main` (কাজের branch-এ পরিবর্তন; অনুমতি ছাড়া main-এ merge নয়)
- সর্বশেষ জানা app version: `0.6.0`, versionCode `8`
- Minimum SDK: 26; Target SDK: 35
- লক্ষ্য ডিভাইস: Samsung Galaxy M12 (Android 13; arm64/armeabi-v7a সমর্থন)
- Build workflow: `.github/workflows/build-apk.yml`

## 2. ব্যবহারকারীর প্রধান নির্দেশনা
1. বিদ্যমান ScanPro source/project-ই উন্নত করতে হবে; নতুন করে আলাদা app বা duplicate project বানানো যাবে না।
2. আগে source audit করতে হবে। ইতিমধ্যে কাজ করা feature অক্ষত রাখতে হবে; অসম্পূর্ণ/ভাঙা feature মেরামত করতে হবে।
3. Premium, পরিষ্কার, সহজে বোঝা যায় এমন UI এবং বাস্তবে কাজ করে এমন feature দিতে হবে।
4. API key, paid API, paid service, domain বা credit card নির্ভরতা এড়িয়ে চলতে হবে। কোনো feature বাস্তবে offline/free করা সম্ভব না হলে সেটি স্পষ্টভাবে নথিভুক্ত করতে হবে; ভুয়া AI button বা placeholder feature দেওয়া যাবে না।
5. ব্যবহারকারী non-coder এবং ফোনের file picker-এ সমস্যা হয়; GitHub থেকেই source সংরক্ষণ ও build workflow চালাতে হবে।
6. কাজ চলাকালে interim/test APK download link পাঠানো যাবে না। সব কাজ ও যাচাই শেষ হলে final APK এবং সম্পূর্ণ source ZIP দিতে হবে।
7. প্রতিটি গুরুত্বপূর্ণ পরিবর্তন GitHub-এ commit করতে হবে এবং এই master record ও status file আপডেট করতে হবে।
8. কোনো কাজ শেষ হয়েছে বলার আগে build/test evidence দিতে হবে। CI build success মানেই Samsung ফোনে বাস্তব পরীক্ষা সম্পন্ন হয়েছে—এমন দাবি করা যাবে না।
9. ভবিষ্যৎ update-এ একই repository/branch-এর project চালিয়ে যেতে হবে; user approval ছাড়া project reset/recreate বা feature বাদ দেওয়া যাবে না।

## 3. কাঙ্ক্ষিত feature scope — যাচাই করে বাস্তবায়ন
### Scan ও image processing
- Camera Scan; camera থেকে সরাসরি capture করে document/PDF তৈরি
- Gallery Import
- Auto Edge Detection
- Manual Crop
- Perspective Correction
- Auto Enhance
- Black & White, Grayscale এবং অতিরিক্ত document filters
- Multi-page Scan
- পরিষ্কার, sharp ও readable PDF output
- camera UI reference screenshot/video-এর মতো সহজ capture flow

### OCR ও search
- OCR: English এবং Bangla—শুধু বাস্তবে সমর্থিত ও নির্ভরযোগ্য engine/model থাকলে
- Recognized text search
- Scanned paper থেকে searchable PDF/text তৈরি করা অগ্রাধিকার
- OCR না চললে UI-তে স্পষ্ট error/unsupported status; মিথ্যা success নয়

### Document/PDF management
- PDF/JPG export
- Document rename
- Folder management
- PDF Split
- Extract Pages
- Delete Pages
- Reorder Pages
- Rotate Pages
- Compress PDF
- Add Watermark
- Page Numbers
- Text Annotation
- Highlight
- Bookmark
- Search PDF Text
- Print
- Share
- Password-protected PDF: কেবল বৈধ অনুমতি/সঠিক password এবং ব্যবহৃত library-র নিরাপদ সমর্থন থাকলে; security bypass নয়।

## 4. বর্তমান পরিচিত implementation/change log
- `gradle/libs.versions.toml`: KSP plugin version `2.0.21-1.0.27` করা হয়েছিল (আগের `2.0.21-1.0.28` resolve হচ্ছিল না)।
- `app/src/main/java/com/scanpro/pdfdocscanner/core/ImageProcessor.kt`: PageFilter-এ AUTO, LIGHTEN, DARKEN, COLOR এবং সংশ্লিষ্ট processing branch যোগ করা হয়েছে। Visual output quality ও edge case এখনও যাচাই করতে হবে।
- `app/src/main/java/com/scanpro/pdfdocscanner/ui/editor/PageEditor.kt`: CropArea-তে quadrilateral/perspective corner handle ও drag support যোগ করা হয়েছে। Coordinate order, drag stability এবং বাস্তব perspective transform pipeline যাচাই করা বাকি।
- `app/build.gradle.kts`: versionName `0.6.0`, versionCode `8`।
- `.github/workflows/build-apk.yml`: Room KSP schema generation-এর concurrent variant সমস্যা এড়াতে debug ও release build sequential করা হয়েছে।
- `DOCUMENT_SCANNER_STATUS_BN.md`: আগের status/verification notes; প্রতিটি session-এ পড়তে ও আপডেট করতে হবে।

## 5. সর্বশেষ CI/build record (2026-11-27 তারিখে যাচাইকৃত)
- সর্বশেষ branch workflow run: **#19**
- Run ID: `45876446027`
- Commit: `5a35917f33012a62c507dc739e8934a985794ddb`
- Status: `completed`
- Conclusion: `success`
- Workflow URL: https://github.com/alamin999bd/ScanPro/actions/runs/45876446027
- এটি CI build success-এর প্রমাণ; সব feature-এর functional test বা Samsung Galaxy M12-এ physical device test-এর প্রমাণ নয়।
- আগের run #18 (ID `45876362520`) sequential build fix-এর পর success ছিল; latest run #19-ই প্রধান reference।

## 6. পরবর্তী কাজের বাধ্যতামূলক অগ্রাধিকার
1. Branch HEAD ও status file পুনরায় fetch করে বর্তমান source নিশ্চিত করা।
2. সর্বশেষ workflow jobs/logs/artifacts পরীক্ষা করে debug এবং release APK সত্যিই তৈরি হয়েছে কি না, artifact name ও build SHA যাচাই করা।
3. Project source audit: CameraX/camera flow, gallery import, image processing, crop/perspective pipeline, PDF engine, Room/database, navigation, permissions, OCR dependency, PDF viewer/editor এবং existing PDF tools।
4. Feature matrix তৈরি: Implemented + tested / Implemented but untested / Partial / Missing / Not feasible offline. অনুমান করে feature-কে complete চিহ্নিত নয়।
5. প্রথমে core scan pipeline (camera/gallery → edge detection/crop/perspective → enhancement/filter → multi-page → PDF/JPG) সম্পূর্ণ করা।
6. তারপর OCR Bangla+English ও searchable text, engine capability/size/offline constraints যাচাই করে যুক্ত করা।
7. তারপর PDF tools, file/folder management, share/print এবং password-protected PDF-এর safe support যাচাই করা।
8. Premium UI, app icon/logo, navigation, empty/error/loading states, accessibility ও Galaxy M12 performance/memory tuning।
9. Unit/instrumentation tests ও CI build; প্রতিটি bug fix-এর পর পুনরায় build।
10. সবশেষে final release APK artifact এবং complete source ZIP প্রস্তুত; final build SHA ও version নথিভুক্ত করা।

## 7. Build ও test-এর সতর্কতা
- বর্তমান CI success-কে কেবল compile/package workflow success হিসেবে গণ্য করতে হবে।
- APK artifact পাওয়া গেলে run ID ও commit SHA মিলিয়ে নিতে হবে; পুরোনো build artifact final হিসেবে দেওয়া যাবে না।
- Debug-signed release fallback থাকতে পারে; release signing configuration পরীক্ষা করতে হবে। Debug-signed APK-কে Play Store-ready বা production-signed বলা যাবে না।
- Samsung Galaxy M12-এ physical install/camera/OCR/PDF test না করা পর্যন্ত device-tested status দেওয়া যাবে না। ব্যবহারকারীকে final install test-এর জন্য নির্দেশনা দিতে হবে, কিন্তু interim APK পাঠানো যাবে না।
- Camera permission, Android 13 media/photo access, file picker/SAF, scoped storage, large image memory, PDF page rendering এবং Bangla OCR font/text encoding বিশেষভাবে পরীক্ষা করতে হবে।

## 8. Session resume protocol
প্রতিবার কাজ শুরু করার আগে:
1. এই ফাইল পড়ো।
2. `DOCUMENT_SCANNER_STATUS_BN.md` পড়ো।
3. `document-scanner-premium` branch-এর latest commit ও GitHub Actions run যাচাই করো।
4. Source code বর্তমান অবস্থায় fetch করে audit করো; আগের summary-কে source-এর বিকল্প মনে করো না।
5. আগের কাজ পুনরায় তৈরি না করে যেখানে থেমেছে সেখান থেকে এগোও।
6. প্রতিটি session শেষে এই master record এবং status file-এ date, commit SHA, changed files, build result, test evidence, blockers ও next steps লিখে commit করো।

## 9. Final delivery checklist
- [ ] সব agreed core features বাস্তবে implement ও test
- [ ] কোনো placeholder/ভুয়া feature নেই
- [ ] Debug ও Release build সফল
- [ ] Final APK artifact latest commit থেকে
- [ ] Release signing status স্পষ্টভাবে যাচাই
- [ ] Samsung Galaxy M12 device test status স্পষ্ট
- [ ] Complete source ZIP
- [ ] README/build instructions/dependencies/version documentation
- [ ] এই master record ও status file আপডেট
- [ ] ব্যবহারকারীকে শুধু final delivery links ও সংক্ষিপ্ত install guidance দেওয়া


## 10. Audit update — 2026-11-27 (run #21)
- Latest confirmed CI run: #21, ID `45877343244`, commit `d98de944e225e4b3951fb47fdebd0fdd077ce0d7`; build job succeeded. Artifact `Document-Scanner-APK` ID `15145796196` (68,382,545 bytes). CI build evidence only; not final distribution.
- Camera source audit: `ScanScreen.kt` uses CameraX live preview, tap-to-focus, flash, MAXIMIZE_QUALITY capture and gallery multi-select. `ScanViewModel.kt` supports multi-page capture, reorder/remove, editing and PDF save.
- Image pipeline audit: `ImageProcessor.kt` performs perspective warp, rotation, crop and filter processing. Perspective transform uses Android `Matrix.setPolyToPoly`; no OpenCV dependency is present.
- OCR audit: `OcrEngine.kt` runs offline Tesseract with `ben+eng`, requiring both traineddata assets. `DocumentsViewModel.kt` stores recognized text and document search matches OCR text.
- Important distinction: current OCR text is stored in Room for in-app document search; the exported scanned PDF does not currently receive an invisible/searchable OCR text layer. This remains a priority gap.
- PDF tools exposed: Merge, Split/Extract, PDF-to-Images, Compress, Lock, Unlock, Watermark, Sign. Page delete/reorder/rotate, page numbering, text annotation, highlight, bookmarks and PDF internal text search are not exposed by the current PDF tools/viewer code.
- Compression trade-off: current `PdfEngine.compress()` rasterizes PDF pages to JPEG, which can remove selectable/searchable text. The UI should clearly disclose this.
- Release signing falls back to debug signing unless a release keystore path is configured; production/Play signing is not established.
- No physical Samsung Galaxy M12 test or automated unit/instrumentation suite was confirmed during this audit.
- Continue with scan/perspective validation, OCR/searchable-PDF design, missing PDF operations, tests and final signing/build verification.

## 2026-11-27 source audit update
- Latest confirmed workflow run #21 succeeded: `45877343244`, commit `d98de944e225e4b3951fb47fdebd0fdd077ce0d7`; artifact `Document-Scanner-APK`, ID `15145796196` (68,382,545 bytes). Do not distribute as final.
- Source audit confirmed existing scan, gallery import, multipage capture, offline Bangla+English OCR, in-app recognized-text search, PDF tools (merge/extract/compress/lock/unlock/watermark/sign), document/folder management and viewer/share/export code.
- Critical distinction: OCR text is stored in the app database for document search, but scanned PDF pages currently do not receive a positioned hidden text layer. So PDF-internal text selection/search is not yet supported for scanned PDFs. Compression rasterizes pages and loses text selectability/search.
- PDF Tools UI only lists Merge, Split/Extract, PDF-to-Images, Compress, Lock, Unlock, Watermark, and Sign. Page delete/reorder/rotate, page numbering, text annotation, highlight, bookmarks and internal PDF text search are not currently surfaced/implemented in the audited screen/engine.
- No unit/instrumentation tests found in inspected source tree; no physical M12 testing done.
- Next: validate perspective geometry; implement PDF page management; then annotations/page numbers/bookmarks/text search; investigate dependable offline Bengali OCR text-layer support. Record test evidence and limitations accurately.


## 2026-11-27 feature implementation log — PDF page management
- Added page deletion, custom page ordering and clockwise page rotation in `PdfEngine.kt`.
- Exposed these as PDF Tools actions via `PdfToolsViewModel.kt` and `PdfToolsScreen.kt`. Operations save a new PDF copy and preserve the original.
- Input validation: deletion must leave at least one page; reordering must include each page exactly once; rotation limited to 90/180/270 degrees.
- CI runs triggered by these commits were still in progress when recorded. Do not mark as verified until latest HEAD build completes successfully.
