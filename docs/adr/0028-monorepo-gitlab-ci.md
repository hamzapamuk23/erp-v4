# ADR-0028: Monorepo, GitLab CI

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §14 |

## Bağlam

Backend, frontend, dağıtım ve dokümantasyon birlikte değişir; bir API ve UI değişikliği tek bir PR'da yapılabilmeli. v1'de GitLab zaten kullanılıyor, 19 servis `.gitlab-ci.yml` taşıyor (§12.3). Depo şu an GitHub'da barındırılıyor; `.gitlab-ci.yml` mevcut ama henüz çalışmadı. Dokümana göre GitHub'a geçilecekse aynı hat GitHub Actions'a uyarlanır (§12.3).

## Karar

- Tek bir Git deposu (monorepo) kullanılır: backend, frontend, dağıtım ve dokümantasyon birlikte sürümlenir. Yerleşim §14'teki ağaçtır (`platform/`, `modules/`, `packs/`, `customers/`, `app/`, `tools/`, `web/`, `deploy/`, `docs/`).
- Backend Maven çok modüllüdür, bağımlılık sürümleri üst POM + BOM'da tek yerdedir; frontend pnpm workspace'tir (§9.2).
- Paket kökü `com.smart.erp`'dir (Faz 0'da kesinleşir).
- Commit kuralı Conventional Commits'tir.
- Kod deposu ve CI GitLab + GitLab CI, imaj kayıt deposu GitLab Container Registry'dir (§12.3).
- Karar kayıtları `docs/adr/` altında `NNNN-baslik.md` olarak tutulur.

## Sonuçlar

- Olumlu: API ve UI değişikliği tek PR'da; sürümler tek yerde.
- Olumsuz: Doküman ayrı bir bedel adlandırmıyor. CI sağlayıcısı sorusu açıktır (§20 soru 5).
- Takip: §20 soru 5 (GitLab ile mi devam edilecek) ve soru 7 (ürün adı ve paket kökü), ikisi de Faz 0.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| GitHub + GitHub Actions | Seçilmedi değil, açık soru (§20 soru 5); geçilirse aynı hat GitHub Actions'a uyarlanır (§12.3) |

## Doğrulama

GitLab projesi ve runner hazır olduğunda ilk yeşil pipeline (§20 soru 5). Runner gereksinimi (ayrıcalıklı docker:dind) `docs/guides/ci.md`'dedir.
