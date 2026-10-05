# ADR-0006: BFF + `__Host-` oturum çerezi; tarayıcıda token yok

| | |
|---|---|
| **Durum** | Önerildi |
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
