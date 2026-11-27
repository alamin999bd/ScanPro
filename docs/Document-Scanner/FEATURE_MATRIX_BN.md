# Feature Matrix — Document Scanner

| ফিচার | বর্তমান নথিভুক্ত অবস্থা | QA অবস্থা |
|---|---|---|
| CameraX scan / multi-page capture | Source audit-এ আছে | Samsung M12-এ বাস্তব যাচাই বাকি |
| Gallery import | Source audit-এ আছে | বিভিন্ন image format ও বড় ফাইল QA বাকি |
| Auto edge detection / manual crop / perspective correction | Source audit-এ আছে; custom 4-point homography | geometry ও device QA বাকি |
| Image filters | Original, Enhance, B&W, Grayscale, Vivid; AUTO/LIGHTEN/DARKEN/COLOR branch; temporary bitmap lifecycle cleanup ও adaptive-threshold working pixel cap যুক্ত | CI Build #111 সফল; visual/device QA বাকি |
| Offline Bangla + English OCR | Tesseract ben+eng; text DB-তে সংরক্ষণ ও app search | Bangla/English mixed-document accuracy QA বাকি |
| OCR hidden text layer in generated PDF | hOCR word geometry থেকে invisible Unicode text layer তৈরি; Noto Sans Bengali font asset CI-তে প্যাকেজ হয় | Unit/geometry test আছে; PDFTextStripper + external viewer + বাংলা/ইংরেজি বাস্তব QA বাকি |
| PDF merge / extract / split / text-preserving optimize / lock / unlock / watermark / signature | Source audit-এ আছে; Optimize PDF rewrite-এর আগে/পরে page count ও extracted text যাচাই করে এবং ছোট না হলে copy save করে না | প্রতিটি operation-এর sample-PDF functional QA বাকি |
| Delete / reorder / rotate PDF pages | Engine, ViewModel ও UI action যোগ | sample PDF regression QA বাকি |
| Add page numbers | Engine, ViewModel ও UI action যোগ | rotated/CropBox PDF QA বাকি |
| Search selectable PDF text | PDFTextStripper search যোগ; image-only scan-এ কাজ করে না | sample PDF QA বাকি |
| Add PDF bookmark | PDF outline bookmark engine, ViewModel ও UI action যোগ | external viewer navigation QA বাকি |
| Text annotation / highlight | PDFBox sticky-note এবং rectangular yellow highlight; CropBox coordinate mapping ও finite/range validation যোগ | বিভিন্ন viewer-এ annotation visibility QA বাকি |
| Standard PDF viewer-এ scanned PDF search | OCR text layer implementation যুক্ত; Build #73 compile/package সফল | বাস্তব PDF extraction, select/search ও Bengali rendering QA বাকি |
| Automated tests | test coverage এখনো পর্যাপ্ত নয় | বাকি |
| Performance / memory checks | Samsung Galaxy M12 বাস্তব QA হয়নি | বাকি |
| Final release signing / Source ZIP | final delivery প্রস্তুত নয় | বাকি |

## সর্বশেষ যাচাইকৃত পরিবর্তন
- Build #73 সফল; run 45949496949; shared OCR/PDF bitmap decode dimension যাচাই ও Debug/Release APK verification/artifact upload সফল।
- OCR PDF geometry-র invalid/zero-area এবং image-edge clipping test বাড়ানো হয়েছে; এই নতুন test-এর CI ফলাফল পরবর্তী Build-এ যাচাই হবে.
- Build #69 সফল; run 45948288495; Unit Tests, Debug/Release APK, APK verification ও artifact upload সফল।
- Build #66–#68-এর compile errors পরবর্তী commit-গুলোতে সংশোধিত; Build #69 consolidated branch state সফলভাবে যাচাই করেছে।
- Build #55 সফল; commit d0825def927e2b874dc92b4364c9284fcb28a98a.
- PDF Annotation/Highlight coordinate mapping CropBox origin-এ সংশোধিত; invalid/non-finite/out-of-page bounds validate হয়।
- CI success ফিচারের বাস্তব viewer compatibility বা physical-device QA-র বিকল্প নয়।


## 2026-11-27 — Serial 6/7 verification update
- PDF bookmark navigation: Viewer displays bookmark chips and navigates to selected page; recursive outline traversal is implemented. CI Build #141 passed.
- In-viewer selectable-text search: search results show matching page chips and navigate to those pages; Build #143 passed. Image-only pages without OCR text layer are not searchable through this PDF text search.
- In-app note/highlight overlays: supported PDF text-note annotations and rectangular highlight bounds are read from PDFBox metadata and drawn over rendered pages; tapping a note opens its text. Geometry handles CropBox and right-angle page rotation. Unit tests and Build #151 passed.
- Viewer header layout was adjusted to measure its actual height, preventing the search/bookmark header from relying on fixed padding. PDF page rendering now caps requested width at 1600 px, uses normalized cache keys, rechecks cache under render lock, and recycles a bitmap if rendering throws.
- Latest CI Build #154, run 45981830547, commit 181ab3df369da6ad3b2b5a84d767df15f1ce6297: Unit Tests, Debug/Release APK builds, APK output verification and artifact upload all passed. Artifact ID 15190061888; debug APK 45 MB, release APK 37 MB.
- These are CI/code-level results, not proof of real-device performance or external PDF viewer compatibility. Highlight overlay currently uses the annotation rectangle bounds, not each individual text quad; multiline highlights may visually cover intervening spaces. The markup UI remains coordinate-based rather than text-selection based.

| Automated regression tests | 9 core JVM test files covering OCR parsing/geometry, searchable text layer, page-quad geometry, PDF ranges/order, markup geometry, page-markup normalization and text search; new non-finite/invalid-threshold cases added | CI unit tests passed through Build #164; UI/instrumentation and physical-device QA remain pending |
| APK signing verification | CI now verifies Debug/Release APK signatures and records SHA-256 certificate fingerprints with apksigner | Release uses debug-key fallback unless a secure SCANPRO keystore is configured; not Play Store production signing |


## 2026-11-27 — Final CI gates
| Release signature verification | Debug ও Release APK উভয়ের `apksigner verify` validation CI-তে পাস | Build #167 (run 45985668334) SUCCESS; Release uses debug-key fallback, not Play Store production signing |
| Final source archive | Workflow now packages tracked project at checked-out HEAD with `git archive`, checks ZIP integrity and includes source ZIP in artifact | New final packaging Build pending; do not mark final until its artifact is inspected |
| Samsung Galaxy M12 device QA | এই সেশনে বাস্তব ডিভাইসে পরীক্ষা হয়নি | Pending; CI success দিয়ে device QA বোঝায় না |


## 2026-11-27 — Regression response to user video
- Observed in supplied 121-second recording: after tapping Save, “Creating PDF…” remained visible from approximately 84 seconds through the end of the clip.
- Main Scan save now writes a regular image-based PDF first and registers it before optional OCR; OCR runs afterward in the background and cannot block or invalidate PDF creation.
- Important trade-off: this main save route no longer writes the invisible OCR text layer. OCR text is still attempted for in-app document search. Standard PDF-reader searchability of newly scanned PDFs is not currently guaranteed.
- Theme palette changed to teal/coral, and editor Apply action is now explicitly labelled.
- Latest code HEAD `471ce385368d24036f222e0c2ca8006b058bfd74`; build status not yet confirmed. Device QA pending.



## 2026-11-27 — Background searchable-PDF recovery
- Scan Save now writes and registers a regular image PDF first so OCR is no longer on the initial Save critical path.
- Original scan images are retained while background OCR creates per-page hOCR geometry; when OCR produces word results, a searchable PDF candidate is generated and checked for non-empty output and matching page count.
- A same-directory backup/rename replacement installs the searchable PDF while preserving the already-saved image PDF if OCR or replacement fails.
- Document library size metadata is refreshed after a successful replacement. Recognized text is stored for in-app document search.
- This is source-level implementation only until the latest branch HEAD CI build passes. Samsung Galaxy M12 Android 13 testing remains required; OCR background CPU/RAM use and replacement behavior have not yet been measured on-device.
