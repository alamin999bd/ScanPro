# Known Issues ও Roadmap — Document Scanner

## Known limitations
1. Generated scanned PDF-এ OCR থেকে invisible Unicode text layer যোগ করা হয়; তবে বাংলা যুক্তাক্ষর/শেপিং, text extraction order, selectable text alignment এবং বিভিন্ন PDF viewer-এ অনুসন্ধান বাস্তবে যাচাই বাকি।
2. Optimize PDF PDF structure rewrite করে; এটি image recompression নয়। Page count ও extracted text মিলিয়ে দেখে এবং কেবল output ছোট হলে সেটি গ্রহণ করে। তাই image-heavy PDF-এ ফাইল ছোট নাও হতে পারে।
3. Search PDF Text বর্তমানে PDF-এর selectable/searchable text খোঁজে; OCR database-এর text বা image-only PDF-এ আলাদা OCR না চালালে ফল নাও আসতে পারে।
4. PDF viewer Android PdfRenderer-এ page render করে; PDF content editing features আলাদা করে তৈরি করতে হয়।
5. PDF password support PDFBox implementation ও permission/owner restrictions দ্বারা সীমাবদ্ধ হতে পারে।
6. বাস্তব Samsung Galaxy M12 QA এখনো pending।

## Next development order
1. PDF Tools-এর sample-file functional QA: merge, extract, delete, reorder, rotate, optimize, lock/unlock, watermark, signature ও page annotations। বিস্তারিত reproducible checklist: PDF_SAMPLE_QA_BN.md।
2. Searchable PDF-এর PDFTextStripper extraction ও Bangla/English mixed text automated/runtime verification।
3. Standard PDF viewer-এ search/select এবং Bengali rendering যাচাই; shaping ব্যর্থ হলে limitation স্পষ্টভাবে দেখানো বা বিকল্প সমাধান।
4. PDF bookmark, annotation ও highlight বিভিন্ন viewer-এ sample-file QA।
5. Camera, crop/perspective, filters, multipage scan এবং OCR accuracy regression tests।
6. Low-memory/performance checks এবং বাস্তব Samsung Galaxy M12 test।
7. সব feature QA শেষে persistent user-owned release signing, final APK, complete Source ZIP, test report ও GitHub build record।

## Do not overclaim
কোনো feature শুধু menu item বা method থাকার কারণে "সম্পূর্ণ কার্যকর" বলা যাবে না। CI, automated test, sample PDF test এবং real-device test আলাদা করে report করতে হবে। CI সফল হলেও বাস্তব ডিভাইস বা বাহ্যিক PDF viewer-এ feature কাজ করছে—এমন দাবি করা যাবে না।

## 2026-11-27 progress update
- Camera/Gallery/Multi-page safeguards merged; Build #106 passed. Real camera capture and gallery behavior still need physical-device testing.
- Filter pipeline bitmap ownership and temporary memory cleanup improved; adaptive threshold capped to a 4M-pixel working bitmap; Builds #107–#111 passed.
- Do not consider image filters visually validated until sample-image comparisons and Samsung Galaxy M12 memory/performance QA are completed.


## 2026-11-27 progress — latest verified Build #154
- Serial 6 implementation now includes PDF outline bookmark navigation, selectable-text search with page navigation, supported note/highlight annotation overlays, CropBox/rotation coordinate normalization and geometry tests.
- Serial 7 started: dynamic viewer header measurement replaces fixed top padding; PDF page rendering width is capped at 1600 px; cache is checked again inside the render lock to avoid duplicate concurrent rendering; failed bitmap rendering recycles the temporary bitmap.
- Latest HEAD 181ab3df369da6ad3b2b5a84d767df15f1ce6297 Build #154 passed all CI stages, including unit tests, Debug/Release builds, APK verification and artifact upload.
- Remaining Serial 7 work: inspect and tune other large-image/PDF rendering paths, list/editor scrolling and low-memory cleanup; avoid claiming M12 performance until physical-device tests.
- Then Serial 8 automated regression expansion, Serial 9 release-signing verification, Serial 10 final build and complete source ZIP.


## 2026-11-27 — Serial 7/8/9 progress
- Serial 7: document-content search now debounces input by 250 ms; Build #160 passed for the change. Viewer render/header safeguards from Build #154 remain in place. Actual M12 performance remains unverified.
- Serial 8: added numeric edge-case regression tests for OCR PDF dimensions and perspective quad validation. Builds #161–#164 all passed; latest verified HEAD before signing workflow enhancement was 2a198bbd586ddd73efbf146d6b3ecb1598ec2530. Nine core JVM test files are present. UI/instrumentation and device tests are not claimed.
- Serial 9: CI now verifies Debug and Release APK signatures using Android `apksigner` and records SHA-256 certificate fingerprints. With no configured `SCANPRO_KEYSTORE_PATH`, Release falls back to the ephemeral runner's debug key and is not Play Store-ready or guaranteed update-compatible across separate CI runs. A persistent private release keystore must be configured securely before production distribution.
- Serial 10 remains: final latest-HEAD build, final APK artifact and full source ZIP.


## 2026-11-27 final gate update
- Serial 9 CI signature validation is complete: Build #167 (run 45985668334, commit 6205d86a06319e8cba4a5cc940617a14f9452631) passed both Debug and Release `apksigner verify` checks, unit tests and APK builds.
- Important signing limitation: Release currently uses Gradle debug-key fallback when no persistent keystore is configured. It is not Play Store production-signed; do not promise seamless updates across clean CI runs.
- Serial 10: final workflow must package the tracked source at the same commit as the APK, validate the ZIP, and upload it alongside both APK variants and signing reports. The workflow-provisioned OCR/font assets are downloaded during build and are not tracked source files.
- Do not deliver until the new packaging commit itself has a fully successful workflow and the artifact file list is verified. Physical Samsung Galaxy M12 QA remains pending.


## 2026-11-27 — PDF Tools audit update
- Build #198: PDF-to-images page records are registered in one batch to avoid partial Library entries if registration fails.
- Build #199: PDF Tools memory-exhaustion handling and partial-output cleanup added; Unit Tests, Debug/Release builds, APK verification and source ZIP packaging passed.
- Next: sample-PDF regression checks for merge/extract/delete/reorder/rotate/optimize/lock/unlock/watermark/sign/annotations, followed by Samsung Galaxy M12 Android 13 testing. No device QA is claimed.


## 2026-11-27 — PDF Sample QA planning
- Build #200 (run 46006919470, commit 1b227d6d75da992541a147f9a73a6995bae204e2) SUCCESS: Unit Tests, Debug/Release APK, APK verification, signing checks ও source ZIP packaging passed।
- Source audit-এ PDF engine operations-এর জন্য dedicated end-to-end/sample-PDF test suite পাওয়া যায়নি। বর্তমান JVM tests মূলত parsing, geometry, OCR text-layer helper ও markup logic পরীক্ষা করে।
- নতুন PDF_SAMPLE_QA_BN.md-এ test fixtures, operation-by-operation expected results, negative cases ও evidence log format নির্ধারণ করা হয়েছে।
- এই নথি test plan; কোনো sample PDF operation এখনো functional pass হিসেবে গণ্য নয়। Test execution ও Samsung Galaxy M12 QA pending।
