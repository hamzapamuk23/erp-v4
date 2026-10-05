# ADR-0030: Para ve miktar için `numeric` + değer tipleri

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Tek yön |
| **Kaynak** | v4-platform.md §7.3 |

## Bağlam

v1'de para ve miktar alanlarında 342 Float/Double alan, 0 BigDecimal vardı; prim tutarları da Float'tı. Sonuç yuvarlama hatalarıydı (§2.2 H3). Doğruluk birinci kalite hedefidir: para, miktar ve stok asla yanlış hesaplanmaz (§1.3).

## Karar

- Para, miktar, oran ve kur için `float`/`double` kullanılmaz; sadece `BigDecimal` ve `numeric` kullanılır (K5).
- Veritabanı tipleri: tutar `numeric(19,4)` (para biriminin ölçeğinde yuvarlanmış saklanır) + para birimi kolonu; birim fiyat `numeric(19,6)`; miktar `numeric(19,6)` + birim kolonu; kur `numeric(24,10)` + birim çarpanı; oran/yüzde `numeric(9,4)`.
- Java tarafında değer tipleri kullanılır (record + JPA embeddable + Jackson serileştirici): `Money`, `Quantity`, `Percentage`, `ExchangeRate` (§6.1).
- Yuvarlama kuralları merkezi tanımlıdır: para birimine göre ölçek (TRY: 2), birim fiyat için 6 hane, miktar için birime göre ölçek (§6.1).

## Sonuçlar

- Olumlu: Yuvarlama hataları tip seviyesinde önlenir; değer tipleri tutarı para birimiyle, miktarı birimiyle birlikte taşır.
- Olumsuz: Tüm hesaplamalar `BigDecimal` ile, ölçek ve yuvarlama açıkça yönetilerek yazılır.
- Takip: Üç para birimli tutar modeli ADR-0031, yuvarlama politikası ADR-0035.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| `float`/`double` (v1) | Yuvarlama hataları (§2.2 H3); K5 ile yasak |

## Doğrulama

İlgili fazın çıkış kriteri: Faz 1 (kapsam: kernel'de Money/Quantity ve K5 ArchUnit kuralı; §15.3). Çıkış maddesi: satırlı belge kesinleştirildiğinde numara ve bakiye doğru.
