# Yerel geliştirme

## Ön koşullar

| Araç | Nasıl |
|---|---|
| mise | `brew install mise`, sonra kabuk entegrasyonu: `eval "$(mise activate zsh)"` (`~/.zshrc`) |
| Java 25, Node 24, pnpm, gitleaks | Depo kökünde `mise install` (sürümler `mise.toml`'da) |
| Maven | Kurulum gerekmez: `./mvnw` (3.9.16) |
| Docker | Docker Desktop çalışıyor olmalı (Testcontainers, compose) |

## İlk kurulum

```bash
mise install
deploy/compose/init-env.sh                                    # deploy/compose/.env: rastgele yerel sırlar
docker compose -f deploy/compose/compose.yaml --profile dev up -d
./mvnw verify
(cd web && pnpm install)
```

## Günlük çalışma

| İş | Komut |
|---|---|
| Altyapıyı başlat / durdur | `docker compose -f deploy/compose/compose.yaml --profile dev up -d` / `... down` |
| Backend'i çalıştır | `./mvnw -q install -DskipTests && ./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev` (IDE'de: `ErpApplication`, aktif profil `dev`) |
| SPA dev sunucusu | `cd web && pnpm --filter @erp/web dev` → http://localhost:5173 (`/api`, `/actuator` → 8080) |
| Backend testleri | `./mvnw verify` |
| Frontend kontrolleri | `cd web && pnpm lint && pnpm typecheck && pnpm test` |
| Tek imaj duman testi | `deploy/compose/smoke.sh` (ayrı `erp-smoke` projesi; dev verisine dokunmaz) |

## Portlar

| Servis | Adres |
|---|---|
| Uygulama | http://localhost:8080 |
| SPA dev sunucusu | http://localhost:5173 |
| PostgreSQL | 127.0.0.1:5432 (`erp_platform`, `keycloak`) |
| Keycloak | http://localhost:8180 (yönetici: `admin`, şifre `.env` → `KEYCLOAK_ADMIN_PASSWORD`) |
| Mailpit | http://localhost:8025 (SMTP 1025) |

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| `JDK 25 is required` | `mise install`; kabukta `java -version` 25 göstermeli |
| `Could not find a valid Docker environment` | Docker Desktop'ı başlatın |
| `required variable POSTGRES_PASSWORD is missing` | `deploy/compose/init-env.sh` |
| 5432 portu dolu | `.env`'e `POSTGRES_PORT=5433`; backend'i `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/erp_platform` ile çalıştırın |
| Dev veritabanını sıfırlamak | `docker compose -f deploy/compose/compose.yaml down --volumes` (veri silinir) |
| PostgreSQL init betiğini (`deploy/compose/postgres/initdb/`) değiştirdim ama etkisi yok | Betikler `erp-postgres:local` imajına gömülür ve yalnızca boş veri volume'unda çalışır: `docker compose -f deploy/compose/compose.yaml build postgres`, ardından `down --volumes` ile dev volume'unu sıfırlayın (veri silinir) |
