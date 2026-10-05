# ADR-0029: Başlangıç sürümleri

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §12 |

## Bağlam

§12 sürümleri Ekim 2026 itibarıyla verir; kesin yamalar Faz 0'da sabitlenir. Boot 4'e doğrudan başlamak, 3.5'ten başlayıp hemen bir major göç borcuna girmekten iyidir (§12.1). Virtual thread'lerdeki `synchronized` pinning sorunu Java 24'te giderildi (JEP 491); Java 25 LTS bunu içerir. PostgreSQL 18'in desteği Kasım 2030'da biter. Spring Boot 4.x ekosisteminin olgunluğu bir risktir (§19 R9).

## Karar

Faz 0A'da sabitlenen sürümler:

| Bileşen | Sürüm |
|---|---|
| Java | 25 (Eclipse Temurin) |
| Spring Boot | 4.1.1 |
| Spring Modulith | 2.1.1 |
| PostgreSQL | 18.6 (`postgres:18.6`) |
| Keycloak | 26.8.0 (`quay.io/keycloak/keycloak:26.8.0`) |
| Maven wrapper | 3.9.16 |
| Node | 24.21.0 |
| pnpm | 12.9.1 |
| TypeScript | 6.0.3 |
| Vue | 3.5.43 |
| Vite | 8.3.2 |
| Vuetify | 4.2.3 |

- TypeScript 6.0.3'e sabitlenir: `typescript@latest` 7.0.2'dir ama `typescript-eslint` 8.71.0'ın peer aralığı `>=4.8.4 <6.1.0`'dır. TS 7'ye geçiş ayrı bir karardır (Renovate kuralıyla bloklanır).
- Henüz build'de olmayan bileşenler §12.1 ve §12.2'deki sürümlerle hedeflenir (Hibernate ORM 7, jOOQ 3.21, JasperReports 7.x, AG Grid Community 36…).
- Sürüm yükseltme politikası ADR-0025'tedir.

## Sonuçlar

- Olumlu: Spring Boot 4 / Spring Framework 7 ile başlanır, major göç borcu yoktur. Java 25 LTS.
- Olumsuz: Boot 4.x ekosisteminin olgunluğu (§19 R9): springdoc ve JasperReports gibi kütüphanelerin uyumu teyit edilmeli. Ekipte yetkinlik açığı riski (§19 R4). TypeScript 7 şimdilik kullanılamaz.
- Takip: Boot 4.2 GA sonrası alınır (§12.1, §11.8).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Spring Boot 3.5 ile başlamak | Hemen bir major göç borcuna girmek demek (§12.1) |
| TypeScript 7.0.2 | `typescript-eslint` 8.71.0'ın peer aralığı `<6.1.0` |

## Doğrulama

Faz 0A'da bu sürümlerle `./mvnw verify`, frontend hattı ve compose duman testi yeşil. Teyit listesinde kalanlar (§15.3): springdoc'un Boot 4 uyumu, JasperReports 7 + Java 25 uyumu (ADR-0026), Flyway ve Liquibase lisans durumu (ADR-0038).
