# ADR-0008: Özel alanlar: `ext jsonb` + isteğe bağlı ifade index'i

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.4.5 |

## Bağlam

Sektör ve müşteri farklarının bir kısmı kod yazmadan alan eklemeyi gerektirir: gıdada "menşe ülke", tekstilde "gramaj" (genişletme merdiveninin 3. basamağı, §5.5). v1'de stok hareketinin çekirdek verisi jsonb'de tutuldu ve başka servisler onu şema dışından okudu (§2.2 H4). Bu yüzden K6: çekirdek iş verisi jsonb'de tutulmaz, jsonb sadece tenant özel alanları ve sektör ek bilgisi içindir. Emsallerden Odoo (Properties) ve Axelor (`attrs` json) jsonb tercih ediyor.

## Karar

- `extensible: true` olan tablolarda `ext jsonb not null default '{}'` kolonu bulunur.
- Özel alan tanımı (tip, etiket, zorunluluk, değer listesi) tenant overlay'inde tutulur. Değerler sunucuda tipine göre doğrulanır.
- Özel alanlar liste, form, filtre, sıralama, export, import ve yazdırmada **otomatik** görünür.
- Admin bir alanı "indeksli" işaretlerse arka plan işi o tenant DB'sinde `CREATE INDEX CONCURRENTLY` ile ifade index'i oluşturur. Bu index'ler bilinçli tek şema farkıdır; Flyway'de değil tenant metadata'sında kayıtlıdır ve orkestratör yönetir (§4.6).
- Büyük tablolarda index'siz özel alana göre sıralama UI'da kapatılır.

## Sonuçlar

- Olumlu: Alan deploy etmeden eklenir; JPA ve jOOQ codegen sabit şemayla çalışır; tenant başına DB modeli tenant'a özel index'e izin verir.
- Olumsuz: Tenant'lar arasında bir şema farkı (ifade index'leri) doğar ve orkestratörün yönetmesi gerekir. Büyük tablolarda index'siz özel alana göre sıralanamaz.
- Takip: Dış raporlama view'larında `ext` alanları düzleştirilir (§6.15).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Gerçek kolon | JPA ve jOOQ codegen dinamik kolonu bilemez; tenant'lar arası şema farkı migration ve yükseltmeyi zorlaştırır; ERPNext'in gerçek kolon yaklaşımı MariaDB satır boyutu sınırlarına takılıyor |
| EAV | Sorgu ve rapor performansı kötü |

## Doğrulama

Faz 4 çıkış kriteri: overlay editörüyle özel alan ekleme (filtre, sıralama ve export dahil) deploy etmeden yapılabiliyor (E2E); 1 milyon satırlık tabloda index'li alanlarla filtrelenmiş ve sıralanmış listenin p95 süresi ≤ 300 ms.
