# ساخت APK

## روش Android Studio
1. Android Studio و Android SDK را نصب کن.
2. پروژه را باز کن و Gradle Sync را انجام بده.
3. از مسیر Build > Build Bundle(s) / APK(s) > Build APK(s) فایل APK را بساز.
4. خروجی معمولاً در `app/build/outputs/apk/debug/app-debug.apk` قرار می‌گیرد.

## روش ساخت ابری با GitHub Actions
1. محتوای ZIP را در یک مخزن GitHub جدید بارگذاری کن.
2. از زبانه Actions، گردش‌کار `Build Android APK` را اجرا کن.
3. پس از پایان موفق، Artifact با نام `IranosTelegramManager-debug-apk` را دانلود کن.
4. APK خروجی، نسخه Debug است و برای نصب آزمایشی مناسب است؛ برای انتشار عمومی باید نسخه Release با کلید امضای اختصاصی ساخته شود.

توجه: در محیط فعلی Android SDK و Gradle نصب نیستند؛ بنابراین فایل APK در این مرحله کامپایل نشده است.
