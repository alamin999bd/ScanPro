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
- Corrective change: the JVM test now checks the TTF file signature and basic file integrity using only standard byte-stream APIs; it no longer depends on Android PDFBox or Java AWT.
- A separate CI step uses Python FontTools to inspect the font cmap and verify Bangla Unicode block plus required Latin glyph coverage. This keeps font-table validation outside Android's JVM test classpath.
- This font check does **not** prove PDFBox writes/extracts the hidden text layer or that Bengali shaping looks correct in a PDF viewer; those remain runtime/instrumentation QA items.
- Static Noto Sans Bengali font and its OFL license remain provisioned by CI at the production asset path.
- Next gate: run all unit tests, then Debug and Release APK builds, output verification and artifact upload. If any stage fails, diagnose and fix before feature work.
