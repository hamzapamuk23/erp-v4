# Katkı rehberi

Mimari kararların kaynağı [docs/architecture/v4-platform.md](docs/architecture/v4-platform.md), gerekçeleri [docs/adr/](docs/adr/) altındadır. Kodlama kuralları: [docs/guides/coding-rules.md](docs/guides/coding-rules.md).

## Akış

1. `main`'den kısa ömürlü bir dal açın: `feat/<konu>`, `fix/<konu>`, `chore/<konu>`, `docs/<konu>`. Müşteri için dal açılmaz (K8).
2. Commit mesajları [Conventional Commits](https://www.conventionalcommits.org/tr/v1.0.0/) biçimindedir. Kapsam modül adıdır: `feat(identity): ...`, `fix(crud): ...`. Kırıcı değişiklik `feat(api)!:` ve gövdede `BREAKING CHANGE:` ile işaretlenir.
3. Merge request şablonundaki kontrol listesini doldurun. Platform değişikliklerinde platform sahibinin onayı gerekir (§13.2).
4. Pipeline yeşil olmadan merge edilmez.

## Yerelde doğrulama

| Ne | Komut |
|---|---|
| Backend: derleme, Error Prone, test, mimari kurallar, biçim, lisans | `./mvnw verify` |
| Backend biçimini düzeltme | `./mvnw spotless:apply` |
| Frontend | `cd web && pnpm format:check && pnpm lint && pnpm typecheck && pnpm test && pnpm build` |
| Frontend lisans denetimi | `cd web && pnpm licenses:check` |
| Tek imaj + compose + Playwright | `deploy/compose/smoke.sh` |
| Sır taraması | `gitleaks git --config .gitleaks.toml --redact .` |

## Karar kayıtları (ADR)

Mimariyi, bağımlılığı ya da sözleşmeyi etkileyen her karar bir ADR'dir. `docs/adr/0000-template.md` kopyalanır, sıradaki numara verilir, `Önerildi` durumunda açılır ve `docs/adr/README.md` dizinine eklenir. Kabul edilmiş bir ADR'nin kararı değiştirilmez; yeni bir ADR yazılır ve eskisi `Yerini aldı (ADR-NNNN)` olur.

## Bağımlılık eklemek

- Backend sürümleri kök `pom.xml`'de (Boot BOM'u yönetmiyorsa), frontend sürümleri `web/pnpm-workspace.yaml` içindeki `catalog:` bölümünde tutulur.
- Lisans izinli listede olmalıdır (Apache-2.0, MIT, BSD, ISC, EPL, LGPL ve benzeri). Liste dışı bir lisans ailesi ADR gerektirir.
- Yeni altyapı bileşeni (Redis, Kafka, Kubernetes, arama motoru…) ADR olmadan eklenmez (§3.1.7, §12.4).
- Derleme betiği (postinstall vb.) olan yeni bir frontend bağımlılığı için pnpm 12 `pnpm install`'ı reddeder; `web/pnpm-workspace.yaml` → `allowBuilds` altına gerekçeli bir yorumla açık bir karar (`true`/`false`) yazılır.
