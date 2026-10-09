# Açık sorular

Tasarım incelemelerinde çıkan ve henüz karara bağlanmamış sorular. Proje düzeyindeki sorular [v4-platform.md §20](architecture/v4-platform.md#20-açık-sorular)'dedir; burada sadece incelemelerden doğanlar tutulur. Karar verilen soru, kararın yazıldığı yere (ADR, doküman, plan) bağlantıyla birlikte buradan silinir.

| # | Soru | Neden önemli | Güncel öneri | Ne zamana kadar | Kaynak |
|---|---|---|---|---|---|
| 1 | Keycloak organization bağı `tenant` tablosunda mı, `tenant_domain`'da mı durur? §4.3 onu `tenant_domain`'a koyuyor. | Bir tenant'ın birden çok host'u olabilir; bearer isteğinin anlamlı bir host'u yoktur. | `tenant(oidc_issuer, organization_alias)`, `tenant_domain` sadece host eşlemesi. S2 sonucuyla §4.3 güncellenir. | S2 kapanışı | Plan 0C, Tasarım kararı 2 |
| 2 | Önyükleme (`/api/v1/bootstrap`, §8.1) bearer istemcilere de açık mı? | Faz 3'teki mobil istemci de kullanıcı, tenant, izin ve modül bilgisini ister; CSRF alanı sadece tarayıcı içindir. | Açık; bearer yanıtında CSRF alanları olmaz. Spike `/bootstrap`'ı tarayıcıya özel tutar. | Faz 3 | Plan 0C, Task 7 bulguları |
| 3 | BFF oturumunun mutlak ömrü ne olacak, Keycloak'taki iptal (kullanıcıyı devre dışı bırakma, organization'dan çıkarma) oturuma nasıl yansıyacak? | BFF refresh token tutmuyor; `spring.session.timeout` sadece boşta kalma süresi. Hedef ASVS L2 (§10.1). | ID token'ın `auth_time`'ı ile mutlak ömür (süre değeri açık); organization'dan çıkarmada principal index'iyle `<tenant>:<sub>` oturumlarını silme; bütün tenant'lar için `sub`'a göre bulma ya da back-channel logout. | Faz 3 | Plan 0C, Tasarım kararı 5 |
| 4 | Tenant'ı belirleyen başlık `X-Forwarded-Host` mı, sadece `Host` mu? | Boot varsayılanında güvenilen her özel ağ adresi `X-Forwarded-Host` ile tenant'ı seçebilir; on-prem LAN bu aralıktadır. | Boot varsayılanı (`X-Forwarded-Host`) kalır; `internal-proxies` Caddy'ye daraltılır ve uygulama portu dışarı açılmaz. | Faz 1 | Plan 0C, Tasarım kararı 1 |
