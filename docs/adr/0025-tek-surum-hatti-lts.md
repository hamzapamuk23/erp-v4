# ADR-0025: Tek sürüm hattı + LTS

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §11.8 |

## Bağlam

Her müşteri aynı sürüm hattında kalmalı ve özelleştirmeler güncellemeyi kırmamalı (§1.3 hedef 4, §3.1 madde 3). On-prem müşteriler genelde LTS kullanır, SaaS her zaman günceldedir. Platform, modüller ve paketler birlikte geliştirilir ve dağıtılır (ADR-0001).

## Karar

- **Tek sürüm hattı:** platform, modüller ve paketler aynı ürün sürümünü taşır (`MAJOR.MINOR.PATCH`).
- 4–6 haftada bir minor sürüm, gerektiğinde patch.
- Yılda iki minor sürüm **LTS** olarak işaretlenir ve 12 ay desteklenir.
- Değişiklik kaydı Conventional Commits'ten otomatik üretilir; sürüm notlarında "yükseltme notları" ve "kırıcı değişiklikler" bölümleri zorunludur.
- Spring Boot minor sürümleri yayımından sonraki ilk ürün sürümünde yükseltilir (ör. Boot 4.2 Kasım 2026'da çıkınca bir sonraki ürün sürümünde alınır). Renovate PR açar, CI doğrular.
- Desteklenen yükseltme yolları: bir önceki minor sürümden ve desteklenen her LTS'ten güncel sürüme (§11.2). `@Stable` öğeler kaldırılmadan önce en az bir LTS boyunca `@Deprecated(forRemoval)` kalır (§5.4).

## Sonuçlar

- Olumlu: Müşteri dalı yok (K8). Yükseltme yolları sınırlı ve testlenebilir.
- Olumsuz: Her sürümde N-1 ve LTS yükseltme yolları CI'da test edilmelidir. Bağımlılık yükseltmeleri düzenli yapılır, biriktirilmez.
- Takip: Sürüm süreci Faz 8 kapsamındadır (§15.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Müşteri dalı ya da müşteriye özel sürüm | "Tek ürün hattı" ilkesi ve K8 ile yasak (§3.1) |

## Doğrulama

Faz 7 çıkış kriteri: N-1 ve LTS'ten yükseltme CI'da yeşil. Çekirdek tamam kriteri §16.1 madde 11. Renovate yapılandırması Faz 0A'da eklendi (`renovate.json`).
