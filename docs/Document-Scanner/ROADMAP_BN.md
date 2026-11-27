# Known Issues ও Roadmap — Document Scanner

## Known limitations
1. OCR ফলাফল অ্যাপের database-এ searchable; generated scan PDF-এ positioned invisible text layer এখনো embed করা হয় না।
2. Search PDF Text শুধু selectable/searchable text থাকা PDF-এ কাজ করে; image-only scanned page-এ নয়।
3. বর্তমান Compress PDF page rasterize করে; ফলে output copy-তে text selection/search হারায়।
4. PDF viewer Android PdfRenderer-এ page render করে; PDF content editing features আলাদা করে তৈরি করতে হবে।
5. PDF password support PDFBox implementation ও permission/owner restrictions দ্বারা সীমাবদ্ধ হতে পারে।
6. বাস্তব Samsung Galaxy M12 QA এখনো pending।

## Next development order
1. PDF bookmark — সদ্য যোগ করা; CI ও functional verification।
2. Text annotation — নতুন copy-তে annotation content যোগ।
3. Highlight — selectable text selection/geometry থাকলে highlight; image-only page-এ user-defined region selection প্রয়োজন।
4. Standard PDF outline navigation ও viewer UI verification।
5. Scanned PDF OCR text layer embedding — Bangla font positioning/Unicode extraction সহ।
6. Automated tests এবং memory/performance checks।
7. Real-device test, final APK signing verification, Source ZIP ও final report।

## Do not overclaim
কোনো feature শুধু menu item বা method থাকার কারণে "সম্পূর্ণ কার্যকর" বলা যাবে না। CI, automated test, sample PDF test এবং real-device test আলাদা করে report করতে হবে।
