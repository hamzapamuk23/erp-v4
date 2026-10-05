# v4 ERP Platformu — Mimari ve Yol Haritası

| | |
|---|---|
| **Doküman** | v4 platform çekirdeği: mimari, teknoloji, yol haritası, "çekirdek tamam" tanımı |
| **Sürüm** | 1.1 (taslak). İki bağımsız inceleme (mimari; ERP alanı ve mevzuat) sonrası revize edildi. |
| **Tarih** | 2026-10-05 |
| **Durum** | İnceleme bekliyor. Kod yazımı bu doküman onaylanana kadar başlamaz. |
| **Okuyucu** | Ürün sahibi ve v4'ü geliştirecek ekip (Java/Spring ve Vue bilen, v1'i tanıyan geliştiriciler) |
| **Dayanak** | v1 (spring-services, smartiys-client ve çevre uygulamalar), v2 ve v3 kod incelemesi; Ekim 2026 itibarıyla teknoloji, lisans ve mevzuat araştırması (kaynaklar Ek B'de) |

---

## İçindekiler

0. [Yönetici özeti](#0-yönetici-özeti)
1. [Amaç, kapsam ve kalite hedefleri](#1-amaç-kapsam-ve-kalite-hedefleri)
2. [Mevcut sistemden dersler (v1–v3)](#2-mevcut-sistemden-dersler-v1v3)
3. [Temel ilkeler ve kırmızı çizgiler](#3-temel-ilkeler-ve-kırmızı-çizgiler)
4. [Dağıtım ve kiracılık (tenancy) modeli](#4-dağıtım-ve-kiracılık-tenancy-modeli)
5. [Mimari genel bakış](#5-mimari-genel-bakış)
6. [Platform bileşenleri](#6-platform-bileşenleri)
7. [Veri mimarisi ve konvansiyonlar](#7-veri-mimarisi-ve-konvansiyonlar)
8. [API tasarımı](#8-api-tasarımı)
9. [Frontend mimarisi](#9-frontend-mimarisi)
10. [Güvenlik, KVKK ve mevzuat hazırlığı](#10-güvenlik-kvkk-ve-mevzuat-hazırlığı)
11. [Operasyon ve dağıtım](#11-operasyon-ve-dağıtım)
12. [Teknoloji yığını](#12-teknoloji-yığını)
13. [Kalite ve test stratejisi](#13-kalite-ve-test-stratejisi)
14. [Repo ve kod organizasyonu](#14-repo-ve-kod-organizasyonu)
15. [Yol haritası](#15-yol-haritası)
16. ["Çekirdek tamam" tanımı ve sonrası](#16-çekirdek-tamam-tanımı-ve-sonrası)
17. [Üretim hattı süreci](#17-üretim-hattı-süreci)
18. [Karar kayıtları (ADR listesi)](#18-karar-kayıtları-adr-listesi)
19. [Riskler](#19-riskler)
20. [Açık sorular](#20-açık-sorular)
- [Ek A — Sözlük](#ek-a--sözlük)
- [Ek B — Kaynaklar](#ek-b--kaynaklar)

---

## 0. Yönetici özeti

1. **Ne inşa ediyoruz:** Sektörden bağımsız bir ERP çekirdeği. Üzerine iş modülleri (stok, ürün, satın alma, satış, üretim, muhasebe…) ve sektör paketleri (tekstil, gıda, beyaz eşya…) eklenecek. Çekirdek bitmeden hiçbir iş modülü yazılmaz.
2. **Tek ürün, iki kurulum şekli:** Tek kod tabanı ve tek imaj hem çok kiracılı SaaS olarak hem de müşteri sunucusunda tek kiracılı on-prem olarak çalışır. Müşteriye özel build ya da fork yoktur.
3. **Kiracı izolasyonu:** Her müşterinin ayrı bir PostgreSQL veritabanı vardır. Ayrıca bir *platform veritabanı* tutulur: tenant kaydı, oturumlar, işler, lisans ve herkese açık küresel referans veriler (para birimleri, TCMB kurları, GİB kod listeleri) burada durur.
4. **Mimari:** Spring Modulith ile **modüler monolit**. Mikroservis kullanılmaz. Bağımlılık yönü tektir: *bağımlı* modül *sağlayıcı* modülü kullanır (sektör paketi → iş modülü → platform), tersi asla. Modüller birbirleriyle yalnızca yayınlanmış API'ler, olaylar ve yayınlanmış okuma view'ları üzerinden konuşur. Bu kurallar build sırasında denetlenir.
5. **Çekirdeğin kalbi metadata motoru:**
   - Kaynak (resource) tanımları kodda, deklaratif YAML dosyalarında tutulur.
   - Müşteriye özel katmanlar (overlay) veritabanında tutulur ve çalışma zamanında birleştirilir.
   - CRUD motoru liste, filtre, form, yetki, audit, import ve export işlerini otomatik verir.
   - Frontend renderer da aynı metadata'dan ekran üretir.
   - Her noktada özel kod yazmak için kaçış yolu vardır.
6. **ERP'ye özgü ortak çekirdek (tek bir kez yazılır, hiçbir modül kendi versiyonunu icat etmez):**
   - Belge çatısı: birbirinden bağımsız durum eksenleri (yaşam döngüsü, onay, karşılanma, harici durum) ve üç düzeltme deseni (ters kayıt, yasal iptal, düzeltici belge).
   - Dört numaralandırma modu, dönem kilitleri, onay akışları.
   - Ticari primitifler: çok para birimli tutar modeli (işlem, yerel ve raporlama tutarı + kesinleştirmede sabitlenen kur), giriş/temel/ikincil miktarlı satır modeli, vergi ve fiyat için hesaplama hattı, analitik boyutlar, ledger ve taahhüt (rezervasyon) defteri.
   - Dosyalar, bildirimler, korumalı yazdırma, Excel import/export, entegrasyon (outbox, webhook, connector).
7. **Güncel ve lisansı güvenli yığın (Ekim 2026):**
   - Backend: Java 25 LTS, Spring Boot 4.1, Hibernate 7, jOOQ 3.21, PostgreSQL 18, Flyway, Keycloak 26.8 (Organizations), db-scheduler, JasperReports.
   - Frontend: Vue 3, Vite 8, Vuetify 4, AG Grid Community, TanStack Query.
   - PrimeVue 5 artık ücretli lisanslı olduğu için seçilmedi. jOOQ'nun açık kaynak sürümü PostgreSQL major sürümüne bağlı olduğu için ticari jOOQ lisansı bütçelenir (Faz 0 kararı).
8. **Kimlik doğrulama BFF desenine göre yapılır.** Tarayıcıda token tutulmaz, host'a bağlı HttpOnly oturum çerezi kullanılır. RFC 10017 (Ağustos 2026) iş uygulamaları için bu deseni "kesinlikle" öneriyor. Üç kimlik yolu vardır: tarayıcı oturumu, OIDC bearer token (mobil ve entegrasyon) ve uygulamanın ürettiği cihaz kimliği (kiosk, native kabuk, yerel ajan).
9. **v1'in ortaya çıkardığı gizli gereksinimler çekirdekte karşılanır:** saha cihazları (Android kiosk tabletler), yerel donanım ajanı (seri port, sessiz yazdırma), on-prem güncelleme ve kontrol düzlemi, e-İrsaliye, dış ERP entegrasyonu, vardiya ve etkin tarihli atamalar, kayıt bazında dosya, gerçek zamanlı bildirim.
10. **Yol haritası gerçekten dikeydir:** 9 faz (0–8). Faz 1 sonunda giriş yapan bir kullanıcı ana veri girer, satırlı bir belgeyi kesinleştirir ve bakiyeyi görür; sonraki fazlar bu dilimi derinleştirir. Gerçekçi süre 3 geliştiriciyle **15–20 ay**, kesme listesi uygulanırsa 12–14 ay. Çekirdek, §16'daki ölçülebilir kriterlerin hepsi sağlanınca tamam sayılır. Ondan sonra ilk modüller gelir: catalog → partners → trade-base → inventory + e-İrsaliye.

---

## 1. Amaç, kapsam ve kalite hedefleri

### 1.1 Vizyon: ERP üretim hattı

Farklı sektörlerdeki müşterilerin operasyonel ihtiyaçları adlandırma ve ayrıntıda farklılaşır. Teknoloji, mimari ve tasarım dili ise ortaktır. v4'ün amacı, gelen her talebi aşağıdaki akışla karşılayabilen bir üretim hattı kurmaktır:

```
Yeni talep
   │
   ├─ Mevcut modül karşılıyor mu? ──────────► Konfigürasyonla sat (kod yok)
   │
   ├─ Kısmen karşılıyor, genellenebilir mi? ─► Modülü/paketi genişlet → herkese yayınla
   │
   ├─ Yeni bir alan mı? ────────────────────► Aynı tasarım diliyle yeni modül (scaffold)
   │
   └─ Tamamen müşteriye özel mi? ──────────► Son çare: müşteri uzantısı (sadece açık uzatma noktaları)
```

Ayrıntı Bölüm 17'de.

### 1.2 Kapsam

**Çekirdeğin kapsamı:** tenant yönetimi, kimlik ve yetki, organizasyon yapısı, metadata ve CRUD motoru, ayarlar ve değer listeleri, belge çatısı, numaralandırma, dönemler, onaylar, ledger altyapısı, ticari çekirdek primitifleri (para birimi ve kur modeli, belge satırı modeli, hesaplama hattı, analitik boyutlar, taahhüt defteri), audit, dosyalar, bildirimler ve gerçek zamanlı iletişim, arka plan işleri, yazdırma, import/export, entegrasyon altyapısı, arama, referans veriler, cihaz kimliği, web shell, tasarım sistemi, renderer, paketleme, kurulum, güncelleme, yedekleme, lisans, gözlemlenebilirlik, scaffold ve geliştirici rehberi.

**Çekirdeğin kapsamı dışında kalanlar (sonraki modüller):** stok, ürün kataloğu, cari, vergi ve fiyat kuralları (`trade-base`), satın alma, satış, finans (kasa, banka, çek/senet), üretim, muhasebe, e-belge connector'ları, sektör paketleri, yerel donanım ajanının kendisi, native mobil kabuk, BI.

### 1.3 Kalite hedefleri (öncelik sırasıyla)

| # | Hedef | Anlamı | Ölçüt (Bölüm 16'da) |
|---|---|---|---|
| 1 | **Doğruluk** | Para, miktar ve stok asla yanlış hesaplanmaz. Yarım kalmış işlem olmaz. | BigDecimal, tek transaction, ledger testleri |
| 2 | **İzolasyon ve güvenlik** | Bir tenant başka bir tenant'ın verisini hiçbir yoldan göremez. | Otomatik izolasyon testleri (API, iş, olay, dosya, cache, SSE) |
| 3 | **Genişletilebilirlik** | Yeni sektör ihtiyacı çekirdeği değiştirmeden karşılanır. | Overlay, uzatma noktası, paket |
| 4 | **Yükseltilebilirlik** | Her müşteri aynı sürüm hattında kalır. Özelleştirmeler güncellemeyi kırmaz. | N-1 ve LTS atlamalı yükseltme testleri |
| 5 | **İşletilebilirlik** | On-prem kurulum ve güncelleme dakikalar sürer, uzman gerektirmez. | Temiz kurulum ≤ 30 dk |
| 6 | **Geliştirici hızı** | Ana veri ekranı 1 günde, belge tipi 3–5 günde yapılır. | Scaffold ve referans modül |
| 7 | **Performans** | 1 milyon satırlık listede filtreli sorgu hızlı döner. | p95 bütçeleri |

Çatışma olursa üstteki hedef kazanır. Örneğin performans için doğruluktan ödün verilmez.

---

## 2. Mevcut sistemden dersler (v1–v3)

Bu bölüm v1'in kodu okunarak hazırlandı. Rakamlar, inceleme sırasında doğrudan sayılarak elde edildi.

### 2.1 Korunacak fikirler

| Fikir | v1'deki karşılığı | v4'teki hali |
|---|---|---|
| Metadata'dan ekran üretmek | `@Metadata/@MetaColumn` → `MetaDataService` → `SmartDataTable`, `SmartDialogForm`, `PageTab*` | UI'dan bağımsız, overlay'li ve sunucu tarafında da uygulanan metadata motoru (§6.4) |
| Fabrika/şirket kavramı | `factory` kavramı | Organizasyon ağacı ve veri kapsamı (§6.3) |
| Keycloak/OIDC | Realm ve JWT | Keycloak Organizations + BFF (§6.3) |
| PostgreSQL + Flyway + modül başına şema | Servis başına şema | Tenant DB içinde modül başına şema (§7.1) |
| Jasper şablonları | `jrxml/` klasörleri | Tenant ve şirket bazında override edilebilir şablon deposu (§6.13) |
| i18n | vue-i18n sözlükleri | Backend ve frontend için ortak anahtar sistemi (§9.8) |
| Saha uygulamaları | Kiosk tabletler, TV panel, operatör ekranları | Cihaz kimliği ve kiosk arketipi (§6.18, §9.10) |

### 2.2 Tekrarlanmayacak hatalar

| # | v1'deki durum (kanıt) | Sonucu | v4 kuralı |
|---|---|---|---|
| H1 | 2186 Java dosyasının **yalnızca 12'sinde** `@Transactional` var. Stok fişi gibi çok adımlı kayıtlar frontend'den art arda HTTP çağrılarıyla yapılıyor. Çoklu beden girişi beden başına ayrı POST atıyor. | Yarım kalan kayıtlar, tutarsız stok | **K3:** İş işlemi tek bir sunucu komutu ve tek bir transaction'dır. İstemci süreç yönetmez. |
| H2 | **Hiçbir** entity'de `@Version` yok. | Aynı anda düzenlenen kayıtlarda son yazan kazanıyor | Her tabloda optimistic lock, API'de ETag/If-Match |
| H3 | Para ve miktar alanlarında **342 Float/Double alan, 0 BigDecimal** var. Prim (bonus) tutarları da Float. | Yuvarlama hataları | **K5:** `numeric` + Money/Quantity değer tipleri |
| H4 | Stok hareketinin çekirdek verisi (stok, miktar, depolar) `StockStorageTransactionData` jsonb kolonunda tutuluyor. Başka servisler bu jsonb'yi şema dışından SQL ile okuyor. | Bütünlük yok, raporlama zor | **K6:** Çekirdek veri gerçek kolonda tutulur. jsonb sadece özel alanlar içindir. |
| H5 | 25'ten fazla servis aynı PostgreSQL'i paylaşıyor ve birbirinin tablolarını `model/foreign/*` ile map'liyor (ör. `common.factory`). Bonus servisi `orders.*`, `person.*` ve `common.*` şemalarını doğrudan SQL ile okuyor. | Mikroservisin maliyeti var, faydası yok | **K1:** Modüller arası erişim sadece API, olay ve yayınlanmış view üzerinden |
| H6 | Stok servisinin içinde `DyeingPurchaseOrder*`, `KnittingPurchaseOrder*`, `defaultStockForBoyaliKumas` gibi tekstile özgü parçalar var. | Jenerik modül başka sektöre satılamıyor | **K2:** Jenerik modül sektör bilmez. Sektör, paket olarak eklenir. |
| H7 | Dyeing ve knitting servislerinde **146 sınıf adı ortak**. Bunların 54'ü adı değiştirilince birebir aynı (kopyala-yapıştır). Frontend'de de Dyeing ve Knitting satın alma ekranları jenerik akışın adı değiştirilmiş klonları. | Her hata 3 yerde düzeltilmeli | Ortak davranış platformda ya da jenerik modülde. Sektör farkı konfigürasyon veya uzatma noktası olur. |
| H8 | Her modül kendi durum ve parametre tablosunu kuruyor (`PurchaseOrderStatus`, `DyeingPurchaseOrderStatus`, `ReceiptAdviceStatus`…). Kod içinde `"A"`/`"K"`, `DIKIS/ORME/BOYA` gibi sihirli kodlar, uç nokta adlarında `getUHDURMASQuestions` gibi kategori kodları var. | Davranış veriye gömülü, kırılgan | Belge yaşam döngüsü platformda (§6.7). Değer listelerinde sistem kodları ile tenant kodları ayrılır (§6.6). |
| H9 | UUID'ler ve Integer'lar `==` ile karşılaştırılıyor (`OrderPhaseService`, `OrderProductionTransactionRuleService`, bakım planı kontrolü). Kalite metninde aynı alan üç kez kontrol ediliyor (kopyala-yapıştır hatası). | Sessiz mantık hataları | Statik analiz (Error Prone, ArchUnit kuralları) ve kod incelemesi CI'da zorunlu |
| H10 | 12 dosyada `@Scheduled` iş var ve **hiçbiri kilitli değil**. Vardiya takvimi her gece kilitsiz bir cron ile üretiliyor. Prim yeniden hesaplaması bir günün verisini transaction'sız silip yeniden yazıyor. | Çoklu instance'ta çift çalışma, veri kaybı | Kümede güvenli zamanlayıcı (§6.12). Toplu yeniden hesaplamalar transaction'lı, idempotent ve dönem kilidine saygılı. |
| H11 | Ay yeniden hesaplanınca elle girilen ek prim ve ceza sıfırlanıyor. Dönem kilidi yok. | Elle yapılan düzeltmeler kayboluyor | Dönem kilitleri ve "manuel düzeltme korunur" kuralı (§6.7) |
| H12 | `SmartHibernateInterceptor` singleton bir bean ama `crudType` değerini ortak bir alanda tutuyor. Reçetelerde `firstUser/changerUser` gibi elle tutulan audit alanları var. | Yanlış audit kayıtları | Hibernate Envers + iş audit günlüğü (§6.9) |
| H13 | Kod deposunda sırlar duruyor: CI dosyalarında kimlik bilgileri, `smart-update` içinde Telegram bot token'ı, frontend'de Cortex (dış ERP) kullanıcı adı ve şifresi, mobil uygulamada servis hesabı şifresi. | Ciddi güvenlik açığı | **K9:** Repoda sır yok. CI'da secret taraması. Entegrasyonlar sadece sunucu tarafında, sırlar şifreli kasada. |
| H14 | Operatör şifreleri tablete gönderiliyor ve istemci tarafında doğrulanıyor. `admin_password` bir parametre tablosundan okunuyor. Cihaz soket namespace'i kimliksiz çalışıyor ve cihaz listesini bellekte tutuyor. QR kodlarda ham UUID listeleri var. | Saha güvenliği yok | Cihaz kaydı ve token'ları, sunucu tarafında PIN/kart doğrulaması, imzalı kısa kodlar (§6.18) |
| H15 | Zaman damgaları cihaz saatinden alınıyor. Tarihler `Date` + `@JsonFormat(timezone="Asia/Istanbul")` ile tutuluyor. | Yanlış sıralama, saat dilimi hataları | Sunucu saati esastır. İş tarihleri `LocalDate`, olay zamanları `timestamptz` (§7.3). |
| H16 | Her müşteri için ayrı frontend build'i var. `.env` dosyalarından ikisi (mass ve yesiloz) bayt bayt aynı. | Sürüm ve dağıtım karmaşası | **K8:** Tek build. Müşteri farkı çalışma zamanında tenant konfigürasyonuyla gelir. |
| H17 | Jasper ve jsreport aynı anda kullanılıyor. Rapor kodu fabrika bazında parametreyle şablona enjekte ediliyor. | İki motorun bakım yükü | Tek motor (Jasper). Şablon metadata'sı şirket bazında tutulur (§6.13). |
| H18 | `smart-update` merkezi sunucudan soket üzerinden komut alıp müşteri sunucusunda Docker'ı uzaktan yönetiyor. | Uzaktan komut çalıştırma riski | Çekme (pull) temelli, imzalı, onaylı ve yedek alarak yapılan güncelleme (§11.2) |
| H19 | Toplam 20 test dosyası var. | Regresyon yakalanamıyor | Her fazın çıkış kriteri otomatik testtir (§13). |
| H20 | Spring Data REST entity'leri doğrudan dışarı açıyor. v3 demosu bile `findAll()` ile sayfalamasız liste dönüyor. | Sızan model, performans sorunu | API sözleşmesi DTO ve metadata ile tanımlanır. Liste her zaman sayfalı. |
| H21 | Seed verisinde belirli bir fabrikanın UUID'si koda gömülü. Bakım planları sadece içinde bulunulan yıla kadar üretiliyor. | Müşteriye özel veri kodda | Seed veriler tenant provisioning şablonundan gelir (§17.3). |

### 2.3 v1'in ortaya çıkardığı gizli gereksinimler

Aşağıdakiler başlangıçtaki planda yoktu, ama v1 müşterilerinin gerçekten ihtiyaç duyduğu şeyler. Çekirdek bunlara zemin hazırlamak zorunda:

| Gereksinim | v1'deki kanıt | Çekirdeğin sağlayacağı |
|---|---|---|
| **Saha cihazları** | 5 adet Android (Kotlin) kiosk tablet uygulaması (bakım, hat takibi, kalite kontrol, performans, üretim hareketi), WebView + JS köprüsü, TV paneli, cihaz filosu yönetim sayfası | Cihaz kaydı ve token'ları, operatör oturumu, kiosk sayfa arketipi, idempotent komutlar (§6.18, §8.5, §9.10) |
| **Sunucudan yönetilen saha arayüzü** | Kalite kontrol tableti için sunucudan konfigüre edilen "SmartButton" düğme panelleri (OB/HB/KB tipleri, hata ve foto diyalogları, tempo sayacı) | Metadata motorunun kiosk arketipini de kapsaması (§9.4) |
| **Yerel donanım ajanı** | `smart-serial`: Windows'ta paketlenmiş Node ajanı. Seri porttan örgü makinesi verisi okuyor, `SumatraPDF` ile sessiz yazdırıyor. Tarayıcı ajanı 3 saniyede bir yokluyor. Boyahane makine kontrolcüsüne (MSSQL prosedürü) doğrudan yazma denemesi yapılmış. | Cihaz kimliği ve protokolü çekirdekte. Ajanın kendisi sonraki bir modülde (§6.18). |
| **Kontrol düzlemi ve on-prem güncelleme** | `smart-update` ve CRM yığını: müşteri, fabrika, uygulama, port ve ortam değişkeni kayıtları; merkezi güncelleme bildirimi | Platform DB, lisans, sürüm kanalı, `erpctl` (§4.3, §11) |
| **e-İrsaliye / e-belge** | `gib-service`: GİB ve entegratör üzerinden irsaliye (despatch) | Belge çatısında harici durum alt-yaşam döngüsü, GİB numara formatı, connector altyapısı (§10.4) |
| **Dış ERP/muhasebe entegrasyonu** | "Cortex" entegrasyonu: tarayıcıdan, koda gömülü kimlik bilgisiyle sipariş aktarımı | Sunucu tarafı connector SPI, şifreli sır kasası (§6.15) |
| **Vardiya ve etkin tarihli atamalar** | Makine ve hat operatörlerinin vardiyaya göre 10 dakikada bir yeniden atanması, elle tutulan geçmiş tabloları, kıdem geçmişi | Kernel'de etkin tarih (valid_from/valid_to) desteği, takvim referans verisi (§7.6) |
| **Kayıt bazında dosya** | Her belge için Feign ile `rootFolderId` oluşturuluyor, ayrıca "Cloud" dosya yöneticisi var | Sahip kayda bağlı ek dosya servisi (§6.10) |
| **Gerçek zamanlı iletişim ve sohbet** | socket.io, bildirimler, sohbet bileşenleri | SSE tabanlı bildirim ve olay akışı (§6.11) |
| **Dashboard** | `vue-grid-layout` ile özelleştirilebilir paneller, KPI anlık görüntüleri | Dashboard arketipi, metadata ile widget tanımı (§9.4) |
| **Şirket bazında kontrollü doküman kodları** | Rapor çıktısında fabrika bazında `reportCode` | Yazdırma şablon metadata'sı (§6.13) |
| **Barkod/QR ve etiket** | Paket (bundle) QR'ları, iş emri barkodları | İmzalı kısa kod servisi, etiket şablonu (ZPL) altyapısı (§6.13) |
| **Kullanıcı, rol ve audit ekranları** | Kullanıcı/rol yönetimi, audit log görüntüleyici | Yerleşik yönetim ekranları (§6.3, §6.9) |

### 2.4 v2 ve v3 neden yetmedi

v2 (`smart-core-plugin` 2.0.0) ve v3 (`smart-platform`: Boot 3.5.3, Java 21) v1 çekirdeğinin aynı sınıflarla yeni sürümlere taşınmış halleri. `MetaDataService`, `SmartHibernateInterceptor`, `RealmService`, `SmartLogger`, `JasperService` hepsinde var. v3 starter'lara bölünmüş ve docker-compose ile PostgreSQL 17 + Keycloak 26.1 getirmiş. Bunlar doğru adımlar. Ama:

- Çok kiracılık, modül sınırı, jenerik CRUD motoru, belge çatısı ve frontend platformu yok.
- Metadata hâlâ entity üzerinde ve UI'a bağlı.
- Test yok (v3'te 0 test).
- Demo servis entity'yi sayfalamasız döndürüyor.
- docker-compose'da açık şifreler var.

**Sonuç:** v2 ve v3 *sürüm* yükseltmesiydi. v4 *kavram* değişikliğidir. v3'teki docker-compose ve Keycloak ayarları Faz 0'da referans olarak kullanılabilir. Kod taşınmaz.

---

## 3. Temel ilkeler ve kırmızı çizgiler

### 3.1 İlkeler

1. **Önce çekirdek, sonra modül.** Çekirdek bitmeden iş modülü yazılmaz. Platform da kurgusal ihtiyaçlarla şişirilmez: her özellik referans modülde kullanılarak kanıtlanır (§15.1).
2. **Konfigürasyon > uzatma > yeni kod.** Müşteri farkı önce metadata ve ayarlarla, sonra uzatma noktasıyla, en son yeni kodla karşılanır.
3. **Tek ürün hattı.** Her müşteri aynı sürüm hattındadır. Fork, müşteri dalı ve müşteriye özel build yoktur.
4. **Sunucu doğrunun kaynağıdır.** Doğrulama, yetki, hesaplama ve zaman damgası sunucuda yapılır. UI ve cihazlar sadece temsil ve giriş katmanıdır.
5. **Açık sözleşmeler.** Modül API'leri, uzatma noktaları, olaylar, metadata şeması ve REST API'si sürümlenir. Kırıcı değişiklik politikası vardır (§5.4).
6. **Lisansı güvenli bağımlılık.** Öncelik Apache-2.0, MIT ve LGPL bileşenlerde. Ticari lisansa geçebilecek bir kütüphane (PrimeVue örneği) kendi sarmalayıcı katmanımızın arkasında kullanılır.
7. **Sıkıcı teknoloji.** Kanıtlanmış, ekibin öğrenebileceği araçlar seçilir. Gerekmedikçe yeni altyapı bileşeni (Kafka, Redis, Kubernetes) eklenmez.

### 3.2 Kırmızı çizgiler (ihlal eden kod birleştirilmez)

| # | Kural | Nasıl denetlenir |
|---|---|---|
| K1 | Bir modül başka bir modülün tablosuna, entity'sine veya iç paketine erişmez. Sadece `api`, `spi`, olaylar ve yayınlanmış okuma view'ları kullanılır. | Spring Modulith verify, ArchUnit, jOOQ codegen kapsamı |
| K2 | Jenerik modül sektör kavramı içermez (boya, örgü, kumaş…). | Kod incelemesi, paket isim kuralları |
| K3 | Bir iş işlemi tek komut uç noktası ve tek DB transaction'ıdır. İstemci çok adımlı iş akışını yönetmez. | Mimari test: yazma servisleri `@Transactional` application katmanında |
| K4 | Kesinleşmiş (posted) belgenin ve ledger kaydının **içerik kolonları** (tutar, miktar, tarih, taraf, satırlar) değiştirilmez, silinmez. Düzeltme §6.7.3'teki desenlerle yapılır. Değişebilen durum alanları (harici durum, karşılanma, yazdırma sayısı) yan tablolarda tutulur. KVKK anonimleştirmesi audit'lenen ayrıcalıklı bir fonksiyonla yapılır. | DB trigger (kolon beyaz listesi) + domain kuralı + test |
| K5 | Para, miktar, oran ve kur için `float`/`double` kullanılmaz. Sadece `BigDecimal` ve `numeric` kullanılır. | ArchUnit kuralı (entity ve DTO alan tipleri) |
| K6 | Çekirdek iş verisi jsonb içinde tutulmaz. jsonb sadece tenant özel alanları ve sektör ek bilgisi içindir. | Şema incelemesi, metadata doğrulayıcı |
| K7 | Her tablo `@Version` (optimistic lock) taşır. Güncellemeler If-Match ister. | Kernel temel sınıfı + ArchUnit |
| K8 | Müşteriye özel build, dal veya fork yoktur. | Süreç |
| K9 | Repoda sır yoktur. Entegrasyon kimlik bilgileri şifreli saklanır ve sadece sunucu kullanır. | gitleaks (CI), kod incelemesi |
| K10 | Büyük/küçük harf dönüşümü `Locale.ROOT` ile yapılır. Türkçe arama normalizasyonu kernel fonksiyonuyla yapılır. | ArchUnit (`toUpperCase()` parametresiz yasak), Error Prone |
| K11 | Nesneler `==` ile karşılaştırılmaz (UUID, Integer, BigDecimal için `equals`/`compareTo`). | Error Prone |
| K12 | Zamanlanmış işler kümede güvenli zamanlayıcıyla, tenant bağlamı açıkça verilerek çalışır. `@Scheduled` yasaktır. | ArchUnit |
| K13 | Liste uç noktaları her zaman sayfalıdır. Sınırsız `findAll` yoktur. | ArchUnit + API testleri |
| K14 | Entity'ler API'de doğrudan serileştirilmez. | ArchUnit |
| K15 | Kişisel veri içeren alanlar metadata'da işaretlenir. Loglara kişisel veri yazılmaz. | Metadata doğrulayıcı, log maskeleme |

---

## 4. Dağıtım ve kiracılık (tenancy) modeli

### 4.1 Tek ürün, iki kurulum şekli

| | SaaS | On-prem |
|---|---|---|
| Kim işletir | Biz | Müşteri (ya da bizim adımıza müşteri sunucusunda) |
| Tenant sayısı | Çok | 1 (aynı mekanizmayla) |
| İmaj | Aynı | Aynı |
| Tenant kaynağı | Platform DB'deki tenant kaydı | Kurulumda oluşturulan tek tenant kaydı + lisans dosyası |
| Keycloak | Ortak realm + Organizations | Paketle gelen Keycloak, aynı realm yapısı, tek organization |
| Güncelleme | Bizim release train'imiz | `erpctl update` (pull, imzalı, onaylı) |
| Lisans | Platform DB (abonelik) | İmzalı lisans dosyası |

Çalışma modu tek bir ayarla belirlenir (`erp.deployment.mode=saas|onprem`). Bu ayar sadece tenant kaynağını ve bazı operasyon varsayılanlarını değiştirir. İş kodu modu bilmez.

### 4.2 Tenant izolasyonu: tenant başına veritabanı

**Karar (ADR-003):** Her tenant için aynı PostgreSQL sunucusunda ayrı bir veritabanı (`erp_t_<tenantKey>`) açılır.

| Seçenek | Artısı | Eksisi | Değerlendirme |
|---|---|---|---|
| **Tenant başına DB** | Tam izolasyon. Tenant bazında yedek, geri yükleme, dışa aktarma ve on-prem'e taşıma `pg_dump` ile yapılır. Tenant başına DDL (özel alan index'i) mümkün. Gürültülü komşu etkisi sınırlı. | Bağlantı havuzu sayısı artar. Migration her DB'de çalışır. Tenant'lar arası analiz zor. | **Seçildi.** Hedef ölçek onlarca, en fazla birkaç yüz B2B tenant. |
| Tenant başına şema | Tek havuz | `search_path` hataları sızıntı riski taşır. Yedek ve taşıma DB'deki kadar temiz değil. Modulith olay tablosu sorunu (§4.4). | Reddedildi |
| Ortak şema + `tenant_id` + RLS | Binlerce tenant'a ölçeklenir. Tek migration. | Bir sorgu hatası sızıntı demek. Tenant bazında restore ve on-prem'e taşıma zor. | Reddedildi. Hedef ölçek bunu gerektirmiyor. |

**Yeniden değerlendirme eşiği:** Tek bir PostgreSQL kümesinde 300'den fazla aktif tenant olursa ikinci bir küme açılır (DB-per-tenant korunur, tenant kaydı hangi kümede olduğunu tutar). Model değişmez, sadece yatayda çoğalır.

### 4.3 Platform veritabanı (`erp_platform`)

Tenant'tan bağımsız veriler burada tutulur:

| Tablo grubu | İçerik |
|---|---|
| `tenant` | Tenant anahtarı, adı, durumu (`PROVISIONING`, `ACTIVE`, `SUSPENDED`, `MAINTENANCE`, `ARCHIVED`), DB küme ve DB adı, saat dilimi, varsayılan dil, şema sürümü |
| `tenant_module` | Tenant'ta aktif modüller ve paketler, lisans kapsamı |
| `tenant_domain` | Alt alan adları (`acme.erp.com`) ↔ tenant ↔ Keycloak organization |
| `entitlement` / `license` | Abonelik ve lisans hakları (kullanıcı limiti, modüller, bitiş tarihi) |
| `spring_session*` | BFF oturumları (Spring Session JDBC) |
| `scheduled_tasks` | db-scheduler iş tablosu (her iş `tenantKey` taşır) |
| `migration_run` | Tenant bazında migration orkestrasyon durumu |
| `platform_audit` | Operatör işlemleri (tenant açma, askıya alma, impersonation izni) |
| `job_run` | İş geçmişi (tenant, tip, süre, sonuç, hata), saklama süreli |
| `global_refdata` | Herkese açık küresel referans veriler: para birimleri, TCMB kurları, GİB kod listeleri, UN/ECE birim kodları, GTİP, il/ilçe (§6.17) |

Tenant'a ait iş verisi asla platform DB'ye yazılmaz. Belgeler küresel referans verilere FK vermez, kodu ve gerekiyorsa değeri (kur gibi) kopyalayarak saklar.

### 4.4 Tenant çözümleme ve bağlam yayılımı

1. **Tarayıcı (BFF):**
   - İstek alt alan adına göre bir tenant'a eşlenir. Giriş, Keycloak'a `scope=organization:<alias>` ipucuyla başlatılır.
   - İpucu bir güvenlik sınırı değildir: dönen token'daki `organization` iddiası alt alan adının tenant'ıyla **eşleşmek zorundadır**, eşleşmezse giriş reddedilir.
   - Oturum `(kullanıcı, tenant)` çiftine bağlanır. **Her istekte** oturumun tenant'ı host'un tenant'ıyla karşılaştırılır.
   - Kullanıcı birden çok tenant'a üyeyse (mali müşavir gibi) her tenant ayrı bir oturumdur.
2. **OIDC bearer (mobil, entegrasyon):** Tenant, token'daki `issuer` ve `organization` iddiasından çözülür. Client credentials token'larında organization iddiası doğal olarak bulunmaz; bu yüzden her API istemcisine Keycloak'ta **sabit tenant iddiası eşleyicisi** (hardcoded claim mapper) tanımlanır. İddiası olmayan token reddedilir.
3. **Cihaz kimliği (kiosk, native kabuk, yerel ajan):** Uygulamanın ürettiği kimlik tenant'a bağlıdır (§6.18).
4. **Asenkron yürütme:** `TenantContext` her `TaskExecutor` için bir `TaskDecorator` ile taşınır. Bağlamı olmayan bir thread'de tenant DB'sine erişim **hata fırlatır**, varsayılan bir tenant yoktur.
5. **Olaylar (ADR-012):**
   - Her olay `tenantKey` taşır. Spring Modulith olay yayın kaydı tenant'ın kendi DB'sindedir.
   - Modulith'in çok kiracılık desteği yoktur. Bu yüzden Modulith'in **açılışta yeniden yayın ve şema oluşturma özellikleri kapatılır**; tenant bağlamı olmadan çalışıp hata verirler.
   - Yarım kalan yayınlar, tenant'ları dolaşan bir platform işiyle yeniden gönderilir.
   - Tamamlanan yayınlar silinir ya da arşivlenir (completion mode), tablo büyümez.
6. **İşler:** Her iş örneği `tenantKey` taşır. Çalışmadan önce bağlam kurulur, bitince temizlenir (§6.12).
7. **Transaction kuralı:** Tenant, transaction başlamadan önce belirlenir. Transaction içinde tenant değiştirmek yasaktır (koruma kodu ve test vardır).

### 4.5 Bağlantı yönetimi

- **Yönlendirme:** Tenant'a yönlendiren tek bir `DataSource` kullanılır. JPA, jOOQ ve Modulith aynı kaynaktan bağlantı alır. Hibernate multi-tenancy SPI'ı kullanılmaz, ikinci seviye cache kapalıdır (ADR-015).
- **Hibernate tuzakları (Faz 0'daki S1 spike'ında doğrulanır):**
  - Açılışta JDBC metadata erişimi kapatılır ve diyalekt açıkça verilir (`hibernate.boot.allow_jdbc_metadata_access=false`). Aksi halde açılış hangi tenant'a bağlanacağını bilemez.
  - Hibernate'in havuzlu sequence optimizer'ı kullanılmaz, çünkü bir tenant'ın sequence değerlerini başka bir tenant'a verebilir. Kimlikler zaten UUIDv7'dir; sequence modlu numaralandırma doğrudan `nextval` çağırır.
  - Hibernate filtreleri `find(id)` çağrısına uygulanmaz. Yazma tarafında veri kapsamı açıkça kontrol edilir (§7.7).
  - Aynı transaction içinde jOOQ ile okumadan önce JPA `flush` edilir (§7.7).
- **Havuzlar:**
  - Tenant başına HikariCP havuzu tembel açılır: `minIdle=0`, `max=4` (varsayılan), 5 dakika boşta kalan bağlantı kapanır.
  - Instance başına açık havuz sayısı sınırlıdır (ör. 150). Sınıra gelindiğinde en uzun süredir kullanılmayan havuz kapatılır (LRU).
- **Bağlantı tavanı şimdiden hesaplanır:** `instance sayısı × aynı anda aktif tenant × havuz üst sınırı`. Örnek: 2 instance × 100 aktif tenant × 4 = 800 bağlantı. PostgreSQL sunucusunun rahat taşıdığı eşik (ör. 500) aşılacaksa **yeni küme açılır** (§4.2). PgBouncer bu sorunu çözmez, çünkü o da veritabanı başına havuz tutar.
- **Platform DB'si** ayrı, yönlendirilmeyen bir `DataSource` kullanır. `LISTEN/NOTIFY` için instance başına havuz dışında tek bir bağlantı açılır.

### 4.6 Migration orkestrasyonu

- **Sahiplik:** Flyway migration'ları modül klasörlerindedir. Spring Modulith 2'nin modül bazında Flyway desteği tek bir DataSource üzerinde çalışır ve tenant'ları dolaşmaz. **Tenant orkestratörünü biz yazarız** (`tenancy` modülü).
- **Kapsam (ADR-010):**
  - Platform ve iş modüllerinin şemaları **tüm tenant'larda** kurulur. Aktivasyon çalışma zamanı kararıdır; modül açmak migration gerektirmez.
  - Sektör paketleri ve müşteri uzantıları **sadece aktif oldukları tenant'larda** kurulur. Kullanılmayan bir paketin bozuk migration'ı başka tenant'ları etkilemez, müşteri uzantısının tabloları başka müşterinin veritabanında oluşmaz.
  - Tek bilinçli şema farkı, tenant overlay'inin ürettiği özel alan index'leridir (§6.4.5). Bunlar Flyway'de değil, tenant metadata'sında kayıtlıdır ve orkestratör tarafından yönetilir.
- **Migration ayrı bir adımdır, açılışın yan etkisi değildir.**
  - On-prem'de `erpctl migrate`, SaaS'ta lider seçimli bir iş çalıştırır. Paralellik sınırlıdır (ör. aynı anda 4 tenant).
  - Her instance açılışta tüm tenant'ları migrate etmeye çalışmaz.
- **Şema sürüm aralığı:**
  - Her uygulama sürümü, modül başına kabul ettiği şema sürüm aralığını bildirir (`min..max`).
  - Rolling deploy sırasında eski instance'lar genişletilmiş (expand) yeni şemayı kendi aralığında gördüğü için çalışmaya devam eder.
  - Aralık dışındaki bir tenant'a istek kabul edilmez ve tenant `MAINTENANCE` durumuna alınır.
- **Hata yönetimi:** Durum `migration_run` tablosuna yazılır. Başarısız tenant bakım moduna geçer, diğerleri devam eder, uyarı üretilir.
- **Genişlet/daralt (expand/contract):** Bir sürümdeki migration önceki sürümün kodunu kırmaz. Kolon silme en erken bir sonraki sürümde yapılır.
- **Sıra kontrolü:** Migration sürümleri zaman damgalıdır (§7.9). Flyway'in `outOfOrder` seçeneği kapalıdır; CI, yeni bir migration'ın son yayınlanmış sürümden yeni olduğunu denetler.
- Sadece ileri giden migration yazılır. Geri dönüş, yükseltme öncesi otomatik alınan yedekle yapılır (§11.2).

### 4.7 Tenant yaşam döngüsü

| İşlem | Ne yapar |
|---|---|
| **Provision** | DB oluştur → migration'ları çalıştır → Keycloak organization ve ilk admin davetini oluştur → sektör şablonunu uygula (§17.3) → `ACTIVE`. Hedef ≤ 2 dakika. |
| **Suspend** | Girişler kapanır, veri korunur. |
| **Export** | `pg_dump` + dosya deposu + konfigürasyon paketi + **kullanıcı listesi** (e-posta, roller, kapsamlar; şifreler hariç). |
| **Taşıma (SaaS ↔ on-prem)** | Export → hedef kurulumda import → lisans. Kullanıcının kimliği `user_account.id`'dir; Keycloak `sub` değeri değiştirilebilir bir bağlantıdır. Hedef Keycloak'ta kullanıcılar davet edilir ve ilk girişte **e-posta ile yeniden bağlanır**. Mümkünse Keycloak'ın tam realm dışa aktarımı (şifre hash'leri dahil) kullanılır. |
| **Geri yükleme** | §4.7.1'deki el kitabıyla yapılır. |
| **Archive/Delete** | Yasal saklama süreleri (§10.5) ve yasal bekletme bayrağı dikkate alınır. Silme ancak sözleşme ve mevzuat izin verince yapılır. |

#### 4.7.1 Tenant geri yükleme el kitabı

Bir tenant'ı geçmiş bir ana döndürmek dış dünyayla tutarsızlık yaratır: GİB'e gönderilmiş fatura numaraları, tetiklenmiş webhook'lar, kuyruktaki işler. Bu yüzden geri yükleme şu sırayla yapılır:

1. Tenant `MAINTENANCE` durumuna alınır.
2. Yedek geri yüklenir.
3. Yarım kalmış olay yayınları **yeniden gönderilmez, karantinaya alınır**. Operatör tek tek inceler.
4. Boşluksuz sayaçlar dış kaynaklarla (e-belge entegratörünün son numarası, gönderilmiş belgeler) **mutabakata** sokulur. Gönderilmiş bir numara asla yeniden kullanılmaz.
5. Platform DB'sindeki o tenant'a ait işler temizlenir ya da yeniden planlanır.
6. Kontroller tamamlanınca tenant `ACTIVE` olur.

Kayıp penceresini küçültmek için on-prem'de de WAL arşivleme (pgBackRest ile PITR) varsayılan olarak açıktır (§11.4).

### 4.8 Tenant içinde çoklu şirket

- **Organizasyon ağacı:** Tenant → **Şirket** (tüzel kişilik: VKN, unvan, vergi dairesi, MERSİS, fonksiyonel para birimi, raporlama dövizi) → **Şube/Tesis** (fabrika) → **Departman**. Depo gibi birimler kendi modülleri tarafından bir organizasyon birimine bağlanır.
- **Kaynak kapsamı kodda bildirilir:**
  - `tenant`: tüm şirketlerce paylaşılır (ör. ölçü birimi).
  - `company`: `company_id` taşır ve otomatik filtrelenir (ör. belgeler).
  - `configurable`: paylaşım kararı tenant provisioning sırasında verilir ve **veri girildikten sonra kilitlenir**. Sonradan değiştirmek benzersizlik kurallarını bozar.
- **Şirkete bağlı alanlar:** Paylaşılan bir kaydın bazı alanları şirkete göre farklı olabilir (ör. ortak malzeme kartında şirket bazında maliyet ve muhasebe hesabı). Bunun için standart bir desen vardır: `<kaynak>_company` yan tablosu kullanılır, metadata'da `companyDependent: true` ile bildirilir ve motor aktif şirkete göre birleştirir. Bu, Odoo'nun `company_dependent` alanlarına ve Business Central'ın şirket bazlı tablolarına karşılık gelir.
- **Şirketler arası işlemler:** Şirketler aynı tenant DB'sinde olduğu için tek transaction'da birden çok şirkete yazılabilir. `CompanyContext` belge bazında açıkça verilir. Şirketler arası belge eşleme (bir şirketin satışı = diğerinin alışı) ve konsolidasyon sonraki modüllerin işidir.

---

## 5. Mimari genel bakış

### 5.1 Katmanlar

```
┌────────────────────────────────────────────────────────────────────────┐
│ MÜŞTERİ UZANTILARI (istisna)   customers/<müşteri>                       │  sadece açık uzatma noktaları
├────────────────────────────────────────────────────────────────────────┤
│ SEKTÖR PAKETLERİ               packs/textile · packs/food · ...          │  tenant'a göre açılır
├────────────────────────────────────────────────────────────────────────┤
│ İŞ MODÜLLERİ                   modules/catalog · partners · inventory ·  │  sektörden bağımsız
│                                purchasing · sales · production · ...     │
├────────────────────────────────────────────────────────────────────────┤
│ PLATFORM                       kernel · tenancy · identity · metadata ·  │  her şeyin zemini
│                                crud · documents · ledger · audit · files │
│                                messaging · jobs · printing · dataexchange│
│                                integration · refdata                     │
└────────────────────────────────────────────────────────────────────────┘
        Bağımlılık yönü: üstteki katman alttakini kullanır.
        Terimler: kullanan modül = BAĞIMLI, kullanılan modül = SAĞLAYICI (§5.4)
```

- **Platform** iş alanı bilmez. "Stok", "fatura" gibi kelimeler platform kodunda geçmez.
- **İş modülleri** platformu kullanır, birbirini sadece `api`/`spi`/olay/view üzerinden kullanır. Döngüsel bağımlılık yasaktır (Modulith verify). Aynı katmanda da yön tektir: `inventory`, `catalog`'un bağımlısıdır; `catalog` `inventory`'yi bilmez.
- **Sektör paketleri** modüllerin uzatma noktalarına bağlanır, kendi varlıklarını ve ekranlarını ekler.
- **Müşteri uzantıları** son çaredir (§5.5).

### 5.2 Çalışma zamanı görünümü

```
                    ┌──────────────────────────────── Sunucu / Küme ───────────────────────────────┐
 Tarayıcı ──HTTPS──►│ Ters proxy (Caddy / LB)                                                       │
 (Vue SPA)          │      │                                                                        │
                    │      ▼                                                                        │
 Mobil / Cihaz ────►│ ERP uygulaması (tek imaj, N instance)                                          │
 Entegrasyon ──────►│  ├─ SPA statik dosyaları                                                      │
                    │  ├─ BFF (oturum çerezi, OAuth2 client)  ──────► Keycloak (realm + Organizations)│
                    │  ├─ REST API (oturum ya da bearer)                                            │
                    │  ├─ SSE akışları                                                              │
                    │  ├─ db-scheduler işçileri                                                     │
                    │  └─ Modüller (platform + iş + paket)                                          │
                    │      │                    │                                                   │
                    │      ▼                    ▼                                                   │
                    │ PostgreSQL 18: erp_platform + erp_t_<tenant>…  + keycloak DB                  │
                    │ Dosya deposu: dosya sistemi (on-prem) / S3 uyumlu (SaaS)                       │
                    └───────────────────────────────────────────────────────────────────────────────┘
        ▲                                     │
        │ giden WebSocket (cihaz token'ı)     │ giden HTTPS (connector'lar)
 Yerel donanım ajanı (sonraki modül)     e-belge entegratörü, TCMB, pazaryerleri, dış muhasebe
```

- Uygulama durumsuzdur (oturumlar DB'de). Instance'lar yatayda çoğalır.
- Instance'lar arası yayın (SSE ve cache geçersizleştirme) platform DB'sinde PostgreSQL `LISTEN/NOTIFY` ile yapılır. Redis yoktur (ADR-016).

### 5.3 Modül anatomisi

Her modül (platform bileşeni, iş modülü veya paket) bir Maven modülü ve bir Spring Modulith uygulama modülüdür:

```
com.smart.erp.<modül>
 ├─ api/            Açık: facade arayüzleri, DTO record'ları, olaylar        (@NamedInterface, @Stable)
 ├─ spi/            Açık: bağımlı modüllerin (paketler dahil) uygulayacağı uzatma noktaları (@Stable / @Experimental)
 ├─ application/    İç: komutlar (use case), transaction sınırı, yetki kontrolü
 ├─ domain/         İç: entity'ler, aggregate'ler, domain servisleri, kurallar
 ├─ infra/          İç: repository'ler, jOOQ sorguları, dış adaptörler
 └─ web/            İç: özel REST uç noktaları (jenerik CRUD dışında kalanlar)

src/main/resources/
 ├─ db/migration/<modül>/V<sürüm>__<açıklama>.sql
 ├─ metadata/<modül>/*.resource.yaml      (kaynak tanımları)
 ├─ metadata/<modül>/*.codelist.yaml      (değer listeleri)
 ├─ metadata/<modül>/permissions.yaml     (izin kataloğu)
 ├─ i18n/<modül>/messages_tr.properties, messages_en.properties
 └─ print/<modül>/*.jrxml                 (varsayılan yazdırma şablonları)
```

- Yazma işlemleri sadece `application` katmanından geçer. Yetki kontrolü burada (method security) ve CRUD motorunda yapılır. Controller'daki kontrol tek başına yeterli sayılmaz.
- `domain` katmanı Spring'e minimum bağımlıdır ve birim testleriyle doğrulanır.
- Modül testleri `@ApplicationModuleTest(mode = DIRECT_DEPENDENCIES)` ile çalışır. Böylece sağlayıcı modüllerin migration'ları da kurulur ve modüller arası FK'lar test edilebilir.

### 5.4 Modüller arası iletişim ve sözleşmeler

**Terimler:** *Sağlayıcı* modül kullanılan, *bağımlı* modül kullanan taraftır. Örneğin `catalog` sağlayıcı, `inventory` bağımlıdır; `inventory` sağlayıcı, `packs/textile` bağımlıdır. Bağımlılık her zaman bağımlıdan sağlayıcıya doğrudur, döngü yasaktır. ArchUnit kuralları bu terimlerle yazılır.

| Mekanizma | Ne zaman | Kural |
|---|---|---|
| **Senkron API** (sağlayıcının `api` paketi) | Okuma ve doğrulama ("bu malzeme aktif mi?"), aynı transaction içinde gereken komutlar | DTO döner, entity dönmez. Çağrı aynı transaction'a katılabilir. |
| **Domain olayı** | Sağlayıcı "bir şey oldu" der, bağımlılar dinler (`StockDocumentPosted`) | Geçmiş zamanlı isim; `eventId`, `tenantKey`, `companyId`, `occurredAt` ve `schemaVersion` taşır. Yükü kimlikler ve asgari veridir. Dinleyiciler idempotent olur. |
| **Uzatma noktası (SPI)** | Bağımlı modül sağlayıcının davranışına katılır (ör. tekstil paketi, envanterin stok fişi kesinleştirmesine doğrulama ekler) | Arayüz sağlayıcının `spi` paketinde durur, bağımlı uygular, `@Order` ile sıralanır. Sadece tenant'ta aktif modüllerin katkıları çağrılır (§5.6). |
| **Yayınlanmış okuma view'ı** | Bağımlı modül, listede ya da raporda sağlayıcının verisini gösterir (ör. stok hareketinde malzeme adı) | Sağlayıcı `<şema>.v_<ad>` view'larını açık okuma sözleşmesi olarak yayınlar. View'lar **sadece sağlayıcının kendi tablolarını** okur (view üstüne view kurulmaz). View sadece kolon ekleyerek değiştirilebilir (`CREATE OR REPLACE VIEW`). Bağımlının jOOQ codegen'i sağlayıcının tablolarını değil, sadece view'larını görür. |

**Modüller arası foreign key (ADR-009):**
- Bağımlı modül, sağlayıcının tablosuna sadece birincil anahtar üzerinden FK verebilir (ör. `inventory.stock_move.item_id → catalog.item.id`). ERP'de bütünlük bu kısıttan daha değerlidir.
- JPA ilişkisi kurulmaz, sadece ID tutulur.
- Sağlayıcı bağımlıya asla referans vermez.
- Migration sırası bağımlılık grafiğine göre belirlenir.

**Kararlılık ve kırıcı değişiklik politikası** (Business Central ve SAP "clean core" deneyiminden alındı):
- `@Stable`, `@Experimental` ve `@Internal` anotasyonları kernel'de bulunur.
- Paketler ve müşteri uzantıları sadece `@Stable` API ve SPI'ları kullanabilir (ArchUnit).
- `@Stable` öğeler kaldırılmadan önce en az bir LTS boyunca `@Deprecated(forRemoval)` olarak kalır.
- CI'da japicmp önceki sürümle karşılaştırma yapar. İzinsiz kırıcı değişiklik build'i kırar.
- Metadata şeması, olay şemaları, view'lar ve REST API'si de aynı politikaya tabidir.

### 5.5 Genişletme modeli

**Genişletme merdiveni.** Bir ihtiyaç için en alttaki uygun basamak kullanılır, ancak yetmezse bir üste çıkılır:

| Basamak | Mekanizma | Örnek | Kod | Kim yapar |
|---|---|---|---|---|
| 1 | **Ayar / değer listesi** | Onay limiti, fire nedenleri | Hayır | Tenant admin |
| 2 | **Metadata overlay** | Etiket, gizle, zorunlu yap, sırala, varsayılan değer | Hayır | Tenant admin / danışman |
| 3 | **Özel alan** | Gıdada "menşe ülke", tekstilde "gramaj" | Hayır | Tenant admin / danışman |
| 4 | **Yazdırma şablonu override'ı** | Şirkete özel sipariş/irsaliye düzeni (e-belge görselleri XSLT'dir, §6.13) | Hayır (jrxml) | Danışman. SaaS'ta ürün ekibinin incelemesi ve imzası sonrası (§6.13). |
| 5 | **Sektör şablonu** (konfigürasyon paketi) | "Tekstil başlangıç konfigürasyonu" | Hayır | Ürün ekibi |
| 6 | **Uzatma noktası katkısı** (paket ya da modül) | Tekstilde fire oranı kuralı | Evet | Ürün ekibi |
| 7 | **Yeni varlık veya süreç** (paket ya da modül) | Boyahane iş emri, HACCP kaydı | Evet | Ürün ekibi |
| 8 | **Müşteri uzantısı** | Sadece o müşteriye ait, genellenemeyen kural | Evet | Ürün ekibi, istisna onayıyla |

**Müşteri uzantısı politikası:**

- `customers/<müşteri>` altında ayrı bir Maven modülü olur ve sadece `@Stable` SPI/API kullanır.
- Sadece o tenant'ta aktiftir.
- Her uzantının bir sahibi ve gerekçesi vardır (ADR). Yılda bir "ürüne alınabilir mi?" diye gözden geçirilir.
- Bir uzantının base kodu değiştirmesi yasaktır (Odoo'nun "her şeyi override et" kalıtım modeli, sürüm yükseltmelerini bu yüzden zorlaştırıyor).

### 5.6 Modül aktivasyonu ve modül durumları

Tüm kod tek imajda bulunduğu için "bu tenant'ta bu modül açık mı?" sorusu tek bir yerde cevaplanır (`ModuleActivation`).

**Modül durumları (tenant bazında):**

| Durum | Anlamı |
|---|---|
| `INACTIVE` | Modül kapalı. Paket ve uzantılarda şeması da yoktur (§4.6). |
| `ACTIVE` | Tam kullanım. |
| `RETIRED` | Lisans bitti ya da modül kapatıldı. Yeni belge oluşturulamaz; mevcut veriler okunabilir ve **daha önce kesinleştirilmiş belgeler ters çevrilebilir**. Böylece kapatılan bir paketin kesinleştirme katılımcısı devre dışı kaldığı için yarım ters kayıt oluşmaz. |

**Aktivasyon kancası:** Bir modül `ACTIVE` olduğunda bir backfill kancası çalışır (ör. mevcut kayıtlar için başlangıç verisi üretme). Modüller, aktivasyondan önceki olayları kaçırmış olduklarını varsayarak tasarlanır.

**Kapının uygulandığı yerler:**

| Alan | Etkisi |
|---|---|
| Menü ve ekranlar | Aktif olmayan modülün ekranları menüde görünmez, rotaları 404 döner |
| REST uç noktaları | Lisanssız modülün uç noktası 403 döner (filtre seviyesinde) |
| Uzatma noktası katkıları ve olay dinleyicileri | Dispatcher katkının hangi modüle ait olduğunu bilir (Modulith'ten, paket bazlı) ve sadece aktif modüllerinkini çağırır. Atlanan dinleyici olayı **tamamlandı olarak işaretler**; aksi halde yayın sonsuza dek yeniden gönderilir. |
| Metadata overlay'leri | Paket overlay'leri sadece paket aktifse uygulanır |
| İşler | Aktif olmayan modülün işleri o tenant için atlanır |

Bu kapı her geliştiricinin elle `if` yazmasına bırakılmaz; altyapıda uygulanır ve testlenir.

---

## 6. Platform bileşenleri

Her bileşen ayrı bir Maven modülüdür (`platform/<ad>`). Bu bölümde her biri için sorumluluk, temel kararlar ve sınırlar anlatılıyor. Ayarlar ve değer listeleri `metadata` modülünün, arama `crud` modülünün, cihaz kimliği `identity` modülünün parçasıdır.

### 6.1 `kernel`: temel yapı taşları

- **Temel entity** (`BaseEntity`):
  - `id` (UUIDv7, uygulama tarafında üretilir; böylece kayıt yazılmadan önce kimliği bilinir, olay ve idempotency işlerinde kullanılabilir)
  - `version` (optimistic lock)
  - `created_at`, `created_by`, `updated_at`, `updated_by` (sunucu saati, `timestamptz`)
  - Entity'lerde `equals/hashCode` sadece `id` üzerinden tanımlanır. Lombok'un `@Data`'sı entity'lerde yasaktır (v1'deki `@EqualsAndHashCode(callSuper)` kullanımı JPA'da hata kaynağıdır).
- **Değer tipleri** (Java record'ları; JPA embeddable ve Jackson serileştiricileriyle birlikte):
  - `Money(amount: BigDecimal, currency: CurrencyCode)`
  - `Quantity(value: BigDecimal, uom: UomCode)`
  - `Percentage`
  - `ExchangeRate(rate, from, to, date, type)`
  - `TaxId`: VKN/TCKN, algoritmik doğrulamayla
  - `Iban`, `Email`, `Phone` (E.164)
  - `DateRange`
  - Yuvarlama kuralları merkezi tanımlıdır: para birimine göre ölçek (TRY: 2), birim fiyat için 6 hane, miktar için birime göre ölçek.
- **Zaman:** Arayüz üzerinden enjekte edilen `Clock` (testte sabitlenir). İş tarihleri `LocalDate`, anlar `Instant` olarak tutulur. Tenant saat dilimi (varsayılan `Europe/Istanbul`) gün sınırlarını belirler.
- **Hata modeli:**
  - `BusinessException` + hata kodu kataloğu (`modul.hata-kodu`, i18n mesajı ile).
  - HTTP tarafında RFC 9457 `ProblemDetail` + alan hataları listesi.
- **Türkçe metin yardımcıları:**
  - `Locale.ROOT` ile büyük/küçük harf dönüşümü.
  - `TurkishText.fold()`: ı→i, İ→i, ş→s, ğ→g, ü→u, ö→o, ç→c ve küçük harf. Arama normalizasyonunda kullanılır.
  - ICU ile Türkçe sıralama karşılaştırıcısı.
- **Kararlılık anotasyonları:** `@Stable`, `@Experimental`, `@Internal`.
- **Etkin tarih desteği:** `EffectiveDated` arayüzü (`valid_from`, `valid_to`) ve PostgreSQL `daterange` + exclusion constraint ile çakışma önleme yardımcıları (§7.6).
- **Kimlik bağlamı:** `CurrentUser`, `TenantContext`, `CompanyContext`, `RequestContext` (izleme kimliği, istemci tipi, cihaz kimliği).

### 6.2 `tenancy`: kiracı yönetimi

Bölüm 4'teki kararların uygulamasıdır:

- Tenant kaydı ve durum makinesi
- Alan adı → tenant eşlemesi
- Tenant'a yönlendiren DataSource ve tembel havuzlar
- `TenantContext` yayılımı (HTTP, executor, olay, iş)
- Migration orkestratörü
- Provisioning hattı
- Modül aktivasyon kapısı (§5.6)
- Bakım modu
- Operatör API'si ve CLI komutları: `tenant create|suspend|export|import`

### 6.3 `identity`: kimlik, erişim ve organizasyon

#### 6.3.1 Kimlik doğrulama

| Konu | Karar |
|---|---|
| Kimlik sağlayıcı | **Keycloak 26.8**, tek iş realm'i + **Organizations** (ADR-005). Tenant = organization. |
| Neden Organizations | Realm-per-tenant yaklaşımı tek node'da yaklaşık 500 tenant'tan sonra yönetim düzleminde ciddi biçimde yavaşlıyor. 1000 tenant'lık bir ölçümde provisioning 59 dakika ile 84 saniye kıyaslanıyor, bellek 43 GB ile ~0,9 GB. Organizations aynı ölçekte düz kalıyor. Organization'a özel kurumsal IdP bağlama (Entra ID, ADFS, Google) ve alan adına göre yönlendirme destekleniyor; 26.8'de IdP'ler birden çok organization arasında paylaşılabiliyor. |
| Realm genelindeki ayarlar ve sonuçları | Kullanıcı adı ve e-posta realm genelinde benzersizdir. Şifre politikası, MFA ve kaba kuvvet koruması da realm geneldir. Bu yüzden: (1) Tenant adminleri kullanıcıyı **devre dışı bırakmaz**, sadece kendi organization'ından çıkarır; bir mali müşaviri devre dışı bırakmak onu tüm tenant'lardan atardı. Kullanıcıyı tamamen devre dışı bırakma yetkisi sadece platform operatöründedir. (2) E-postası olmayan saha çalışanları Keycloak hesabı almaz, operatör oturumu kullanır (§6.18). E-postasız bir ofis kullanıcısı gerekirse kullanıcı adı tenant önekiyle verilir (`acme.ahmet`). (3) Tenant'a özel daha sıkı şifre/MFA politikası isteyen müşteri, ayrı realm istisnasına girer. |
| Kurumsal dizin | SaaS'ta Active Directory'si olan müşteri ADFS ya da Entra ID üzerinden OIDC/SAML ile bağlanır (organization'a bağlı IdP). Doğrudan LDAP federasyonu realm seviyesindedir; **sadece on-prem'de** (tek tenant) ya da ayrı realm istisnasında kullanılır. Bu sınır satış öncesinde müşteriye açıkça söylenir. |
| Destek personeli | Ayrı bir **personel realm'inde** (`erp-staff`) tutulur. MFA zorunludur ve kimliğe bürünme öncesi adım-yukarı (step-up) doğrulama yapılır. Personel, müşteri realm'ine kullanıcı olarak eklenmez. |
| On-prem | Pakette aynı realm yapısı gelir, içinde tek organization vardır. Müşterinin Active Directory'si realm seviyesinde LDAP federasyonu ile bağlanır. |
| İstisna | Gerçek realm izolasyonu isteyen kurumsal bir SaaS müşterisi için ayrı realm açılabilir. Tenant çözümleyici `(issuer, organization) → tenant` eşlemesini kullandığı için kod değişmez. |
| Tarayıcı | **BFF** (ADR-006). Spring Security `oauth2Login` uygulamanın içinde çalışır. Oturumlar Spring Session JDBC ile platform DB'de tutulur. Çerez `__Host-` önekli, host'a bağlı, HttpOnly, Secure ve SameSite=Lax'tır. Alt alan adları aynı "site" sayıldığı için SameSite tenant'lar arasında koruma sağlamaz; asıl koruma host'a bağlı çerez ve her istekteki host–oturum tenant kontrolüdür (§4.4). Durum değiştiren isteklerde CSRF token'ı zorunludur. Tarayıcıya access ya da refresh token verilmez. RFC 10017 (Ağustos 2026) iş uygulamaları ve kişisel veri işleyen uygulamalar için bu deseni "kesinlikle" öneriyor. |
| Üç kimlik yolu | (1) Tarayıcı oturumu (BFF). (2) OIDC bearer: mobil uygulama ve entegrasyon API istemcileri (client credentials + sabit tenant iddiası, §4.4). (3) Cihaz kimliği: kiosk cihaz çerezi ya da uygulamanın ürettiği cihaz token'ı (§6.18). Her biri ayrı bir `SecurityFilterChain`'dir. |
| Kullanıcı kaydı | Uygulama her tenant DB'sinde kendi `user_account` kaydını tutar: profil, dil, rol ve kapsam atamaları. Kimlik `user_account.id`'dir; Keycloak `sub` değeri değiştirilebilir bir bağlantıdır (tenant taşımada e-posta ile yeniden bağlanır, §4.7). Keycloak sadece kimlik doğrulamadan sorumludur. Davet ve organization'dan çıkarma uygulamanın yönetim ekranından Keycloak Admin API ile yapılır. |
| IdP'den bağımsızlık | Uygulama sadece standart OIDC kullanır. Keycloak'a özgü API sadece provisioning adaptöründe bulunur. Gerekirse Spring Security 7'nin Authorization Server'ı ile değiştirilebilir (küçük on-prem ayak izi riski için yedek plan, §19). |

#### 6.3.2 Yetkilendirme modeli

- **İzin kataloğu:** Her modül izinlerini `permissions.yaml` ile bildirir: `<modül>.<kaynak>.<eylem>`, ör. `inventory.stock-document.post`. Alan grupları için ayrı izinler tanımlanabilir (ör. `catalog.item.field.cost:read`).
- **Rol:** Tenant'ın tanımladığı izin demetidir. Modüller hazır rol şablonları getirir (ör. "Depo sorumlusu").
- **Atama:** `kullanıcı × rol × kapsam`. Kapsam varsayılan olarak bir organizasyon birimleri kümesidir, ör. "Depo sorumlusu, sadece İstanbul tesisi". **Kapsam tipleri genişletilebilir (SPI):** modüller yeni tipler ekler, ör. satış temsilcisi → sadece kendi carileri, depo → sadece belirli depolar.
- **Uygulama noktaları:**
  1. `application` katmanında method security
  2. CRUD motorunda otomatik veri kapsamı: okumada jOOQ koşulu; yazmada yüklenen kaydın kapsamı açıkça kontrol edilir (Hibernate filtreleri `find(id)` çağrısına uygulanmaz)
  3. Alan seviyesi izinler: metadata'ya duyarlı serileştirme ve sorguya hiç dahil etmeme
  4. Belgeye özel kurallar: onay limitleri, "oluşturan onaylayamaz" seçeneği (görevler ayrılığı)
- **Destek amaçlı kimliğe bürünme (impersonation):** Tenant admini süreli izin verir. Destek personeli (personel realm'inden, adım-yukarı doğrulamayla) "X kullanıcısı olarak" işlem yapar, UI'da sürekli bir uyarı bandı görünür, her işlem çift kimlikle (gerçek kullanıcı + bürünülen kullanıcı) audit'e yazılır.
- **Otomatik izin testleri:** Her kaynak için "izinsiz erişim 403 döner" testi metadata'dan üretilir (§13).

#### 6.3.3 Organizasyon

Organizasyon birimi ağacı (§4.8) ve kullanıcının bağlam seçimi (aktif şirket ve tesis) bu modüldedir. Şirket bilgileri (VKN, vergi dairesi, adres, logo) yazdırma ve e-belge için temel kaynaktır.

### 6.4 `metadata`: metadata motoru

Çekirdeğin en kritik bileşeni. v1'deki fikrin doğru kurulmuş halidir.

#### 6.4.1 İlkeler

1. **UI'dan bağımsız.** Metadata ne gösterileceğini söyler, nasıl çizileceğini söylemez. Vuetify sınıfı, piksel değeri, bileşen adı içermez (istisna: kayıtlı özel widget anahtarı).
2. **Sunucuda da geçerli.** Zorunluluk, salt okunurluk, gizlilik ve alan izni API'de de uygulanır. Metadata sadece UI ipucu değildir.
3. **Kodda tanımlı, veritabanında özelleştirilir.** Temel tanım kod deposundadır (incelenir, sürümlenir, testlenir). Müşteri farkları veritabanındaki overlay'lerdedir.
4. **Tek şema.** Temel tanım ile overlay aynı JSON şemasına uyar. Overlay bir "yama"dır.

#### 6.4.2 Tanım biçimi (ADR-007)

Kaynaklar modülün `metadata/` klasöründe deklaratif **YAML** dosyaları olarak tanımlanır. Davranış (hesaplama, doğrulama, eylem) Java'da yazılır ve tanımda adıyla bağlanır.

```yaml
# metadata/catalog/item.resource.yaml  (örnek; nihai şema Faz 4'te kesinleşir)
resource: catalog.item
version: 1
entity: com.smart.erp.catalog.domain.Item
scope: tenant                 # tenant | company
extensible: true              # özel alan eklenebilir (ext jsonb kolonu)
searchable: [code, name]
display: "{code} - {name}"
fields:
  code:     { type: code, required: true, unique: true, maxLength: 40 }
  name:     { type: text, required: true, maxLength: 200, personalData: false }
  kind:     { type: enum, codeList: catalog.item-kind, required: true }
  baseUom:  { type: reference, resource: refdata.uom, required: true }
  cost:     { type: money, readPermission: catalog.item.field.cost:read }
  active:   { type: boolean, default: true }
views:
  list:
    columns: [code, name, kind, baseUom, active]
    defaultSort: [code]
    filters: [kind, active]
  form:
    sections:
      - key: general
        columns: 2
        fields: [code, name, kind, baseUom]
      - key: costing
        fields: [cost]
  detail:
    tabs: [form, attachments, history]
actions:
  deactivate: { handler: catalog.item.deactivate, permission: catalog.item.update, confirm: true }
hooks: [catalog.item.validation]
```

**Neden YAML (Java DSL değil)?**
- Temel tanım ve overlay aynı formatta olur, birleştirme basitleşir.
- İncelemesi ve diff'i kolaydır.
- Scaffold YAML üretir. İleride görsel bir overlay editörü aynı modeli düzenleyebilir.
- Frappe (DocType JSON) ve Axelor (XML) gibi başarılı metadata güdümlü sistemlerin tercihi de deklaratif dosyadır.

**Tip güvenliğindeki kayıp nasıl telafi edilir?** Build sırasında çalışan bir **metadata doğrulayıcı**, her tanımı JSON şemasına, JPA metamodeline, gerçek veritabanı şemasına (Testcontainers ile), izin kataloğuna ve hook kayıtlarına karşı denetler. Uyumsuzluk build'i kırar. Uygulama açılışında da aynı denetim tekrarlanır (fail-fast).

#### 6.4.3 Alan tipleri (anlamsal)

| Tip | Not |
|---|---|
| `text`, `longText`, `code` | `code`: büyük harf ve boşluk kuralı, benzersizlik |
| `integer`, `decimal`, `percentage` | Ölçek tanımlı |
| `money` | Para birimi alanına bağlı veya sabit para birimi |
| `quantity` | Birim alanına bağlı, birime göre ölçek |
| `date`, `dateTime`, `time`, `dateRange` | `date` = iş tarihi (saat dilimi yok) |
| `boolean` | |
| `enum` | Bir değer listesine bağlı (§6.6) |
| `reference` | Başka kaynağa referans. Görünen metin ve arama, hedef kaynağın lookup view'ından gelir. |
| `file`, `image` | Dosya servisine bağlı |
| `taxId`, `iban`, `email`, `phone`, `address` | Kernel değer tipleriyle doğrulanır |
| `computed` | Salt okunur, sunucuda hesaplanır |
| `children` | Alt koleksiyon (belge satırları gibi). Satır kaynağı ayrı bir tanımdır. |

#### 6.4.4 Overlay'ler

**Katman sırası:** kod (modül) → sektör paketi overlay'i (pakette dosya) → tenant overlay'i (DB). Kullanıcı kişiselleştirmeleri (kayıtlı görünüm, sütun sırası) overlay değildir, ayrı tutulur.

**Overlay'in yapabildikleri:**
- Etiket ve yardım metni (her dil için)
- Alanı gizleme (kod zorunlu kılmadıysa)
- Zorunlu kılma (sıkılaştırma)
- Salt okunur yapma
- Varsayılan değer
- Sıra ve bölüm yerleşimi
- Liste sütunları ve filtreler
- Basit doğrulamalar (desen, min/max)
- **Özel alan ekleme** (§6.4.5)

**Overlay'in yapamadıkları:** kodun koyduğu bir kısıtı gevşetmek (zorunluyu opsiyonel yapmak, uzunluk sınırını büyütmek), davranışı (hook, eylem) değiştirmek, script çalıştırmak.

**Kararlılık:** Overlay'ler alanları kararlı anahtarlarla referans verir. Kodda bir alan kaldırılır ya da yeniden adlandırılırsa, kodla birlikte bir **overlay migration** yazılır (doğrulayıcı bunu zorlar).

**Önbellek:** Birleştirilmiş tanım `(tenant, kaynak, sürüm, overlay sürümü)` anahtarıyla önbelleğe alınır (Caffeine) ve `LISTEN/NOTIFY` ile geçersizleştirilir. Bildirim kaybına karşı TTL ve sürüm kontrolü de vardır (§6.11). İstemciye ETag ile sunulur.

#### 6.4.5 Özel alanlar (ADR-008)

- `extensible: true` olan tablolarda `ext jsonb not null default '{}'` kolonu bulunur.
- Özel alan tanımı (tip, etiket, zorunluluk, değer listesi) tenant overlay'inde tutulur.
- Değerler sunucuda tipine göre doğrulanır.
- Liste, form, filtre, sıralama, export, import ve yazdırmada **otomatik** görünür.
- Admin bir alanı "indeksli" işaretlerse arka plan işi o tenant DB'sinde `CREATE INDEX CONCURRENTLY` ile ifade index'i oluşturur. Bu, tenant başına DB modelinin bir artısıdır. Bu index'ler bilinçli tek şema farkıdır; Flyway'de değil tenant metadata'sında kayıtlıdır ve orkestratör yönetir (§4.6). Büyük tablolarda index'siz özel alana göre sıralama UI'da kapatılır.
- **Neden gerçek kolon değil?** JPA ve jOOQ codegen dinamik kolonu bilemez. Tenant'lar arası şema farkı migration ve yükseltmeyi zorlaştırır. ERPNext'in gerçek kolon yaklaşımı MariaDB satır boyutu sınırlarına takılıyor. Odoo (Properties) ve Axelor (`attrs` json) jsonb tercih ediyor.
- **Neden EAV değil?** Sorgu ve rapor performansı kötü.

#### 6.4.6 Ayarlar ve değer listeleri

v1'deki onlarca parametre ve durum tablosunun yerini alır.

**Ayarlar:**
- Modüller ayarlarını tipli olarak tanımlar: tip, varsayılan, doğrulama, kapsam (`system`, `tenant`, `company`, `user`).
- Yönetim ekranı metadata'dan otomatik üretilir.
- Kod ayarı tip güvenli bir erişimciyle okur.

**Değer listeleri:**
- Modül tarafından tanımlanır.
- **Sistem kodları** davranışa bağlıdır, silinemez, sadece etiketleri overlay ile değiştirilebilir.
- **Tenant kodları** tenant'ın eklediği serbest değerlerdir (ör. arıza nedenleri).
- Belge durumları değer listesi **değildir**, belge yaşam döngüsünün parçasıdır (§6.7).

### 6.5 `crud`: jenerik kaynak motoru

Bir geliştirici sadece entity + YAML tanımı + (gerekirse) hook yazar. Motor şunları otomatik verir:

| Yetenek | Ayrıntı |
|---|---|
| Liste/sorgu | Filtre, sıralama, sayfalama (offset + toplam sayı; çok büyük tablolarda tahmini sayı), alan seçimi, referans görünen metinleri. Sorgu jOOQ ile metadata'dan üretilir. Veri kapsamı ve alan izinleri otomatik eklenir. |
| Tekil okuma | Alan izinleri uygulanmış DTO + ETag |
| Oluştur/güncelle | Metadata doğrulaması + hook zinciri + If-Match. JPA entity üzerinden, tek transaction'da. |
| Pasifleştir/sil | Kaynağın silme politikasına göre (§7.5) |
| Eylemler | `POST …/{id}/actions/{eylem}`: tanımdaki handler, izin ve onay kuralıyla |
| Export | CSV/XLSX, büyük veri asenkron iş olarak (§6.14) |
| Import | Şablon, doğrulama önizlemesi, toplu yazma (§6.14) |
| Geçmiş | Envers revizyonları (§6.9) |
| Ekler | Dosya servisi (§6.10) |
| Olaylar | `ResourceCreated/Updated/Deactivated` (genel) + modülün kendi domain olayları |
| Arama | `searchable` alanlardan türetilen `search_text` (Türkçe normalize) + `pg_trgm` GIN index'i. Global arama, izin filtrelemesiyle kaynaklar arasında çalışır. |

**Hook zinciri (SPI, `@Order` ile sıralı, aktif modül filtreli):**

```
beforeValidate → validate → beforeSave → [DB yazma] → afterSave (aynı TX) → afterCommit (TX sonrası, olay yoluyla)
```

**Kaçış yolları (zorunlu tasarım ilkesi):**
1. Özel uç nokta: `web` paketinde normal Spring controller'ı yazılır, motorun parçalarını (sorgu oluşturucu, yetki, serileştirici) kütüphane olarak kullanır.
2. Özel okuma modeli: bir kaynak JPA entity yerine bir jOOQ sorgu sağlayıcısı veya view ile tanımlanabilir (salt okunur raporlar).
3. Özel ekran: frontend tarafında sayfanın tamamı ya da bir bölgesi elle yazılmış bileşenle değiştirilebilir (§9.5).

"Motor buna izin vermiyor" diye iş kuralından ödün verilmez. Motor kolaylaştırıcıdır, hapishane değildir.

### 6.6 Değer listeleri ve ayarlar

§6.4.6'da anlatıldı. Fiziksel olarak `metadata` modülündedir.

### 6.7 `documents`: belge çatısı ve ticari çekirdek

Fiş, sipariş, irsaliye, fatura, iş emri, sayım gibi her "belge" bu çatıyı kullanır. v1'de her modül bunu kendisi icat etmişti. Bu bölüm ayrıca satın alma, satış, envanter ve e-belge modüllerinin ileride yeniden mimari gerektirmemesi için **ticari çekirdek primitiflerini** tanımlar (§6.7.7–§6.7.10).

**Terim:** *Kesinleştirme* (posting), belgenin hukuki ve mali etkisinin doğduğu andır: ledger yazılır, yasal numara verilir. "Kayıt" kelimesi bu dokümanda sadece veritabanı kaydı (record) anlamında kullanılır.

#### 6.7.1 Durum eksenleri

Bir belgenin durumu tek bir alan değildir, birbirinden bağımsız **dört eksenden** oluşur:

| Eksen | Değerler | Örnek |
|---|---|---|
| **Yaşam döngüsü** | `DRAFT` → `POSTED` → `REVERSED`; `DRAFT` → `CANCELLED` | Kesinleşmiş stok fişi |
| **Onay** | `NONE`, `PENDING`, `APPROVED`, `REJECTED` | Tutar eşiğini aşan satın alma siparişi |
| **Karşılanma** | `OPEN`, `PARTIAL`, `CLOSED`, `SHORT_CLOSED` | Kısmen sevk edilmiş sipariş |
| **Harici durum** | Profil bazında takılabilir durum makinesi | e-İrsaliye: gönderildi → alıcı yanıtı (kabul, kısmi kabul, ret) |

- Belge tipi hangi eksenleri kullanacağını konfigüre eder. Onay gerekmiyorsa onay ekseni `NONE` kalır.
- `POSTED` belgenin içerik kolonları değişmez (K4). Durum eksenlerinin değerleri yan tablolarda tutulur.
- `CANCELLED` sadece hiç kesinleşmemiş taslaklar içindir.
- **Harici durum makinesi** belge tipi ve profil bazında takılabilir (SPI). Örnek profiller (compliance-tr modülünde gelecek):
  - e-Fatura `TEMEL`
  - e-Fatura `TICARI`: alıcının 8 günlük kabul/ret süresi
  - e-Fatura `IHRACAT`
  - e-Arşiv: iptal bildirimi
  - e-İrsaliye: alıcının 7 gün içinde kabul, kısmi kabul ya da ret yanıtı; gönderen iptal edemez
  - HKS (gıda)
- Makine, gelen yanıtları da (alıcı yanıtı, GİB teknik reddi) işler. GİB'in teknik reddi (zarf hatası) belgeyi düzeltip yeniden göndermeyi gerektirir, ters kayıt gerektirmez.
- **Revizyon:** Kesinleştirilmeyen ama onaylanmış belgelerde (sipariş gibi) değişiklik yeni bir **revizyon** olarak yapılır. Önceki revizyon arşivlenir, bağlantılar yeni revizyona taşınır (sipariş revizyonu).

#### 6.7.2 Geçişler ve katılımcılar

- **Her geçiş bir komuttur** ve tek transaction'da çalışır. Sıra: izin kontrolü → koruma koşulları (guard) → dönem kontrolü → katılımcılar → numara alma (en son) → olay. Bu, v1'deki "istemci 6 ayrı istek atıyor" sorununu bitirir.
- **Katılımcılar (SPI) her geçişe bağlanabilir**, sadece kesinleştirmeye değil:
  - Sipariş onaylanınca taahhüt defterine rezervasyon yazılır, kapanınca serbest bırakılır.
  - Stok fişi kesinleşince envanter stok defterini yazar, tekstil paketi parti bilgisini işler.
  - Hepsi aynı transaction'dadır; biri başarısız olursa hepsi geri alınır.
- **Koruma SPI'ı:** Kesinleştirme öncesi doğrulamalar koruma olarak takılır. Örneğin compliance-tr'nin yerel UBL-TR şema ve schematron doğrulaması. Hatalı belge kesinleşmez.
- **Numara en son alınır:** Boşluksuz sayacın kilidi, katılımcıların ledger işleri bittikten sonra alınır ve sadece commit'e kadar tutulur. Böylece aynı serideki kesinleştirmeler birbirini gereksiz yere beklemez.

#### 6.7.3 Düzeltme desenleri

Türkiye pratiğinde "iptal" tek bir şey değildir. Çekirdek üç deseni ayrı ayrı destekler:

| Desen | Ne zaman | Nasıl |
|---|---|---|
| **Ters kayıt (storno)** | İç belgeler (stok fişi, iç transfer, sayım) | Bağlantılı bir ters kayıt belgesi oluşur, ledger etkileri ters çevrilir, asıl belge `REVERSED` olur. Ters kaydın tarihi asıl belgeninkinden farklı (sonraki açık dönem) olabilir. |
| **Yasal iptal** | Dış sistemin tanıdığı iptal (ör. e-Arşiv fatura iptali) | Harici durum ekseninde iptal, iç tarafta ters kayıt. Numara yeniden kullanılmaz. |
| **Düzeltici belge** | İptal edilemeyen ya da kabul edilmiş belgeler (ör. kabul edilmiş ticari e-Fatura, e-İrsaliye) | Yeni ve bağlantılı bir belge düzenlenir: iade faturası, fiyat farkı faturası, kur farkı faturası. Asıl belge değişmez. |

**Koruma kuralı:** Bağlı aşağı akış belgeleri olan bir belge (faturası kesilmiş irsaliye gibi), aşağı akış belgeleri düzeltilmeden ters çevrilemez.

#### 6.7.4 Numaralandırma

| Mod | Kullanım | Uygulama |
|---|---|---|
| **Boşluksuz** | Yasal belgeler (fatura, irsaliye, e-belge) | Sayaç satırı `SELECT … FOR UPDATE` ile kilitlenir. Numara **kesinleştirme anında, geçişin son adımında** verilir. Taslak iptal edilirse numara harcanmaz. |
| **Dizi (sequence)** | İç belgeler, yüksek hacim, taslak referans numarası | PostgreSQL `nextval` doğrudan çağrılır (Hibernate optimizer'ı yok). Boşluk olabilir. |
| **Harici** | Numarayı dış sistem veriyorsa (bazı entegratör senaryoları) | Numara alanını entegrasyon doldurur. |
| **Ertelenmiş toplu** | Dönem içinde tarih sırasıyla verilmesi gereken numaralar (e-Defter yevmiye madde numarası gibi) | Kesinleştirmede verilmez. Dönem kapanışında (ya da talep üzerine) belge tarihine göre sıralanıp toplu verilir, sonra kilitlenir. Bu yüzden geriye tarihli kesinleştirmeler sorun yaratmaz. |

- **Her belgenin bir taslak referans numarası vardır** (dizi modunda). Yasal numara ayrıdır ve kesinleştirmede verilir.
- **Seri tanımı:** belge tipi × şirket × (opsiyonel) tesis × yıl × desen. Desendeki yıl **belge tarihinin takvim yılıdır**, özel hesap dönemi kullanan şirketlerde bile. GİB formatı: `{SERI:3}{YIL:4}{SIRA:9}` → `ABC2026000000001` (16 karakter).
- **Tarih monotonluğu:** Yasal serilerde yeni numaralanan belgenin tarihi, serideki son numaralı belgenin tarihinden geride olamaz. Bu yüzden boşluksuz serilerde geriye tarihli kesinleştirme seri ayarıyla engellenir.
- **Seri seçim SPI'ı:** Hangi serinin kullanılacağı çalışma zamanında belirlenebilir. Örneğin alıcı GİB'de e-Fatura mükellefiyse e-Fatura serisi, değilse e-Arşiv serisi kullanılır (compliance-tr sağlar).
- Business Central ve Odoo da aynı ikiliyi kullanıyor: boşluksuz ama yavaş, boşluklu ama hızlı.

#### 6.7.5 Dönemler

- Şirket bazında mali takvim tutulur: yıl ve dönemler.
- Her modül için ayrı dönem durumu vardır: `OPEN`, `SOFT_CLOSED` (sadece yetkili rol kesinleştirebilir) ve `CLOSED`.
- Her kesinleştirmede kesinleştirme tarihi (posting date) dönem durumuna karşı kontrol edilir.
- **Açılış dönemi:** Sisteme geçişte açılış bakiyeleri, kapalı dönemler olsa bile ayrıcalıklı bir açılış süreciyle ve dış numaralarıyla girilir (§6.14).
- **Yeniden hesaplama kuralı:** Toplu yeniden hesaplamalar kapalı döneme dokunmaz. Elle girilmiş düzeltmeleri ayrı kayıt olarak korur (v1'deki prim sorunu, H11).

#### 6.7.6 Onaylar

- **Onay politikası:** belge tipi + koşullar (tutar eşiği, şirket, tip, özel alan) → adımlar (rol veya kullanıcı; "biri yeter" ya da "hepsi gerekli"; sıralı ya da paralel).
- Vekalet, zaman aşımında yükseltme ve "oluşturan onaylayamaz" seçeneği vardır.
- Onay zaman çizelgesi belgede görünür.
- Bildirim gönderilir. Mobil ya da e-posta bağlantısıyla güvenli onay yapılabilir (tek kullanımlık, süreli, oturum gerektiren bağlantı).
- BPMN motoru kullanılmaz (ADR-019). İhtiyaç bunun ötesine geçerse ayrı bir ADR açılır.

#### 6.7.7 Belge satırı modeli ve bağlantılar (ADR-034)

Satırlar her modülde yeniden icat edilmez. Standart satır sözleşmesi:

| Alan grubu | İçerik |
|---|---|
| Kalem | Kaynak referansı (malzeme, hizmet, masraf…), açıklama |
| Miktar | **Girilen miktar + girilen birim**; **temel miktar** (temel birime çevrilmiş, dönüşüm katsayısı kesinleştirmede sabitlenir); opsiyonel **ikincil miktar + birim** (ör. kumaşta metre ve kg, gıdada adet ve kg) |
| Fiyat ve tutar | Birim fiyat, iskontolar, vergi satırları (alt koleksiyon), satır tutarları (§6.7.8'deki üç para birimiyle) |
| Boyutlar | Analitik boyut seti (§6.7.10) |
| Bağlantı | Kaynak satır referansları |

- **Belge bağlantıları satır bazında ve çoka-çoktur:** `document_line_link(source_line, target_line, quantity, link_type)`.
- Örneğin toplu faturalamada bir fatura birden çok irsaliyenin satırlarını karşılar; UBL'de her irsaliyeye referans verilir.
- Karşılanma ekseni (§6.7.1) bu bağlantılardan hesaplanır.
- "Kısa kapat" (short-close) eylemi kalan miktarı iptal eder.

#### 6.7.8 Para birimi ve kur modeli (ADR-031)

Tutar taşıyan her başlık, satır ve ledger kaydı şunları saklar:

| Alan | Açıklama |
|---|---|
| İşlem tutarı + para birimi | Belgenin para birimi (ör. USD) |
| Yerel tutar | Şirketin fonksiyonel para birimi (genelde TRY) |
| Raporlama tutarı + para birimi (opsiyonel) | Şirket ayarı (ör. ihracatçı tekstil firmasında EUR) |
| Kur bilgisi | Kur değeri, kur tipi, kur tarihi ve kaynağı (TCMB, sözleşme, elle) |

- Kur ve karşılık tutarlar **kesinleştirmede sabitlenir**. Sonradan değişen kur geçmiş belgeyi değiştirmez.
- **Kur seçme kuralı** belge tipine göre konfigüre edilir. Fatura için varsayılan: sözleşmede kur yoksa, belge tarihinden bir önceki iş günü TCMB'nin saat 15:30'da ilan ettiği döviz alış kuru. Tatil ve hafta sonunda son ilan edilen kur kullanılır. Kesin kural mali müşavirle Faz 5'te teyit edilir.
- **Kur hassasiyeti:** `numeric(24,10)` + birim çarpanı (TCMB bazı dövizleri, ör. JPY, 100 birim için ilan eder). Ters kurlar (TRY→USD) hassasiyet kaybetmez.
- **Kur farkı** sadece değer taşıyan (miktarsız) bir ledger kaydı olarak modellenir. Kur farkı faturası bir düzeltici belgedir (§6.7.3).
- **Enflasyon düzeltmesi** 2025–2027 için askıya alındı (VUK geçici 37. madde) ama geri gelebilir. Fiyat endeksleri referans verilerde tutulur (§6.17).

#### 6.7.9 Hesaplama hattı: vergi, fiyat, yuvarlama (ADR-035)

Satın alma ve satış modülleri KDV, tevkifat, ÖTV ve zincir iskontoyu ayrı ayrı yazarsa v1'deki kopyala-yapıştır sorunu (H7) tekrarlanır. Bu yüzden:

- **Hesaplama hattı:** Çekirdek sıralı bir hesaplama hattı SPI'ı sağlar, adımları modüller katkı olarak ekler. Tipik sıra: fiyat → zincir iskonto (10+5+2) → ÖTV → KDV matrahı → KDV → tevkifat → yuvarlama → toplamlar. Sıra önemlidir, çünkü ÖTV KDV matrahına girer.
- **Yuvarlama politikası** çekirdekte tanımlıdır. Tutarlar para biriminin ölçeğinde saklanır (TRY için 2 hane). Varsayılan kural: önce satır yuvarlanır, toplam yuvarlanmış satırlardan hesaplanır (UBL-TR doğrulamasıyla uyumlu). Belge tipi bazında değiştirilebilir.
- **Vergi satırları** satırın alt koleksiyonudur: vergi tipi, matrah, oran, tutar, istisna veya tevkifat kodu.
- **Simülasyon uç noktası:** Taslak belge kaydedilmeden `POST …/simulate` ile hesaplanır. UI toplamları bu uç noktadan alır (debounce ile). Sunucu tek doğru kaynaktır; istemci sadece önizleme yapabilir.
- **Türkiye'ye özgü vergi kuralları ve fiyat listeleri** `trade-base` modülündedir ve satın alma ile satıştan **önce** yazılır (§16.2). Çekirdek sadece hattı, yuvarlama politikasını ve vergi satırı modelini verir.

#### 6.7.10 Analitik boyutlar (ADR-036)

- Tenant analitik boyutlar tanımlayabilir: masraf merkezi, proje, bölge, satış kanalı vb.
- Satırlar bir **boyut seti** taşır. Boyutlar satırdan ledger'a taşınır, raporlar boyutlara göre kırılır.
- Organizasyon birimleri (şirket, tesis) zorunlu boyutlardır, diğerleri tenant tanımlıdır.

### 6.8 `ledger`: defter altyapısı (ADR-037)

Stok, cari hesap, muhasebe, prim ve kapasite gibi "hareket + bakiye" yapıları için ortak araç takımı. Business Central'daki miktar defteri ile değer defterinin ayrılması ve Frappe'nin geriye tarihli kayıt (repost) maliyeti incelenerek tasarlandı.

- **Ledger kaydı sözleşmesi:**
  - Sadece eklenir (append-only). DB seviyesinde içerik kolonlarında `UPDATE` ve `DELETE` engellenir (trigger). KVKK anonimleştirmesi audit'lenen ayrıcalıklı bir fonksiyonla yapılır.
  - Taşıdığı alanlar: belge ve satır referansı, kesinleştirme tarihi, boyutlar (§6.7.10), temel miktar + opsiyonel ikincil miktar, üç para birimli tutarlar ve kur bilgisi (§6.7.8), ters kayıt referansı.
  - Sadece değer taşıyan kayıtlar (kur farkı, değerleme düzeltmesi) miktarsız olabilir.
- **Bakiye projeksiyonu:** boyut kırılımında anlık bakiye tablosu tutulur ve aynı transaction'da satır kilidiyle upsert edilir. Tutarlılık testi: "bakiye = hareketlerin toplamı".
- **Taahhüt defteri:** Kesinleşmemiş ama bağlayıcı miktarlar (sipariş rezervasyonu, üretim tahsisi) için ayrı bir defter tipidir. Herhangi bir geçişte (ör. onay) yazılır, karşılanma ya da kapanışta serbest bırakılır. Kullanılabilir miktar = bakiye − taahhüt.
- **Ters kayıt yardımcıları:** belgenin tüm ledger etkisini ters çevirir.
- **"Belirli tarih itibarıyla" sorguları:** dönem sonu anlık görüntüleri (snapshot) + sonrasındaki hareketler.
- **Geriye tarihli kesinleştirme politikası:** Modül bazında konfigüre edilir: izin var mı, kaç gün geriye, değerleme yeniden hesaplanacak mı. Stok değerlemesinde geriye tarihli kesinleştirmenin maliyeti büyük olduğu için bu kararı envanter modülü verir; çekirdek sadece mekanizmayı sağlar.

### 6.9 `audit`: denetim izi

- **Entity geçmişi:** Hibernate Envers. Revizyon kaydı kullanıcıyı, bürünülen kullanıcıyı, istek kimliğini, istemci tipini ve opsiyonel bir gerekçeyi tutar. Geçmiş sekmesi, metadata etiketleriyle okunur bir fark (diff) gösterir.
- **İş ve güvenlik günlüğü (append-only):** giriş/çıkış, başarısız girişler, rol ve izin değişiklikleri, ayar değişiklikleri, export'lar, impersonation, belge geçişleri, lisans olayları.
- **KVKK uyumu:** Kişisel veri olarak işaretli alanlar için anonimleştirme işi audit tablolarını da kapsar (§10.3).
- **Saklama:** Yasal süreler parametreyle belirlenir (§10.5). Eski revizyonlar arşiv tablolarına taşınabilir.

### 6.10 `files`: dosyalar

- **Depolama SPI'ı:** on-prem'de yerel dosya sistemi, SaaS'ta S3 uyumlu nesne deposu. Tenant izolasyonu için anahtar öneki `tenantKey/` kullanılır.
- **Ek modeli:** `attachment` tablosu. Sahip kaynak ve kayıt, ad, MIME tipi, boyut, SHA-256, depolama anahtarı, sürüm ve yükleyen bilgisini tutar. v1'deki "her kayda klasör aç" yaklaşımının yerini alır.
- **İndirme** yetki kontrolünden sonra, BFF'nin verdiği kısa ömürlü imzalı bağlantıyla ve ayrı bir **sandbox alan adından** `Content-Disposition: attachment` ile yapılır (yüklenen içerikle XSS önlemi). Herkese açık kalıcı URL verilmez.
- **Kotalar** (SaaS), MIME beyaz listesi ve opsiyonel virüs taraması (ClamAV hook'u) desteklenir.
- Belge çıktıları (PDF), e-belge XML'leri ve import dosyaları da aynı servisi kullanır.

### 6.11 `messaging`: bildirimler ve gerçek zamanlı iletişim

- **Bildirim servisi:**
  - Kanallar: uygulama içi gelen kutusu ve e-posta. SMS ve mobil push sağlayıcı SPI'ı ile sonra eklenir.
  - Şablonlar i18n destekli ve tenant tarafından override edilebilir.
  - Kullanıcı tercihleri tutulur.
- **Gerçek zamanlı iletişim:** SSE (`/api/v1/stream`) kullanılır. Kullanıcıya özel bildirimler ve "kayıt değişti" olayları gönderilir; bu olaylar UI önbelleğini geçersizleştirir. Instance'lar arası yayın `LISTEN/NOTIFY` ile yapılır.
- **Tek bağlantı:** Bir tarayıcıdaki tüm sekmeler tek SSE bağlantısını paylaşır (BroadcastChannel/SharedWorker). Aksi halde HTTP/1.1'de host başına 6 bağlantı sınırı birkaç sekmede dolar.
- **Bildirim kaybı:** `LISTEN/NOTIFY` mesajı yeniden bağlanma ya da çökme anında kaybolabilir. Bu yüzden metadata ve izin önbelleklerinde TTL vardır, yeniden bağlanmada tüm önbellek temizlenir ve sürüm numarası kontrol edilir. Dinleyici bağlantısı havuz dışıdır.
- WebSocket sadece çift yönlü gerçek zamanlı ihtiyaç olduğunda eklenir (ör. yerel donanım ajanı protokolü, §6.18).
- v1'deki sohbet özelliği çekirdekte yoktur. Kayıt üzerinde yorum ve aktivite akışı sonraki bir platform özelliği olarak planlanır.

### 6.12 `jobs`: arka plan işleri ve zamanlama

- **Motor:** **db-scheduler** (Apache-2.0). Platform DB'sinde çalışır ve kümede güvenlidir (ADR-014).
- **Neden JobRunr değil?** Açık kaynak sürümünde en fazla 100 tekrarlayan iş tanımlanabiliyor. Transaction eklentisi ve öncelik kuyrukları ücretli Pro sürümde. Pro, üretim kümesi başına ücretlendiriliyor; her on-prem kurulum ayrı bir küme sayıldığı için bu model bizimle uyuşmuyor.
- **Tenant bağlamı:** Her iş örneği (task instance) `tenantKey` taşır.
- **Tekrarlayan işler tenant başına dağıtılır:**
  - Küresel bir zamanlayıcı işi, sadece **işi olan** tenant'lar için (son aktiviteye ve bekleyen iş göstergelerine göre) tenant başına iş örnekleri oluşturur.
  - Tüm tenant DB'lerini sırayla taramaz. Böylece boşta duran tenant'lar için havuz açılmaz ve iş tek düğümde seri çalışmaz.
  - Tenant başına kota uygulanır; bir tenant'ın ağır importu diğerlerini bekletmez.
- **Transaction ile kuyruğa alma:**
  - İş verisi tenant DB'sinde, iş kuyruğu platform DB'sindedir. İki DB arasında atomik yazma olmadığı için iş, tenant transaction'ında yazılan bir olayla (Modulith outbox) tetiklenir. Dinleyici commit sonrası işi kuyruğa alır.
  - **İş örneği kimliği olayın `eventId`'sidir** ve `scheduleIfNotExists` kullanılır. Olay iki kez teslim edilse bile iş bir kez planlanır.
  - İşler idempotent yazılır.
- **İş geçmişi:** platform DB'sindeki `job_run` tablosunda (tenant, tip, süre, sonuç, hata), saklama süreli.
- **İş merkezi (UI):** Kullanıcı kendi işlerini (export, import, rapor) izler. Sonuç dosyası süreli olarak saklanır.

### 6.13 `printing`: yazdırma ve çıktılar

- **Şablon tipleri:**
  - **Jasper** (`jrxml`): belge ve rapor çıktıları.
  - **XSLT:** e-Fatura, e-Arşiv ve e-İrsaliye görselleri UBL içine gömülü XSLT ile oluşur, Jasper ile değil.
  - **ZPL:** etiketler.
  - jsreport kaldırılır.
- **Güvenlik (ADR-032):** Jasper ifadeleri derlenmiş Java'dır, yani bir şablon sunucuda rastgele kod çalıştırabilir. Bu yüzden:
  - SaaS'ta tenant'ın ya da danışmanın yüklediği şablon doğrudan kullanılmaz; ürün ekibinin incelemesinden geçip **imzalanır**.
  - Jasper sınıf beyaz listesi (class allowlist) açıktır.
  - Ürün dışı şablonlar, **DB kimlik bilgisi ve ağ erişimi olmayan ayrı bir render işçisinde** çalışır ve veriyi JSON DTO olarak alır. Render işçisi aynı imajın ayrı bir giriş noktasıdır.
- **Şablon deposu:**
  - Modül varsayılan şablonları getirir.
  - Tenant ve şirket kendi şablonunu kullanabilir. Şablonlar sürümlüdür, önizlenebilir ve geri alınabilir.
  - Şablon metadata'sı kontrollü doküman kodu (v1'deki fabrika bazında `reportCode`), kağıt boyutu ve kopya sayısı gibi bilgileri taşır.
- **Veri sağlayıcılar:** jOOQ sorguları ile yazdırma DTO'ları. Şablon doğrudan SQL çalıştırmaz.
- **Türkçe karakterler:** Noto veya DejaVu fontları paketle gelir ve PDF'e gömülür. Jasper'da Türkçe karakter kaybı klasik bir sorundur, çekirdekte çözülür.
- **Toplu yazdırma ve yönlendirme:** Birden çok belge tek işte basılabilir. İş istasyonu bazında yazıcı profilleri tanımlanır ("bu kullanıcı bu belgeyi şu yazıcıdan basar").
- **Etiket ve barkod:** ZPL şablonları, barkod ve QR üretimi, gıda için GS1-128. QR içeriği ham UUID değil, **imzalı kısa kod** olur (doğrulanabilir ve tahmin edilemez).
- **Sessiz yazdırma ve yerel yazıcılar:** yerel donanım ajanı (§6.18) üzerinden yapılır; çekirdek iş kuyruğunu sağlar.

### 6.14 `dataexchange`: import, export ve geçiş

- **Export:** Liste görünümü olduğu gibi (filtreler ve sütunlar dahil) CSV/XLSX olarak alınır. Büyük veri asenkron iş olarak çalışır. Apache POI'nin akış modu (SXSSF) kullanılır.
- **Import hattı:** şablon indir → yükle → sütun eşleme → **doğrulama önizlemesi (dry-run raporu)** → onay → toplu yazma (doğal anahtarla upsert, idempotent) → sonuç raporu.
- **Excel şablonları metadata'dan üretilir:** başlıklar etiketlerden gelir, enum ve değer listeleri için açılır liste eklenir, zorunlu alanlar işaretlenir.
- **Türkçe dosya gerçekleri:** `1.234,56` sayı biçimi, `gg.aa.yyyy` tarih biçimi ve Windows-1254 kodlamalı CSV'ler otomatik tanınır.
- **Açılış bakiyeleri:** Geçişte stok (lot ve maliyetle), açık cari kalemler (vade ve dövizle), çek/senet portföyü gibi bakiyeler **açılış belgeleri** olarak, dış numaralarıyla ve açılış dönemine (§6.7.5) kesinleştirilir.
- **Doğrudan okuyucular:** Logo (`LG_*` tabloları) ve Mikro veritabanlarından doğrudan okuyan geçiş connector'ları sonraki bir `migration-tools` modülüdür. Çekirdek import hattını ve connector SPI'ını sağlar.
- Logo, Mikro ve Netsis gibi sistemlerden geçiş Türk KOBİ pazarında satışın kritik parçasıdır.

### 6.15 `integration`: entegrasyon altyapısı

| Yetenek | Ayrıntı |
|---|---|
| **Outbox** | Spring Modulith olay yayın kaydı (tenant DB'sinde) + dışa aktarma |
| **Webhook'lar** | Tenant bazında abonelik. HMAC imzası, yeniden deneme (artan bekleme), dead-letter, teslimat günlüğü ve UI'dan yeniden gönderme. |
| **Connector SPI** | Dış sistem adaptörleri: e-belge entegratörleri, TCMB kurları, pazaryerleri, dış muhasebe ve ERP'ler (v1'deki Cortex gibi). Her connector'ın konfigürasyonu, sağlık durumu ve senkron günlüğü vardır. |
| **Sır kasası** | Connector kimlik bilgileri AES-GCM ile şifrelenir. Ana anahtar ortam değişkeninden veya KMS'ten gelir, anahtar döndürme desteklenir. **Entegrasyon asla tarayıcıdan yapılmaz** (H13). |
| **Açık API** | Aynı kaynak API'si + API istemcileri (client credentials) + oran sınırlama (Bucket4j) + `Idempotency-Key` desteği (§8.5) |
| **Dış kimlik eşleme** | `external_ref(system, type, external_id, internal_id)` tablosu. Muhasebesini bir süre Logo'da tutmaya devam eden müşteri gibi birlikte çalışma senaryolarının temeli. Gelen e-Fatura'lar ETTN ile tekilleştirilir (compliance-tr). |
| **Raporlama view'ları** | Excel ve Power BI gibi dış araçlar için salt okunur, `ext` alanları düzleştirilmiş view'lar ve ayrı salt okunur DB rolü |
| **AI hazırlığı** | Tüm işlemler aynı yetki kontrollü application servislerinden ve tek tip kaynak API'sinden geçer. Bu yüzden ileride bir MCP sunucusu ya da AI asistanı eklemek ucuz olur. Keycloak 26.8'deki token exchange delegasyonu (önizleme aşamasında) bu senaryoyu hedefliyor. Çekirdekte ayrıca bir iş yapılmaz, sadece bu ilke korunur. |

### 6.16 Arama

§6.5'te anlatıldı: `search_text` + `pg_trgm`, global arama ve komut paleti (§9.3). Ayrı bir arama motoru (OpenSearch vb.) kullanılmaz (ADR-017).

### 6.17 `refdata`: referans veriler (ADR-041)

**Küresel referans veriler (platform DB'sinde, herkese açık veri).** Tüm tenant'lar için tek kopya tutulur; tenant'lar bunları platform API'si ve önbellek üzerinden salt okunur kullanır. Belgeler bu verilere FK vermez, kodu ve gerekiyorsa değeri (kur gibi) kopyalayarak saklar.
- Para birimleri (ISO 4217)
- Döviz kurları: tarih, kur tipi (döviz alış/satış, efektif alış/satış), birim çarpanı ve ilan zamanıyla. TCMB connector'ı (`today.xml` günlük, EVDS geçmiş).
- Ölçü birimi kodları ve UN/ECE Rec 20 eşlemesi (UBL'de zorunlu)
- Ülke, il, ilçe
- GİB kod listeleri: fatura tipleri ve profilleri, istisna ve tevkifat kodları
- GTİP kodları
- Fiyat endeksleri (enflasyon düzeltmesi geri gelirse diye)
- Resmi tatiller

**Tenant referans verileri (tenant DB'sinde):** tenant'a özel birimler ve dönüşümler, vergi ayarları, çalışma takvimleri. Malzemeye özel birim dönüşümleri catalog modülündedir; vardiya desenleri üretim ve İK modüllerinin işidir.

On-prem'de de platform DB'si vardır. Küresel referans veriler sürümle birlikte gelir ve connector'larla güncellenir.

### 6.18 Cihazlar ve saha (identity modülünde)

- **Cihaz kaydı:** tenant bazında cihaz (tablet, el terminali, kiosk, TV paneli, yerel ajan). Tek kullanımlık kayıt koduyla eşleştirilir, iptal edilebilir.
- **Kimlik biçimi cihaz tipine göre değişir (ADR-040):**
  - **Tarayıcıda çalışan kiosk** (tablette tam ekran web): kayıt sırasında BFF, cihaza bağlı uzun ömürlü bir **HttpOnly cihaz oturum çerezi** oluşturur. Tarayıcıda token tutmama kuralı (§9.7) korunur.
  - **Native kabuk ve yerel ajan:** uygulamanın ürettiği, döndürülebilir **cihaz token'ı** kullanılır (hash'li saklanır).
- **Operatör oturumu:** Ortak cihazda operatör kart ya da PIN ile tanınır. Doğrulama **sunucuda** yapılır (hash'li PIN, deneme sınırı). İşlemler "cihaz + operatör" olarak kaydedilir. Şifre cihaza asla gönderilmez (H14). Operatörler Keycloak hesabı almaz.
- **Çevrimdışı çalışma kuralları:**
  - Çevrimdışıyken sadece **taslak oluşturan** ya da olay kaydeden idempotent komutlar kuyruğa alınır (ör. üretim sayımı, kalite kaydı). Her komut `Idempotency-Key` ve `client_time` taşır.
  - Numaralandırma, dönem kontrolü ve kesinleştirme senkronizasyon sırasında sunucuda yapılır. Senkron anında dönem kapanmışsa işlem bir **istisna kuyruğuna** düşer ve yetkili onayına gider.
  - Kayıt zamanı her zaman sunucu saatidir.
- **Yerel donanım ajanı protokolü:** giden WebSocket, cihaz token'ı, komut ve sonuç mesajları (yazdır, tartım oku, seri port verisi). **Ajanın kendisi çekirdek kapsamında değildir.** Protokol ve cihaz kimliği çekirdektedir; ajan, ilk ihtiyaç duyan modülle (üretim ya da depo) yazılır (ADR-022).
- **Barkod okuyucular:** Klavye taklidi yapan (keyboard wedge) okuyucular Türkçe Q klavyede karakterleri bozabilir. Okuyucular mümkünse HID POS modunda kullanılır; giriş katmanı tarama algılama ve karakter düzeltmesi yapar.

---

## 7. Veri mimarisi ve konvansiyonlar

### 7.1 Veritabanı düzeni

- **Platform DB'si:** `erp_platform`. **Tenant DB'leri:** `erp_t_<tenantKey>`. **Keycloak'ın kendi DB'si:** `keycloak`.
- **Tenant DB'si içinde her modülün kendi şeması vardır:** `kernel`, `identity`, `metadata`, `documents`, `catalog`, `inventory`… Modulith olay tablosu `platform_events` şemasında durur.
- **Yayınlanmış okuma view'ları** `<şema>.v_<ad>` adını taşır ve modülün açık sözleşmesidir (§5.4).

### 7.2 Standart kolonlar ve isimlendirme

| Kolon | Tip | Not |
|---|---|---|
| `id` | `uuid` | UUIDv7, uygulama üretir |
| `version` | `bigint` | Optimistic lock |
| `created_at`, `updated_at` | `timestamptz` | Sunucu saati |
| `created_by`, `updated_by` | `uuid` | `user_account.id` |
| `company_id` | `uuid` | Sadece `company` kapsamlı kaynaklarda |
| `ext` | `jsonb` | Sadece `extensible` kaynaklarda |
| `search_text` | `text` | Sadece `searchable` kaynaklarda (normalize) |
| `active` | `boolean` | Pasifleştirilebilen ana verilerde |

- **İsimlendirme:** `snake_case`, tablo adları tekil (`stock_move`). FK kolonları `<hedef>_id`.
- **Index:** Her FK için index, her benzersizlik kuralı için unique constraint.

### 7.3 Tipler

| Kavram | DB tipi | Java tipi |
|---|---|---|
| Tutar | `numeric(19,4)` (para biriminin ölçeğinde yuvarlanmış saklanır) + para birimi kolonu | `Money` (`BigDecimal`) |
| Birim fiyat | `numeric(19,6)` | `BigDecimal` |
| Miktar | `numeric(19,6)` + birim kolonu | `Quantity` |
| Kur | `numeric(24,10)` + birim çarpanı | `ExchangeRate` |
| Oran / yüzde | `numeric(9,4)` | `Percentage` |
| İş tarihi (belge, kayıt, vade) | `date` | `LocalDate` |
| An (olay zamanı) | `timestamptz` | `Instant` |
| Durum ve tip | `text` + check constraint | Java `enum` (sistem) veya değer listesi kodu |
| Ek bilgi | `jsonb` | Sadece §3.2 K6'nın izin verdiği yerlerde |

Tutar taşıyan her başlık, satır ve ledger kaydı üç para birimli modeli kullanır (§6.7.8).

### 7.4 Kimlikler ve numaralar

- Teknik kimlik UUIDv7'dir. Kullanıcıya gösterilen numara ayrı bir alandır (`document_no`, `code`) ve numaralandırma servisinden ya da kullanıcıdan gelir.
- Ana verilerde doğal anahtar (kod) tenant ya da şirket kapsamında benzersizdir. Import ve entegrasyonda upsert anahtarı olarak kullanılır.
- Dışarıya (QR, URL) ham UUID listesi verilmez. İmzalı kısa kod kullanılır (§6.13).

### 7.5 Silme ve arşiv politikası

| Kayıt türü | Politika |
|---|---|
| Ana veri (malzeme, cari…) | **Pasifleştirme** (`active=false`). Hiç kullanılmamışsa fiziksel silme serbest. |
| Taslak belge | Silinebilir veya iptal edilebilir |
| Kesinleşmiş belge, ledger | İçerik kolonları **değiştirilemez, silinemez** (K4). Düzeltme §6.7.3'teki desenlerle yapılır. Durum alanları yan tablolardadır. |
| Audit | Silinemez. Saklama süresi sonunda arşive alınır. KVKK anonimleştirmesi uygulanır. Yasal bekletme (legal hold) bayrağı varken arşivleme ve anonimleştirme çalışmaz. |
| Dosya | Sahip kayda bağlı. Kayıt yasal saklamadaysa dosya da saklanır. |

### 7.6 Etkin tarihli kayıtlar

Operatör–makine ataması, kıdem, fiyat listesi geçerliliği ve organizasyon değişiklikleri gibi zamana bağlı ilişkiler için:

- Gün hassasiyetindeki ilişkiler için `daterange` (`valid_from date not null`, `valid_to date null`, açık uçlu).
- Saat hassasiyetindeki ilişkiler için (vardiya atamaları, gece yarısını geçen vardiyalar) `tstzrange`.
- PostgreSQL exclusion constraint (`daterange` + `btree_gist`) ile aynı anahtar için çakışan dönemler engellenir.
- "Belirli bir tarihte geçerli olan" sorgusu kernel yardımcısıyla yapılır.
- v1'deki elle yazılmış geçmiş tablolarının ve 10 dakikada bir yeniden atama işlerinin yerini alır.

### 7.7 Transaction ve eşzamanlılık kuralları

1. Transaction sınırı `application` katmanındaki komuttur. Controller'da ya da repository'de transaction açılmaz.
2. Bir komut tek tenant'ta çalışır. Tenant'lar arası işlem iş (job) seviyesinde yapılır.
3. Optimistic lock varsayılandır. Çakışmada istemciye `409/412` döner, UI "kayıt başkası tarafından değiştirildi" akışını gösterir.
4. Pessimistic lock sadece sayaçlarda (numaralandırma) ve bakiye projeksiyonlarında kullanılır. Kilit sırası her yerde aynıdır (deadlock önleme): belge ve satırları → bakiye projeksiyonları (anahtar sırasıyla) → numara sayacı (en son, §6.7.2).
5. Dış sistem çağrısı transaction içinde yapılmaz. Outbox + iş kullanılır.
6. Uzun süren toplu işlemler parçalar (chunk) halinde, her parça ayrı transaction olarak ve kaldığı yerden devam edebilecek şekilde çalışır.
7. Aynı transaction'da JPA ile yazılan veri jOOQ ile okunacaksa önce `flush` edilir. CRUD motoru ve belge çatısı bunu katılımcılardan önce otomatik yapar.
8. Yazma işlemlerinde veri kapsamı (şirket, tesis, kapsam tipi) yüklenen kayıt üzerinde açıkça kontrol edilir.

### 7.8 Türkçe metin, sıralama ve arama

- **Kod:** `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)`. Parametresiz çağrı yasaktır (K10). `equalsIgnoreCase` Türkçe metinde kullanılmaz.
- **Arama:** `search_text = TurkishText.fold(...)` uygulamada üretilir. Sorgu metni de aynı şekilde normalize edilir. `pg_trgm` GIN index'i ile `ILIKE '%…%'` ve benzerlik araması yapılır. Böylece "ısık", "Işık" ve "isik" aynı sonucu bulur.
- **Sıralama:** İsim sıralamasında ICU Türkçe collation kullanılır (`COLLATE "tr-TR-x-icu"`, kolon ya da sorgu bazında). Veritabanı varsayılan collation'ı `und-x-icu` ya da `C.UTF-8` olur, tüm DB'yi Türkçe collation'a almak `lower()` ve index davranışında sürprizler yaratır.
- **Sayı ve tarih biçimi:** kullanıcının diline göre UI'da yapılır. API her zaman ISO-8601 ve noktalı ondalık kullanır.

### 7.9 Migration kuralları

- Flyway SQL migration'ları modül klasöründe durur. Sürüm adı: `V<yyyyMMddHHmm>__<açıklama>.sql`. Zaman damgası paralel geliştirmede çakışmayı önler.
- Her migration genişlet/daralt kuralına uyar (§4.6). Uzun süren veri dönüşümleri migration'da değil, idempotent veri işi olarak yazılır.
- CI'da her PR için: boş DB'den kurulum + bir önceki sürümün şemasından yükseltme + örnek tenant verisiyle yükseltme testi çalışır.
- Overlay migration'ları (§6.4.4) aynı sürümle birlikte gelir.

---

## 8. API tasarımı

### 8.1 Yapı ve sürümleme

- **Taban yol:** `/api/v1`.
- **Jenerik kaynaklar:** `/api/v1/{modül}/{kaynak}` (ör. `/api/v1/catalog/items`).
- **Özel uç noktalar:** aynı ad alanında, `web` paketinde yazılır.
- **Metadata:** `GET /api/v1/meta/resources/{kaynak}` birleştirilmiş, kullanıcıya göre filtrelenmiş tanımı ETag ile döner.
- **Önyükleme:** `GET /api/v1/bootstrap` döner: kullanıcı, tenant, aktif şirket, izinler, aktif modüller, dil, marka (logo/renk) ve özellik bayrakları.
- **Sürümleme:** API sürümü sadece kırıcı değişiklikte artar (`v2`). Kaynak tanımları kendi `version` alanını taşır. Kırıcı değişiklik politikası §5.4'tedir.

### 8.2 Liste ve sorgu dili

- **Basit kullanım:** `GET …/items?q=kumaş&sort=-updatedAt&page=0&size=50&fields=code,name`.
- **Gelişmiş filtre:** `POST …/items/query`. Gövde, tipli bir JSON filtre ağacıdır:

```json
{
  "filter": { "and": [
    { "field": "kind", "op": "in", "value": ["RAW", "SEMI"] },
    { "field": "ext.originCountry", "op": "eq", "value": "TR" },
    { "or": [ { "field": "name", "op": "contains", "value": "pamuk" },
              { "field": "code", "op": "startsWith", "value": "PM" } ] }
  ]},
  "sort": [ { "field": "code", "dir": "asc" } ],
  "page": { "size": 50, "cursor": null },
  "fields": ["code", "name", "kind", "baseUom"]
}
```

- **Operatörler:** `eq, ne, in, notIn, gt, gte, lt, lte, between, contains, startsWith, isNull, isNotNull`. Hangi alanda hangi operatörün kullanılabileceğini metadata belirler. Filtrelenemeyen alana filtre uygulanırsa 400 döner.
- **Sayfalama:** UI listeleri için offset + toplam sayı (sayım bütçesi aşılırsa tahmini sayı ve `totalIsEstimate` işareti). Export ve entegrasyon için keyset cursor.

### 8.3 Hata modeli

RFC 9457 `application/problem+json` kullanılır:

```json
{
  "type": "https://errors.erp.local/documents/period-closed",
  "title": "Dönem kapalı",
  "status": 422,
  "code": "documents.period-closed",
  "detail": "2026-09 dönemi stok modülü için kapalı.",
  "traceId": "4bf92f3577b34da6",
  "errors": [ { "field": "postingDate", "code": "period-closed", "message": "…" } ]
}
```

| HTTP | Anlamı |
|---|---|
| 400 | Biçim hatası |
| 401 / 403 | Kimlik / yetki |
| 404 | Bulunamadı (yetkisiz kayıt da 404 döner, var olduğu sızmaz) |
| 409 / 412 | Çakışma / If-Match uyumsuzluğu |
| 422 | İş kuralı ihlali |
| 429 | Oran sınırı |

### 8.4 Eşzamanlılık

`GET` yanıtı `ETag: "<version>"` taşır. `PATCH`, `PUT`, `DELETE` ve eylemler `If-Match` ister. Eksikse 428, uyumsuzsa 412 döner.

### 8.5 İdempotency

- `POST` istekleri (özellikle cihaz, mobil ve entegrasyon istekleri) `Idempotency-Key` başlığı taşıyabilir. Sunucu anahtarı ve yanıtı tenant DB'sinde 24–72 saat saklar. Aynı anahtar ve aynı gövde gelirse aynı yanıtı döner. Farklı gövde gelirse 422 döner.
- Çevrimdışı kuyruğu olan saha cihazları için bu zorunludur (§6.18).

### 8.6 Komut uç noktaları

Durum değişiklikleri CRUD güncellemesi değil, **komut** olarak modellenir:
`POST /api/v1/inventory/stock-documents/{id}/actions/post`. Her komut tek transaction'dır, yanıtında güncel temsili ve yeni ETag'i döner.

### 8.7 OpenAPI ve istemci üretimi

- springdoc ile OpenAPI 3.1 üretilir. Jenerik kaynaklar için metadata'dan **kaynak başına tipli yollar ve şemalar** üretilir; özel ekranlar tipli istemci kullanır.
- Frontend istemcisi `openapi-typescript` + `openapi-fetch` ile üretilir. Backend sözleşmesi değişirse frontend derleme hatası verir.
- API dokümantasyonu her sürümde otomatik yayınlanır (açık API müşterileri ve entegratörler için).

---

## 9. Frontend mimarisi

### 9.1 Yığın

| Konu | Seçim | Gerekçe |
|---|---|---|
| Çatı | **Vue 3** (Composition API, `<script setup>`), **TypeScript** | Ekip Vue biliyor |
| Build | **Vite 8** (Rolldown) | Mart 2026'da çıktı, tek bundler, çok hızlı |
| Bileşen kütüphanesi | **Vuetify 4** (MIT), kendi `@erp/ui` katmanımızın arkasında | Şubat 2026'da çıktı. Material Design 3, CSS layers, sistem teması. Ekip Vuetify 2'yi biliyor. Lisans riski yok. |
| Veri ızgarası | **AG Grid Community** (MIT, v36), `@erp/ui` içinde sarmalanmış | Sanallaştırma, klavye gezintisi, hücre düzenleme, sütun sabitleme ve taşıma ücretsiz sürümde var |
| Sunucu durumu | **TanStack Query** (Vue) | Önbellek, yeniden doğrulama, SSE ile geçersizleştirme |
| İstemci durumu | **Pinia** | Oturum, tercihler, çalışma alanı sekmeleri |
| Router / i18n | Vue Router, vue-i18n | |
| Formlar | **vee-validate + Zod**. Zod şeması çalışma zamanında metadata'dan üretilir. | Sunucu kuralıyla tutarlı istemci doğrulaması |
| API istemcisi | openapi-typescript + openapi-fetch | Hafif, tip güvenli |
| Monorepo | **pnpm workspaces** (Turborepo opsiyonel) | |
| Test | Vitest (birim ve bileşen), Playwright (E2E) | |
| Kalite | ESLint + Prettier, `vue-tsc` tip kontrolü | |

**Değerlendirilip reddedilenler:**

| Seçenek | Neden reddedildi |
|---|---|
| PrimeVue 5 | Artık PrimeUI ticari lisansıyla dağıtılıyor: geliştirici başına 599 $ (2027'den itibaren 799 $). Ücretsiz Community lisansı sadece 5 geliştiricinin, 10 çalışanın ve 1 M$ cironun altındaki şirketlere açık. OEM maddesi "low-code / uygulama oluşturucu" ürünler için ayrı lisans istiyor; metadata güdümlü platformumuz bu tanıma girme riski taşıyor. |
| PrimeVue 4 | MIT kalıyor ama yeni major sürüm gelmeyecek |
| Element Plus, Naive UI, Quasar | Teknik olarak uygunlar ama ekip deneyimi Vuetify'da |
| React | Ekip deneyimi |
| Mikro-frontend / module federation | Tek ekip, tek sürüm hattı için gereksiz karmaşıklık |
| `oidc-client-ts` | BFF deseni yüzünden tarayıcıda OIDC istemcisine gerek yok |

**AG Grid Enterprise kararı ertelendi (ADR-021):** Excel'den hücre aralığı yapıştırma, aralık seçimi, satır gruplama ve Excel export Enterprise sürümde. Excel export sunucuda yapılır (§6.14). "Excel'den satır yapıştırma" Community sürümün klavye ve pano olaylarıyla `@erp/ui` içinde yazılır. Kullanıcı ihtiyacı bunu aşarsa Enterprise lisansı ayrıca değerlendirilir.

### 9.2 Paket yapısı

```
web/
 ├─ packages/
 │   ├─ ui/              @erp/ui: tasarım token'ları, sarmalanmış bileşenler (ErpTextField, ErpGrid…), temalar
 │   ├─ core/            @erp/core: API istemcisi, oturum/BFF, izinler, i18n, SSE, hata işleme, biçimlendirme
 │   ├─ meta-renderer/   @erp/meta: metadata → sayfa/form/liste/detay render motoru, widget kayıt defteri
 │   └─ shell/           @erp/shell: uygulama iskeleti, menü, sekmeli çalışma alanı, arama, bildirim merkezi
 ├─ modules/
 │   └─ <modül>/         @erp/mod-<modül>: modül UI manifesti, özel sayfalar, özel widget'lar, çeviriler
 └─ apps/
     └─ web/             Tüm paketleri birleştiren tek SPA (backend imajına gömülür)
```

- **Bağımlılık yönü:** `apps → modules → shell → meta-renderer → core → ui`. Modül paketleri birbirini import etmez.
- **Modül UI manifesti:** rotalar, menü öğeleri, özel sayfa ve widget kayıtları, çeviriler. Shell aktif modülleri `/bootstrap`'tan öğrenir ve sadece onların manifestlerini **lazy** yükler.
- **Tek build:** Tüm müşteriler aynı SPA'yı kullanır (K8). Marka, dil ve modül farkı çalışma zamanında gelir.

### 9.3 Shell

- **Giriş:** BFF yönlendirmesi. Tenant ve şirket seçici.
- **Menü:** modül manifestleri + izinler + lisans + tenant overlay'i (menü sırası ve gizleme).
- **Sekmeli çalışma alanı:** ERP kullanıcıları aynı anda çok kayıt açar. Sekmeler kirli form uyarısı verir ve oturum boyunca korunur.
- **Global arama / komut paleti** (Ctrl+K): kayıt arama, sayfaya git, eylem çalıştır.
- **Bildirim merkezi ve iş merkezi:** SSE ile canlı.
- **Kullanıcı tercihleri:** dil, tema, yoğunluk (kompakt/rahat), varsayılan şirket.
- **Klavye öncelikli veri girişi:** kısayollar, Enter ile alan atlama, satır ekleme. ERP'nin "güçlü kullanıcı" (power user) gerçekliği.
- **Klavye haritası** belgelenir (`docs/guides/keymap.md`). Çıkış testi: 20 satırlı bir belge fare kullanılmadan girilebilmeli.

### 9.4 Tasarım sistemi ve sayfa arketipleri

"Aynı tasarım dili" hedefi, her ekranın birkaç **arketipten** birine uyması ile sağlanır:

| Arketip | Kullanım | Metadata ile otomatik mi? |
|---|---|---|
| **Liste** | Kaynak listesi, filtre paneli, kayıtlı görünümler, toplu eylemler, export | Evet |
| **Form** | Oluştur/düzenle | Evet |
| **Detay** | Kayıt + sekmeler (form, alt koleksiyonlar, ekler, geçmiş, ilişkili kayıtlar) | Evet |
| **Belge** | Başlık + satır ızgarası + toplamlar + durum eylemleri + onay zaman çizelgesi | Büyük ölçüde (satır ızgarası metadata'dan, hesaplama hook'larla) |
| **Dashboard** | Widget ızgarası (KPI, grafik, liste) | Widget tanımları metadata'dan |
| **Sihirbaz** | Çok adımlı süreçler (import, kurulum) | Kısmen |
| **Kiosk** | Saha tableti: büyük düğmeler, operatör oturumu, çevrimdışı kuyruk | Düğme panelleri metadata'dan (v1 "SmartButton" kavramının genellenmiş hali) |

`@erp/ui` şunları tanımlar: tasarım token'ları (renk, aralık, tipografi, yoğunluk), açık ve koyu tema, tenant marka renkleri (çalışma zamanında), erişilebilirlik (kontrast, klavye, ARIA).

### 9.5 Metadata renderer ve kaçış yolları

- **Widget kayıt defteri:** anlamsal tip → varsayılan widget eşlemesi. Kaynak veya alan bazında kayıtlı başka bir widget seçilebilir (`widget: color-swatch`). Modül paketleri kendi widget'larını kaydeder.
- **Bölge (slot) override'ı:** otomatik sayfanın bir bölgesi (ör. detay sayfasına özel bir sekme veya formun bir bölümü) elle yazılmış bileşenle değiştirilebilir.
- **Tam sayfa override'ı:** bir kaynak görünümü tamamen özel sayfaya bağlanabilir. Özel sayfa da renderer parçalarını (ErpForm, ErpList) bileşen olarak kullanır, tasarım dili korunur.
- **Sunucu hataları:** ProblemDetail'deki alan hataları ilgili alanlara eşlenir.

### 9.6 Veri ve önbellek

- **TanStack Query anahtarı:** `[tenant, kaynak, sorgu]`. SSE'den gelen "kayıt değişti" olayı ilgili anahtarları geçersizleştirir.
- **İyimser (optimistic) güncelleme** sadece güvenli durumlarda yapılır. Belge eylemleri her zaman sunucu yanıtını bekler.
- **Büyük listeler** sunucu tarafında sayfalanır, ızgara sanallaştırılır.

### 9.7 Kimlik doğrulama (BFF)

- SPA, backend ile aynı origin'den sunulur. Oturum çerezi otomatik gider, CSRF token'ı başlığa eklenir.
- Oturum düşerse `/bootstrap` 401 döner ve SPA girişe yönlendirir. Kirli formlar yerel taslak olarak korunur.
- Tarayıcıda token saklanmaz.

### 9.8 Uluslararasılaştırma

- Varsayılan dil Türkçe, ikinci dil İngilizce. İhracatçı tekstil müşterileri için sonradan başka diller eklenebilir. RTL şimdilik kapsam dışıdır.
- **Anahtar düzeni:** `<modül>.<kaynak>.<alan>` (metadata etiketleriyle aynı anahtar). Backend mesajları ve frontend metinleri aynı anahtar ailesini kullanır. Tenant overlay'i etiketleri dil bazında override edebilir.
- Biçimlendirme (sayı, para, tarih) `Intl` ile kullanıcı diline göre yapılır.

### 9.9 Performans bütçeleri

| Ölçüt | Hedef |
|---|---|
| İlk yükleme (shell + ilk sayfa, sıkıştırılmış JS) | ≤ 400 KB, orta donanımda TTI ≤ 2,5 sn |
| Liste sayfası geçişi (önbellek yokken) | ≤ 1 sn |
| Satır ızgarasında 1000 satırlık belge | Akıcı kaydırma, istemci içi düzenleme gecikmesi ≤ 50 ms |
| Toplamların yeniden hesaplanması (simülasyon uç noktası, §6.7.9) | ≤ 300 ms |

### 9.10 Saha ve mobil stratejisi

1. **Varsayılan:** duyarlı (responsive) web + PWA. Onaylar, basit sorgular ve depo işlemleri mobil tarayıcıda çalışır.
2. **Kiosk arketipi:** tabletlerde tam ekran web. Cihaz oturum çereziyle (§6.18) ve operatör oturumuyla çalışır. Çevrimdışı kuyruk IndexedDB'de tutulur ve sadece taslak/olay komutlarını içerir (idempotency anahtarlarıyla).
3. **Native kabuk** gerekirse (kiosk kilidi, donanım barkod tarayıcı, NFC kart okuyucu): Capacitor ile aynı web kodunu sarmalayan bir kabuk yapılır. v1'deki 5 ayrı Kotlin uygulaması + WebView + JS köprüsü yaklaşımı tekrarlanmaz. Bu karar ilk saha modülünde verilir (ADR-023).

---

## 10. Güvenlik, KVKK ve mevzuat hazırlığı

### 10.1 Tehdit modeli özeti

| Tehdit | Önlem |
|---|---|
| Tenant'lar arası veri sızıntısı | Tenant başına DB, bağlamsız erişimde hata, izolasyon test paketi (API, iş, olay, dosya, cache, SSE) |
| Yetki atlatma | `application` katmanında method security + motorda veri kapsamı + otomatik 403 testleri + 404 ile var olma gizleme |
| Tarayıcıda token çalınması | BFF, HttpOnly çerez, tarayıcıda token yok |
| CSRF | SameSite=Lax + CSRF token'ı |
| Tenant'lar arası oturum karışması | `__Host-` önekli, host'a bağlı çerez; her istekte host ile oturum tenant'ı karşılaştırılır; token'daki organization iddiası zorunlu (§4.4) |
| Yüklenen dosyalarla XSS | Dosyalar ayrı sandbox alan adından, `Content-Disposition: attachment` ile sunulur |
| Şablonla sunucuda kod çalıştırma | İmzalı şablonlar, Jasper sınıf beyaz listesi, izole render işçisi (§6.13) |
| XSS | Vue şablon kaçışı, `v-html` yasak (lint), CSP başlığı |
| Sırların sızması | Repoda sır yok (gitleaks), şifreli connector sırları, ortam değişkeni ya da KMS |
| Saha cihazının ele geçirilmesi | Cihaz token'ı iptali, sunucu tarafı PIN doğrulaması, cihaz bazında yetki |
| Toplu veri çekme | Export izni ayrıdır, export işlemleri audit'e yazılır, oran sınırı |
| Tedarik zinciri | Bağımlılık taraması, SBOM, imzalı imajlar (cosign), Renovate ile güncelleme |
| Uzaktan komut çalıştırma | Gelen uzaktan yönetim kanalı yok (H18). Güncelleme pull temelli ve imzalı. |

Hedef seviye: **OWASP ASVS 4 Seviye 2**. Faz 7'de bağımsız bir sızma testi yapılır.

### 10.2 Sır yönetimi

- **SaaS:** sırlar barındırma platformunun sır yöneticisinden ortam değişkeni olarak gelir.
- **On-prem:** `erpctl install` sırları üretir ve kök kullanıcıya özel bir dosyada (izin 600) tutar.
- **Connector sırları:** zarf şifreleme (envelope encryption) kullanılır. Ana anahtar döndürülebilir.
- **CI:** her PR'da gitleaks çalışır. Geçmişte sızmış v1 sırları (§2.2 H13) **v4'te kullanılmaz** ve v1 tarafında iptal edilmelidir (§20).

### 10.3 KVKK

- **Kişisel veri envanteri metadata'dadır:** alanlar `personalData: true` ve kategori (kimlik, iletişim, özlük, finans, özel nitelikli) ile işaretlenir. Bu işaretten şu çıktılar otomatik üretilir:
  - Kişisel veri envanteri raporu (VERBIS hazırlığı için)
  - Loglarda maskeleme
  - İlgili kişi taleplerinde dışa aktarma ("bende hangi verim var?")
  - Saklama süresi bitince anonimleştirme işi
- **Değişmez kayıtlar ve silme hakkı çatışması:** Yasal saklama yükümlülüğü olan kayıtlar (VUK ve TTK kapsamındaki ticari belgeler) süre dolana kadar silinmez. Bu, KVKK'da öngörülen bir istisnadır. Süre dolunca kişisel alanlar audit ve Envers tabloları dahil anonimleştirilir. Ledger tutarları ve belge numaraları korunur. Anonimleştirme, K4 trigger'ını audit'lenen ayrıcalıklı bir fonksiyonla aşar. İmzalı e-belge XML'leri anonimleştirilemez; saklama süresi sonunda silinir.
- **Yasal bekletme:** Vergi incelemesi veya dava durumunda tenant ya da kayıt bazında bekletme bayrağı konur; bayrak varken süresi dolmuş kayıtlar da silinmez, anonimleştirilmez.
- **Yedekler** de kişisel veri içerir; saklama politikasında ve VERBIS kaydında yer alır.
- **Yurt dışı aktarım:** SaaS'ın **Türkiye'de barındırılması** önerilir. 2024 KVKK değişikliğiyle yurt dışı aktarım standart sözleşme ve bildirim gibi ek yükümlülüklere bağlandı. Yurt dışı barındırma hukuki değerlendirme gerektirir (§20).
- **Erişim günlüğü:** Özel nitelikli kişisel veri görüntülemeleri (ör. sağlık raporu eki) audit'e yazılır.

### 10.4 Türkiye mevzuatı için çekirdek hazırlığı

e-belge connector'ları sonraki bir modüldür (`compliance-tr`). Çekirdek, bu modülün yeniden mimari gerektirmemesi için şunları sağlar:

| İhtiyaç | Çekirdekteki karşılığı |
|---|---|
| e-Fatura, e-Arşiv, e-İrsaliye, e-SMM, e-Müstahsil yaşam döngüleri | Profil bazında takılabilir harici durum makinesi (§6.7.1), koruma SPI'ı ile yerel UBL-TR doğrulaması (§6.7.2), connector SPI'ı (§6.15), outbox ve yeniden deneme, UBL XML ve PDF'in eklere bağlanması, XSLT görsel şablonları (§6.13) |
| e-İrsaliye'nin gönderen tarafından iptal edilememesi, alıcı yanıtı (7 gün, kısmi kabul) | Harici durum profili + düzeltici belge deseni (§6.7.3) |
| Ticari e-Fatura'nın 8 günlük kabul/ret süresi, kabul sonrası iade faturası; e-Arşiv iptali | Harici durum profili + yasal iptal ve düzeltici belge desenleri (§6.7.3) |
| GİB numara formatı (3 karakter seri + yıl + 9 hane, boşluksuz, tarih sırası) | Boşluksuz numaralandırma, tarih monotonluğu, seri seçim SPI'ı (§6.7.4) |
| Toplu faturalama (bir faturada çok irsaliye) | Satır bazında çoka-çok belge bağlantıları (§6.7.7) |
| KDV, tevkifat, ÖTV, istisnalar | Hesaplama hattı + vergi satırı modeli (§6.7.9); kurallar `trade-base`'de |
| Dövizli işlemler, TCMB kurları | Üç para birimli model ve sabitlenen kur (§6.7.8), TCMB connector'ı (§6.17) |
| GİB kod listeleri, UN/ECE birim kodları, GTİP | Küresel referans veriler (§6.17) |
| VKN/TCKN, MERSİS, vergi dairesi | Kernel değer tipleri + şirket kartı |
| e-Defter (muhasebe modülüyle) | Ledger değişmezliği, dönem kilitleri, ertelenmiş toplu numaralandırma (yevmiye madde numarası, §6.7.4) |
| HKS, e-Müstahsil (gıda) | Harici durum profilleri + connector SPI'ı (gıda paketi ve compliance-tr) |

**Mevzuat sık değişir:**
- VUK 589 sıra no.lu tebliğ (31.12.2025) bazı e-Arşiv geçiş takvimlerini bir yıl erteledi.
- VUK 593 sıra no.lu tebliğ (8 Mayıs 2026) yeni nesil ÖKC'lerin e-belge düzenlemesine izin verdi.
- Hadler her yıl güncelleniyor.

Bu yüzden zorunluluk eşikleri ve tarihler **kod değil parametredir**. Mevzuata özgü mantık sadece `compliance-tr` modülünde bulunur.

### 10.5 Saklama süreleri

- Ticari defter ve belgeler için genel kural: VUK md. 253'e göre 5 yıl, TTK md. 82'ye göre 10 yıl. Varsayılan saklama süresi **10 yıl** olarak konfigüre edilir.
- Süre, belgenin ait olduğu yılı izleyen takvim yılının başından itibaren işler.
- Saklama politikası kaynak bazında metadata'da tanımlanır. Arşivleme ve anonimleştirme işleri bu politikayı okur.
- Yasal bekletme bayrağı (§10.3) saklama süresi dolmuş olsa bile silmeyi durdurur.
- Kesin süreler ve istisnalar mali müşavir ve hukuk danışmanıyla Faz 5'te teyit edilir (§20).

---

## 11. Operasyon ve dağıtım

Operasyon son faza bırakılmaz. **Faz 0'dan itibaren** her commit, on-prem ile aynı compose paketiyle CI'da ayağa kaldırılır (yürüyen iskelet). Faz 7 bu temelin sertleştirilmesidir.

### 11.1 Paketleme

| Bileşen | İmaj |
|---|---|
| ERP uygulaması | Tek OCI imajı: Spring Boot (katmanlı jar) + gömülü SPA. Java 25 JRE tabanlı minimal imaj. Cosign ile imzalı. |
| Keycloak | Resmi imaj + bizim realm şablonumuz ve temamızla üretilmiş türev imaj |
| PostgreSQL | Resmi PostgreSQL 18 imajı (on-prem paketi) |
| Ters proxy | Caddy (on-prem; otomatik TLS ya da müşterinin sertifikası) |

**On-prem paketi:** `docker compose` dosyaları + **`erpctl`** komut satırı aracı. `erpctl`'in sorumlulukları:

- `install`: ön kontroller (CPU/RAM/disk, port, DNS, saat), sırları üretme, ilk kurulum, tenant ve lisans yükleme
- `update`: §11.2
- `backup` / `restore`
- `status`
- `support-bundle`
- `license install`

**TLS her kurulumda zorunludur.** Alan adı olmayan on-prem kurulumlarda Caddy'nin iç sertifika otoritesi kullanılır ve kök sertifika istemcilere dağıtılır. `Secure` çerezler, `__Host-` öneki ve HTTP/2 bunu gerektirir.

**Desteklenen işletim sistemi:** Linux (Ubuntu LTS, Debian, RHEL uyumlu).
**Windows Server müşterileri** (Türk KOBİ'lerinde yaygın): Windows Server üzerinde Linux konteyner çalıştırmak desteklenmez. Bunun yerine **hazır Linux sanal makine imajı** (Hyper-V VHDX / VMware OVA, içinde `erpctl` kurulu) sunulur. Bu yaklaşım Faz 7'de gerçek bir müşteri ortamında doğrulanır (ADR-024).
**İnternetsiz (air-gapped) kurulum:** imajlar ve imzalı manifest tek bir çevrimdışı arşivde gelir.

### 11.2 On-prem güncelleme

```
erpctl update
  1. Sürüm kanalını kontrol et (imzalı manifest; LTS ya da güncel kanal)
  2. Sürüm notlarını ve uyumluluğu göster → yönetici onayı
  3. Otomatik yedek al (tüm DB'ler + dosya deposu + konfigürasyon) ve yedeği doğrula
  4. İmajları çek, imzaları doğrula
  5. Bakım modu → bekleyen olay yayınlarını boşalt → uygulamayı durdur
  6. Migration (önce platform DB'si, sonra tenant DB'si; §4.6)
  7. Uygulamayı başlat → sağlık kontrolleri ve duman testleri
  8a. Başarılıysa bakım modundan çık
  8b. Başarısızsa otomatik geri dön (önceki imajlar + 3. adımdaki yedekten geri yükleme)
```

- **Olay yayınları neden boşaltılır?** Yayın kayıtları serileştirilmiş olay sınıfları içerir. Yeni sürüm eski biçimi okuyamayabilir.
- **Sadece pull temelli** çalışır. Sunucumuzdan müşteri sunucusuna komut gitmez (H18).
- **Heartbeat** isteğe bağlıdır ve müşteri onayıyla açılır: sürüm, sağlık ve lisans kullanımı bilgisi kontrol düzlemine gönderilir. Kişisel ya da iş verisi gönderilmez.
- **Desteklenen yükseltme yolları:** bir önceki minor sürümden ve desteklenen her LTS'ten güncel sürüme. CI her sürümde bu yolları test eder.
- **PostgreSQL major yükseltmesi** ayrı ve planlı bir işlemdir: `erpctl pg-upgrade` (yedek → `pg_upgrade` → doğrulama). PG 18'in desteği Kasım 2030'da biter. jOOQ sürüm politikası da bu adımı gerektirebilir (ADR-011).

### 11.3 SaaS işletimi

- **Uygulama:** N instance, yük dengeleyici arkasında, durumsuz. Her yerde güvenli kalabilmek için rolling deploy expand/contract kuralına dayanır.
- **Veritabanı:** PostgreSQL birincil + streaming replica (HA). pgBackRest ile PITR. Tenant bazında gece `pg_dump`.
- **Migration orkestrasyonu:** tenant bazında çalışır. Başarısız tenant bakım moduna geçer, uyarı üretilir.
- **Kapasite eşikleri:** küme başına tenant sayısı, bağlantı sayısı, DB boyutu izlenir. Eşik aşılınca yeni küme açılır (§4.2).
- **Barındırma:** Türkiye'de (§10.3).

### 11.4 Yedekleme ve geri yükleme

| | On-prem | SaaS |
|---|---|---|
| Varsayılan | Gece mantıksal yedek (tüm DB'ler + dosyalar), 14 gün rotasyon, **WAL arşivleme (pgBackRest, PITR)**, opsiyonel uzak kopya (S3) | pgBackRest PITR (en az 14 gün) + tenant bazında mantıksal yedek (30–90 gün) |
| Geri yükleme | `erpctl restore`, tenant geri yükleme el kitabıyla (§4.7.1) | Tenant bazında (tek tenant'ı geçmiş bir ana döndürme), aynı el kitabıyla |
| Doğrulama | Haftalık otomatik geri yükleme denemesi (geçici DB'ye) | Günlük otomatik geri yükleme denemesi + aylık tatbikat |
| Şifreleme | Yedekler şifreli | Yedekler şifreli |

### 11.5 Lisans

- **Biçim:** Ed25519 ile imzalanmış JSON (JWS). İçerik: lisans sahibi, tenant anahtarı, modüller ve paketler, kullanıcı limiti, bitiş tarihi, destek bitiş tarihi, ek süre (grace).
- **Doğrulama:** çevrimdışı yapılır (açık anahtar imajda bulunur). Açılışta ve günde bir kez kontrol edilir.
- **İhlal davranışı:** önce uyarı → ek süre → **salt okunur mod**. Veriye erişim asla kilitlenmez, dışa aktarma her zaman mümkündür.
- **SaaS:** Aynı hak modeli platform DB'deki abonelikten okunur. Kod tek, kaynak iki.
- **Lisans üretme aracı:** Faz 7'de iç kullanım için bir CLI. Tam bir operatör konsolu (müşteri, lisans, kurulum takibi) çekirdek sonrası bir iştir.

### 11.6 Gözlemlenebilirlik ve destek

- **Loglar:** JSON formatında. Her satırda `traceId`, `tenantKey`, `userId`, `module` alanları bulunur. Kişisel veri maskelenir.
- **Metrikler:** Micrometer + Prometheus uç noktası. İş metrikleri de toplanır (kesinleşen belge sayısı, iş kuyruğu uzunluğu, tenant bazında istek sayısı).
- **İzleme (tracing):** OTLP export (opsiyonel).
- **On-prem izleme:** isteğe bağlı bir compose profili (Prometheus + Grafana + Loki) ve hazır panolar.
- **SaaS izleme:** tam izleme yığını + hata takibi (Sentry uyumlu, kendi barındırdığımız bir çözüm) + uyarılar.
- **Destek paketi:** `erpctl support-bundle` sürümleri, migration durumunu, son loglar ve konfigürasyonu (sırlar hariç) tek bir arşivde toplar.

### 11.7 Donanım boyutlandırma (başlangıç tahmini; Faz 7'de ölçülecek)

| Profil | Eşzamanlı kullanıcı | vCPU | RAM | Disk |
|---|---|---|---|---|
| Küçük on-prem | ≤ 50 | 4 | 16 GB | 200 GB SSD |
| Orta on-prem | ≤ 200 | 8 | 32 GB | 500 GB SSD |
| SaaS düğümü | Ölçüme göre | 8+ | 32 GB+ | Ayrı DB sunucusu |

Kabaca bellek dağılımı (küçük profil): uygulama ~4 GB heap, PostgreSQL ~4 GB shared_buffers + işletim sistemi önbelleği, Keycloak ~1–1,5 GB.

### 11.8 Sürüm yönetimi

- **Tek sürüm hattı:** platform, modüller ve paketler aynı ürün sürümünü taşır (`MAJOR.MINOR.PATCH`).
- **Yayın sıklığı:** 4–6 haftada bir minor sürüm, gerektiğinde patch.
- **LTS:** yılda iki minor sürüm LTS olarak işaretlenir ve 12 ay desteklenir. On-prem müşteriler genelde LTS kullanır, SaaS her zaman günceldedir.
- **Değişiklik kaydı:** Conventional Commits'ten otomatik üretilir. Sürüm notlarında "yükseltme notları" ve "kırıcı değişiklikler" bölümleri zorunludur.
- **Bağımlılık politikası:** Spring Boot minor sürümleri yayımından sonraki ilk ürün sürümünde yükseltilir. Örneğin Boot 4.2 Kasım 2026'da çıkınca bir sonraki ürün sürümünde alınır. Renovate PR açar, CI doğrular.

---

## 12. Teknoloji yığını

Sürümler Ekim 2026 itibarıyla. Kesin yamalar Faz 0'da sabitlenir.

### 12.1 Backend

| Bileşen | Seçim | Lisans | Not |
|---|---|---|---|
| Dil / runtime | **Java 25 LTS** (Eclipse Temurin) | GPLv2+CE | Virtual thread'lerde `synchronized` pinning sorunu Java 24'te giderildi (JEP 491) |
| Framework | **Spring Boot 4.1.x** (Spring Framework 7) | Apache-2.0 | Güncel sürüm 4.1.1. 4.2 RC aşamasında, GA sonrası alınır. Boot 4'e doğrudan başlamak, 3.5'ten başlayıp hemen bir major göç borcuna girmekten iyidir. |
| Modülerlik | **Spring Modulith 2.x** | Apache-2.0 | Boot 4 tabanlı. Modül bazında Flyway migration, açılışta yapı doğrulaması, Jackson 3 ile olay serileştirme. |
| Güvenlik | Spring Security 7 (OAuth2 Client + Resource Server), Spring Session JDBC | Apache-2.0 | Authorization Server artık Spring Security'nin içinde (yedek plan) |
| ORM (yazma) | **Hibernate ORM 7** + **Envers** | Apache-2.0 (Hibernate 7 ile) | İkinci seviye cache kapalı |
| Sorgu (okuma) | **jOOQ 3.21** | Apache-2.0 (OSS) / ticari | OSS sürümü sadece en güncel PostgreSQL diyalektini (18) destekliyor; her jOOQ yükseltmesi tüm kurulumlarda PostgreSQL major yükseltmesini zorlayabilir. Sürümlü diyalekte sabitlemek için ticari lisans (Express/Professional) bütçelenir; karar Faz 0'da (ADR-011). |
| Veritabanı | **PostgreSQL 18** | PostgreSQL | Güncel 18.6. Destek bitişi Kasım 2030. PG 19 beta aşamasında. |
| Migration | **Flyway** (açık kaynak motor) | Apache-2.0 | Modulith 2 entegrasyonu var. Liquibase lisans değişikliği nedeniyle tercih edilmedi (teyit Faz 0'da). |
| Kimlik | **Keycloak 26.8** | Apache-2.0 | Organizations, SCIM (destekleniyor), istemci sır döndürme, kullanıcı davet akışı |
| İşler | **db-scheduler** (+ db-scheduler-ui) | Apache-2.0 | JobRunr reddedildi (§6.12) |
| Yazdırma | **JasperReports 7.x** | LGPL | Java 25 uyumu Faz 0'da doğrulanacak |
| Excel | Apache POI (SXSSF) | Apache-2.0 | |
| API dokümanı | springdoc-openapi (Boot 4 uyumlu sürüm) | Apache-2.0 | |
| Önbellek | Caffeine | Apache-2.0 | Instance içi. Geçersizleştirme LISTEN/NOTIFY ile. |
| Oran sınırlama | Bucket4j | Apache-2.0 | |
| Gözlemlenebilirlik | Micrometer, OpenTelemetry (OTLP) | Apache-2.0 | |
| Statik analiz | Error Prone, ArchUnit, Spotless, japicmp | Apache-2.0 | |
| Test | JUnit (Boot BOM sürümü), Testcontainers, AssertJ, Modulith test | Apache-2.0 / MIT | |
| Build | **Maven** (çok modüllü + BOM) | Apache-2.0 | v1'deki Maven/Gradle karışıklığı biter |
| Kodlama kuralları | DTO ve değer nesneleri için Java `record`. Lombok sadece entity'lerde `@Getter/@Setter` ile sınırlı, `@Data` yasak. MapStruct sadece özel uç noktalarda ve opsiyonel. | | |

### 12.2 Frontend

§9.1'deki tablo. Özetle: Vue 3 + TypeScript, Vite 8, Vuetify 4, AG Grid Community 36, TanStack Query, Pinia, vee-validate + Zod, openapi-typescript/openapi-fetch, pnpm, Vitest, Playwright.

### 12.3 Altyapı ve araçlar

| Konu | Seçim |
|---|---|
| Kod deposu ve CI | **GitLab + GitLab CI** (v1'de zaten kullanılıyor, 19 servis `.gitlab-ci.yml` taşıyor). GitHub'a geçilecekse aynı hat GitHub Actions'a uyarlanır (§20). |
| İmaj kayıt deposu | GitLab Container Registry |
| İmaj imzası ve SBOM | cosign, Syft/CycloneDX |
| Bağımlılık güncelleme | Renovate |
| Sır taraması | gitleaks |
| Lisans taraması | CI'da bağımlılık lisans denetimi (izin verilen lisans listesi) |
| Yerel geliştirme | `docker compose` (PostgreSQL, Keycloak, Mailpit), `./mvnw`, `pnpm dev` |

### 12.4 Bilinçli olarak kullanılmayanlar

| Teknoloji | Neden | Ne zaman yeniden değerlendirilir |
|---|---|---|
| Mikroservisler | Küçük ekip için operasyon maliyeti büyük. Modüler monolit aynı sınırları daha ucuza verir. | Bir modülün bağımsız ölçeklenme ya da yayın ihtiyacı kanıtlanırsa (o modül ayrılır) |
| Kafka / RabbitMQ | Outbox + Modulith yeterli | Dış sistemlere yüksek hacimli olay akışı gerekirse |
| Redis | LISTEN/NOTIFY + Caffeine + DB oturumları yeterli | SaaS'ta ölçülmüş bir darboğaz olursa |
| Kubernetes | Compose ve tek düğüm on-prem için yeterli. SaaS başlangıçta VM + compose ile işletilir. | SaaS instance sayısı ve operasyon ihtiyacı büyürse |
| OpenSearch / Elasticsearch | pg_trgm yeterli | Belge içi tam metin arama veya çok büyük hacim gerekirse |
| BPMN motoru | Onay politikaları yeterli | Karmaşık, kullanıcı tanımlı süreç ihtiyacı kanıtlanırsa |
| GraphQL | REST + metadata yeterli, önbellek ve yetki daha basit | Gerekmiyor |
| MinIO | 2025'teki topluluk sürümü kısıtlamaları; on-prem'de dosya sistemi yeterli | S3 uyumlu yerel depo gerekirse alternatifler (Garage, SeaweedFS) değerlendirilir |

---

## 13. Kalite ve test stratejisi

### 13.1 Test piramidi

| Seviye | Ne test eder | Araç | Ne zaman çalışır |
|---|---|---|---|
| Birim | Domain kuralları, değer tipleri, hesaplamalar | JUnit, AssertJ | Her commit |
| Modül | Bir modülün application katmanı ve DB ile birlikte çalışması | `@ApplicationModuleTest`, Testcontainers | Her commit |
| Mimari | Modül sınırları, kırmızı çizgiler (K1–K15) | Modulith verify, ArchUnit, Error Prone | Her commit |
| Metadata | Tanımların şemaya, entity'ye ve DB'ye uyumu | Metadata doğrulayıcı | Her commit |
| Sözleşme | OpenAPI ve `@Stable` API'lerde kırıcı değişiklik | japicmp, openapi-diff | Her PR |
| İzolasyon | Tenant sızıntısı (API, iş, olay, dosya, cache, SSE) | Özel test paketi (2+ tenant) | Her PR |
| Yetki | Her kaynak ve eylem için izinsiz erişim | Metadata'dan üretilen testler | Her PR |
| Migration | Boş DB'den kurulum, N-1'den ve LTS'ten yükseltme | Testcontainers + şema anlık görüntüleri | Her PR / her sürüm |
| Frontend birim | Renderer, widget'lar, store'lar | Vitest | Her commit |
| E2E | Referans modül üzerinden kullanıcı akışları | Playwright (compose ortamında) | Her PR (duman testi), gece (tam paket) |
| Performans | Liste sorguları, belge kesinleştirme, eşzamanlı numaralandırma | k6 veya Gatling + örnek veri üretici | Gece / sürüm öncesi |
| Güvenlik | Bağımlılık açıkları, sır taraması, DAST (temel) | OWASP Dependency-Check / Trivy, gitleaks, ZAP | Her PR / gece |
| Kurulum | Temiz makinede `erpctl install` ve `update` | CI'da sanal makine | Her sürüm |

### 13.2 Kalite kapıları (merge için zorunlu)

- Bütün testler yeşil, mimari ve metadata doğrulayıcıları temiz.
- Platform modüllerinde yeni koda satır kapsamı ≥ %80. Kapsam araç olarak kullanılır, hedef değildir; asıl kriter her açık API davranışının bir testinin olmasıdır.
- Kırıcı değişiklik raporu boş ya da ADR ile onaylı.
- En az bir kod incelemesi. Platform değişikliklerinde platform sahibinin onayı.
- Performans bütçesi aşılmadı (gece raporu).

### 13.3 Referans modül

`modules/reference` (adı "Örnek Modül") satılmaz; platformun **yaşayan dokümantasyonu ve regresyon testidir.** Alanı nötrdür ama gerçek ERP akışlarını taklit eder ve platformun her yeteneğini kullanır:

- Ana veri: özel alanlı, şirkete bağlı alanlı
- Alt koleksiyon
- Örnek sipariş → örnek sevk → örnek fatura zinciri: iki para birimi, kısmi sevk, birim dönüşümü, ikincil miktar, hesaplama hattına takılmış örnek bir vergi adımı
- Boşluksuz numara, onay, kesinleştirme, ters kayıt, düzeltici belge
- Ledger, bakiye ve taahhüt
- Geriye tarihli kesinleştirme ve açılış bakiyesi importu
- Yazdırma şablonu, import ve export, webhook, kiosk ekranı

Faz 1'de doğar ve platformla birlikte büyür (§15.1). Gerçek `catalog` ve minimal `inventory` modüllerinin bu rolü üstlenmesi alternatifi §20'de açık soru olarak duruyor.

---

## 14. Repo ve kod organizasyonu

Tek bir Git deposu (monorepo) kullanılır: backend, frontend, dağıtım ve dokümantasyon birlikte sürümlenir. Bir API ve UI değişikliği tek bir PR'da yapılabilir.

```
erp/
 ├─ pom.xml                         Üst POM + BOM (bağımlılık sürümleri tek yerde)
 ├─ platform/
 │   ├─ kernel/  tenancy/  identity/  metadata/  crud/  documents/  ledger/
 │   ├─ audit/  files/  messaging/  jobs/  printing/  dataexchange/  integration/  refdata/
 │   └─ platform-test/             Ortak test altyapısı (Testcontainers, tenant fixture'ları, izolasyon paketi)
 ├─ modules/
 │   └─ reference/                 Referans modül (çekirdek döneminde tek modül)
 ├─ packs/                         (çekirdek sonrası)
 ├─ customers/                     (istisna; politika §5.5)
 ├─ app/                           Tek Spring Boot uygulaması: tüm modülleri birleştirir, SPA'yı gömer
 ├─ tools/
 │   ├─ erpctl/                    On-prem kurulum ve işletim CLI'ı
 │   ├─ scaffold/                  Modül ve kaynak üreteci (backend + frontend + metadata + migration + test)
 │   └─ metadata-validator/        Build zamanı doğrulayıcı (Maven eklentisi)
 ├─ web/                           pnpm workspace (§9.2)
 ├─ deploy/
 │   ├─ docker/                    Dockerfile'lar, Keycloak realm ve tema
 │   ├─ compose/                   dev, ci, onprem, monitoring profilleri
 │   └─ appliance/                 Sanal makine imajı tarifleri
 └─ docs/
     ├─ architecture/              Bu doküman
     ├─ adr/                       Karar kayıtları (NNNN-baslik.md)
     ├─ guides/                    Geliştirici rehberi, "10 adımda yeni modül"
     └─ runbooks/                  Operasyon el kitapları
```

**Paket kökü:** `com.smart.erp` (Faz 0'da kesinleşir).
**Kod dili:** Kod, tablo ve kolon adları **İngilizce**. Kullanıcıya görünen metinler i18n dosyalarında. Domain sözlüğü Ek A'da (v1'deki `uretimhareket` ve `getUHDURMASQuestions` gibi karışık adlandırma tekrarlanmaz).
**Commit kuralı:** Conventional Commits (v1'de commitlint zaten vardı).

---

## 15. Yol haritası

### 15.1 Yaklaşım: gerçekten dikey

Platform projelerinin en bilinen başarısızlık biçimi "framework önce" tuzağıdır: aylarca kurgusal ihtiyaçlar için soyutlama yazılır, ilk gerçek modül geldiğinde soyutlamaların yanlış olduğu anlaşılır. Bunu önlemek için:

1. **Önce ince bir uçtan uca dilim (Faz 1).** Faz 1 sonunda tek tenant'lı bir kurulumda kullanıcı giriş yapar, bir ana veri kaydı oluşturur, satırlı bir belge girer, kesinleştirir ve bakiyeyi görür. Her parça ilkel ama uçtan uca çalışır ve compose ile dağıtılmıştır. Sonraki fazlar bu dilimi **derinleştirir**.
2. **Referans modül Faz 1'de doğar** ve her fazda büyür. Bir yetenek ancak referans modülde kullanılıyorsa platforma girer.
3. **En riskli kısım erken gelir.** Metadata motorunu en çok zorlayacak yer belge ve satır ızgarasıdır. Bu yüzden Faz 0'daki S3 spike'ı ve Faz 1 dilimi, satırlı bir belgenin kesinleştirilmesini kapsar.
4. **Her fazda frontend işi vardır.** Her faz kendi yönetim ekranlarını da teslim eder (kullanıcı/rol, overlay ve özel alan editörü, ayarlar, şablonlar, webhook'lar, cihazlar). Ayrı bir "UI fazı" yoktur.
5. **Her faz bir demo ve otomatik testlerle kanıtlanan bir çıkış kriteriyle biter.** Kriter sağlanmadan sonraki faza geçilmez.
6. **Kesme listesi (§15.4) önceden yayınlanır.** Süre baskısında bu liste kesilir; çıkış kriterleri gevşetilmez.

### 15.2 Faz özeti

| Faz | Ad | Tahmini süre* | Bağımlılık |
|---|---|---|---|
| 0 | Zemin ve risk spike'ları | 3–4 hafta | – |
| 1 | İnce dikey dilim (yürüyen iskelet) | 6–8 hafta | 0 |
| 2 | Çok kiracılık ve platform DB | 5–6 hafta | 1 |
| 3 | Kimlik, yetki, organizasyon (+ yönetim ekranları) | 5–6 hafta | 2 |
| 4 | Metadata ve CRUD motoru, tam sürüm (+ overlay editörü) | 8–10 hafta | 3 |
| 5 | Belge ve ticari çekirdek | 9–11 hafta | 4 |
| 6 | Platform servisleri | 7–9 hafta | 5 |
| 7 | Operasyon ve teslimat sertleştirme | 6–8 hafta | 6 (temeli Faz 0'dan beri var) |
| 8 | Üretim hattı | 4–5 hafta | 7 |

\* **Varsayım:** 3 tam zamanlı geliştirici (2 backend ağırlıklı, 1 frontend ağırlıklı) + ürün sahibi. Faz toplamı 53–67 hafta. Entegrasyon, hata düzeltme ve öğrenme payıyla **gerçekçi süre 15–20 ay**; kesme listesi uygulanırsa 12–14 ay. 2 geliştiriciyle süre yaklaşık 1,4 katına çıkar. Bunlar tahmindir; asıl taahhüt çıkış kriterleridir.

```
Faz:   0 ──► 1 (uçtan uca dilim) ──► 2 ──► 3 ──► 4 ──► 5 ──► 6 ──► 7 ──► 8 ──► ÇEKİRDEK TAMAM
                                                                                     │
                                         catalog → partners → trade-base → inventory + e-İrsaliye …
Referans modül:     Faz 1'de doğar ─────────────────────────────────────► her fazda büyür
Operasyon temeli:   Faz 0'dan itibaren (compose, CI, imaj) ─────────────► Faz 7'de sertleşir
Frontend:           her fazda (o fazın ekranları ve yönetim arayüzleri)
```

### 15.3 Faz ayrıntıları

#### Faz 0 — Zemin ve risk spike'ları (3–4 hafta)

**Kapsam**
- Repo iskeleti (§14), Maven BOM, pnpm workspace.
- CI hattı: build, test, Modulith verify, ArchUnit, Error Prone, gitleaks, lisans denetimi, SBOM, imaj build ve imzası. Renovate.
- Compose profilleri (`dev`, `ci`). Tek imajda boş uygulama + SPA iskeleti. CI'da compose ile duman testi.
- Kodlama kuralları, PR ve ADR şablonları. Bölüm 18'deki ADR'ler "Önerildi" statüsünde açılır.
- **Üç spike (atılacak kod):**
  - **S1 — Tenancy yığını:** 2 tenant DB ile routing DataSource + Hibernate 7 + jOOQ + Modulith olay kaydı + db-scheduler. Bağlam yayılımı, tenant dolaşan yeniden yayın ve §4.5'teki Hibernate tuzakları.
  - **S2 — Kimlik:** BFF + Keycloak 26.8 Organizations + alt alan adıyla tenant seçimi + organization iddiası kontrolü + bearer zinciri (sabit tenant iddiası) + CSRF.
  - **S3 — Metadata ve belge dilimi:** bir YAML kaynak ve satırlı bir belge → jOOQ liste sorgusu → REST → Vuetify 4 formu + AG Grid satır ızgarası → kesinleştirme ve ledger yazımı.
- **Teyit listesi:**
  - Flyway ve Liquibase lisans durumu
  - JasperReports 7 + Java 25 uyumu ve sınıf beyaz listesi
  - springdoc'un Boot 4 uyumu
  - AG Grid Community'de Excel'den yapıştırma prototipi
  - Hibernate 7'de UUIDv7 üretimi
  - jOOQ ticari lisans kararı

**Çıkış kriteri:** CI yeşil. Tek imaj compose ile ayağa kalkıyor. Üç spike'ın sonucu ilgili ADR'lere "Kabul" ya da "Değişti" olarak işlendi. Spike kodu silindi.

#### Faz 1 — İnce dikey dilim / yürüyen iskelet (6–8 hafta)

**Kapsam**
- **Kernel'in çekirdeği:** BaseEntity, UUIDv7, `@Version`, Money/Quantity, Clock, ProblemDetail, TurkishText, temel ArchUnit kuralları (K5, K7, K10–K14).
- **Tek tenant, ama tenancy soyutlamalarıyla** (on-prem modu): tek tenant'lı routing DataSource, `TenantContext`, migration orkestratörünün ilk hali.
- **Giriş:** BFF (Keycloak, tek organization) ve basit rol kontrolü.
- **Metadata v0:** YAML yükleyici, temel alan tipleri, doğrulayıcının ilk hali; CRUD liste, form ve tekil okuma.
- **Belge v0:** taslak → kesinleşmiş, satırlar, boşluksuz numara, basit ledger ve bakiye.
- **Frontend:** shell iskeleti, `@erp/ui` temel bileşenleri, Liste/Form/Belge sayfalarının ilk hali (AG Grid satır ızgarası dahil).
- **Referans modül v0:** bir ana veri kaynağı + bir satırlı belge.
- Compose ile dağıtım ve CI'da E2E testi.

**Çıkış kriteri:** CI'da compose üzerinde çalışan bir Playwright testi: giriş → ana veri oluştur → satırlı belge gir → kesinleştir → numara ve bakiye doğru.

#### Faz 2 — Çok kiracılık ve platform DB (5–6 hafta)

**Kapsam**
- §4'ün tamamı: platform DB'si, tenant durum makinesi, alan adı eşleme.
- Çoklu tenant routing ve tembel havuzlar (LRU).
- Bağlam yayılımı, olay kaydı ve tenant dolaşan yeniden yayın.
- db-scheduler: tenant başına iş örnekleri ve kotalar.
- Migration orkestratörü: ayrı adım, şema sürüm aralığı, paketler için aktivasyonda kurulum.
- Provisioning (Keycloak organization dahil).
- Modül durumları ve aktivasyon kapısı (§5.6).
- Tenant export/import ve geri yükleme el kitabı (§4.7).
- Operatör CLI'ı.
- **Frontend:** tenant seçimi, bakım modu ekranı, operatör için temel tenant yönetim ekranı.

**Çıkış kriteri**
- 3 tenant ile izolasyon paketi (API, repository, olay, iş, cache) yeşil.
- Bağlamsız erişim hata fırlatıyor.
- Token'ın organization iddiası host'un tenant'ıyla eşleşmezse giriş reddediliyor.
- Bir tenant'taki migration hatası diğerlerini etkilemiyor. Eski sürüm instance'lar genişletilmiş şemayla çalışmaya devam ediyor.
- Tenant export → başka bir kuruluma import çalışıyor (kullanıcıların yeniden bağlanması dahil).
- Geri yükleme el kitabı otomatik testle doğrulandı: karantina, sayaç mutabakatı, iş temizliği.

#### Faz 3 — Kimlik, yetki, organizasyon (5–6 hafta)

**Kapsam**
- İzin kataloğu, roller, atamalar, kapsam tipleri SPI'ı, method security.
- Organizasyon ağacı ve şirkete bağlı alan deseni (§4.8).
- API istemcileri (sabit tenant iddiası).
- Cihaz kaydı (kiosk cihaz çerezi + native token) ve operatör oturumunun temeli.
- Personel realm'i ve impersonation.
- Güvenlik audit günlüğü, `/bootstrap`.
- **Yönetim ekranları:** kullanıcılar (davet, organization'dan çıkarma), roller ve izinler, organizasyon ağacı, API istemcileri, cihazlar.

**Çıkış kriteri**
- İzinsiz erişim 403, kapsam dışı kayıt 404 dönüyor (okuma ve yazma).
- Impersonation işlemleri audit'e çift kimlikle yazılıyor.
- Tenant admini bir kullanıcıyı kendi organization'ından çıkardığında kullanıcı diğer tenant'larda çalışmaya devam ediyor.
- E2E testi tarayıcıda token olmadığını doğruluyor.

#### Faz 4 — Metadata ve CRUD motoru, tam sürüm (8–10 hafta)

**Kapsam**
- **Metadata:** şema ve doğrulayıcı (build ve açılışta), tüm alan tipleri.
- **Overlay'ler:** model, birleştirme, önbellek (TTL, yeniden bağlanmada temizleme, sürüm kontrolü).
- **Özel alanlar** ve index işi; ayarlar ve değer listeleri.
- **CRUD motoru:** sorgu dili, eylemler, hook zinciri, alan izinleri, arama.
- Envers geçmişi, kaynak başına OpenAPI şemaları, metadata'dan üretilen yetki testleri, import/export'un temel hattı.
- **Frontend:** renderer'ın Liste/Form/Detay arketipleri, widget kayıt defteri, slot ve tam sayfa override, kayıtlı görünümler.
- **Yönetim ekranları:** **overlay ve özel alan editörü**, ayarlar, değer listeleri.

**Çıkış kriteri**
- Referans modülün ana verileri hiç controller ve Vue kodu yazılmadan tam çalışıyor.
- Overlay editörüyle etiket değiştirme, alan gizleme, zorunlu yapma ve özel alan ekleme (filtre, sıralama ve export dahil) deploy etmeden yapılabiliyor (E2E testi).
- 1 milyon satırlık tabloda **index'li alanlarla** filtrelenmiş ve sıralanmış listenin p95 süresi ≤ 300 ms.

#### Faz 5 — Belge ve ticari çekirdek (9–11 hafta)

**Kapsam**
- §6.7'nin tamamı:
  - Durum eksenleri, geçişler ve katılımcılar, düzeltme desenleri
  - Dört numaralandırma modu, dönemler ve açılış dönemi, onaylar
  - Satır modeli ve satır bazında bağlantılar
  - Para birimi ve kur modeli
  - Hesaplama hattı ve simülasyon uç noktası
  - Analitik boyutlar
- §6.8: ledger ve taahhüt defteri.
- **Frontend:** Belge arketipinin tam hali: satır ızgarası, Excel'den yapıştırma, klavye haritası, toplamlar, durum eylemleri, onay zaman çizelgesi.
- **Referans modül:** örnek sipariş → örnek sevk → örnek fatura zinciri.

**Çıkış kriteri** (hepsi referans modülde, UI dahil)
- İki para birimli sipariş → kısmi sevk → fatura zinciri çalışıyor; satır bazında bağlantılar ve karşılanma durumu doğru.
- Birim dönüşümü ve ikincil miktar doğru taşınıyor. Kur ve karşılık tutarlar kesinleştirmede sabitleniyor.
- Hesaplama hattına takılan örnek bir vergi adımı simülasyonda ve kesinleştirmede aynı sonucu veriyor. Yuvarlama kuralı testli.
- Yumuşak kapalı döneme yetkiyle geriye tarihli kesinleştirme, sonraki döneme ters kayıt ve açılış bakiyesi importu çalışıyor.
- 50 paralel kesinleştirmede boşluk ya da çift numara oluşmuyor. "Bakiye = hareketlerin toplamı" testi yeşil.

#### Faz 6 — Platform servisleri (7–9 hafta)

**Kapsam**
- `files`: sandbox alan adı.
- `messaging`: bildirim, e-posta, tek bağlantılı SSE, LISTEN/NOTIFY.
- `printing`: Jasper + XSLT + ZPL, şablon deposu, imzalama, izole render işçisi, toplu yazdırma, yazıcı profilleri.
- `dataexchange` tam hali: Türkçe biçimler, Windows-1254, açılış belgeleri.
- `integration`: outbox dışa aktarımı, webhook'lar, connector SPI'ı, sır kasası, idempotency, oran sınırlama, dış kimlik eşleme, raporlama view'ları.
- `refdata`: küresel veriler, TCMB connector'ı, UN/ECE, GTİP, fiyat endeksleri.
- Dashboard arketipi.
- **Yönetim ekranları:** şablonlar, webhook'lar, connector'lar.

**Çıkış kriteri**
- Referans belge PDF olarak basılıyor. Tenant şablonu izole işçide çalışıyor; şablondan DB'ye ya da ağa erişim denemesi engelleniyor (güvenlik testi).
- Import dry-run raporu doğru, tekrar yükleme idempotent. Türkçe biçimli ve Windows-1254 kodlamalı dosya doğru okunuyor.
- Webhook imza ve yeniden deneme testleri yeşil. TCMB kurları her gün otomatik çekiliyor.

#### Faz 7 — Operasyon ve teslimat sertleştirme (6–8 hafta)

**Kapsam**
- `erpctl`'in tüm komutları (`pg-upgrade` dahil).
- On-prem compose paketi + Caddy (zorunlu TLS), WAL arşivleme.
- Appliance VM imajı, imzalı imajlar ve manifest, air-gapped paket.
- Yükseltme ve geri dönüş otomasyonu (olay yayınlarının boşaltılması dahil).
- Lisans (üretme CLI'ı, doğrulama, salt okunur mod).
- Gözlemlenebilirlik profili.
- Kiosk arketipi.
- Güvenlik sertleştirmesi + bağımsız sızma testi.
- Boyutlandırma ölçümleri, Türkiye'de SaaS ortamının kurulması.

**Çıkış kriteri**
- Temiz bir Linux makinede kurulum ≤ 30 dakika sürüyor.
- Windows Server üzerinde appliance VM ile kurulum doğrulandı.
- Kasıtlı olarak bozulan bir migration'da otomatik geri dönüş çalışıyor.
- N-1 ve LTS'ten yükseltme CI'da yeşil. PostgreSQL major yükseltme provası başarılı.
- Geri yükleme tatbikatı otomatik çalışıyor.
- Sızma testinde kritik ya da yüksek seviyeli bulgu kalmadı.

#### Faz 8 — Üretim hattı (4–5 hafta)

**Kapsam**
- Scaffold: modül, kaynak ve belge tipi üreteçleri.
- Geliştirici rehberi: "10 adımda yeni modül", kaynak ve belge yazma, uzatma noktası ekleme, sektör paketi yazma.
- Modül katalog şablonu (§17.2).
- Sektör şablonu mekanizması, yükseltme yolu dahil (§17.3).
- B sınıfı ayarların yaşam döngüsü (§17.5).
- Sürüm süreci.
- §16 denetimi.

**Çıkış kriteri**
- Çekirdeği yazmamış bir geliştirici, rehber ve scaffold ile yeni bir ana veri kaynağını 1 günde, bir belge tipini 3–5 günde üretime hazır hale getiriyor (prova ile ölçülür).
- §16 kontrol listesinin tamamı yeşil.

### 15.4 Kesme listesi

Süre baskısında, çekirdek tamam kriterlerini bozmadan ilk modüllerin sonrasına ertelenebilecekler. Önce kesilecekler üsttedir:

1. Dashboard arketipi ve widget'lar
2. Kiosk arketipi ve operatör oturumu (ilk saha modülüyle gelir)
3. Air-gapped kurulum paketi
4. Appliance VM imajı (ilk Windows Server müşterisiyle gelir)
5. Onay akışında vekalet ve zaman aşımı yükseltmesi
6. TCMB dışındaki connector'lar
7. Toplu yazdırma ve yazıcı profilleri
8. Komut paleti ve global arama
9. Webhook yönetim ekranında yeniden gönderme

Kesilen her madde §16 kontrol listesinde "ertelendi" olarak işaretlenir ve hangi modülle geleceği yazılır.

---

## 16. "Çekirdek tamam" tanımı ve sonrası

### 16.1 Kontrol listesi

Aşağıdaki maddelerin **hepsi** otomatik testlerle ya da kayıtlı bir provayla kanıtlandığında çekirdek tamamdır ve iş modüllerine başlanır.

| # | Kriter | Kanıt |
|---|---|---|
| 1 | Yeni modül iskeleti tek komutla üretiliyor, derleniyor ve testleri geçiyor | Scaffold CI testi |
| 2 | Ana veri kaynağı **sadece entity + YAML + migration** ile uçtan uca çalışıyor: liste, filtre, form, detay, geçmiş, ekler, yetki, import ve export. Controller veya Vue kodu yazılmıyor. | Referans modül E2E + prova (≤ 1 gün) |
| 3 | Belge tipi şunları **hazır** alıyor: dört durum ekseni, numaralandırma modları, onay, dönem kontrolü, ledger, üç düzeltme deseni, yazdırma | Referans belge E2E + prova (≤ 3–5 gün) |
| 4 | Ticari çekirdek çalışıyor: iki para birimli sipariş → kısmi sevk → fatura zinciri, birim dönüşümü, ikincil miktar, hesaplama hattı, geriye tarihli kesinleştirme, sonraki döneme ters kayıt, açılış bakiyesi importu | Referans modül E2E |
| 5 | Modüller arası erişim sadece API, olay ve view üzerinden, build'de denetleniyor | Modulith verify + ArchUnit (negatif testlerle) |
| 6 | Müşteri özelleştirmesi deploy olmadan overlay editörüyle yapılıyor: etiket, gizleme, zorunluluk, özel alan (filtre/sıralama/export dahil), değer listesi, yazdırma şablonu, ayarlar | E2E testi |
| 7 | Aynı imaj SaaS'ta çok tenant'la, on-prem'de tek tenant'la çalışıyor | Compose profilleriyle E2E |
| 8 | Tenant izolasyonu API, iş, olay, dosya, cache ve SSE seviyesinde testle kanıtlı | İzolasyon paketi |
| 9 | Tenant provisioning ≤ 2 dakika, sektör şablonu uygulanıyor | Otomatik test |
| 10 | Tenant taşıma (kullanıcılarla) ve geri yükleme el kitabı çalışıyor | Otomatik test |
| 11 | On-prem temiz kurulum ≤ 30 dakika. N-1 ve LTS'ten yükseltme, otomatik geri dönüş ve PostgreSQL major yükseltme provası başarılı. | Kurulum CI'ı |
| 12 | Yedekten geri yükleme otomatik doğrulanıyor | Tatbikat işi |
| 13 | Lisans doğrulama ve salt okunur mod çalışıyor | Test |
| 14 | Güvenlik: repoda sır yok, BFF/CSRF/`__Host-` çerez aktif, otomatik yetki testleri var, admin işlemleri ve impersonation audit'leniyor, şablon sandbox testi geçiyor, sızma testi temiz | CI + rapor |
| 15 | Performans bütçeleri (§9.9 ve Faz 4–6 hedefleri) karşılanıyor | Gece performans raporu |
| 16 | Dokümantasyon: ADR'ler kabul edildi; geliştirici rehberi, klavye haritası, API referansı ve operasyon el kitapları hazır | Docs incelemesi |

### 16.2 Çekirdekten sonra: ilk modüller

| Sıra | Modül | Neden bu sırada |
|---|---|---|
| 1 | `catalog`: malzeme/ürün kartı, varyant eksenleri, birim dönüşümleri, kategori | Stok, satın alma, satış ve üretim hepsi buna dayanır |
| 2 | `partners`: cari kartları (müşteri, tedarikçi), adres, iletişim, vergi bilgileri | Belgelerin karşı tarafı |
| 3 | `trade-base`: vergi kuralları (KDV, tevkifat, ÖTV, istisnalar), fiyat listeleri, iskontolar | Satın alma ve satışın ortak hesaplama adımları (§6.7.9). Tevkifat, ilk hedef sektör olan tekstil fasonunda merkezidir. |
| 4 | `inventory` + `compliance-tr` (e-İrsaliye), birlikte | Adresler arası sevkiyat sevk irsaliyesi ister; e-İrsaliye mükellefi bir müşteride ikisi birlikte canlıya çıkar. **Müşterinin stok talebi burada karşılanır.** Lot, seri, son kullanma tarihi ve varyant takibi açılıp kapanabilir. |
| 5 | `purchasing` / `sales` | Sipariş → irsaliye → fatura zinciri |
| 6 | `compliance-tr`: e-Fatura ve e-Arşiv | Gelen e-Fatura alımı ve ETTN ile tekilleştirme dahil |
| 7 | `finance`: kasa, banka, çek/senet, cari hesap hareketleri | Türk KOBİ ERP'lerinin temel beklentisi |
| 8 | `production` + yerel donanım ajanı + kiosk saha ekranları | v1'deki saha uygulamalarının v4 karşılığı |
| 9 | `packs/textile`, `packs/food` | Tekstil: boyahane, örgü, kumaş kalite. Gıda: HKS ve e-Müstahsil dahil. |
| 10 | Muhasebe entegrasyonu → `accounting` (e-Defter) | Önce Logo gibi dış muhasebeyle birlikte çalışma, sonra kendi muhasebe modülü. Ledger altyapısı hazır. |

---

## 17. Üretim hattı süreci

### 17.1 Talep sınıflandırma (fit-gap)

Her yeni müşteri talebi modül kataloğuna karşı analiz edilir ve her ihtiyaç satırı şu sınıflardan birine atanır:

| Sınıf | Tanım | Karşılama | Ürün etkisi |
|---|---|---|---|
| **A — Konfigürasyon** | Mevcut modül ayar, overlay, özel alan ya da şablonla karşılıyor | Danışman konfigüre eder | Yok |
| **B — Ürün genişletme** | Mevcut modülde yok ama başka müşterilerde de olabilir | Modüle ya da pakete **ayarla açılıp kapanan** bir özellik olarak eklenir ve herkese yayınlanır | Sürüm notu, katalog güncellemesi |
| **C — Yeni modül/paket** | Yeni bir iş alanı | Tasarım incelemesi + ADR + scaffold ile yeni modül | Katalog girişi, lisans kalemi |
| **D — Müşteri uzantısı** | Genellenemeyen, sadece o müşteriye ait ihtiyaç | `customers/<müşteri>` altında, sadece açık uzatma noktalarıyla (§5.5) | Yıllık "ürüne alınabilir mi?" incelemesi |
| **E — Entegrasyon** | Dış bir sistemle veri alışverişi (muhasebe, e-ticaret, banka, makine) | Connector olarak (connector SPI'ı), mümkünse yeniden kullanılabilir şekilde | Connector kataloğu |

**Kural:** Bir talep D sınıfına ancak B'nin neden mümkün olmadığı yazılı olarak gerekçelendirilirse girer.

### 17.2 Modül kataloğu

Her modül ve paket için tek sayfalık bir katalog girişi tutulur. Satışın ve fit-gap analizinin ana aracıdır:

- Amaç ve kapsam, ana yetenekler
- Konfigürasyon seçenekleri (ayarlar, açılıp kapanan özellikler)
- Uzatma noktaları
- Bağımlılıklar (hangi modüllere dayanır)
- Lisans kalemi
- Olgunluk (deneysel / genel kullanım / LTS)
- Sınırlar ("bu modül şunu yapmaz")

### 17.3 Sektör şablonları

"Şablonlar değiştikçe müşteriye özel tasarımlar oturtacağım" ihtiyacının mekanizması budur.

- **Sektör şablonu**, sürümlü bir **konfigürasyon paketidir:** overlay'ler, özel alanlar, değer listeleri, ayarlar, yazdırma şablonları, rol şablonları, menü düzeni ve (varsa) aktif edilecek paketler.
- Tenant provisioning sırasında uygulanır. Örnek: "Tekstil üretici başlangıç", "Gıda üretim başlangıç".
- Bir müşterinin konfigürasyonu dışa aktarılabilir. Başarılı bir kurulum, bir sonraki benzer müşteri için yeni şablon adayıdır.
- **Yükseltme yolu:** Şablonun yeni bir sürümü, onu daha önce uygulamış tenant'lara **üç yollu birleştirme** ile önerilir: şablon v1 → tenant'ın değiştirdiği hal → şablon v2. Çakışmayan değişiklikler otomatik uygulanır, çakışanlar danışman onayıyla.
- Şablonlar kod gibi incelenir ve sürümlenir. CI'da "temiz tenant'a uygulanabiliyor mu?" ve "önceki sürümden yükseltilebiliyor mu?" testleri koşulur.

### 17.4 Müşteri devreye alma

```
Sözleşme/lisans → Provisioning → Sektör şablonu → Organizasyon ve kullanıcılar → Ana veri ve açılış bakiyeleri importu
 → Yazdırma şablonları → Eğitim → Paralel çalışma / test → Canlıya geçiş → Yoğun destek dönemi → Normal destek
```

Her adımın kontrol listesi `docs/runbooks/onboarding.md` dosyasında tutulur (Faz 8).

### 17.5 Kurallar

1. Müşteri için dal açılmaz. Her müşteri aynı sürüm hattındadır.
2. Müşteriye özel konfigürasyon tenant DB'sinde durur ve dışa aktarılabilir. Kod deposuna müşteri verisi girmez.
3. **B sınıfı ayarların yaşam döngüsü:**
   - Her B sınıfı geliştirme varsayılan olarak **kapalı** bir ayarla gelir; mevcut müşterilerin davranışı değişmez.
   - Her ayarın bir sahibi, bir gözden geçirme tarihi ve bir test matrisi vardır: ilgili testler ayar açıkken de kapalıyken de koşar.
   - Olgunlaşan ayar varsayılan olarak açılır. Anlamını yitirmiş ayar kaldırılır.
   - Ayar sayısının kontrolsüz büyümesi kod karmaşıklığı demektir; her sürümde ayar envanteri gözden geçirilir.
4. Her sürüm referans modül, izolasyon ve yükseltme testlerinden geçmeden yayınlanmaz.
5. B sınıfı geliştirmenin maliyetini kimin karşılayacağı ticari bir karardır (§20).

---

## 18. Karar kayıtları (ADR listesi)

Her karar Faz 0'da `docs/adr/` altında ayrı bir dosya olarak "Önerildi" statüsünde açılır. Spike ya da inceleme sonrası "Kabul edildi" olur.

**Geri dönüş maliyeti:**
- *Tek yön:* sonradan değiştirmek neredeyse yeniden yazım demek.
- *Pahalı:* her tenant'a ya da tüm modüllere dokunan, aylarca sürecek bir göç.
- *Kolay:* yerel bir değişiklik.

| ADR | Karar | Bölüm | Geri dönüş |
|---|---|---|---|
| 001 | Modüler monolit (Spring Modulith), mikroservis yok | §5 | Pahalı |
| 002 | Tek imaj ve tek kod tabanı: SaaS + on-prem | §4.1 | Tek yön |
| 003 | Tenant başına veritabanı | §4.2 | Tek yön |
| 004 | Platform DB ayrımı (tenant, oturum, iş, lisans, küresel referans veriler) | §4.3 | Pahalı |
| 005 | Keycloak 26.8, tek iş realm'i + Organizations. Tenant = organization. Organization iddiası host ile eşleşmeli. Tenant adminleri kullanıcıyı devre dışı bırakmaz. | §6.3.1, §4.4 | Pahalı |
| 006 | BFF + `__Host-` oturum çerezi; tarayıcıda token yok | §6.3.1 | Pahalı |
| 007 | Metadata: deklaratif YAML + Java davranış, UI'dan bağımsız, overlay'ler DB'de | §6.4 | Tek yön |
| 008 | Özel alanlar: `ext jsonb` + isteğe bağlı ifade index'i | §6.4.5 | Pahalı |
| 009 | Modüller arası FK sadece bağımlıdan sağlayıcıya ve sadece PK'ya; JPA ilişkisi yok. View'lar sadece kendi tablolarını okur ve eklemeli değişir. | §5.4 | Pahalı |
| 010 | Platform ve iş modülü şemaları tüm tenant'larda, paket ve uzantı şemaları sadece aktivasyonda. Modül durumları `INACTIVE/ACTIVE/RETIRED`. | §4.6, §5.6 | Pahalı |
| 011 | Yazma JPA/Hibernate, okuma jOOQ. Sürümlü diyalekt için ticari jOOQ lisansı, PostgreSQL major yükseltme yolu. | §12.1, §11.2 | Pahalı |
| 012 | Olay kaydı tenant DB'sinde; Modulith'in açılışta yeniden yayın özelliği kapalı, tenant dolaşan yeniden yayın; completion mode silme/arşiv | §4.4 | Pahalı |
| 013 | UUIDv7, uygulama tarafında üretilir | §6.1 | Pahalı |
| 014 | db-scheduler platform DB'sinde; tenant başına iş örnekleri; `eventId` ile tekilleştirme | §6.12 | Pahalı |
| 015 | Routing DataSource; Hibernate multi-tenancy SPI'ı yok; ikinci seviye cache yok; tembel ve sınırlı havuzlar | §4.5 | Pahalı |
| 016 | Redis yok; instance'lar arası yayın LISTEN/NOTIFY ile, TTL ve sürüm kontrolüyle | §5.2, §6.11 | Kolay |
| 017 | Arama: `search_text` + `pg_trgm`, ayrı arama motoru yok | §6.16 | Kolay |
| 018 | Belge durum eksenleri (yaşam döngüsü, onay, karşılanma, harici) ve üç düzeltme deseni | §6.7.1–§6.7.3 | Pahalı |
| 019 | BPMN yok, onay politikaları | §6.7.6 | Kolay |
| 020 | Vuetify 4 + `@erp/ui` sarmalayıcısı; PrimeVue reddedildi | §9.1 | Pahalı |
| 021 | AG Grid Community; Enterprise kararı ertelendi | §9.1 | Kolay |
| 022 | Yerel donanım ajanı: protokol ve kimlik çekirdekte, ajan sonraki modülde | §6.18 | Kolay |
| 023 | Saha: PWA ve kiosk arketipi; native kabuk gerekirse Capacitor | §9.10 | Kolay |
| 024 | On-prem: Linux + compose + `erpctl`, zorunlu TLS; Windows için appliance VM | §11.1 | Pahalı |
| 025 | Tek sürüm hattı + LTS; Boot minor sürümleri düzenli yükseltilir | §11.8 | Pahalı |
| 026 | Yazdırma: Jasper + XSLT (e-belge) + ZPL; jsreport yok | §6.13 | Kolay |
| 027 | Kod İngilizce, UI i18n; Türkçe metin kuralları (`Locale.ROOT`, fold, ICU) | §7.8 | Kolay |
| 028 | Monorepo (backend + frontend + deploy + docs), GitLab CI | §14 | Kolay |
| 029 | Başlangıç sürümleri: Java 25, Spring Boot 4.1, PostgreSQL 18 | §12 | Pahalı |
| 030 | Para ve miktar için `numeric` + değer tipleri; Float/Double yasak | §7.3 | Tek yön |
| 031 | Para birimi ve kur modeli: işlem, yerel ve raporlama tutarı + kesinleştirmede sabitlenen kur | §6.7.8 | Tek yön |
| 032 | Yazdırma şablonu güvenliği: imzalı şablon, sınıf beyaz listesi, izole render işçisi | §6.13 | Kolay |
| 033 | Numaralandırma modları (boşluksuz en son, dizi, harici, ertelenmiş toplu), tarih monotonluğu, seri seçim SPI'ı | §6.7.4 | Pahalı |
| 034 | Belge satırı modeli (girilen/temel/ikincil miktar) ve satır bazında çoka-çok bağlantılar | §6.7.7 | Tek yön |
| 035 | Hesaplama hattı SPI'ı + yuvarlama politikası + simülasyon uç noktası; vergi ve fiyat kuralları `trade-base`'de | §6.7.9 | Pahalı |
| 036 | Analitik boyut seti (satırdan ledger'a) | §6.7.10 | Pahalı |
| 037 | Ledger sözleşmesi + taahhüt defteri | §6.8 | Pahalı |
| 038 | Migration orkestratörü ayrı adım, şema sürüm aralığı, `outOfOrder` kapalı | §4.6 | Pahalı |
| 039 | Oturum deposu: Spring Session JDBC (platform DB) | §6.3.1 | Kolay |
| 040 | Üç kimlik yolu: tarayıcı oturumu, OIDC bearer (sabit tenant iddiası), cihaz kimliği (kiosk çerezi / native token) | §6.3.1, §6.18 | Pahalı |
| 041 | Küresel referans veriler platform DB'sinde; belgeler kod/değer kopyalar | §6.17 | Pahalı |
| 042 | Personel realm'i, step-up doğrulama ve audit'li impersonation | §6.3 | Kolay |
| 043 | Tenant geri yükleme el kitabı ve on-prem WAL arşivleme | §4.7.1, §11.4 | Kolay |
| 044 | Platformu nötr bir referans modülle kanıtlama (alternatif: gerçek catalog + minimal inventory; §20) | §13.3 | Kolay |

---

## 19. Riskler

| # | Risk | Olasılık | Etki | Azaltma |
|---|---|---|---|---|
| R1 | **"Framework önce" tuzağı:** yanlış soyutlamalar, aşırı genel motor | Yüksek | Yüksek | Faz 1'deki uçtan uca dilim, referans modül, "kullanılmayan yetenek platforma girmez" kuralı, her fazda demo (§15.1) |
| R2 | Metadata motoru katılaşır, özel ihtiyaçlarda engel olur | Orta | Yüksek | Kaçış yolları zorunlu ilke (§6.5, §9.5). Özel sayfa ve uç nokta oranı izlenir. |
| R3 | Süre aşımı (gerçekçi tahmin 15–20 ay) | Yüksek | Orta | Faz çıkış kriterleri, yayınlanmış kesme listesi (§15.4), ADR'li kapsam kararları |
| R4 | Ekipte yetkinlik açığı (Boot 4, Modulith, jOOQ, Vue 3, Vuetify 4) | Orta | Orta | Faz 0 spike'ları, eşli çalışma, rehberler, kod inceleme |
| R5 | Keycloak'ın karmaşıklığı ve küçük on-prem kurulumdaki ayak izi | Orta | Orta | Standart OIDC, Keycloak'a özgü kod tek adaptörde, yedek plan: Spring Authorization Server |
| R6 | Tenant başına DB'nin operasyon yükü (havuzlar, migration, yedek) | Orta | Orta | Tam otomasyon (`erpctl`, orkestratör), bağlantı tavanı hesabı, eşik izleme, küme bölme (§4.2, §4.5) |
| R7 | Bağımlılık lisans değişiklikleri (PrimeVue örneği); jOOQ OSS'nin tek diyalekt kısıtı | Orta | Orta | Sarmalayıcı katmanlar, CI'da lisans taraması, Apache/MIT tercihi, ticari jOOQ bütçesi, PostgreSQL yükseltme yolu |
| R8 | Sık mevzuat değişikliği (e-belge hadleri, formatlar) | Yüksek | Orta | `compliance-tr` modülünde izolasyon, eşikler parametre, takılabilir harici durum profilleri |
| R9 | Spring Boot 4.x ekosisteminin olgunluğu | Orta | Orta | Faz 0 teyit listesi; sorunlu kütüphane için alternatif ya da bekleme |
| R10 | v1 müşterilerinin bakım yükü (iki sistemi birden taşımak) | Yüksek | Orta | v1 için dondurma politikası ve geçiş planı (§20) |
| R11 | Bilginin tek kişide toplanması | Orta | Yüksek | ADR'ler, rehberler, kod sahipliği rotasyonu |
| R12 | Saha ve donanım gereksinimlerinin hafife alınması | Orta | Orta | Cihaz kimliği ve protokolü çekirdekte; v1 envanteri (§2.3) üretim modülünün girdisi |
| R13 | Tek realm'in küresel kuralları (benzersiz kullanıcı adı, realm geneli politikalar, LDAP sınırı) müşteri beklentisiyle çatışır | Orta | Orta | §6.3.1'deki kurallar, ayrı realm istisnası, satış öncesi açık iletişim |
| R14 | Tenant'ın yüklediği içerikle kod çalıştırma (şablonlar, dosyalar) | Orta | Kritik | İmzalı şablonlar, izole render işçisi, sandbox alan adı, güvenlik testleri (§6.13, §10.1) |
| R15 | Geri yükleme sonrası dış dünyayla tutarsızlık (numara tekrarı, çift webhook) | Düşük | Yüksek | Tenant geri yükleme el kitabı (§4.7.1), WAL arşivleme |
| R16 | Ticari çekirdeğin (para birimi, satır, vergi hattı) eksik tasarlanması; ilk modüllerde yeniden mimari ihtiyacı | Orta | Yüksek | §6.7.7–§6.7.10 Faz 5'te referans modülün ticari zinciriyle kanıtlanır; mali müşavir incelemesi |

---

## 20. Açık sorular

| # | Soru | Neden önemli | Ne zamana kadar |
|---|---|---|---|
| 1 | Ekip büyüklüğü ve rol dağılımı nedir? | Süre tahminleri ve paralellik (§15.2) | Faz 0 öncesi |
| 2 | v1 müşterileri v4'e geçecek mi, v1'de mi kalacak? v1 ne zaman dondurulacak? | Bakım yükü, geçiş araçları ihtiyacı | Faz 0 |
| 3 | v1'de sızmış sırlar iptal edildi mi? (CI kimlik bilgileri, Telegram bot token'ı, Cortex ve mobil servis hesabı şifreleri) | Aktif güvenlik riski | **Hemen** |
| 4 | SaaS hangi sağlayıcıda (Türkiye'de) barındırılacak? | KVKK, maliyet, yedekleme mimarisi | Faz 7 öncesi |
| 5 | GitLab ile mi devam edilecek, başka bir sağlayıcıyla mı? | CI hattı | Faz 0 |
| 6 | Dışarıdan iş ortağı ya da bayi geliştiricisi olacak mı? | `@Stable` API kapsamı, UI kütüphanesi lisansları | Faz 4 |
| 7 | Ürün adı ve paket kökü ne olacak? | Repo, imaj ve paket isimleri | Faz 0 |
| 8 | Saklama süreleri, kur kuralları, KVKK ve e-belge yorumları için mali müşavir ve hukuk desteği kimden alınacak? | §6.7.8, §10.3–§10.5 | Faz 5 |
| 9 | AG Grid Enterprise gerekli mi? | Kullanıcı testlerine göre (Excel benzeri kullanım) | Faz 5 sonu |
| 10 | Fiyatlandırma modeli ne olacak (kullanıcı, modül, tesis başına)? | Lisans dosyası alanları, hak modeli | Faz 7 |
| 11 | Platformu nötr bir referans modülle mi, yoksa gerçek `catalog` + minimal `inventory` ile mi kanıtlayalım? | Gerçek modüller gerçek ihtiyaçları daha erken ortaya çıkarır ve stok talebini öne çeker. Nötr modül ise çekirdeği müşteri baskısından korur. Varsayılan: nötr referans modül ("önce çekirdek" tercihin). | Faz 0 |
| 12 | B sınıfı geliştirmelerin maliyeti kimde: talep eden müşteride mi, ürün bütçesinde mi, paylaşımlı mı? | Üretim hattının ticari sürdürülebilirliği (§17.5) | Faz 8 |
| 13 | jOOQ ticari lisansı bütçelenecek mi? | ADR-011, PostgreSQL sürüm bağımlılığı | Faz 0 |

---

## Ek A — Sözlük

| Terim | Anlamı |
|---|---|
| **Tenant (kiracı)** | Sistemi kullanan müşteri kuruluş. Her birinin ayrı veritabanı vardır. |
| **Platform DB** | Tenant'tan bağımsız verilerin tutulduğu veritabanı: tenant kaydı, oturum, iş, lisans, küresel referans veriler |
| **Sağlayıcı / bağımlı modül** | Kullanılan modül sağlayıcı, kullanan modül bağımlıdır (ör. `catalog` → sağlayıcı, `inventory` → bağımlı). Bağımlılık her zaman bağımlıdan sağlayıcıya doğrudur. |
| **Kaynak (resource)** | CRUD motorunun yönettiği iş nesnesi tanımı (ör. `catalog.item`) |
| **Kayıt** | Veritabanı kaydı (record). Bu dokümanda "posting" anlamında kullanılmaz. |
| **Overlay** | Kodda tanımlı metadata'nın üzerine tenant ya da paket bazında uygulanan özelleştirme yaması |
| **Özel alan** | Tenant'ın kod yazmadan eklediği alan (`ext` jsonb) |
| **Değer listesi** | Modülün tanımladığı, tenant'ın genişletebildiği seçenek listesi |
| **Belge** | Yaşam döngüsü olan iş kaydı (fiş, sipariş, irsaliye, fatura…) |
| **Durum eksenleri** | Belgenin birbirinden bağımsız dört durumu: yaşam döngüsü, onay, karşılanma, harici durum |
| **Kesinleştirme (posting)** | Belgenin hukuki ve mali etkisinin doğduğu an: ledger yazılır, yasal numara verilir. Kesinleşmiş belge = `POSTED`. |
| **Kesinleştirme tarihi** | Belgenin ledger'a ve döneme etki ettiği tarih (posting date) |
| **Düzeltme desenleri** | Ters kayıt (storno), yasal iptal, düzeltici belge (iade, fiyat farkı, kur farkı faturası) |
| **Revizyon** | Onaylanmış ama kesinleşmemiş bir belgenin yeni sürümü (sipariş revizyonu) |
| **Ledger (defter)** | Sadece eklenen hareket kayıtları + bakiye projeksiyonu |
| **Taahhüt defteri** | Kesinleşmemiş ama bağlayıcı miktarların (rezervasyon, tahsis) tutulduğu defter |
| **Boyut seti** | Satırdan ledger'a taşınan analitik boyutlar (masraf merkezi, proje…) |
| **Hesaplama hattı** | Fiyat, iskonto ve vergi adımlarının sıralı çalıştığı, modüllerin katkı verdiği hesaplama mekanizması |
| **Yerel / raporlama tutarı** | Şirketin fonksiyonel para birimindeki ve raporlama dövizindeki karşılık tutarlar |
| **Mali dönem** | Kesinleştirme kontrolünde kullanılan dönem (yıl/ay). *Özel hesap dönemi* takvim yılından farklı mali yıldır. |
| **Dönem kilidi** | Bir dönemde kesinleştirmeyi engelleyen durum (`SOFT_CLOSED`, `CLOSED`) |
| **BFF** | Backend-for-Frontend: tarayıcı adına OAuth akışını yürüten ve oturum çereziyle çalışan sunucu katmanı |
| **Personel realm'i** | Destek personelinin ayrı tutulduğu Keycloak realm'i |
| **Outbox** | Olayın iş verisiyle aynı transaction'da yazılıp sonra güvenle iletilmesi deseni |
| **Uzatma noktası (SPI)** | Bağımlı modülün sağlayıcının davranışına katılabildiği açık arayüz |
| **Sektör paketi** | Tekstil, gıda gibi bir sektöre özgü kod (varlık, süreç, uzatma katkıları) |
| **Sektör şablonu** | Sürümlü konfigürasyon paketi (overlay, ayar, değer listesi, yazdırma şablonları); provisioning'de uygulanır. "Yazdırma şablonu" ve "Excel şablonu" ile karıştırılmamalıdır. |
| **Müşteri uzantısı** | Son çare olarak tek müşteriye özel kod |
| **Referans modül** | Satılmayan, platformun her yeteneğini kullanan örnek ve regresyon modülü |
| **Render işçisi** | Ürün dışı yazdırma şablonlarının DB ve ağ erişimi olmadan çalıştığı izole süreç |
| **Yürüyen iskelet** | İlk günden uçtan uca çalışan, dağıtılabilir en küçük sistem |
| **Genişlet/daralt (expand/contract)** | Eski ve yeni kodun aynı şemayla çalışabildiği migration disiplini |
| **Yasal bekletme (legal hold)** | Süresi dolmuş kayıtların silinmesini ve anonimleştirilmesini durduran bayrak |
| **LTS** | Uzun süreli desteklenen sürüm |
| **Kiosk** | Saha tabletleri için tam ekran, operatör odaklı arayüz |
| **Yerel donanım ajanı** | Müşteri sahasında seri port, yazıcı, tartı gibi cihazlara köprü olan küçük servis |
| **Etkin tarih** | `valid_from/valid_to` ile zamana bağlı geçerlilik (`daterange` ya da `tstzrange`) |
| **İdempotency** | Aynı isteğin tekrarlanmasının ek etki yaratmaması |

---

## Ek B — Kaynaklar

Teknoloji, lisans ve mevzuat bilgileri aşağıdaki kaynaklardan Ekim 2026'da doğrulandı. Kod incelemesi bulguları ve ham araştırma kayıtları `docs/architecture/_research/` altındadır.

> **Uyarı:** `_research/raw/` ham ajan kayıtlarını içerir. v1 kodundan okunmuş sırlar burada da bulunabilir. Bu klasör kod deposuna eklenmemeli, işi bitince silinmelidir.

**Platform ve backend**
- Spring Boot: https://spring.io/projects/spring-boot · 4.1 sürüm notları: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes
- Spring Modulith 2.0 GA: https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released/
- Spring Modulith ve çok kiracılık tartışması: https://github.com/spring-projects/spring-modulith/discussions/128
- Spring Authorization Server'ın Spring Security 7'ye taşınması: https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/
- PostgreSQL sürüm politikası: https://www.postgresql.org/support/versioning/
- jOOQ destek matrisi: https://www.jooq.org/download/support-matrix
- Flyway sürümleri: https://www.red-gate.com/products/flyway/editions
- JobRunr fiyatlandırma ve özellikler: https://www.jobrunr.io/en/pricing/
- Hibernate ORM 7 multi-tenancy: https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/context/spi/MultiTenancy.html

**Kimlik**
- Keycloak 26.8.0 sürüm duyurusu: https://www.keycloak.org/2026/10/keycloak-2680-released
- Keycloak realm ölçeklenebilirliği: https://github.com/keycloak/keycloak/discussions/11074
- Realm ve Organizations ölçümü (Mayıs 2026): https://gofranz.com/blog/keycloak-multi-tenancy-realms-vs-organizations/
- Keycloak Organizations: https://www.keycloak.org/2024/06/announcement-keycloak-organizations
- RFC 10017, OAuth 2.0 for Browser-Based Applications: https://www.rfc-editor.org/rfc/rfc10017.html

**Frontend**
- PrimeUI lisans değişikliği: https://primeui.dev/nextchapter · https://primeui.dev/licenses/community · https://primeui.dev/licenses/commercial
- Vite 8: https://vite.dev/blog/announcing-vite8
- Vuetify 4.0.0: https://github.com/vuetifyjs/vuetify/releases/tag/v4.0.0
- AG Grid Community ve Enterprise: https://www.ag-grid.com/vue-data-grid/community-vs-enterprise/

**ERP emsalleri**
- Odoo özel modül yükseltme: https://www.odoo.com/documentation/19.0/developer/reference/upgrades/upgrade_custom_db.html · Desteklenen sürümler: https://www.odoo.com/documentation/19.0/administration/supported_versions.html
- Frappe özelleştirme ve hook'lar: https://docs.frappe.io/framework/user/en/basics/doctypes/customize · https://docs.frappe.io/framework/user/en/python-api/hooks
- Business Central IsHandled deseni: https://learn.microsoft.com/en-us/dynamics365/business-central/dev-itpro/developer/devenv-use-ishandled-pattern
- Business Central numara dizileri: https://learn.microsoft.com/en-us/dynamics365/business-central/dev-itpro/developer/devenv-number-sequences
- Business Central kullanımdan kaldırma kuralları: https://learn.microsoft.com/en-us/dynamics365/business-central/dev-itpro/developer/devenv-deprecation-guidelines
- SAP clean core: https://news.sap.com/2025/08/extend-sap-s4hana-cloud-right-way-clean-clear/
- Axelor özel alanlar: https://docs.axelor.com/adk/6.1/dev-guide/models/custom-fields.html · Görünüm uzantıları: https://docs.axelor.com/adk/8.0/dev-guide/views/extensions.html

**Türkiye mevzuatı**
- GİB e-Belge: https://ebelge.gib.gov.tr/duyurular.html · Teknik kılavuzlar: https://ebelge.gib.gov.tr/efaturateknikkilavuzlar.html
- VUK Genel Tebliği Sıra No 589: https://www.alomaliye.com/2025/12/31/vergi-usul-kanunu-genel-tebligi-sira-no-589/
- VUK Genel Tebliği Sıra No 593: https://www.kardemymm.com.tr/bulten-tr/bulten-2026-56-593-sira-no-lu-vergi-usul-kanunu-genel-tebligi-resmi-gazetede-yayimlandi/
