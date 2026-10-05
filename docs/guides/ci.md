# CI hattı

Not: Deponun şu anki uzak adresi GitHub'dır, ancak CI §12'ye göre GitLab CI'dır (açık soru §20 soru 5). Hat henüz GitLab'da çalıştırılmadı; Faz 0 çıkış maddesi "CI yeşil", bir GitLab projesi ve ayrıcalıklı (privileged) bir dind runner bekliyor.

Tanım: [.gitlab-ci.yml](../../.gitlab-ci.yml). Her iş yerelde aynı komutla çalıştırılabilir.

| Aşama | İş | Ne yapar | Yerel karşılığı |
|---|---|---|---|
| check | `secrets:gitleaks` | Tüm git geçmişinde sır taraması | `gitleaks git --config .gitleaks.toml --redact .` |
| verify | `backend:verify` | Derleme + Error Prone, testler (Testcontainers), Modulith/ArchUnit, Spotless, lisans, SBOM | `./mvnw verify` |
| verify | `frontend:verify` | Biçim, lint, tip, test, build, lisans | `cd web && pnpm format:check && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm licenses:check` |
| package | `image:build` | Tek imaj, SBOM ve provenance attestation'ı ile registry'ye | `docker build -f deploy/docker/app.Dockerfile -t erp-app:local .` |
| smoke | `smoke:compose` | `ci` compose yığını + Playwright | `deploy/compose/smoke.sh` |
| sign | `image:sign` | cosign imzası (sadece varsayılan dal ve tag) | — |

## Runner gereksinimleri

- Docker executor, `privileged = true` (docker:dind için).
- dind TLS'siz (`tcp://docker:2375`) çalışır; ağ iş başına özeldir. TLS'e geçmek için runner'da `/certs/client` volume'u tanımlanır, `DOCKER_HOST: tcp://docker:2376`, `DOCKER_TLS_CERTDIR: "/certs"`, `DOCKER_TLS_VERIFY: "1"`, `DOCKER_CERT_PATH: "/certs/client"` yapılır.
- Duman yığını bind mount kullanmaz (imajlar build context'ten üretilir); bu yüzden uzak bir dind daemon'unda da çalışır.

## CI değişkenleri

| Değişken | Nitelik | Amaç |
|---|---|---|
| `COSIGN_PRIVATE_KEY` | protected | İmaj imzalama anahtarı (çok satırlı PEM) |
| `COSIGN_PASSWORD` | protected, masked | Anahtar parolası |

`COSIGN_PRIVATE_KEY` maskelenemez: GitLab çok satırlı bir PEM değerini maskelemez. Bu yüzden anahtar yalnızca `protected` tutulur: sadece korumalı dallarda ve etiketlerde, yani `image:sign` işinin çalıştığı yerlerde bulunur. Tek satırlı parola maskelenebilir. Anahtar parolayla şifrelidir; yine de pipeline'a bu değişkeni yazdıran bir komut eklenmez.

`smoke:compose`, `image:sign`'ın imzaladığı imajın aynısını sınar: etiketi değil `image:build`'in ürettiği digest'i (`$CI_REGISTRY_IMAGE/app@$APP_IMAGE_DIGEST`) çalıştırır; digest boşsa iş hemen başarısız olur.

Anahtar üretimi (bir kez, yetkili bir kişi tarafından): `cosign generate-key-pair gitlab://<grup>/<proje>`. Bu komut anahtarı doğrudan proje değişkenlerine yazar; açık anahtar (`cosign.pub`) on-prem doğrulama için Faz 7'de imajla birlikte dağıtılır.

## Sık karşılaşılan kırılmalar

| Kırılma | Ne yapılır |
|---|---|
| Lisans denetimi (backend) | `app/target/generated-sources/license/THIRD-PARTY.txt`'e bakın. Yazım farkıysa `app/pom.xml`'deki `licenseMerges`; izinli olmayan lisansta bağımlılık değişir ya da ADR açılır. |
| Lisans denetimi (frontend) | Betik ihlal eden paketi yazar. Aynı kural. |
| `smoke:compose` | `deploy/compose/smoke-logs/` artifact'ında `compose.log` ve `playwright-report/index.html`. |
| e2e: `ERR_SSL_PROTOCOL_ERROR` / `net::ERR_...` | e2e koşucusu uygulamaya `http://erp-app:8080` (compose ağ takma adı) ile ulaşır, çünkü Chromium'un HSTS preload listesi `app` TLD'sini kapsar ve `http://app` adresini zorla HTTPS'e çevirir. Takma ad, preload listesinde olmayan bir ad olarak kalmalıdır. |
| gitleaks | Gerçek sırsa: sır iptal edilir, geçmişten temizlenir. Yanlış alarmsa `.gitleaks.toml` allowlist'ine gerekçeli kayıt. |

## Renovate

`renovate.json` kuralları: major güncellemeler onay ister, Spring Boot ve Modulith gruplanır, Playwright paketi ile imajı birlikte güncellenir, TypeScript `<6.1`'de tutulur, PostgreSQL major güncellemeleri kapalıdır. Renovate botunun GitLab'a kurulumu depo dışı bir iştir.
