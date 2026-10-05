# ADR-0011: Yazma JPA/Hibernate, okuma jOOQ; ticari jOOQ lisansı

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §12.1, §11.2 |

## Bağlam

Yazma tarafı entity, optimistic lock ve Envers geçmişi ister (§6.1, §6.9). Okuma tarafı metadata'dan üretilen liste sorguları, veri kapsamı koşulları, yayınlanmış view'lar ve yazdırma veri sağlayıcıları ister (§6.5, §5.4, §6.13). jOOQ'nun açık kaynak sürümü sadece en güncel PostgreSQL diyalektini (18) destekliyor; her jOOQ yükseltmesi tüm kurulumlarda PostgreSQL major yükseltmesini zorlayabilir. On-prem'de PostgreSQL major yükseltmesi ayrı ve planlı bir işlemdir (§11.2).

## Karar

- Yazma **Hibernate ORM 7** (+ Envers) ile, JPA entity üzerinden yapılır. İkinci seviye cache kapalıdır.
- Okuma **jOOQ 3.21** ile yapılır: CRUD liste sorguları metadata'dan üretilir, veri kapsamı ve alan izinleri otomatik eklenir (§6.5); yazdırma verisi jOOQ sorgularıyla hazırlanır (§6.13).
- Aynı transaction'da JPA ile yazılan veri jOOQ ile okunacaksa önce `flush` edilir; CRUD motoru ve belge çatısı bunu katılımcılardan önce otomatik yapar (§7.7).
- Sürümlü diyalekte sabitlemek için ticari jOOQ lisansı (Express/Professional) bütçelenir; karar Faz 0'dadır (§20 soru 13).
- PostgreSQL major yükseltmesi `erpctl pg-upgrade` ile yapılır (yedek → `pg_upgrade` → doğrulama). jOOQ sürüm politikası bu adımı gerektirebilir (§11.2).

## Sonuçlar

- Olumlu: Yazmada entity modeli ve audit, okumada SQL üzerinde tam denetim. jOOQ codegen kapsamı K1'i destekler: bağımlı modül sadece sağlayıcının view'larını görür (§5.4).
- Olumsuz: İki veri erişim teknolojisi ve `flush` disiplini. Ticari lisans maliyeti; lisans alınmazsa jOOQ yükseltmeleri PostgreSQL major yükseltmesine bağlanır (§19 R7).
- Takip: Lisans kararı kullanıcıdadır (§20 soru 13). PG 18'in desteği Kasım 2030'da biter (§11.2).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| jOOQ OSS (Apache-2.0) | Sadece en güncel PostgreSQL diyalektini destekliyor; her jOOQ yükseltmesi tüm kurulumlarda PostgreSQL major yükseltmesini zorlayabilir (karar Faz 0'da) |

## Doğrulama

S1 spike'ı: routing DataSource üzerinde Hibernate 7 + jOOQ, jOOQ okumadan önce `flush` (§15.3, §4.5). S3 spike'ı: jOOQ liste sorgusu. Teyit listesi: jOOQ ticari lisans kararı (kullanıcı kararı, §20 soru 13).
