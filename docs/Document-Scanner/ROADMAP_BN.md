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
