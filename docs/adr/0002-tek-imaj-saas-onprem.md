# ADR-0002: Tek imaj ve tek kod tabanı: SaaS + on-prem

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Tek yön |
| **Kaynak** | v4-platform.md §4.1 |

## Bağlam

Ürün hem bizim işlettiğimiz çok kiracılı SaaS olarak hem de müşteri sunucusunda tek kiracılı on-prem olarak çalışmalı (§0 madde 2). v1'de her müşteri için ayrı frontend build'i vardı ve `.env` dosyalarından ikisi bayt bayt aynıydı; sonuç sürüm ve dağıtım karmaşasıydı (§2.2 H16). İlke tek ürün hattıdır: fork, müşteri dalı ve müşteriye özel build yoktur (§3.1 madde 3).

## Karar

- Tek kod tabanı ve tek OCI imajı (Spring Boot + gömülü SPA, §11.1) hem SaaS'ta hem on-prem'de çalışır. On-prem tek tenant'tır ama SaaS ile aynı mekanizmayı kullanır.
- Çalışma modu tek bir ayarla belirlenir: `erp.deployment.mode=saas|onprem`. Ayar sadece tenant kaynağını ve bazı operasyon varsayılanlarını değiştirir; iş kodu modu bilmez.
- İki kurulum şekli arasındaki farklar §4.1 tablosuyla sınırlıdır: tenant kaynağı (platform DB'deki tenant kaydı / kurulumda oluşturulan tek tenant kaydı + lisans dosyası), Keycloak (ortak realm + Organizations / paketle gelen Keycloak, aynı realm yapısı, tek organization), güncelleme (release train / `erpctl update`) ve lisans (platform DB'deki abonelik / imzalı lisans dosyası).
- Müşteriye özel build, dal ya da fork yoktur (K8). Marka, dil ve modül farkı çalışma zamanında tenant konfigürasyonuyla gelir (§9.2).

## Sonuçlar

- Olumlu: Tek sürüm hattı ve tek test matrisi. Tenant'lar SaaS ile on-prem arasında taşınabilir (§4.7). Lisans tarafında "kod tek, kaynak iki" (§11.5).
- Olumsuz: Her özellik iki kurulum şeklinde de çalışmak zorundadır. On-prem paketi Keycloak, PostgreSQL ve ters proxy'yi de taşır (§11.1).
- Takip: §16.1 madde 7 (aynı imajın iki modda çalıştığının kanıtı).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Müşteri başına build (v1) | Sürüm ve dağıtım karmaşası (§2.2 H16); K8 ile yasak |

## Doğrulama

Çekirdek tamam kriteri §16.1 madde 7: aynı imaj SaaS'ta çok tenant'la, on-prem'de tek tenant'la çalışıyor (compose profilleriyle E2E). Faz 0 çıkış kriterinin "tek imaj compose ile ayağa kalkıyor" maddesi için Faz 0A'da compose duman testi (`deploy/compose/smoke.sh`) tek imajı ayağa kaldırıyor.
