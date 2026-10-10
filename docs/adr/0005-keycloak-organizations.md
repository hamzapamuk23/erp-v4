# ADR-0005: Keycloak 26.8, tek iş realm'i + Organizations

| | |
|---|---|
| **Durum** | Değişti |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.3.1, §4.4 |

## Bağlam

v1 zaten Keycloak/OIDC kullanıyordu (§2.1). Realm-per-tenant yaklaşımı tek node'da yaklaşık 500 tenant'tan sonra yönetim düzleminde ciddi biçimde yavaşlıyor: 1000 tenant'lık bir ölçümde provisioning 59 dakika ile 84 saniye, bellek 43 GB ile ~0,9 GB kıyaslanıyor; Organizations aynı ölçekte düz kalıyor. Organizations, organization'a özel kurumsal IdP bağlamayı (Entra ID, ADFS, Google) ve alan adına göre yönlendirmeyi destekliyor; 26.8'de IdP'ler birden çok organization arasında paylaşılabiliyor.

## Karar

- Kimlik sağlayıcı **Keycloak 26.8**'dir: tek iş realm'i + **Organizations**. Tenant = organization.
- Tarayıcı girişi alt alan adının tenant'ıyla `scope=organization:<alias>` ipucuyla başlatılır. İpucu güvenlik sınırı değildir: dönen token'daki `organization` iddiası alt alan adının tenant'ıyla eşleşmezse giriş reddedilir (§4.4).
- Realm geneli kuralların sonuçları: Tenant adminleri kullanıcıyı devre dışı bırakmaz, sadece kendi organization'ından çıkarır; tamamen devre dışı bırakma yetkisi platform operatöründedir. E-postası olmayan saha çalışanları Keycloak hesabı almaz, operatör oturumu kullanır (§6.18). E-postasız ofis kullanıcısına tenant önekli kullanıcı adı verilir (`acme.ahmet`).
- Kurumsal dizin: SaaS'ta ADFS ya da Entra ID üzerinden organization'a bağlı IdP; doğrudan LDAP federasyonu sadece on-prem'de ya da ayrı realm istisnasında.
- On-prem'de aynı realm yapısı, tek organization ile gelir.
- İstisna: gerçek realm izolasyonu ya da tenant'a özel daha sıkı şifre/MFA politikası isteyen müşteri için ayrı realm. Çözümleyici `(issuer, organization) → tenant` eşlemesini kullandığı için kod değişmez.
- Uygulama sadece standart OIDC kullanır; Keycloak'a özgü API sadece provisioning adaptöründedir. Kullanıcının kimliği `user_account.id`'dir; Keycloak `sub` değeri değiştirilebilir bir bağlantıdır.

## Sonuçlar

- Olumlu: Yüzlerce tenant'ta düz kalan yönetim düzlemi; organization bazında kurumsal IdP; IdP'den bağımsızlık.
- Olumsuz: Kullanıcı adı ve e-posta benzersizliği, şifre politikası, MFA ve kaba kuvvet koruması realm geneldir; bu müşteri beklentisiyle çatışabilir (§19 R13). LDAP sınırı satış öncesinde müşteriye açıkça söylenir.
- Takip: Keycloak'ın karmaşıklığı ve küçük on-prem kurulumdaki ayak izi (§19 R5); yedek plan Spring Security 7'nin Authorization Server'ı.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Realm-per-tenant | ~500 tenant'tan sonra yönetim düzleminde ciddi yavaşlama; 1000 tenant'ta provisioning 59 dk (Organizations: 84 sn), bellek 43 GB (~0,9 GB). Sadece istisna olarak kalır. |
| Spring Security 7 Authorization Server | Doküman ayrı bir gerekçe vermiyor; yedek plan (§6.3.1, §19 R5). |

## Doğrulama

S2 spike'ı: Keycloak 26.8 Organizations + alt alan adıyla tenant seçimi + organization iddiası kontrolü (§15.3). Faz 2 çıkış kriteri: token'ın organization iddiası host'un tenant'ıyla eşleşmezse giriş reddediliyor. Faz 3 çıkış kriteri: tenant admini bir kullanıcıyı kendi organization'ından çıkardığında kullanıcı diğer tenant'larda çalışmaya devam ediyor.

**S2 sonucu (2026-10-10, `bde87e6`):** Değişen: İstisna maddesindeki "Çözümleyici `(issuer, organization) → tenant` eşlemesini kullandığı için kod değişmez" cümlesi. Çözümleyici gerçekten değişmez: tenant kaydının anahtarı `(oidc_issuer, organization_alias)`'tır ve aynı alias başka bir issuer altında çözülmez (birim testleri). Ama güvenlik zincirleri tek issuer'a güvenir: bearer zinciri tek `issuer-uri`'nin anahtarlarıyla doğrular (başka realm'in token'ı imzada 401 alır), tarayıcı zincirinin tek client registration'ı tek issuer'lıdır. Ayrı realm istisnası bu yüzden kod ister: bearer'da çoklu issuer çözümleyicisi (`JwtIssuerAuthenticationManagerResolver`, güvenilen issuer'lar = `tenant.oidc_issuer`), tarayıcıda tenant'ın issuer'ına göre client registration ve çıkış uç noktası. Bu iş ilk ayrı realm müşterisiyle gelir; spike'ta kurulmadı. Aynı cümle §6.3.1'de de var; düzeltme önerisi bulgularda. Kararın geri kalanı doğrulandı. Keycloak 26.8 `organization:<alias>` ipucuyla ID ve access token'a sadece o organization'ı `["acme"]` biçiminde koyuyor, üye olmayana iddiayı hiç koymuyor (reddetmiyor da), `organization:*` bütün üyelikleri veriyor, sabit eşleyici service account'a tenant iddiasını ve `erp-api` audience'ını veriyor; öğrenme testleri yazıldığı gibi geçti. İpucu güvenlik sınırı değil: kontrol ID token'da yapılıyor (`iss` tenant'ın issuer'ı, `organization` kümesi tam olarak host'un alias'ı); kurcalanmış, silinmiş ya da joker ipucu ve üye olmayan kullanıcı 403 alıyor ve oturum açılmıyor. Kontrol olmadan üye olmayan kullanıcı acme'de tam oturum açtı. Bu kontrol, callback'in başka host'ta tekrar oynatılmasına karşı tek savunmadır: Spring Security 7.1.1 `redirect_uri`'yi karşılaştırmıyor ve kontrol yokken acme'de başlatılan giriş globex'te tamamlanıp sadece acme üyesi kullanıcıya globex oturumu verdi. `iss` karşılaştırması tek issuer'lı spike'ta testle sınanmadı. Keycloak redirect URI'de alt alan adı jokerini eşleştirmiyor; her tenant host'u `erp-web`'in redirect ve post-logout URI'lerine provisioning ile eklenir (Faz 2). Organization alias'ı `TenantKey` biçimini (`_` dahil) kabul ediyor. Keycloak'a özgü bilgi (ipucu biçimi, iddianın adı ve şekli) uygulamada tek sınıfta kaldı; Admin API sadece test fixture'ında. Faz 2 ve Faz 3 çıkış kriterleri geçerliliğini korur. Ayrıntı: [docs/spikes/s2-identity.md](../spikes/s2-identity.md) B1–B5, B18–B20.
