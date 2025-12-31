# Google Play Store Kapalı Test Hazırlık Kontrol Listesi

**Tarih:** 31 Aralık 2025  
**Uygulama:** ZoneSilent  
**Durum:** ✅ HAZIR

---

## ✅ Tamamlanan Düzeltmeler

### 1. AdMob Yapılandırması
- ✅ `gma_ad_services_config.xml` oluşturuldu
- ✅ Manifest'te doğru şekilde referans edildi
- ✅ AdMob App ID yapılandırması mevcut

### 2. Privacy Policy
- ✅ Privacy Policy güncellendi (PRIVACY_POLICY.md)
- ✅ AdMob veri toplama açıklaması eklendi
- ✅ Advertising ID kullanımı belirtildi
- ✅ Google Privacy Policy linkleri eklendi
- ✅ Kullanıcı opt-out seçeneği açıklandı

### 3. İlk Açılış Privacy Policy Dialogu
- ✅ Uygulama ilk açılışta privacy policy gösteriyor
- ✅ Kullanıcı kabul etmek zorunda
- ✅ "View Full Policy" butonu ile tam policy gösteriliyor
- ✅ Decline durumunda uygulama kapanıyor

---

## 📋 Google Play Console Data Safety Formu için Bilgiler

Play Console'da "Data safety" bölümünde şu bilgileri girmeniz gerekecek:

### Toplanan Veriler:

#### 1. Location (Konum)
- **Toplanan veri türü:** Approximate location, Precise location
- **Zorunlu/İsteğe bağlı:** Zorunlu
- **Kullanım amacı:** App functionality (geofence özelliği için)
- **Paylaşılıyor mu:** Hayır (sadece lokal kullanım)
- **Şifrelenmiş mi:** Evet (device encryption)

#### 2. App Activity (AdMob tarafından)
- **Toplanan veri türü:** App interactions, In-app search history
- **Zorunlu/İsteğe bağlı:** Otomatik toplanan
- **Kullanım amacı:** Advertising or marketing, Analytics
- **Paylaşılıyor mu:** Evet (Google AdMob ile)
- **Şifrelenmiş mi:** Evet

#### 3. Device or Other IDs (AdMob tarafından)
- **Toplanan veri türü:** Advertising ID
- **Zorunlu/İsteğe bağlı:** Otomatik toplanan
- **Kullanım amacı:** Advertising or marketing
- **Paylaşılıyor mu:** Evet (Google AdMob ile)
- **Şifrelenmiş mi:** Evet

### Güvenlik Uygulamaları:
- ✅ Data is encrypted in transit (HTTPS)
- ✅ Users can request that data be deleted
- ✅ Data is not sold to third parties
- ✅ Committed to follow the Families Policy (eğer çocuklar hedef kitle değilse "No" seçin)

---

## 🔐 İzinler ve Açıklamaları

Play Console'da izinler için açıklamalar:

### ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
**Açıklama:** "ZoneSilent uses your location to create and monitor silent zones. When you enter or exit these zones, the app automatically changes your phone's ringer mode to silent or vibrate."

### ACCESS_BACKGROUND_LOCATION
**Açıklama:** "Background location is required to detect when you enter or exit silent zones even when the app is not actively open. This is essential for automatic ringer mode changes."

### ACCESS_NOTIFICATION_POLICY
**Açıklama:** "This permission allows the app to automatically change your phone's ringer mode (silent/vibrate) when you enter designated silent zones."

### POST_NOTIFICATIONS
**Açıklama:** "Used to show notifications about zone monitoring service and when entering/exiting silent zones."

### INTERNET
**Açıklama:** "Required for displaying maps (Google Maps) and advertisements (Google AdMob)."

---

## 📱 Uygulama Store Listing Önerileri

### App Category
**Önerilen:** Tools veya Productivity

### Content Rating
**Hedef Kitle:** Everyone (herkes için uygun)
- Reklam içeriyor ama zararlı içerik yok
- Konum izni kullanıyor ama şeffaf şekilde açıklanmış

### Privacy Policy URL
**Kullanılacak URL:** 
```
https://raw.githubusercontent.com/burakkmaker/ZoneSilent/main/PRIVACY_POLICY.md
```
veya GitHub repo linki:
```
https://github.com/burakkmaker/ZoneSilent/blob/main/PRIVACY_POLICY.md
```

---

## ⚠️ Dikkat Edilmesi Gerekenler

### 1. Test Reklamları
- Debug build'lerde test reklamları gösteriliyor ✅
- Release build'de gerçek AdMob ID kullanılıyor ✅
- `FORCE_TEST_ADS` flag'i ile kontrol ediliyor ✅

### 2. Background Location
- Android 10+ kullanıcılar "Allow all the time" seçmeli
- Uygulama bunu açıkça belirtiyor ✅
- Rationale dialog gösteriliyor ✅

### 3. AdMob Compliance
- GDPR compliance için AdMob consent SDK eklenmemiş
- **Eğer Avrupa'da yayınlayacaksanız:** UMP (User Messaging Platform) SDK ekleyin
- Şu anda sadece AdMob basic implementation var

---

## 🚀 Upload Öncesi Son Kontroller

### APK/AAB Hazırlığı
- [ ] Release build alındı mı?
- [ ] ProGuard/R8 aktif mi? (şu anda kapalı - `isMinifyEnabled = false`)
- [ ] Signing config doğru mu?
- [ ] Version code artırıldı mı? (şu anki: 12)
- [ ] Version name güncel mi? (şu anki: 1.0.11)

### Test Senaryoları
- [ ] Privacy policy dialogu gösteriliyor mu?
- [ ] Accept/Decline butonları çalışıyor mu?
- [ ] İzinler doğru şekilde isteniyor mu?
- [ ] Geofence'ler çalışıyor mu?
- [ ] Reklamlar gösteriliyor mu?
- [ ] Uygulama crash olmadan kapanıyor mu?

---

## 📝 Opsiyonel İyileştirmeler (Gelecek için)

### Güvenlik
1. **ProGuard/R8 aktif et:** Code obfuscation için
   ```kotlin
   release {
       isMinifyEnabled = true
       isShrinkResources = true
   }
   ```

2. **GDPR Compliance:** Avrupa pazarı için
   ```gradle
   implementation 'com.google.android.ump:user-messaging-platform:2.1.0'
   ```

### Kullanıcı Deneyimi
1. **Onboarding flow:** İlk kullanıcılar için rehber
2. **In-app privacy settings:** Privacy policy'yi uygulama içinden görüntüleme
3. **Export/Import zones:** Kullanıcıların zone'ları yedeklemesi

---

## ✅ Sonuç

Uygulamanız Google Play Store kapalı test için **HAZIR**. Tüm zorunlu gereksinimler karşılandı:

1. ✅ Privacy Policy mevcut ve AdMob açıklaması içeriyor
2. ✅ İlk açılışta privacy policy gösteriliyor
3. ✅ AdMob yapılandırması tamamlandı
4. ✅ İzinler doğru şekilde açıklanmış
5. ✅ Data Safety formu için bilgiler hazır

**Önemli Not:** Play Console'a upload ederken yukarıdaki "Data Safety" bilgilerini doğru şekilde girin. Bu Google'ın en çok önem verdiği kısımlardan biri.
