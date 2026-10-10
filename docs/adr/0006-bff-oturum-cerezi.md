# ADR-0006: BFF + `__Host-` oturum çerezi; tarayıcıda token yok

| | |
|---|---|
| **Durum** | Kabul edildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.3.1 |

## Bağlam

Tarayıcıda tutulan token çalınabilir (§10.1). Tenant'lar alt alan adlarıyla ayrılır ve alt alan adları aynı "site" sayıldığı için SameSite tenant'lar arasında koruma sağlamaz. RFC 10017 (Ağustos 2026) iş uygulamaları ve kişisel veri işleyen uygulamalar için BFF desenini "kesinlikle" öneriyor.

## Karar

- Tarayıcı kimlik doğrulaması **BFF** desenidir: Spring Security `oauth2Login` uygulamanın içinde çalışır; oturumlar Spring Session JDBC ile platform DB'de tutulur (ADR-0039).
- Çerez `__Host-` önekli, host'a bağlı, HttpOnly, Secure ve SameSite=Lax'tır.
- Tenant'lar arası asıl koruma host'a bağlı çerez ve her istekteki host–oturum tenant kontrolüdür. Oturum `(kullanıcı, tenant)` çiftine bağlanır; birden çok tenant'a üye kullanıcı için her tenant ayrı bir oturumdur (§4.4).
- Durum değiştiren isteklerde CSRF token'ı zorunludur.
- Tarayıcıya access ya da refresh token verilmez. SPA backend ile aynı origin'den sunulur (§9.7).

## Sonuçlar

- Olumlu: Tarayıcıda token çalınması tehdidi ortadan kalkar (§10.1). Tarayıcıda OIDC istemcisine gerek kalmaz (§9.1).
- Olumsuz: Oturum sunucuda tutulur (platform DB; instance'lar yine durumsuzdur, §5.2). `Secure` çerez ve `__Host-` öneki her kurulumda TLS gerektirir (§11.1, ADR-0024).
- Takip: Oturum düşerse `/bootstrap` 401 döner, SPA girişe yönlendirir ve kirli formlar yerel taslak olarak korunur (§9.7).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| `oidc-client-ts` (tarayıcıda OIDC istemcisi, token tarayıcıda) | BFF deseni yüzünden tarayıcıda OIDC istemcisine gerek yok (§9.1); tarayıcıda token çalınması tehdidi (§10.1) |

## Doğrulama

S2 spike'ı: BFF + Keycloak 26.8 Organizations + CSRF (§15.3). Faz 3 çıkış kriteri: E2E testi tarayıcıda token olmadığını doğruluyor. Çekirdek tamam kriteri §16.1 madde 14 (BFF/CSRF/`__Host-` çerez aktif).

**S2 sonucu (2026-10-10, `bde87e6`):** BFF Spring Security 7.1.1 `oauth2Login` ile uygulamanın içinde çalıştı; oturumlar Spring Session JDBC ile platform DB'sinde (ADR-0039). Çerez `__Host-SESSION=…; Path=/; Secure; HttpOnly; SameSite=Lax`, `Domain` yok; oturum kimliği girişte değişiyor. Uygulama yanıtlarının gövdesinde, başlıklarında ve çerezlerinde token yok. BFF access ve refresh token saklamıyor (`OAuth2AuthorizedClientRepository` onları atıyor); oturumda principal'ın içinde yalnızca ID token var. Belgelenmiş tek istisna: RP-initiated logout ID token'ı bir kez, Keycloak'ın çıkış uç noktasına giden 302'nin `Location`'ında (`id_token_hint`) tarayıcıdan geçirir; tarayıcı geçmişinde, proxy ve Keycloak erişim loglarında görünür. Karar tarayıcıya access ya da refresh token verilmesini yasaklar, ID token'ı değil; oturumda ID token zaten bunun için tutulur, bu yüzden karar değişmez. İstisnanın iki sınırı Faz 1/3'e geçer: Keycloak 26.8 `id_token_hint`'in `exp`'ini denetlemez (bytecode okuması; spike sadece taze ipucuyla çıkış denedi), bu yüzden sızan bir ipucu o SSO oturumu yaşadıkça onaysız çıkış tetikleyebilir; ID token'ı bearer zincirinden ayıran tek şey `aud`'udur, bu yüzden `erp-api` ID token'ın audience'ına eklenmez (çıkarım). Alternatifler ipucusuz çıkış (Keycloak bu durumda onay sayfası gösterir; spike'ta denenmedi) ve back-channel logout'tur; ikincisi Faz 3 kararıdır. Her istekte host ↔ principal tenant kontrolü çalışıyor: başka tenant'ın host'una taşınan oturum 401 alıyor (oturum silinmez; tenant bağlamının kurulmadığı kod okumasıdır); mali müşavirin iki tenant'ta iki bağımsız oturumu var. CSRF token'ı oturumda (Spring Security varsayılanı, XOR maskeli) ve SPA onu `/bootstrap`'tan alıyor; token'sız istek ve aynı kullanıcının diğer tenant oturumunun token'ıyla gelen istek 403. Çerez tabanlı double-submit seçilmedi: kardeş alt alan adı üst alan için `XSRF-TOKEN` basabilir (cookie tossing) ve SameSite=Lax kardeşler arası POST'u durdurmaz; basılmış çerez ve aynı değerli başlıkla gelen istek 403 aldı. Oturumsuz `/bootstrap` 401 (Takip maddesi). Açık kalanlar: oturumun mutlak ömrü ve Keycloak'taki iptalin (devre dışı bırakma, organization'dan çıkarma) oturuma yansıması; BFF refresh token tutmadığı için oturum Keycloak'tan bağımsız yaşar ve `spring.session.timeout` sadece boşta kalma süresidir (Faz 3 kararı). `/bootstrap`'ın §8.1 yolu (`/api/v1/bootstrap`) ve bearer istemcideki durumu Faz 3'te. SPA'nın aynı origin'den sunulması S2'nin konusu değildi. Faz 3 E2E kriteri (gerçek tarayıcıda token yok) geçerliliğini korur. Ayrıntı: [docs/spikes/s2-identity.md](../spikes/s2-identity.md) B8–B10, B14–B16.
