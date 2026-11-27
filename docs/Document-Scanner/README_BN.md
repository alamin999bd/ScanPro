# Document Scanner — প্রকল্প নথি

এই ফোল্ডারটি Document Scanner Android অ্যাপের আলাদা ও কেন্দ্রীয় প্রকল্প রেকর্ড। Source code, build configuration এবং workflow repository-র মূল স্থানে থাকবে; এই ফোল্ডারে পরিকল্পনা, ফিচার তালিকা, build/test ইতিহাস ও সীমাবদ্ধতা রাখা হবে।

## পরিচিতি
- App: Document Scanner
- Repository: https://github.com/alamin999bd/ScanPro
- Working branch: document-scanner-premium
- Application ID: com.scanpro.pdfdocscanner
- Minimum Android: 8.0 (API 26)
- Compile SDK: 35
- সর্বশেষ পরিচিত version: 0.6.0 (versionCode 8; build পরিবর্তনে যাচাই করতে হবে)

## নথি
- [Feature Matrix](FEATURE_MATRIX_BN.md)
- [Build ও Test Log](BUILD_TEST_LOG_BN.md)
- [Known Issues ও Roadmap](ROADMAP_BN.md)

## গুরুত্বপূর্ণ নিয়ম
1. বিদ্যমান source audit না করে duplicate feature যোগ করা যাবে না।
2. মূল PDF অপরিবর্তিত রেখে edit করা হলে নতুন copy তৈরি করতে হবে।
3. CI build success-কে feature QA বা বাস্তব ফোনে পরীক্ষার প্রমাণ বলা যাবে না।
4. চূড়ান্ত APK এবং source ZIP প্রস্তুত না হওয়া পর্যন্ত পরীক্ষামূলক APK ব্যবহারকারীর কাছে পাঠানো হবে না।
5. Samsung Galaxy M12 (Android 13) বাস্তব ডিভাইস পরীক্ষা আলাদা করে নথিভুক্ত করতে হবে।
