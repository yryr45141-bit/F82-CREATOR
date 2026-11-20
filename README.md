# FF Creator — منصة فيديو مجتمع Free Fire

تطبيق أندرويد متكامل مبني بلغة **Kotlin** و **Jetpack Compose (Material 3)**، مخصص لمجتمع لاعبي وصنّاع محتوى **Free Fire** بتجربة عرض عمودية بملء الشاشة مستوحاة من TikTok، مع دعم كامل للغة العربية واتجاه **RTL** وهوية ألعاب ليلية بالأسود الداكن مع الأحمر النيون والبنفسجي الكهربائي.

---

## المزايا الرئيسية المُنفّذة

- **الصفحة الرئيسية (TikTok-Style Vertical Feed)**:
  - عرض فيديو واحد بملء الشاشة باستخدام `VerticalPager` مع تشغيل تلقائي وتحميل مسبق للفيديو التالي عبر `FeedPlayerPool` (Media3 ExoPlayer).
  - السحب لأعلى/لأسفل للتنقل، الضغط لإيقاف/تشغيل الفيديو، زر كتم/فتح الصوت، وعرض اسم المنشئ، الوصف، الهاشتاجات، وعدد المشاهدات.
- **التفاعل المباشر**:
  - الإعجاب وإلغاء الإعجاب، حفظ الفيديوهات في المفضلة، مشاركة الفيديو، متابعة وإلغاء متابعة المستخدمين مع تحديث الأعداد لحظياً.
  - التعليقات والردود المتداخلة، الإعجاب بالتعليقات، وتثبيت التعليقات من قِبل صاحب الفيديو أو المالك.
- **الحسابات والتسجيل (Firebase Auth + Credential Manager)**:
  - تسجيل الدخول عبر Google، إنشاء الملف الشخصي تلقائياً (`users/{uid}` مع عزل البيانات الحساسة في `users/{uid}/private/info`).
  - اسم مستخدم فريد، صورة شخصية، نبذة شخصية، تعديل الملف الشخصي، وتسجيل الخروج / إعادة المزامنة.
- **نشر الفيديوهات وضغطها**:
  - اختيار الفيديو وصورة الغلاف عبر **Android Photo Picker** (بدون طلب أذونات تخزين واسعة، متوافق مع سياسات Google Play).
  - ضغط فيديوهات MP4 محلياً بدقة 720p (H.264 / AAC) باستخدام **Media3 Transformer** لتقليل استهلاك البيانات.
  - إضافة وصف وهاشتاجات، أو نشر رابط MP4 مباشر، مع إمكانية حذف الفيديو من الملف الشخصي.
- **قسم اكتشف**:
  - البحث عن اللاعبين والفيديوهات، تصفح الهاشتاجات الرائجة، الفيديوهات الأكثر مشاهدة، والفيديوهات الرائجة.
- **الرسائل الخاصة (1-on-1 Chat)**:
  - محادثات فورية بين المستخدمين، عرض وقت الرسالة، إشعارات الرسائل الجديدة، وإمكانية حظر أو فك حظر المستخدمين.
- **نظام التوثيق والاشتراكات (Verification & VIP)**:
  - تقديم طلب توثيق الحساب ومتابعة حالته (قيد المراجعة / مقبول / مرفوض مع سبب الرفض) وعرض الشارة الزرقاء تلقائياً بعد موافقة المالك.
  - باقات VIP (الفضية / الذهبية / الألماسية الأسطورية) مع إطارات حساب متحركة وشارة VIP، تُفعّل فقط بعد موافقة المالك.
- **الإشعارات**:
  - سجل إشعارات فوري داخل التطبيق عند الإعجاب، التعليق، الرد، المتابعة، الإشارة `@mention`، الرسائل الجديدة، حالة التوثيق وVIP، وإعلانات الإدارة.
  - قناة إشعارات أندرويد محلية (`LocalNotificationHelper`) + خدمة `FfFirebaseMessagingService` لاستقبال إشعارات **FCM** وتحديث التوكن في المسار الخاص المحمي.
- **لوحة تحكم المالك (Admin Panel)**:
  - محمية بصلاحيات **Firebase Authentication** + **Firestore Security Rules** للبريد الموثّق `y27985332@gmail.com` فقط.
  - تشمل: إحصائيات التطبيق، إدارة المستخدمين (حظر/فك حظر)، مراجعة طلبات التوثيق وVIP (قبول أو رفض مع ذكر السبب)، حذف الفيديوهات المخالفة، مراجعة البلاغات، إدارة الهاشتاجات الرائجة، وإرسال إشعار عام لجميع المستخدمين.

---

## البنية التقنية (Architecture)

- **الواجهة**: Jetpack Compose + Material 3 + Navigation Compose (Type-safe `@Serializable` routes) + دعم كامل للأجهزة اللوحية (`NavigationRail` على الشاشات العريضة).
- **تشغيل الفيديو وضغطه**: `androidx.media3` (`media3-exoplayer`, `media3-ui-compose`, `media3-transformer`, `media3-effect`).
- **البيانات والمصادقة**: Firebase Authentication (Google Sign-In via Jetpack `CredentialManager`), Cloud Firestore Enterprise (قاعدة بيانات مسماة مضبوطة عبر `firebase_applet_config.xml`), Firebase Storage, Firebase Cloud Messaging.
- **حالة التطبيق**: MVVM (`FfMainViewModel`) مع `StateFlow` و `collectAsStateWithLifecycle`.

---

## إعداد Firebase في الإنتاج (خطوات لوحة تحكم Firebase)

تم تجهيز إعدادات المشروع وقواعد أمان **Firestore** و **Google Sign-In** مسبقاً. لتفعيل المزايا السحابية الإضافية في بيئة الإنتاج على حسابك في Firebase Console:

1. **Firebase Storage (لرفع الفيديوهات والصور سحابياً)**:
   - افتح **Storage** في Firebase Console وأنشئ Bucket للمشروع.
   - أضف قواعد الأمان المناسبة للسماح للمستخدمين المسجلين برفع فيديوهات MP4 والصور في مسارهم:
     ```
     rules_version = '2';
     service firebase.storage {
       match /b/{bucket}/o {
         match /videos/{userId}/{allPaths=**} {
           allow read: if true;
           allow write: if request.auth != null && request.auth.uid == userId
                        && request.resource.size < 100 * 1024 * 1024;
         }
         match /covers/{userId}/{allPaths=**} {
           allow read: if true;
           allow write: if request.auth != null && request.auth.uid == userId
                        && request.resource.size < 10 * 1024 * 1024;
         }
       }
     }
     ```
   - *ملاحظة*: في حال عدم تفعيل Storage Bucket بعد، يقوم التطبيق تلقائياً بحفظ الفيديو المضغوط محلياً لتجربة فورية دون توقف، أو استخدام رابط MP4 مباشر.

2. **Firebase Cloud Messaging (FCM) و Cloud Functions (للإشعارات الفورية عبر الأجهزة)**:
   - يتم حفظ توكن FCM الخاص بكل مستخدم تلقائياً في `users/{uid}/private/info`.
   - لتوصيل إشعارات Push بين الأجهزة عندما يكون التطبيق مغلقاً، انشر Cloud Function تستمع للإنشاء في:
     - `users/{userId}/notifications/{notificationId}`
     - `adminBroadcasts/{broadcastId}` (لإرسال Topic `ff_creator_all` عبر Admin SDK).
   - كما يشترك التطبيق تلقائياً في موضوع `ff_creator_all` عبر `FirebaseMessaging`.

3. **حساب المالك الأساسي**:
   - صلاحيات لوحة المالك (`AdminPanel`) مقيدة حصرياً بالحساب ذي البريد الإلكتروني الموثّق:
     `y27985332@gmail.com`

---

## بناء حزمة APK

للمعاينة أو التثبيت التجريبي (Debug APK):
```bash
gradle :app:assembleDebug
```
لبناء نسخة الإصدار (Release APK) بعد ضبط متغيرات التوقيع البيئية (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`):
```bash
gradle :app:assembleRelease
```
لاختبار قواعد أمان Firestore محلياً مقابل المحاكي:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```
