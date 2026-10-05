# ADR-0044: Platformu nötr bir referans modülle kanıtlama

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §13.3 |

## Bağlam

Platform projelerinin en bilinen başarısızlık biçimi "framework önce" tuzağıdır: kurgusal ihtiyaçlar için yazılan soyutlamalar ilk gerçek modülde yanlış çıkar (§15.1, §19 R1). Çekirdek bitmeden iş modülü yazılmaz ve her platform özelliği kullanılarak kanıtlanmalıdır (§3.1 madde 1).

## Karar

- `modules/reference` ("Örnek Modül") satılmaz; platformun **yaşayan dokümantasyonu ve regresyon testidir**. Alanı nötrdür ama gerçek ERP akışlarını taklit eder ve platformun her yeteneğini kullanır:
  - Özel alanlı ve şirkete bağlı alanlı ana veri; alt koleksiyon.
  - Örnek sipariş → örnek sevk → örnek fatura zinciri: iki para birimi, kısmi sevk, birim dönüşümü, ikincil miktar, hesaplama hattına takılmış örnek bir vergi adımı.
  - Boşluksuz numara, onay, kesinleştirme, ters kayıt, düzeltici belge; ledger, bakiye ve taahhüt.
  - Geriye tarihli kesinleştirme ve açılış bakiyesi importu; yazdırma şablonu, import ve export, webhook, kiosk ekranı.
- Faz 1'de doğar ve platformla birlikte büyür. Bir yetenek ancak referans modülde kullanılıyorsa platforma girer (§15.1).

## Sonuçlar

- Olumlu: Nötr modül çekirdeği müşteri baskısından korur (§20 soru 11).
- Olumsuz: Gerçek modüller gerçek ihtiyaçları daha erken ortaya çıkarır ve stok talebini öne çekerdi (§20 soru 11). Referans modül satılmayan bir koddur.
- Takip: §20 soru 11 Faz 0'da cevaplanır; varsayılan nötr referans modüldür ("önce çekirdek" tercihi).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Gerçek `catalog` + minimal `inventory` | Açık soru (§20 soru 11). Gerçek ihtiyaçları erken çıkarır ve stok talebini öne çeker; nötr modül ise çekirdeği müşteri baskısından korur. Varsayılan nötr modül. |

## Doğrulama

§20 soru 11'in Faz 0'da cevaplanması. Faz 1 çıkış kriteri: referans modül v0 (bir ana veri kaynağı + bir satırlı belge) üzerinde compose'da çalışan Playwright testi: giriş → ana veri oluştur → satırlı belge gir → kesinleştir → numara ve bakiye doğru.
