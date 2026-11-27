# Build ও Test Log — Document Scanner

## Build history
- Build #38 — run ID 45880933475 — commit b8f7b5440e1f24dbd8324673d237d239654fd40f — সফল। Debug/Release assemble, OCR data download, APK output verification ও artifact upload সম্পন্ন।
- Build #39 — PDF bookmark engine commit 2a97a1f527c8c3643a8039314e1b450772302fba — build status যাচাই চলমান।
- Build #40 — bookmark ViewModel commit 425406814b14065f817e3db104711249ed7de5c6 — build status যাচাই চলমান।
- Bookmark UI commit 5cde145594b8b2360e33ec973bbf137a2d8ee988 — Build #41 (run 45931150296) সফল।
- Text annotation engine commit 9a9a39178460606f90f4a0de5eb5f7c39533f053, ViewModel commit 1214d058e7ca246e7497844943bcf0703e51e572, UI commit 4188c52e27efee1246ed2a12f0a23eb15d1f1aa5 — Build #46–#48 চলছে; latest ফলাফল pending।

## Test limitations
- CI compilation সফল হওয়া মানে UI action বাস্তবে সঠিকভাবে কাজ করছে—এমন নয়।
- Samsung Galaxy M12 (SM-M127G, Android 13)-এ বাস্তব install ও interaction test এখনো সম্পন্ন বলে দাবি করা হচ্ছে না।
- OCR accuracy, PDF page rendering, PDF bookmark navigation, export/share এবং বড় PDF memory use বাস্তব test প্রয়োজন।

## Final delivery gate
চূড়ান্ত APK ও Source ZIP দেওয়ার আগে latest branch HEAD-এর CI সফল হতে হবে, artifact যাচাই করতে হবে, source ZIP প্রস্তুত করতে হবে এবং যতটুকু পরীক্ষা বাস্তবে করা হয়েছে তার সঠিক রিপোর্ট দিতে হবে।
