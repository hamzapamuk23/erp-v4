# ADR-0039: Oturum deposu: Spring Session JDBC (platform DB)

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.3.1 |

## Bağlam

BFF deseninde tarayıcı oturumu sunucuda tutulur (ADR-0006). Uygulama durumsuz kalmalı ve instance'lar yatayda çoğalabilmeli (§5.2). Redis kullanılmıyor (ADR-0016).

## Karar

- BFF oturumları **Spring Session JDBC** ile platform DB'sinde tutulur (`spring_session*` tabloları, §4.3).
- Oturum `(kullanıcı, tenant)` çiftine bağlanır; birden çok tenant'a üye kullanıcı için her tenant ayrı bir oturumdur (§4.4).

## Sonuçlar

- Olumlu: Oturumlar DB'de olduğu için instance'lar durumsuzdur ve yatayda çoğalır (§5.2). Ek bileşen gerekmez.
- Olumsuz: Doküman ayrı bir bedel adlandırmıyor.
- Takip: SaaS'ta ölçülmüş bir darboğaz olursa Redis yeniden değerlendirilir (§12.4).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Redis | LISTEN/NOTIFY + Caffeine + DB oturumları yeterli (§12.4) |

## Doğrulama

S2 spike'ı (Faz 0 kimlik spike'ı, §15.3): BFF oturumunun Spring Session JDBC ile platform DB'de tutulması.
