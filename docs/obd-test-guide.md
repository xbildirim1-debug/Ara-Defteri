# Araç Defteri — OBD test sürümü

Bu sekme test aşamasındadır. Fotoğraftaki HH OBD Advanced adaptörün gerçek yonga, firmware ve protokol desteği fotoğraftan doğrulanamaz. Uygulama Bluetooth Classic SPP üzerinden ELM327 komutlarıyla çalışmak üzere hazırlandı. BLE ve Wi-Fi adaptörler bu bağlantıyla çalışmaz. Araç üzerinde test henüz yapılmadı.

## Hangi bilgiler okunuyor?

Bağlantıda ECU'nun desteklediği standart PID'ler sorgulanır. Desteklenmeyen değer sıfır olarak gösterilmez. Yenile düğmesi yeni ölçüm grubu ve okuma zamanı üretir; otomatik sürekli akış yoktur.

| Alan | Kapsam |
|---|---|
| Motor | Devir, hesaplanan yük, gaz kelebeği, ateşleme avansı, çalışma süresi |
| Sıcaklık | Soğutma suyu, emme havası, dış hava, motor yağı; araç desteklerse |
| Yakıt ve hava | Yakıt seviyesi, tüketim debisi, kısa/uzun yakıt düzeltmesi B1, hava kütle akışı, manifold/yakıt rayı/atmosfer basıncı, EGR isteği |
| Elektrik | Kontrol ünitesinin bildirdiği voltaj; akü sağlık testi değildir |
| Mesafe | Araç hızı, motor lambası açıkken mesafe, kod silmeden sonraki mesafe ayrı alanlardır |
| Toplam kilometre | Yalnız PID A6 desteklenir ve geçerli yanıt dönerse; diğer mesafelerden türetilmez |
| Arızalar | Kayıtlı (03), bekleyen (07), kalıcı (0A) kodlar ve MIL durumu |

Ölçümler anlık durumdur. Yakıt seviyesi veya debisinden satın alınmış yakıt kaydı oluşturulmaz. Araç bilgilerini güncelle bölümünde yalnız toplam kilometre aktarılabilir. İki dakikadan eski, çelişkili, geriye düşen veya araç seçimi değişmiş ölçüm aktarılmaz. Kullanıcı gösterge ve seçili aracı kontrol edip onaylar.

## Marka farkları

Standart kodların bir bölümü ortak tanımlıdır; üreticiye ayrılan kodlar için marka, model yılı, motor ve kontrol ünitesi gerekir. Kodun yalnız ilk harfinden kesin açıklama çıkarılmaz. P güç aktarma, B gövde, C şasi, U iletişim ailesidir; bir kodun biçimini çözebilmek ilgili modüle bağlanabilmek anlamına gelmez. Bu sürüm bilinmeyen kodun ham değerini gösterir ve açıklamayı doğrulanamadı olarak işaretler.

ABS/ESP, airbag, TPMS, şanzıman modülü, DPF, AdBlue, gerçek yağ basıncı, servis sıfırlama ve hibrit batarya sağlığı genellikle üreticiye özgü erişim gerektirir. Bu sürüm bu modülleri taramaz. VIN, freeze-frame, readiness ayrıntıları ve üreticiye özel sensör okuma bu sürümde uygulanmadı. Standart OBD'nin erişebildiği ECU'lar dışındaki arızalar görülmeyebilir.

## Motor ve yağ lambası

Bir DTC, kesin değişecek parçayı değil tespit edilen koşulu bildirir. Örneğin P0300 rastgele/çoklu teklemedir; ateşleme, yakıt, hava kaçağı veya kompresyon incelemesi gerekebilir. P0420 tek başına katalizör değişim emri değildir. P0521 yağ basıncı sensörü aralık/performans koşuludur; sensör/kablo sorunu ile gerçek basınç sorunu ölçümle ayrılır.

Motor çalışırken kırmızı yağ basıncı uyarısı varsa güvenli yerde durup motoru kapatmak gerekir. Yağ sıcaklığı normal veya DTC listesi boş diye basıncın normal olduğu söylenemez. Yağ seviyesi, basıncı, sıcaklığı ve bakım hatırlatıcısı farklıdır. Yanıp sönen motor lambası ve belirgin tekleme servis desteği gerektirir; standart MIL biti yanıp sönme durumunu güvenilir biçimde ayırmaz.

## Cihaz geldiğinde test

1. Araç park halinde, üreticinin adaptör/kontak talimatlarına göre eşleştirilir. Telefonda Bluetooth ve yakın cihaz izni verilir.
2. Doğru adaptör seçilir. Bağlantı, ECU desteği, desteklenmeyen değerler ve ölçüm zamanı kontrol edilir.
3. Devir/sıcaklık gibi alanlar bilinen bir okuyucuyla karşılaştırılır. Toplam km desteklenmiyorsa boş kalmalıdır.
4. Bilinen mevcut bir arıza varsa kod ve durum karşılaştırılır; arıza oluşturulmaz. Kod silme ve ECU'ya ayar yazma bu uygulamada yoktur.
5. Bağlantı kopması, uygulamadan çıkış ve yeniden bağlantı denenir. Eski verinin güncelmiş gibi aktarılmadığı kontrol edilir.

## Kaynaklar

- [ELM Electronics ELM327 veri sayfası](https://cdn.sparkfun.com/assets/learn_tutorials/8/3/ELM327DS.pdf): seri komutlar, destek maskesi, DTC okuma ve çoklu yanıtlar.
- [CSS Electronics OBD PID açıklamaları](https://www.csselectronics.com/pages/obd2-pid-table-on-board-diagnostics-j1979): standart veri sorgusu ve sensör dönüşümleri.
- [OBDLink üreticiye özel tanı kapsamı](https://support.obdlink.com/support/solutions/articles/43000705533-are-enhanced-diagnostics-available-for-my-vehicle-): marka/model/cihaz uyumluluğu.
- [OBDLink tanı açıklamaları](https://support.obdlink.com/support/solutions/articles/43000711154-get-started-with-diagnostics): kodların durumları ve tanı sınırları.
- [Ford kullanım kılavuzu — yağ basıncı uyarısı](https://www.fordservicecontent.com/Ford_Content/vdirsnet/OwnerManual/Home/Content?ProcUid=G2423131&Uid=G2466960&buildtype=web&countryCode=USA&div=f&languageCode=en&moidRef=G2421376&userMarket=GBR&vFilteringEnabled=False&variantid=10464).
- [OBDeleven P0521 açıklaması](https://obdeleven.com/p0521-engine-oil-pressure-sensor-switch-range-performance).

## Doğrulama sınırları

Saf Java regresyonları protokol çözümleme ve metin alan eşleşmelerini doğrular. Gerçek Bluetooth adaptörü ve ECU kullanılmadı. Ekspertiz örneği piksel verisiyle sınandı; yöntem tüm rapor tasarımlarını kapsamaz. OCR motorunun el yazısı başarısı yazı ve fotoğraf kalitesine bağlıdır; okunamayan alanlar elle kontrol edilmelidir. Paylaşılan yağ kartı boş olduğundan gerçek el yazısı örneğiyle test yapılmadı.
