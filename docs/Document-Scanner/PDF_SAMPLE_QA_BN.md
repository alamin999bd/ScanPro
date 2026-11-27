# PDF Sample QA Protocol — Document Scanner

এই checklist PDF Tools-কে বাস্তব sample PDF দিয়ে যাচাই করার জন্য। এটি test plan, test result নয়। প্রতিটি case চালানোর পর তারিখ, APK/build, input file, expected/actual result এবং evidence লিখতে হবে।

## Test setup ও নিরাপদ নিয়ম
- একই APK build ও একই input set দিয়ে সব operation চালান; build number/commit লিখুন।
- Original PDF কখনো overwrite করবেন না। প্রতিটি operation-এর output আলাদা নামে তৈরি করুন।
- শুরুতে প্রতিটি input-এর page count, file size, password status, selectable text এবং page rotation নোট করুন।
- Output তৈরি হলে app Library এবং আলাদা PDF viewer—দুই জায়গায় খুলে দেখুন।
- App crash, blank page, missing page, unreadable text, wrong rotation বা corrupted output হলে PASS নয়।
- Password-protected ফাইলের ক্ষেত্রে কেবল নিজের/অনুমোদিত test document ব্যবহার করুন।

## প্রয়োজনীয় sample set
| ID | Sample PDF | বৈশিষ্ট্য |
|---|---|---|
| P1 | Basic-3-pages.pdf | ৩ পৃষ্ঠা, selectable English text, আলাদা page content |
| P2 | Mixed-BN-EN.pdf | বাংলা ও ইংরেজি selectable text, একাধিক paragraph |
| P3 | Scanned-Images.pdf | ২–৩টি image-only page, selectable text নেই |
| P4 | Rotated-CropBox.pdf | ৯০°/১৮০° rotation ও non-default CropBox |
| P5 | ImageHeavy-Large.pdf | বড় image-heavy, বহু পৃষ্ঠা; low-memory আচরণ যাচাই |
| P6 | Password-Protected.pdf | test-only password-protected PDF |
| P7 | Single-Page.pdf | একটি মাত্র পৃষ্ঠা; boundary/negative test |

## Operation checklist
| Operation | Positive test / expected result | Negative বা edge test |
|---|---|---|
| Merge | P1 + P2 merge; output page count দুই ফাইলের মোট পৃষ্ঠার সমান, সব পৃষ্ঠা সঠিক ক্রমে | একটি input, corrupt input, protected input; error দেখাবে, partial output/Library row থাকবে না |
| Extract | P1 থেকে ১ ও ৩ নম্বর পৃষ্ঠা; output-এ ঠিক ২ পৃষ্ঠা ও সঠিক content | empty selection, ০ বা page-count-এর বেশি নম্বর; reject |
| Delete pages | P1 থেকে একটি page delete; output count এক কম | সব page delete করার চেষ্টা; reject, original অক্ষত |
| Reorder | P1-এর page order ৩,১,২; output content সেই ক্রমে | duplicate, missing, out-of-range page; reject |
| Rotate | P4-এ ৯০°, ১৮০°, ২৭০°; সব page rotation ঠিকভাবে বদলাবে | ০°/অবৈধ degree; reject |
| Optimize | P1/P2 output খুলবে; page count ও extracted text অপরিবর্তিত; output ছোট না হলে original/copy রাখা হবে | image-heavy P5-এ size কমবেই এমন প্রত্যাশা নয়; বড় output গ্রহণ করা যাবে না |
| Lock | P1-এ test password; reopen করে password prompt/permission যাচাই | empty/invalid password; clear error, corrupt output নয় |
| Unlock | অনুমোদিত password দিয়ে P6 খুলে unlocked copy; page/text অক্ষত | ভুল password; reject, source অক্ষত |
| Watermark | P1-এ watermark প্রতিটি intended page-এ দেখা যাবে | বাংলা watermark আলাদা করে যাচাই; unsupported glyph হলে তা নথিভুক্ত |
| Signature image | test signature image নির্দিষ্ট page/position-এ দেখা যাবে | এটি cryptographic/legal digital signature নয়; UI-তে এভাবে দাবি করা যাবে না |
| Page numbers | P1 ও P4-এ page number দেখা যাবে; rotated/CropBox page-এ clipping হবে না | empty/zero-page/corrupt input; reject |
| Bookmark | bookmark যোগ করে app ও external viewer-এ target page-এ navigate | invalid page বা blank title; reject |
| Note / highlight | note ও highlight save; app এবং external viewer-এ অবস্থান/visibility যাচাই | CropBox/rotated page-এ coordinate shift বা missing annotation হলে fail |
| PDF text search | P1/P2-এ বিদ্যমান text পাওয়া; সঠিক page দেখাবে | P3 image-only হলে text search match আশা করা যাবে না; blank query reject |

## PASS criteria
1. Output file exists, non-zero size, PDF header এবং external viewer-এ open হয়।
2. Page count ও page order expected result-এর সঙ্গে মেলে।
3. Merge/extract/delete/reorder/rotate-এর content ভুল/হারানো নয়।
4. Optimize-এর ক্ষেত্রে page count এবং extracted text অপরিবর্তিত; output কেবল ছোট হলে optimize result হিসেবে গ্রহণ।
5. Failure path-এ original PDF অক্ষত এবং orphan/partial Library entry নেই।
6. Large PDF-এ app crash বা স্থায়ী UI freeze হয় না; memory pressure হলে পরিষ্কার error দেয়।
7. প্রতিটি test-এর evidence ও result log সংরক্ষিত।

## Result log template
- Date/time:
- App version / Build number / commit:
- Device / Android:
- Test ID + operation:
- Input filename / page count / file size:
- Expected result:
- Actual result:
- Output filename / page count / file size:
- External viewer result:
- PASS / FAIL / BLOCKED:
- Screenshot বা error log reference:

## Static code audit (execution নয়)

এই অংশটি source-code inspection থেকে তৈরি; এখানে কোনো PDF sample চালানো হয়নি। তাই নিচের বিষয়গুলো QA-তে বিশেষভাবে যাচাই করতে হবে।

| Area | Source-level observation | Sample QA-তে যাচাই |
|---|---|---|
| Merge / Extract / Delete / Reorder | PDFBox দিয়ে page import/remove করা হয়; page selection 1-based | page count ছাড়াও প্রতিটি page-এর text/visual identity মিলিয়ে দেখুন |
| Rotate | প্রতিটি page-এর আগের rotation-এর সঙ্গে 90/180/270 যোগ হয় | আগে থেকেই rotated page-এ cumulative rotation ও visual orientation |
| Optimize | PDF পুনরায় save করে page count ও extracted text exact match যাচাই; output ছোট না হলে UI copy রাখে না | text-preservation ও external viewer; image-heavy PDF ছোট হবে এমন প্রত্যাশা নয় |
| Lock / Unlock | ৪+ অক্ষরের password; unlock-এ supplied password দিয়ে PDF load হয় | ভুল password, reopen prompt, সঠিক password-এ text/page count |
| Watermark | Latin built-in font; অন্য script bitmap হিসেবে render হয় | বাংলা glyph, transparency, CropBox/MediaBox ও rotated page placement |
| Signature | signature image বসায়; rotated page হলে operation reject করে | page 1/last page, position, source unchanged; এটি cryptographic signature নয় |
| Page numbers | selectable Helvetica text; CropBox-এর ভিত্তিতে placement | rotated/CropBox page-এ clipping ও external viewer visibility |
| Notes / Highlights | annotation rectangle PDF user-space-এ CropBox offset ধরে তৈরি | rotation, non-zero CropBox origin ও external viewer appearance |

### QA execution boundary
- CI Build #201 সফল হলেও উপরের PDF operations sample-file end-to-end হিসেবে চালানো হয়নি।
- Repository-তে dedicated PDF sample fixture বা PdfEngine integration test পাওয়া যায়নি; বর্তমান JVM test-গুলো geometry, parsing ও text-search helper-এর মতো isolated logic কভার করে।
- তাই এই পর্যায়ে কোনো operation PASS ঘোষণা করা হচ্ছে না। প্রথমে controlled P1/P2/P4/P6 fixtures তৈরি/সংগ্রহ করে PDF engine-এর positive ও negative paths চালাতে হবে।
- Test fixture যোগ করার সময় copyrighted বা ব্যক্তিগত নথি ব্যবহার করা যাবে না; synthetic test content ব্যবহার করতে হবে।

## বর্তমান অবস্থা
- Build #200 CI সফল; এটি PDF Tools-এর end-to-end functional test নয়।
- Dedicated PDF engine sample-fixture test suite source audit-এ পাওয়া যায়নি।
- এই checklist-এর কোনো case execution evidence ছাড়া PASS হিসেবে চিহ্নিত করা যাবে না।
- Samsung Galaxy M12 Android 13 manual QA এখনো pending।
