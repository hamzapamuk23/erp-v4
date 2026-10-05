# ADR-0020: Vuetify 4 + `@erp/ui` sarmalayıcısı; PrimeVue reddedildi

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §9.1 |

## Bağlam

Ekip Vue biliyor ve Vuetify 2 deneyimi var. İlke, ticari lisansa geçebilecek bir kütüphanenin kendi sarmalayıcı katmanımızın arkasında kullanılmasıdır (§3.1 madde 6); PrimeVue 5 bunun örneğidir, artık ücretli lisansla dağıtılıyor. Her ekran birkaç sayfa arketipinden birine uyarak aynı tasarım dilini taşımalı (§9.4).

## Karar

- Bileşen kütüphanesi **Vuetify 4**'tür (MIT), kendi `@erp/ui` katmanımızın arkasında kullanılır.
- `@erp/ui` tasarım token'larını, sarmalanmış bileşenleri (ErpTextField, ErpGrid…), açık ve koyu temayı, çalışma zamanında tenant marka renklerini ve erişilebilirliği tanımlar (§9.2, §9.4).
- Paket bağımlılık yönü `apps → modules → shell → meta-renderer → core → ui`'dır; modül paketleri birbirini import etmez (§9.2).
- Metadata Vuetify sınıfı, piksel değeri ya da bileşen adı içermez (§6.4.1).
- Yığının geri kalanı §9.1'deki tablodur: Vue 3 + TypeScript, Vite 8, TanStack Query, Pinia, vee-validate + Zod, openapi-typescript/openapi-fetch, pnpm workspaces, Vitest, Playwright.

## Sonuçlar

- Olumlu: Lisans riski yok. Material Design 3, CSS layers, sistem teması. Kütüphaneye bağımlılık `@erp/ui` ile sınırlı kalır.
- Olumsuz: Sarmalayıcı katmanın bakımı. Ekipte Vuetify 4 yetkinlik açığı riski (§19 R4).
- Takip: Dışarıdan iş ortağı ya da bayi geliştiricisi olacaksa UI kütüphanesi lisansları yeniden ele alınır (§20 soru 6).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| PrimeVue 5 | PrimeUI ticari lisansı: geliştirici başına 599 $ (2027'den itibaren 799 $). Ücretsiz Community lisansı sadece 5 geliştiricinin, 10 çalışanın ve 1 M$ cironun altındaki şirketlere açık. OEM maddesi "low-code / uygulama oluşturucu" ürünler için ayrı lisans istiyor; metadata güdümlü platform bu tanıma girme riski taşıyor. |
| PrimeVue 4 | MIT kalıyor ama yeni major sürüm gelmeyecek |
| Element Plus, Naive UI, Quasar | Teknik olarak uygun, ama ekip deneyimi Vuetify'da |
| React | Ekip deneyimi |
| Mikro-frontend / module federation | Tek ekip, tek sürüm hattı için gereksiz karmaşıklık |

## Doğrulama

S3 spike'ı: Vuetify 4 formu + AG Grid satır ızgarası (§15.3).
