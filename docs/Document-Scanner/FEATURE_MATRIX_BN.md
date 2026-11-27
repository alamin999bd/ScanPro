# Feature Matrix — Document Scanner

| ফিচার | বর্তমান নথিভুক্ত অবস্থা | QA অবস্থা |
|---|---|---|
| CameraX scan / multi-page capture | Source audit-এ আছে | বাস্তব ফোনে যাচাই বাকি |
| Gallery import | Source audit-এ আছে | বাস্তব ফোনে যাচাই বাকি |
| Auto edge detection / manual crop / perspective correction | Source audit-এ আছে; custom 4-point homography | geometry ও device QA বাকি |
| Image filters | Original, Enhance, B&W, Grayscale, Vivid; অতিরিক্ত AUTO/LIGHTEN/DARKEN/COLOR branch যোগ করা হয়েছে | visual QA বাকি |
| Offline Bangla + English OCR | Tesseract ben+eng; app database-এ text সংরক্ষণ ও app search | OCR accuracy QA বাকি |
| OCR hidden text layer in generated PDF | এখনো নেই | বাকি |
| PDF merge / extract / split / compress / lock / unlock / watermark / signature | source audit-এ আছে | functional QA বাকি |
| Delete / reorder / rotate PDF pages | engine, ViewModel ও UI action যোগ করা হয়েছে | functional QA বাকি |
| Add page numbers | engine, ViewModel ও UI action যোগ করা হয়েছে | functional QA বাকি |
| Search selectable PDF text | PDFTextStripper-ভিত্তিক search যোগ করা হয়েছে; image-only scan-এ কাজ করবে না | functional QA বাকি |
| Add PDF bookmark | PDF outline bookmark engine, ViewModel ও UI action যোগ করা হয়েছে; CI যাচাই চলমান | build/functional QA pending |
| Text annotation / highlight | এখনো সম্পন্ন নয় | বাকি |
| Standard PDF viewer-এ scanned PDF search | OCR text layer না থাকায় এখনো সমর্থিত নয় | বাকি |
| PDF split/extract pages | source audit-এ আছে | functional QA বাকি |
