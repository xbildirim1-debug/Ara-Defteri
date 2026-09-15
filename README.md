# Araç Defteri — Türkiye 0.9.0

Android 8 ve üzeri için cihazda çalışan araç günlüğü. İlk açılışta kullanıcı kendi aracını ekler; otomatik örnek kayıt oluşturulmaz. Mevcut sürümdeki kayıtlar veritabanı yükseltmesiyle ilk araca bağlı kalır.

## Kullanım

- **Garaj:** birden fazla araç, araç seçimi ve satılan araçların arşivi. Arşivdeki araçların bildirimleri durur; geçmişleri saklanır.
- **Kilometre:** araç kartındaki güncelleme düğmesiyle manuel giriş, kameradan veya galeriden OCR. Sonuç otomatik kaydedilmez; kullanıcı kontrol eder. Belirsiz sonuçlarda seçim, okunamayan görüntüde manuel giriş sunulur. Güncellemeler kaynak ve tarihle kaydedilir.
- **Türkiye kayıtları:** bakım ve servis, değişen parçalar, hasar ve kaporta durumu, ekspertiz, araç muayenesi/egzoz emisyon, MTV yılı/taksiti/ödeme durumu, trafik sigortası/kasko, yakıt/şarj, lastik ve diğer giderler.
- **Belgeler:** bir kayda birden fazla fotoğraf veya PDF; onarım öncesi/sonrası, fatura/makbuz ve poliçe/rapor etiketleri. Yeni eklenen dosyalar uygulamanın alanına kopyalanır.
- **Hatırlatmalar:** kullanıcının girdiği tarihten 7 ve 1 gün önce; cihaz açılışı, güncelleme ve saat dilimi değişiminden sonra yeniden planlama. Kilometre hedefi yeni kilometre girildiğinde kontrol edilir. Tamamlanan kayıt ve ödenen MTV için hatırlatma yapılmaz. Android pil ve bildirim ayarları teslim zamanını etkileyebilir.
- **Harcama:** aylık/yıllık toplam ve kategori dağılımı. Tüketim, aynı yakıt türünün tam depo aralıklarında, aradaki kısmi dolumlar dahil hesaplanır. Şarj kayıtlarında kWh ve tutar tutulur; pil doluluk verisi olmadığı için elektrik tüketimi tahmini gösterilmez.
- **Yedek:** Ayarlar üzerinden kayıtlar, bütün araçlar, fotoğraflar, belgeler ve tercihler ZIP dosyasına aktarılır. Geri yükleme mevcut verilerin yerini alır; arşiv ve ilişkiler önce doğrulanır, veritabanı işlemi transaction içinde yapılır. Fotoğraflar için 100 MB/dosya, yedek için 2 GB sınırı vardır. Eski sürümün dış dosya bağlantıları yedek sırasında kopyalanır; erişilemeyen dosya varsa yedek başarısız olarak bildirilir.
- **Araç CV:** geçmiş kayıtlarını seçme, en fazla 10 ek araç fotoğrafı, seçili kayıtların fotoğrafları, ekspertiz ve kaporta bilgileri, uzun metinlerde sayfa geçişi, PDF açma/paylaşma. Kayıtlara eklenen PDF raporların sayfaları CV'ye gömülmez. Şase/VIN alanı yoktur; plaka CV'ye yazılmaz. Telefon, açıklama, fiyat ve maliyetler isteğe bağlıdır.
- **Görünüm:** açık/koyu/sistem teması; fotoğraflı araç kartı, sade ana ekran ve ayrı kayıt sayfaları. Ana ekranda hızlı menü yoktur.

Hasar ve teknik bilgiler kullanıcı beyanı/katalog verisidir; resmî sorgu veya doğrulama sunulmaz. Vergi ve poliçe tarihleri kullanıcı tarafından girilir. Bulut hesabı, otomatik devlet sistemi sorgusu veya reklam SDK'sı bulunmaz.

## Derleme ve doğrulama

Java 17, Gradle 8.9, Android SDK 35. OCR modeli `com.google.mlkit:text-recognition:16.0.1` ile APK'ya dahildir.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
gradle :app:connectedDebugAndroidTest
```

GitHub Actions debug APK'yı ve test raporlarını saklar. Android testleri eski veritabanından yükseltmeyi, iki araç arasında veri ayrımını, dosyalı yedek/geri yüklemeyi, bozuk yedek reddini, ekran açılışını ve uzun PDF'yi doğrular. Ekran görüntüleri doğrulama çıktısına eklenir.
