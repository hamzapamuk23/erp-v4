# ERP v4

Sektörden bağımsız ERP çekirdeği: tek kod tabanı ve tek imaj, hem çok kiracılı SaaS hem tek kiracılı on-prem.

- Mimari ve yol haritası: [docs/architecture/v4-platform.md](docs/architecture/v4-platform.md)
- Karar kayıtları: [docs/adr/](docs/adr/)
- Yerel geliştirme: [docs/guides/local-development.md](docs/guides/local-development.md)
- Katkı kuralları: [CONTRIBUTING.md](CONTRIBUTING.md)

## Hızlı başlangıç

```bash
mise install                                             # Java 25, Node 24, pnpm, gitleaks
deploy/compose/init-env.sh                               # yerel sırları üretir (bir kez)
docker compose -f deploy/compose/compose.yaml --profile dev up -d
./mvnw verify                                            # backend: derleme, test, mimari kurallar
(cd web && pnpm install && pnpm --filter @erp/web dev)   # SPA: http://localhost:5173
deploy/compose/smoke.sh                                  # tek imaj + compose + Playwright
```
