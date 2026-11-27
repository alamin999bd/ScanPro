# Build ও Test Log — Document Scanner

## Latest verified CI
- **Build #52 — SUCCESS**; run ID: `45932150003`.
- Commit: `5f85ff19073b576a0ad23efb811b2983b2a24995` — `fix(ui): correct PDF markup callback signature`.
- Debug ও Release APK build উভয়ই সফল; APK output verification এবং artifact upload সফল।
- Artifact: `Document-Scanner-APK`, ID `15169275522`, size 68,434,716 bytes; not expired (2026-12-27 পর্যন্ত)।
- এটি CI build/package success; ফিচারের বাস্তব ব্যবহার বা Samsung ফোনে পরীক্ষা সম্পন্ন হওয়ার প্রমাণ নয়।

## Failed builds diagnosed and consolidated fix
- Build #46 (run `45931442097`): `PdfEngine.kt:317` — unsupported `isOpen` property।
- Build #47 (run `45931458281`): একই `isOpen` error।
- Build #48 (run `45931478387`): `isOpen` error; `PdfToolsScreen.kt:147` addMarkup callback-এর argument mismatch; lines 467–468-এ `p10` parameter missing।
- Build #49 (run `45931502573`) ও #50 (run `45931506391`): একই compile errors পুনরাবৃত্তি।
- Build #51 (run `45932143474`): `isOpen` error বাদ গেলেও addMarkup callback mismatch এবং দুই call-site-এ `p10` missing ছিল।
- Build #52-এর commit-এ annotation API থেকে unsupported property সরানো, callback signature/call-site arguments এক করা হয়েছে। Build #52-এর compiler log-এ Debug ও Release উভয় build সফল।

## Earlier verified build
- Build #38 — run ID `45880933475` — successful; historical CI evidence only.

## Functional test limitations
- CI compilation সফল হওয়া মানে Bookmark/Note/Highlight UI বাস্তবে সঠিকভাবে কাজ করছে—এমন নয়।
- Samsung Galaxy M12 (SM-M127G, Android 13)-এ বাস্তব install ও interaction test এখনো সম্পন্ন হয়নি।
- PDF viewer compatibility, note/highlight visibility, bookmark navigation, page coordinates/orientation, export/share এবং বড় PDF memory use বাস্তব test প্রয়োজন।
- Annotation/Highlight UI-তে বর্তমানে coordinate-based area input ব্যবহৃত হয়; text-select-and-highlight interaction এখনো নেই।

## Final delivery gate
চূড়ান্ত APK ও Source ZIP দেওয়ার আগে latest branch HEAD-এর CI সফল হতে হবে, artifact যাচাই করতে হবে, source ZIP প্রস্তুত করতে হবে এবং যতটুকু পরীক্ষা বাস্তবে করা হয়েছে তার সঠিক রিপোর্ট দিতে হবে।

## Recent OCR searchable-PDF build corrections (2026-11-27)
- Build #65 (run 45948093924) — SUCCESS; added image-dimension-aware OCR page output.
- Build #66 (run 45948096846) — FAILED: ScanScreen passed constructor arguments in the old order and imagesToSearchablePdf had not yet been added. Corrected by wiring OcrEngine into ScanViewModel and adding the PDF export method.
- Build #67 (run 45948101408) — FAILED: the new ViewModel call had no matching PdfEngine.imagesToSearchablePdf method at that commit. The method was added in the subsequent PDF implementation commit.
- Build #68 (run 45948285063) — FAILED: the PDF export referenced the OCR geometry mapper before that helper existed. Added OcrPdfGeometry.kt in the next commit.
- Build #69 (run 45948288495) — SUCCESS: consolidated branch state verified; Unit Tests, Debug APK, Release APK, output verification and artifact upload all passed.
- Runtime-readiness fix: the exporter loads Noto Sans Bengali from app assets. CI downloads the font and its OFL license to the exact asset path before packaging; compile success alone would not detect a missing runtime asset.
- Geometry unit tests and PDF word spacing are added; PDF viewer search/select and Bengali rendering still require runtime validation.

## PDF text-layer font test corrections (2026-11-27)
- Builds #86 (run `45952041353`), #87 (`45952052745`), #89 (`45952517734`) failed when PDFBox attempted to load a font in a plain JVM test without Android PDFBox resource initialization.
- Build #91 (run `45952867570`) failed at Kotlin test compilation because `java.awt.Font` is unavailable in the Android unit-test compile environment.
- Build #93 (run `45953402648`) failed in font coverage because the check incorrectly required the entire Bengali Unicode block and Latin characters from the Bengali-only font. This was an over-broad test expectation.
- Corrective change: the JVM test now checks the TTF file signature and basic file integrity using only standard byte-stream APIs; it no longer depends on Android PDFBox or Java AWT.
- The app now embeds separate Noto Sans Bengali and Noto Sans Latin fonts and selects the font for each OCR word based on whether it contains Bengali characters.
- A separate CI step uses Python FontTools to inspect each font cmap against the actual sample characters needed for its script, rather than demanding every code point in the full Unicode block.
- This font check does **not** prove PDFBox writes/extracts the hidden text layer or that Bengali shaping looks correct in a PDF viewer; those remain runtime/instrumentation QA items.
- Static Noto Sans Bengali font and its OFL license remain provisioned by CI at the production asset path.
- Next gate: run all unit tests, then Debug and Release APK builds, output verification and artifact upload. If any stage fails, diagnose and fix before feature work.

## Camera/Gallery এবং Image Filter/Memory CI (2026-11-27)
- Build #106 (run `45969060299`, commit `d557db97655e1e71fed0244a052a6b02c3cd581f`) — SUCCESS: gallery import/cancellation cleanup, camera startup failure handling ও capture-state safeguards; Unit Tests, Debug/Release APK, APK verification এবং artifact upload সফল।
- Image filter/memory changes: commits `e9702053a38d8d2d70e1b5e7a38052c1cd874cf6`, `57bdad91fa0f33593683f8721b1780d9e6febd39`, `aaec49ad65926bc91a9248a47b890ece2e557999`, `1242a6f1851023b2f0459348e4c16eeb187a5b51`, `8aca7bba29cba7d6311785e6a78d17f23da19ac5` — Builds #107–#111 all SUCCESS।
- Changes include filter intermediate bitmap recycling, editor preview/source bitmap disposal, auto-detection preview cleanup, adaptive-threshold working pixel cap reduced from 8M to 4M, and export bitmap cleanup.
- Latest verified HEAD at this entry: `8aca7bba29cba7d6311785e6a78d17f23da19ac5`; Build #111 run `45969721491` — Unit Tests, Debug/Release, APK output verification and artifact upload succeeded.
- CI does not verify filter visual quality or actual device memory behaviour. Samsung Galaxy M12 visual/RAM QA remains pending.


## PDF viewer annotation and UI/performance builds (2026-11-27)
- Build #151 — run 45980965443, commit 82be4ca1272ccd901b85be41f66a8ec189d5d0a0: SUCCESS. Unit Tests, Debug/Release APK, output verification and artifact upload passed. Added in-viewer note/highlight overlay reading and drawing; geometry test expectations were corrected after earlier failed test runs.
- Build #152 — run 45981747526, commit f6da4c104c2984fe24dc6cb40161c3f3cc6574d4: SUCCESS.
- Build #153 — run 45981783269, commit ef49c06542c8d8a525a470b1cfa9a893eaf00ab7: FAILED. This run was for an intermediate commit and was superseded by Build #154; it is not the latest branch HEAD.
- Build #154 — run 45981830547, commit 181ab3df369da6ad3b2b5a84d767df15f1ce6297: SUCCESS. Confirmed branch HEAD matched this SHA. Unit Tests passed; Debug and Release APK builds succeeded; APK output verification passed; artifact upload succeeded.
- Build #154 APK output: Debug 45 MB, Release 37 MB. Artifact Document-Scanner-APK, ID 15190061888, uploaded successfully; artifact digest f4dbc9c79ebcbf09955891f2fbee0a177dcaae72aec4343ce0e6b686787a124f.
- CI success proves compilation/package/test pipeline success only. No physical Samsung Galaxy M12 test was performed.
- Serial 7 changes in the latest commit: Viewer search/bookmark header uses measured height rather than fixed top padding; PDF rendering width is capped at 1600 px; cache is rechecked under the render lock; failed bitmap rendering recycles its temporary bitmap.


## Automated regression expansion and signing verification (2026-11-27)
- Serial 7: added 250 ms debounce to document-content search; Build #160 (run 45982533902, commit dc2347ed94ba104bb3a1731cc1407b9bb7dcff42) passed.
- Serial 8: expanded pure JVM regression coverage for invalid/non-finite OCR PDF dimensions and invalid perspective-geometry thresholds. The suite currently contains 9 core test files, including OCR parsing/geometry, searchable text layer, page quad, PDF range/order, markup geometry, page-markup normalization and PDF text search.
- Builds #161–#164 (runs 45983560847, 45983564837, 45983568778, 45983573696) all passed; Build #164 matches branch HEAD 2a198bbd586ddd73efbf146d6b3ecb1598ec2530. Unit tests, Bengali/Latin font coverage, Debug/Release APK builds, APK output verification and artifact upload succeeded.
- Serial 8 automated unit regression CI gate is complete. This does not represent automated UI/instrumentation tests or physical-device QA.
- Serial 9: added an `apksigner` verification step to CI. It validates both APK signatures, records certificate SHA-256 fingerprints, and checks that the release APK uses the expected certificate. When `SCANPRO_KEYSTORE_PATH` is absent, Gradle intentionally falls back to its debug key; that APK is not Play Store production-signed. Never store a private keystore in the repository.
