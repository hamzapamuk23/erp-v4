# ADR-0012: Olay kaydı tenant DB'sinde; tenant dolaşan yeniden yayın

| | |
|---|---|
| **Durum** | Kabul edildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §4.4 |

## Bağlam

Domain olayları ve outbox (§5.4, §6.15) iş verisiyle aynı transaction'da yazılmalı; iş verisi tenant DB'sindedir. Spring Modulith'in çok kiracılık desteği yoktur: açılışta yeniden yayın ve şema oluşturma özellikleri tenant bağlamı olmadan çalışıp hata verir. Tenant başına şema seçeneğinin reddedilme nedenlerinden biri de Modulith olay tablosuydu (§4.2).

## Karar

- Her olay `tenantKey` taşır (ayrıca `eventId`, `companyId`, `occurredAt`, `schemaVersion`; §5.4). Spring Modulith olay yayın kaydı tenant'ın kendi DB'sindedir (`platform_events` şeması, §7.1).
- Modulith'in **açılışta yeniden yayın ve şema oluşturma özellikleri kapatılır**.
- Yarım kalan yayınlar, tenant'ları dolaşan bir platform işiyle yeniden gönderilir.
- Tamamlanan yayınlar silinir ya da arşivlenir (completion mode); tablo büyümez.
- Dinleyiciler idempotenttir (§5.4). Aktif olmayan modülün atlanan dinleyicisi olayı tamamlandı olarak işaretler; aksi halde yayın sonsuza dek yeniden gönderilir (§5.6).

## Sonuçlar

- Olumlu: Olay iş verisiyle atomik yazılır. Tenant izolasyonu olaylara da uzanır.
- Olumsuz: Yeniden yayın işini biz yazarız. Yayın kayıtları serileştirilmiş olay sınıfları içerdiği için on-prem güncellemede bekleyen yayınlar önce boşaltılır (§11.2).
- Takip: Geri yüklemede yarım kalan yayınlar yeniden gönderilmez, karantinaya alınır (§4.7.1, ADR-0043).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Modulith'in açılışta yeniden yayın ve şema oluşturma özellikleri (varsayılan) | Tenant bağlamı olmadan çalışıp hata verirler |

## Doğrulama

S1 spike'ı: Modulith olay kaydı, açılışta yeniden yayın kapalı, tenant dolaşan yeniden yayın (§15.3). Faz 2 çıkış kriteri: izolasyon paketi (olay seviyesi dahil) 3 tenant ile yeşil.

**S1 sonucu (2026-10-05, `dafa8dc`):** Yayın kaydı tenant DB'sinde (`platform_events.event_publication`, Modulith 2.1.1 v2 düzeni, migration ile); async dinleyici yayıncının tenant'ında çalıştı; `completion-mode=delete` ile tamamlanan yayın silindi; başarısız yayın tenant dolaşan bir db-scheduler işiyle kendi tenant'ında yeniden gönderildi, erişilemeyen tenant diğerlerini durdurmadı. Uygulama kuralları: Modulith 2.1.1 açılışta `databaseType` için ve kapanışta `destroy()` için tenant'sız sorgu yapıyor; `ModulithTenancySupport` ile bağlantısız karşılandı (upstream'e bildirilecek). `schema-initialization.enabled=false` açıkça verilmeli (özellik yokken açık). Dolaşan iş her tenant için havuz açtığından Faz 2'de etkinlik göstergesine bağlanmalı. Ayrıntı: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md) B1–B3, B10, B16.
