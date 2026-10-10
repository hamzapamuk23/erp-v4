# Faz 0 spike'ları

[v4-platform.md](../docs/architecture/v4-platform.md) §15.3'teki riskli kararları gerçek kodla sınayan **atılacak** projeler.

- Varsayılan Maven reaktöründe değildir: `./mvnw verify` ve CI bunları derlemez. Sadece `spikes` profiliyle derlenir.
- Ön koşul (bir kez ve `platform/` değiştikçe): `./mvnw -q install -DskipTests` (parent ve `erp-platform-test` yerel depoya kurulur).
- Çalıştırma: `./mvnw -Pspikes -pl spikes/<ad> verify`. Docker çalışıyor olmalıdır (Testcontainers).
- Sonuçlar `docs/spikes/<ad>.md` ve ilgili ADR'lerin "Doğrulama" bölümlerindedir. Faz 0 kapanışında (plan 0E) bu klasör silinir; kalıcı olan sadece dokümanlardır. Spike kodu ürün koduna kopyalanmaz; Faz 1/2 kodu bulgulara göre yeniden yazılır.

| Spike | Klasör | Sonuç dokümanı |
|---|---|---|
| S1 — Tenancy yığını | [`s1-tenancy/`](s1-tenancy/) | [docs/spikes/s1-tenancy.md](../docs/spikes/s1-tenancy.md) |
| S2 — Kimlik | [`s2-identity/`](s2-identity/) | [docs/spikes/s2-identity.md](../docs/spikes/s2-identity.md) |
