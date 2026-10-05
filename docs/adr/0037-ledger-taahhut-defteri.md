# ADR-0037: Ledger sözleşmesi + taahhüt defteri

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.8 |

## Bağlam

Stok, cari hesap, muhasebe, prim ve kapasite gibi "hareket + bakiye" yapıları ortak bir araç takımı ister. Kesinleşmiş belge ve ledger kaydının içerik kolonları değişmez (K4). v1'de toplu yeniden hesaplama transaction'sız silip yeniden yazıyor ve elle girilen düzeltmeleri sıfırlıyordu (§2.2 H10, H11). Tasarım Business Central'daki miktar defteri ile değer defterinin ayrılması ve Frappe'nin geriye tarihli kayıt (repost) maliyeti incelenerek yapıldı.

## Karar

- Ledger kaydı **sadece eklenir**. DB seviyesinde içerik kolonlarında `UPDATE` ve `DELETE` trigger ile engellenir; KVKK anonimleştirmesi audit'lenen ayrıcalıklı bir fonksiyonla yapılır.
- Kayıt alanları: belge ve satır referansı, kesinleştirme tarihi, boyutlar (§6.7.10), temel miktar + opsiyonel ikincil miktar, üç para birimli tutarlar ve kur bilgisi (§6.7.8), ters kayıt referansı. Sadece değer taşıyan kayıtlar (kur farkı, değerleme düzeltmesi) miktarsız olabilir.
- **Bakiye projeksiyonu** boyut kırılımında tutulur ve aynı transaction'da satır kilidiyle upsert edilir. Tutarlılık testi: "bakiye = hareketlerin toplamı".
- **Taahhüt defteri** kesinleşmemiş ama bağlayıcı miktarlar (sipariş rezervasyonu, üretim tahsisi) için ayrı bir defter tipidir: herhangi bir geçişte yazılır, karşılanma ya da kapanışta serbest bırakılır. Kullanılabilir miktar = bakiye − taahhüt.
- Ters kayıt yardımcıları belgenin tüm ledger etkisini ters çevirir. "Belirli tarih itibarıyla" sorguları dönem sonu anlık görüntüleri + sonraki hareketlerle yapılır.
- Geriye tarihli kesinleştirme politikası modül bazında konfigüre edilir; stok değerlemesindeki kararı envanter modülü verir, çekirdek mekanizmayı sağlar.

## Sonuçlar

- Olumlu: Tüm modüller aynı değişmez defteri ve bakiye mekanizmasını kullanır; e-Defter için ledger değişmezliği hazırdır (§10.4).
- Olumsuz: Bakiye projeksiyonları pessimistic lock ile güncellenir ve kilit sırası her yerde aynı olmalıdır (§7.7). Düzeltme sadece ters kayıtla yapılır.
- Takip: Geriye tarihli kesinleştirmenin değerleme maliyeti envanter modülünün kararıdır.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

S3 spike'ı: kesinleştirme ve ledger yazımı (§15.3). Faz 5 çıkış kriteri: "bakiye = hareketlerin toplamı" testi yeşil; yumuşak kapalı döneme geriye tarihli kesinleştirme ve sonraki döneme ters kayıt çalışıyor.
