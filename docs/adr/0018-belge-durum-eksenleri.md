# ADR-0018: Belge durum eksenleri ve üç düzeltme deseni

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.7.1–§6.7.3 |

## Bağlam

v1'de her modül kendi durum tablosunu kurdu (`PurchaseOrderStatus`, `DyeingPurchaseOrderStatus`…) ve kodda `"A"`/`"K"` gibi sihirli kodlar vardı (§2.2 H8). Çok adımlı kayıtlar istemciden art arda HTTP çağrılarıyla yapılıyordu (H1). Türkiye pratiğinde "iptal" tek bir şey değildir: e-İrsaliye gönderen tarafından iptal edilemez, ticari e-Fatura'nın 8 günlük kabul/ret süresi vardır, e-Arşiv iptali bildirimlidir (§10.4). Kesinleşmiş belgenin içerik kolonları değişmez (K4).

## Karar

- Belge durumu birbirinden bağımsız **dört eksendir**: yaşam döngüsü (`DRAFT` → `POSTED` → `REVERSED`; `DRAFT` → `CANCELLED`), onay (`NONE`, `PENDING`, `APPROVED`, `REJECTED`), karşılanma (`OPEN`, `PARTIAL`, `CLOSED`, `SHORT_CLOSED`) ve harici durum (belge tipi ve profil bazında takılabilir durum makinesi, SPI). Belge tipi hangi eksenleri kullanacağını konfigüre eder; eksen değerleri yan tablolarda tutulur.
- `CANCELLED` sadece hiç kesinleşmemiş taslaklar içindir. Onaylanmış ama kesinleşmemiş belgede değişiklik yeni bir **revizyon** olarak yapılır.
- Her geçiş tek transaction'lı bir komuttur. Sıra: izin kontrolü → koruma koşulları → dönem kontrolü → katılımcılar → numara alma (en son) → olay. Katılımcılar (SPI) her geçişe bağlanabilir; biri başarısız olursa hepsi geri alınır.
- Üç düzeltme deseni: **ters kayıt** (iç belgeler; ledger etkileri ters çevrilir, asıl belge `REVERSED`), **yasal iptal** (harici durum ekseninde iptal + iç tarafta ters kayıt; numara yeniden kullanılmaz), **düzeltici belge** (iade, fiyat farkı, kur farkı faturası; asıl belge değişmez).
- Koruma kuralı: bağlı aşağı akış belgeleri olan bir belge, onlar düzeltilmeden ters çevrilemez.

## Sonuçlar

- Olumlu: Her belge aynı çatıyı kullanır; v1'deki "istemci 6 ayrı istek atıyor" sorunu biter. e-belge yaşam döngüleri `compliance-tr` profilleri olarak yeniden mimari gerektirmeden eklenir (§10.4).
- Olumsuz: Durum tek bir alan değildir; her belge tipi eksen konfigürasyonu ve yan tablolarla çalışır.
- Takip: Harici durum profilleri (e-Fatura `TEMEL`/`TICARI`/`IHRACAT`, e-Arşiv, e-İrsaliye, HKS) compliance-tr modülünde gelir.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Modül başına durum tablosu ve tek durum alanı (v1) | Davranış veriye gömülü, kırılgan (§2.2 H8) |

## Doğrulama

S3 spike'ı: satırlı bir belgenin kesinleştirilmesi ve ledger yazımı (§15.3). Faz 5 çıkış kriteri: iki para birimli sipariş → kısmi sevk → fatura zincirinde karşılanma durumu doğru; yumuşak kapalı döneme yetkiyle geriye tarihli kesinleştirme ve sonraki döneme ters kayıt çalışıyor. Çekirdek tamam kriteri §16.1 madde 3.
