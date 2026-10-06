# ADR-0003: Tenant başına veritabanı

| | |
|---|---|
| **Durum** | Kabul edildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Tek yön |
| **Kaynak** | v4-platform.md §4.2 |

## Bağlam

İkinci kalite hedefi izolasyondur: bir tenant başka bir tenant'ın verisini hiçbir yoldan göremez (§1.3). Hedef ölçek onlarca, en fazla birkaç yüz B2B tenant'tır. Tenant bazında yedek, geri yükleme, dışa aktarma ve SaaS ↔ on-prem taşıma gerekiyor (§4.7). Özel alan index'i gibi tenant başına DDL ihtiyacı var (§6.4.5).

## Karar

- Her tenant için aynı PostgreSQL sunucusunda ayrı bir veritabanı açılır: `erp_t_<tenantKey>`. Tenant'tan bağımsız veriler platform DB'sindedir (ADR-0004).
- Tenant DB'si içinde her modülün kendi şeması vardır (§7.1).
- **Yeniden değerlendirme eşiği:** Tek bir PostgreSQL kümesinde 300'den fazla aktif tenant olursa ikinci bir küme açılır. DB-per-tenant korunur; tenant kaydı hangi kümede olduğunu tutar. Model değişmez, sadece yatayda çoğalır.

## Sonuçlar

- Olumlu: Tam izolasyon. Tenant bazında yedek, geri yükleme, dışa aktarma ve on-prem'e taşıma `pg_dump` ile yapılır. Tenant başına DDL mümkün. Gürültülü komşu etkisi sınırlı.
- Olumsuz: Bağlantı havuzu sayısı artar (ADR-0015). Migration her DB'de çalışır (ADR-0038). Tenant'lar arası analiz zordur. Operasyon yükü bir risktir (§19 R6).
- Takip: Bağlantı tavanı hesabı ve küme başına kapasite eşikleri (§4.5, §11.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Tenant başına şema | `search_path` hataları sızıntı riski taşır; yedek ve taşıma DB'deki kadar temiz değil; Modulith olay tablosu sorunu (§4.4) |
| Ortak şema + `tenant_id` + RLS | Binlerce tenant'a ölçeklenir ama bir sorgu hatası sızıntı demek; tenant bazında restore ve on-prem'e taşıma zor. Hedef ölçek bunu gerektirmiyor. |

## Doğrulama

S1 spike'ı: 2 tenant DB ile routing DataSource + Hibernate 7 + jOOQ + Modulith olay kaydı + db-scheduler (§15.3). Faz 2 çıkış kriteri: 3 tenant ile izolasyon paketi (API, repository, olay, iş, cache) yeşil; bağlamsız erişim hata fırlatıyor.

**S1 sonucu (2026-10-05, `dafa8dc`):** Platform DB + iki aktif, bir askıdaki ve DB'si olmayan bir tenant ile JDBC, JPA ve jOOQ yazma/okumaları sadece bağlı tenant'ın DB'sine gitti (routing'i atlayan süper kullanıcı sorgusuyla doğrulandı); tenant rolü platform DB'sine bağlanamıyor. Ayrıntı ve bulgular: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md). Faz 2 çıkış kriteri (3 tenant'lı izolasyon paketi) geçerliliğini korur.
