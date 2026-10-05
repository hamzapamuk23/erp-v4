# ADR-0017: Arama: `search_text` + `pg_trgm`

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.16 |

## Bağlam

Kullanıcılar kayıtları Türkçe metinle arar; "ısık", "Işık" ve "isik" aynı sonucu bulmalı (§7.8). Global arama ve komut paleti (Ctrl+K) kaynaklar arasında, izin filtrelemesiyle çalışmalı (§6.5, §9.3). Gerekmedikçe yeni altyapı bileşeni eklenmez (§3.1 madde 7).

## Karar

- `searchable` alanlardan türetilen `search_text` kolonu kullanılır. Değer uygulamada `TurkishText.fold(...)` ile üretilir; sorgu metni de aynı şekilde normalize edilir (§7.8).
- `pg_trgm` GIN index'i ile `ILIKE '%…%'` ve benzerlik araması yapılır.
- Global arama, izin filtrelemesiyle kaynaklar arasında çalışır (§6.5).
- Ayrı bir arama motoru (OpenSearch vb.) kullanılmaz.

## Sonuçlar

- Olumlu: Ek bileşen yok; arama tenant DB'sinde, aynı izolasyon ve yetki modeliyle çalışır.
- Olumsuz: Belge içi tam metin arama yoktur.
- Takip: Belge içi tam metin arama ya da çok büyük hacim gerekirse yeniden değerlendirilir (§12.4). Komut paleti ve global arama kesme listesindedir (§15.4 madde 8).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| OpenSearch / Elasticsearch | `pg_trgm` yeterli (§12.4) |

## Doğrulama

İlgili fazın çıkış kriteri: Faz 4 (kapsam: CRUD motoru, sorgu dili ve arama; §15.3). `TurkishText` normalizasyonu Faz 1'de kernel ile gelir.
