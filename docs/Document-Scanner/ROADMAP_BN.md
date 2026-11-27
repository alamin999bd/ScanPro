# Known Issues ও Roadmap — Document Scanner

## Known limitations
1. Generated scanned PDF-এ OCR থেকে invisible Unicode text layer যোগ করা হয়; তবে বাংলা যুক্তাক্ষর/শেপিং, text extraction order, selectable text alignment এবং বিভিন্ন PDF viewer-এ অনুসন্ধান বাস্তবে যাচাই বাকি।
2. Compress PDF বর্তমানে প্রতিটি পৃষ্ঠা rasterize করে নতুন PDF বানায়; ফলে searchable/selectable text layer হারায়। এই সীমাবদ্ধতা সমাধান না হওয়া পর্যন্ত Compress-কে searchable-PDF-preserving feature বলা যাবে না।
3. Search PDF Text বর্তমানে PDF-এর selectable/searchable text খোঁজে; OCR database-এর text বা image-only PDF-এ আলাদা OCR না চালালে ফল নাও আসতে পারে।
4. PDF viewer Android PdfRenderer-এ page render করে; PDF content editing features আলাদা করে তৈরি করতে হয়।
5. PDF password support PDFBox implementation ও permission/owner restrictions দ্বারা সীমাবদ্ধ হতে পারে।
6. বাস্তব Samsung Galaxy M12 QA এখনো pending।

## Next development order
1. Searchable PDF-এর PDFTextStripper extraction ও Bangla/English mixed text automated verification।
2. Standard PDF viewer-এ search/select এবং Bengali rendering যাচাই; shaping ব্যর্থ হলে limitation স্পষ্টভাবে দেখানো বা বিকল্প সমাধান।
3. Compress PDF-এর text-preservation architecture নির্ধারণ; নিরাপদ সমাধান না পাওয়া পর্যন্ত rasterization limitation UI/docs-এ স্পষ্ট রাখা।
4. PDF bookmark, annotation ও highlight বিভিন্ন viewer-এ sample-file QA।
5. Camera, crop/perspective, filters, multipage scan, OCR accuracy এবং PDF operations-এর regression tests।
6. Low-memory/performance checks এবং বাস্তব Samsung Galaxy M12 test।
7. সব feature QA শেষে final APK signing verification, complete Source ZIP, test report ও GitHub build record।

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
