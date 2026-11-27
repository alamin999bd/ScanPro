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
