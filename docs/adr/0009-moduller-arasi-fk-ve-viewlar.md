# ADR-0009: Modüller arası FK ve yayınlanmış view kuralları

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §5.4 |

## Bağlam

v1'de servisler birbirinin tablolarını `model/foreign/*` ile map'liyor, başka şemaları doğrudan SQL ile okuyordu (§2.2 H5). K1'e göre modüller arası erişim sadece `api`, `spi`, olaylar ve yayınlanmış okuma view'ları üzerinden olur. Öte yandan ERP'de referans bütünlüğü değerlidir (stok hareketinin malzemesi gibi) ve bağımlı modüller listede ya da raporda sağlayıcının verisini göstermek ister. Bir tenant'ın tüm modül şemaları aynı tenant DB'sindedir (§7.1).

## Karar

- Bağımlı modül, sağlayıcının tablosuna **sadece birincil anahtar üzerinden** FK verebilir (ör. `inventory.stock_move.item_id → catalog.item.id`). ERP'de bütünlük bu kısıttan daha değerlidir.
- JPA ilişkisi kurulmaz, sadece ID tutulur. Sağlayıcı bağımlıya asla referans vermez. Migration sırası bağımlılık grafiğine göre belirlenir.
- Sağlayıcı `<şema>.v_<ad>` view'larını açık okuma sözleşmesi olarak yayınlar. View'lar **sadece sağlayıcının kendi tablolarını** okur (view üstüne view kurulmaz) ve sadece kolon ekleyerek değiştirilir (`CREATE OR REPLACE VIEW`).
- Bağımlının jOOQ codegen'i sağlayıcının tablolarını değil, sadece view'larını görür.
- View'lar da `@Stable` API'lerle aynı kırıcı değişiklik politikasına tabidir.

## Sonuçlar

- Olumlu: Bütünlük veritabanında korunur. Okuma sözleşmesi açık ve sürümlenebilir. K1 jOOQ codegen kapsamıyla da denetlenir (§3.2).
- Olumsuz: Modüller FK ile aynı veritabanında birbirine bağlanır. Sağlayıcı yayınladığı view'u sadece eklemeli değiştirebilir.
- Takip: Modül testleri `@ApplicationModuleTest(mode = DIRECT_DEPENDENCIES)` ile sağlayıcı migration'larını da kurar; böylece modüller arası FK'lar test edilebilir (§5.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Modüllerin birbirinin tablolarını map'lemesi ve doğrudan okuması (v1) | Mikroservisin maliyeti var, faydası yok (§2.2 H5); K1 ile yasak |

## Doğrulama

Çekirdek tamam kriteri §16.1 madde 5: modüller arası erişim sadece API, olay ve view üzerinden, build'de denetleniyor (Modulith verify + ArchUnit, negatif testlerle).
