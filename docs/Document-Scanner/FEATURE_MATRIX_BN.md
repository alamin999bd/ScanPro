# Feature Matrix — Document Scanner

| ফিচার | বর্তমান নথিভুক্ত অবস্থা | QA অবস্থা |
|---|---|---|
| CameraX scan / multi-page capture | Source audit-এ আছে | Samsung M12-এ বাস্তব যাচাই বাকি |
| Gallery import | Source audit-এ আছে | বিভিন্ন image format ও বড় ফাইল QA বাকি |
| Auto edge detection / manual crop / perspective correction | Source audit-এ আছে; custom 4-point homography | geometry ও device QA বাকি |
| Image filters | Original, Enhance, B&W, Grayscale, Vivid; AUTO/LIGHTEN/DARKEN/COLOR branch | visual QA বাকি |
| Offline Bangla + English OCR | Tesseract ben+eng; text DB-তে সংরক্ষণ ও app search | Bangla/English mixed-document accuracy QA বাকি |
| OCR hidden text layer in generated PDF | hOCR word geometry থেকে invisible Unicode text layer তৈরির source যুক্ত; Noto Sans Bengali font asset CI-তে প্যাকেজ করার ব্যবস্থা যোগ | PDFTextStripper + external viewer + বাংলা/ইংরেজি বাস্তব QA বাকি |
| PDF merge / extract / split / compress / lock / unlock / watermark / signature | Source audit-এ আছে | প্রতিটি operation-এর sample-PDF functional QA বাকি |
| Delete / reorder / rotate PDF pages | Engine, ViewModel ও UI action যোগ | sample PDF regression QA বাকি |
| Add page numbers | Engine, ViewModel ও UI action যোগ | rotated/CropBox PDF QA বাকি |
| Search selectable PDF text | PDFTextStripper search যোগ; image-only scan-এ কাজ করে না | sample PDF QA বাকি |
| Add PDF bookmark | PDF outline bookmark engine, ViewModel ও UI action যোগ | external viewer navigation QA বাকি |
| Text annotation / highlight | PDFBox sticky-note এবং rectangular yellow highlight; CropBox coordinate mapping ও finite/range validation যোগ | বিভিন্ন viewer-এ annotation visibility QA বাকি |
| Standard PDF viewer-এ scanned PDF search | OCR text layer implementation যুক্ত; Build #69 compile/package সফল | বাস্তব PDF extraction, select/search ও Bengali rendering QA বাকি |
| Automated tests | test coverage এখনো পর্যাপ্ত নয় | বাকি |
| Performance / memory checks | Samsung Galaxy M12 বাস্তব QA হয়নি | বাকি |
| Final release signing / Source ZIP | final delivery প্রস্তুত নয় | বাকি |

## সর্বশেষ যাচাইকৃত পরিবর্তন
- Build #69 সফল; run 45948288495; Unit Tests, Debug/Release APK, APK verification ও artifact upload সফল।
- Build #66–#68-এর compile errors পরবর্তী commit-গুলোতে সংশোধিত; Build #69 consolidated branch state সফলভাবে যাচাই করেছে।
- Build #55 সফল; commit d0825def927e2b874dc92b4364c9284fcb28a98a.
- PDF Annotation/Highlight coordinate mapping CropBox origin-এ সংশোধিত; invalid/non-finite/out-of-page bounds validate হয়।
- CI success ফিচারের বাস্তব viewer compatibility বা physical-device QA-র বিকল্প নয়।
