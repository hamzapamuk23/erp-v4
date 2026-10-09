# Faz 0C — S2 Kimlik spike'ı Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** §15.3'teki S2 spike'ını yapmak: Keycloak 26.8 Organizations ile BFF girişinin tenant'ı alt alan adından seçtiğini, token'daki `organization` iddiasını host'un tenant'ına karşı zorladığını, oturumu `(kullanıcı, tenant)` çiftine bağlayıp Spring Session JDBC ile platform DB'sinde tuttuğunu, CSRF korumasının kardeş alt alan adlarına (başka tenant'lara) karşı da çalıştığını ve bearer zincirinin tenant'ı sabit tenant iddiasından çözdüğünü otomatik testlerle kanıtlamak; sonucu ADR-0005/0006/0039/0040'a (notlarla 0004/0024'e) işlemek.

**Architecture:** Spike, `spikes/s2-identity/` altında, `-Pspikes` ile derlenen tek bir Spring Boot uygulamasıdır. Modulith modülleri: `kernel` (S1'den kopya `TenantKey`, `TenantContext`), `tenancy` (platform DataSource, tenant dizini, host normalleştirme, tenant DataSource yer tutucusu), `identity` (actuator, bearer ve tarayıcı `SecurityFilterChain`'leri; organization iddiası; oturum), `sample` (`/`, `/bootstrap`, `/api/whoami`, `/api/echo`). Testler tek bir PostgreSQL 18.6 (sadece platform DB) ve tek bir Keycloak 26.8.0 konteynerine karşı gerçek HTTP ile koşar. Test tarayıcısı (`SpikeBrowser`) uygulamaya Caddy'nin yaptığı gibi `Host: acme.erp.test` + `X-Forwarded-Proto: https` ile, Keycloak'a doğrudan gider. Tenant DB'si yoktur. Varsayılan DataSource, Faz 1'in routing DataSource'unun yerini tutan sayaçlı bir yer tutucudur: oturum, güvenlik ve actuator bileşenlerinin ona hiç dokunmadığı kanıtlanır.

**Tech Stack:** Java 25, Spring Boot 4.1.1 (yönettiği: Spring Security **7.1.1**, Spring Session **4.1.1**, Spring Framework 7.0.9, Testcontainers 2.0.5; yerel Boot 4.1.1 BOM'undan doğrulandı), Keycloak **26.8.0** (`quay.io/keycloak/keycloak:26.8.0`, ADR-0029), PostgreSQL 18.6, Flyway (sadece fixture), Nimbus JOSE (Spring Security'nin getirdiği), JUnit 5, AssertJ.

**Spec:** [docs/architecture/v4-platform.md](../../architecture/v4-platform.md): §4.3 (platform DB: `tenant_domain`, `spring_session*`), §4.4 madde 1–2, §6.3.1 (kimlik doğrulama tablosu), §9.7, §10.1 (tehdit modeli), §11.1 (zorunlu TLS), §15.3 Faz 0 S2. ADR'ler: [0005](../../adr/0005-keycloak-organizations.md), [0006](../../adr/0006-bff-oturum-cerezi.md), [0039](../../adr/0039-spring-session-jdbc.md), [0040](../../adr/0040-uc-kimlik-yolu.md); notla [0004](../../adr/0004-platform-veritabani.md), [0024](../../adr/0024-onprem-linux-compose-erpctl.md). Önceki plan: [2026-10-05-faz-0b-s1-tenancy.md](2026-10-05-faz-0b-s1-tenancy.md); S1 bulguları: [docs/spikes/s1-tenancy.md](../../spikes/s1-tenancy.md).

## Global Constraints

- **Spike atılacak koddur** (§15.3). `spikes/s2-identity/` varsayılan reaktörde değildir, sadece `-Pspikes` ile derlenir ve plan 0E'de silinir. Kalıcı çıktılar: `docs/spikes/s2-identity.md`, ADR güncellemeleri ve `spikes/README.md`'deki S2 satırı.
- Sürümler yukarıdaki gibidir. **Yeni üçüncü taraf bağımlılık yoktur:** eklenen starter'lar Boot 4.1.1 BOM'undandır ve §12.1 yığınındadır (`spring-boot-starter-security-oauth2-client`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-session-jdbc`). Keycloak konteyneri Testcontainers çekirdeğindeki `GenericContainer` ile kurulur; `testcontainers-keycloak` eklenmez.
- Paket kökü **`com.smart.erp.spike.s2`**. Kod İngilizce, dokümanlar Türkçe (§14).
- **Tarayıcıya token verilmez** (ADR-0006): ne gövdede, ne başlıkta, ne çerezde. BFF access ve refresh token saklamaz; oturumda sadece principal'ın taşıdığı ID token vardır (Tasarım kararı 5).
- **Oturum çerezi:** `__Host-SESSION`, `Path=/; Secure; HttpOnly; SameSite=Lax`, `Domain` yok.
- **Varsayılan tenant yoktur:** kayıtsız host → 404, `ACTIVE` olmayan tenant → 503, bağlamsız `TenantContext.require()` → `MissingTenantContextException`.
- Uygulama kodu sadece standart OIDC kullanır (ADR-0005). Keycloak'a özgü iki bilgi, yani ipucu biçimi `organization:<alias>` ile iddianın adı ve şekli, tek sınıftadır: `identity.KeycloakOrganizations`. Keycloak Admin API'si sadece test fixture'ındadır (provisioning adaptörünün rolü).
- **K9:** Keycloak admin şifresi, kullanıcı şifreleri, client secret'lar ve DB rol şifresi her çalıştırmada üretilir ya da Keycloak'tan okunur. Realm JSON'unda sır yoktur.
- **K10, K11, K12** build'i kırar (Error Prone + `ArchitectureRulesTests`). Spring Session'ın kendi temizlik zamanlayıcısı kapalıdır (`cleanup-cron: "-"`).
- **Migration ayrı adımdır:** platform şemasını fixture Flyway API'siyle kurar; `spring.session.jdbc.initialize-schema: never`.
- Commit'ler Conventional Commits, kapsam `spike`. Her commit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` ile biter. `git add` açık yollarla yapılır; izlenmeyen `docs/guides/gelistirici-kilavuzu.pdf` commit'e girmez.

## Ortam notu

0B ile aynıdır: `mise` etkin kabuk (değilse komutların başına `mise x --`), Docker Desktop çalışıyor, bir kez `./mvnw -q install -DskipTests`. Ek olarak ilk çalıştırma Keycloak imajını çeker (`docker pull quay.io/keycloak/keycloak:26.8.0`). Keycloak test çalıştırması başına bir kez açılır (~15–30 sn).

| İş | Komut |
|---|---|
| Spike'ın tek test sınıfı | `./mvnw -Pspikes -pl spikes/s2-identity test -Dtest=<Sınıf>` |
| Spike'ın tamamı (test + Spotless + Error Prone) | `./mvnw -Pspikes -pl spikes/s2-identity verify` |
| Spike biçimlendirme | `./mvnw -Pspikes -pl spikes/s2-identity spotless:apply` |
| Ürün reaktörü (spike hariç) | `./mvnw verify` |

## Tasarım kararları (plan içinde verilenler)

1. **Test topolojisi üretim topolojisiyle aynıdır (Caddy arkası).** TLS proxy'de biter. Uygulama `server.forward-headers-strategy: native` ile Tomcat `RemoteIpValve`'i kullanır. Boot 4.1.1 varsayılanlarıyla (`TomcatServerProperties.Remoteip`) `internal-proxies` bütün özel ağ aralıklarıdır (10/8, 172.16/12, 192.168/16, 100.64/10, 127/8 ve IPv6 karşılıkları) ve `host-header` `X-Forwarded-Host`'tur. Yani bu aralıklardan gelen istekte `X-Forwarded-Proto` ve `X-Forwarded-Host`'a güvenilir; tenant'ı belirleyen `request.getServerName()`, `X-Forwarded-Host` varsa ondan, yoksa `Host`'tan gelir. Caddy ikisini de aynı değerle gönderir. On-prem'de LAN'daki her makine bu aralıktadır; bu yüzden üretimde `internal-proxies` Caddy'ye daraltılır ve uygulama portu dışarı açılmaz (Faz 1/7 girdisi, bulgu). Spike varsayılanı değiştirmez, davranışı testle sabitler (`BffLoginTests#forwardedHostFromATrustedProxyDecidesTheTenant`). Bu yüzden test tarayıcısı `http://127.0.0.1:<port>`'a `Host: <tenant>.erp.test` + `X-Forwarded-Proto: https` ile gider (`-Djdk.httpclient.allowRestrictedHeaders=host`). Uygulamanın ürettiği `redirect_uri` portsuzdur (`https://acme.erp.test/login/oauth2/code/keycloak`), bu yüzden Keycloak'a sabit olarak kaydedilebilir. Çerez önekini ve SameSite'ı gerçek tarayıcıda doğrulayan E2E testi Faz 3'tedir; burada `Set-Cookie` öznitelikleri doğrulanır.
2. **Tenant eşlemesi platform DB'sinde iki tablodadır:** `tenant(tenant_key, status, oidc_issuer, organization_alias)` (`(oidc_issuer, organization_alias)` tekil) ve `tenant_domain(host → tenant_key)`. §4.3 organization bağını `tenant_domain`'a koyuyor. Oysa bir tenant'ın birden çok host'u olabilir ve bearer isteğinin anlamlı bir host'u yoktur, bu yüzden bağ `tenant` tablosundadır (bulgu adayı). Çözümleme anahtarı `(issuer, alias)`'tır; böylece ayrı realm istisnasında (ADR-0005) **çözümleyici** değişmez. **Zincirler ise değişir:** bearer zinciri tek `issuer-uri` ile doğrular (başka realm'in token'ı imzada düşer), tarayıcı zincirinin tek client registration'ı tek issuer'lıdır. Ayrı realm için bearer'da `JwtIssuerAuthenticationManagerResolver` (güvenilen issuer'lar = `tenant.oidc_issuer`), tarayıcıda tenant'ın issuer'ına göre client registration gerekir. ADR-0005'in "kod değişmez" cümlesi bu yüzden doğru değildir; ADR-0005 S2 sonunda **Değişti** olur (Task 7). Çoklu issuer spike'ta kurulmaz; ilk ayrı realm müşterisiyle gelir.
3. **Tek kural: host'un tenant'ı varsa her kimlik onunla uyuşmak zorundadır.** Tarayıcıda oturum principal'ının tenant'ı host'unkinden farklıysa 401 döner; oturum silinmez, tenant bağlamı kurulmaz. Bearer'da tenant token'dan `(iss, organization)` ile çözülür. İstek kayıtlı bir tenant host'una gelmişse ve tenant farklıysa 403 döner. Nötr bir host'ta (`api.erp.test`) tenant'ı token belirler.
4. **Oturum principal'ı tenant'ı taşır:** `TenantOidcUser` (`DefaultOidcUser` + `TenantKey`), adı `<tenant>:<sub>`. Ayrı bir oturum özniteliği yoktur ve kontrol tek yerde yapılır. Spring Session'ın principal index'i `(kullanıcı, tenant)` oturumlarını bulur; Faz 3'teki "organization'dan çıkar → o tenant'taki oturumları bitir" işlemi buna dayanır.
5. **BFF token saklamaz:** `OAuth2AuthorizedClientRepository` token'ları tutmayan bir uygulamadır (`DiscardingAuthorizedClientRepository`). BFF kullanıcı token'ıyla aşağı akışta API çağırmaz. Boot'un varsayılanı (`InMemoryOAuth2AuthorizedClientService`) token'ları instance belleğinde sınırsız biriktirir ve instance'lar arasında tutarsızdır. RP-initiated logout ID token'ı principal'dan alır; bu yüzden oturumda **yalnızca ID token** vardır (`TenantOidcUser` içinde, e-posta ve adla birlikte), access ve refresh token yoktur. Sonuç: uygulama oturumu Keycloak oturumundan bağımsız yaşar. `spring.session.timeout` yalnızca boşta kalma süresidir; aktif kullanılan oturumun mutlak bir ömrü yoktur ve Keycloak'ta kullanıcının devre dışı bırakılması ya da organization'dan çıkarılması oturuma kendiliğinden yansımaz. Mutlak ömür (ör. ID token'ın `auth_time`'ı ile) ve iptal Faz 3 kararıdır (bulgu; hedef ASVS L2, §10.1).
6. **CSRF token'ı oturumda tutulur (senkronizör token).** Spring Security'nin varsayılanı kullanılır: `HttpSessionCsrfTokenRepository` + XOR maskesi. SPA token'ı `/bootstrap` gövdesinden alır ve `X-CSRF-TOKEN` başlığıyla geri gönderir. Çerez tabanlı double-submit (`XSRF-TOKEN`) seçilmez: kardeş bir alt alan adı (başka bir tenant) `.erp.test` için çerez basabilir (cookie tossing) ve SameSite=Lax kardeşler arası POST'u durdurmaz.
7. **Organization ipucu `DefaultOAuth2AuthorizationRequestResolver`'ın özelleştiricisinde eklenir; PKCE kayıttan gelir.** Spring Security 7.1.1 PKCE'yi gizli istemcide varsayılan olarak eklemez; kayıtta `ClientSettings.requireProofKey(true)` verilir. İpucu bir güvenlik sınırı değildir. **Kontrol ID token'da yapılır:** `iss` tenant'ın issuer'ına eşit olmalı, `organization` iddiası da tam olarak `{tenant'ın alias'ı}` olmalıdır (fazlası da eksiği de ret). Giriş hatası yönlendirme değil 403 üretir; yönlendirme olsaydı Keycloak SSO oturumu yüzünden sonsuz döngüye girerdi. **Bu kontrol, callback'in başka bir host'ta tekrar oynatılmasına karşı tek savunmadır:** Spring Security 7.1.1 (`OidcAuthorizationCodeAuthenticationProvider`) callback'te sadece `state`'i karşılaştırır, `redirect_uri`'yi karşılaştırmaz ve kodu oturumdaki (ilk host'un) `redirect_uri`'siyle takas eder. Kontrol olmasaydı acme'de başlatılıp globex'te tamamlanan giriş globex oturumu açardı.
8. **İstemci kaydı tembeldir:** `LazyClientRegistrationRepository` OIDC discovery'yi ilk girişte yapar. Boot'un `issuer-uri` ile oluşturduğu kayıt açılışta Keycloak'a gider ve on-prem compose'da Keycloak'tan önce kalkan uygulamayı düşürür. Bearer tarafında Boot'un `issuer-uri` decoder'ı zaten tembeldir.
9. **Spring Session platform DB'sinde, kendi transaction'ıyla çalışır:** platform DataSource `@SpringSessionDataSource` ile işaretlenir. Spring Session 4.1.1 (`JdbcHttpSessionConfiguration`) transaction için iki kanca tanır: adı `springSessionTransactionOperations` olan bir `TransactionOperations` ya da `@SpringSessionTransactionManager` nitelikli bir `PlatformTransactionManager`. İkisi de yoksa bağlamdaki tek `PlatformTransactionManager`'ı alır; o da Boot'un varsayılan DataSource'taki (Faz 1'de routing DataSource'taki) yöneticisidir. Spike ilk kancayı kullanır: `@Bean(name = "springSessionTransactionOperations", defaultCandidate = false)`, platform DS üzerinde `DataSourceTransactionManager`, `PROPAGATION_REQUIRES_NEW` (kütüphanenin kendi varsayılanı). `defaultCandidate = false` zorunludur: Boot'un `transactionTemplate`'i `@ConditionalOnMissingBean(TransactionOperations.class)` ile kurulur ve varsayılan aday olan bir `TransactionOperations` onu bastırır; uygulamanın `TransactionTemplate`'i ya kaybolur ya da platform DB'sine gider. Tenant DataSource yer tutucusu varsayılan aday olduğu için yanlış kablolama sayaçta görünür.
10. **Okunamayan oturum S2'nin konusu değildir (Faz 1 notu).** Spring Security 7.1.1 serileştirme kimliklerini sabit tutar (`SpringSecurityCoreVersion.SERIAL_VERSION_UID = 620L`; `OAuth2AuthenticationToken` ve `SecurityContextImpl` 620L, `DefaultOidcUser` ve `OidcIdToken` sabit UID'li), yani minör sürüm geçişi oturumu bozmaz. Kalan riskler (oturumda serileştirilen kendi sınıflarımızdaki uyumsuz değişiklik, Spring Security major yükseltmesi, bozuk satır) Faz 1'e not olarak geçer: kendi sınıflarımızda sabit `serialVersionUID` ve gerekirse Spring Session'ın `springSessionConversionService` kancasıyla toleranslı bir deserializer.

## Review Focus

1. **Sahte ya da kurcalanmış organization:** ipucu başka bir organization'la ya da `organization:*` ile değiştirilir, ipucu silinir veya kullanıcı organization üyesi değildir. Beklenen: giriş 403, kimliği doğrulanmış oturum oluşmaz. → Task 4 `OrganizationClaimCheckTests`.
2. **Oturumun başka bir tenant host'unda kullanılması:** çerez başka bir host'a taşınır, callback başka bir host'ta tekrar oynatılır ya da mali müşavirin iki oturumu karışır. Beklenen: 401 ya da 403; diğer tenant'ın bağlamı hiç kurulmaz. Callback tekrarını Spring durdurmaz, sadece iddia kontrolü durdurur (Tasarım kararı 7). → Task 4 `SessionTenantBindingTests`.
3. **Host başlığı varyantları:** büyük harf, Türkçe `İ`, sondaki nokta, `_`, tam genişlikli karakter, IP literal, boş değer, kayıtsız host, güvenilen adresten gelen `X-Forwarded-Host`. Beklenen: normalleştirme ya da 404; asla varsayılan tenant yok; `X-Forwarded-Host`'un etkisi sabitlenir (Tasarım kararı 1). → Task 1 `TenantHostTests`, Task 3 `BffLoginTests#unknownHostIs404AndStartsNoLogin`, `#forwardedHostFromATrustedProxyDecidesTheTenant`.
4. **Aynı site içinden (kardeş alt alan adından) CSRF:** basılmış double-submit çerezi, başka bir tenant oturumunun token'ı, token'sız çıkış. Beklenen: 403. → Task 5 `CsrfTests`, `LogoutTests`.
5. **Geçerli ama yanlış bearer token:** organization iddiası yok, iki organization var, organization kayıtsız, audience yanlış, yabancı anahtarla imzalı, tenant askıda, token başka bir tenant'ın host'unda; geçersiz token geçerli oturum çereziyle gelir. Beklenen: 401, 403 ya da 503; oturum oluşmaz, istek çereze düşmez. → Task 6 `BearerChainTests`.

---

## Dosya haritası (bu planın sonunda)

```
erp-v4/
 ├─ pom.xml                                   `spikes` profiline + spikes/s2-identity
 ├─ spikes/
 │   ├─ README.md                             + S2 satırı
 │   └─ s2-identity/
 │       ├─ pom.xml                           erp-spike-s2-identity (parent: erp-parent)
 │       └─ src/
 │           ├─ main/java/com/smart/erp/spike/s2/
 │           │   ├─ SpikeApplication.java
 │           │   ├─ kernel/    TenantKey, TenantContext, MissingTenantContextException,
 │           │   │             TenantSwitchInTransactionException            (S1'den kopya)
 │           │   ├─ tenancy/   PlatformDb, PlatformDataSourceProperties, PlatformDataSourceConfiguration,
 │           │   │             TenantStatus, TenantRecord, TenantDirectory, JdbcTenantDirectory, TenantHost,
 │           │   │             TenantDataSourceStandIn
 │           │   ├─ identity/  IdentityProperties, SecurityConfiguration, SessionConfiguration, KeycloakOrganizations,
 │           │   │             BrowserTenantFilter, BearerTenantFilter, TenantFilterChain, TenantOidcUser,
 │           │   │             TenantOidcUserService, LazyClientRegistrationRepository,
 │           │   │             DiscardingAuthorizedClientRepository, TenantJwtAuthentication,
 │           │   │             TenantJwtAuthenticationConverter
 │           │   └─ sample/    SampleController, Bootstrap, WhoAmI
 │           ├─ main/resources/
 │           │   ├─ application.yaml
 │           │   └─ db/platform/V202610061200__tenant_registry.sql, V202610061201__spring_session.sql
 │           └─ test/
 │               ├─ resources/keycloak/erp-realm.json
 │               └─ java/com/smart/erp/spike/s2/
 │                   ├─ support/   SpikeDatabases, SpikeKeycloak, SpikeBrowser, SpikeEnvironment, SpikeTest, SpikeContexts
 │                   ├─ ModularityTests, ArchitectureRulesTests, SpikeDatabasesTests, StartupIsolationTests,
 │                   │  KeycloakUnavailableTests
 │                   ├─ kernel/    TenantKeyTests, TenantContextTests
 │                   ├─ tenancy/   TenantHostTests, JdbcTenantDirectoryTests, TenantDataSourceStandInTests
 │                   ├─ keycloak/  KeycloakOrganizationsLearningTests
 │                   └─ identity/  KeycloakOrganizationsTests, LazyClientRegistrationRepositoryTests, SessionWiringTests,
 │                                 BffLoginTests, OrganizationClaimCheckTests, SessionTenantBindingTests, CsrfTests,
 │                                 LogoutTests, BearerChainTests, TenantJwtAuthenticationConverterTests
 └─ docs/
     ├─ spikes/s2-identity.md                 Bulgular, kanıt tablosu, Faz 1/2/3 girdileri
     └─ adr/                                  0005, 0006, 0039, 0040 (+ not: 0004, 0024) + README dizini
```

Test kullanıcıları ve tenant'lar (Task 2'de Keycloak'ta, Task 3'te platform DB'sinde kurulur):

| Kullanıcı | Organization üyeliği | Kullanım |
|---|---|---|
| `ayse` | acme | Tek tenant'lı kullanıcı |
| `zeynep` | globex | acme'de "üye değil" senaryosu |
| `mm` | acme, globex | Mali müşavir: iki tenant, iki oturum |
| `ipek` | initech | Askıya alma senaryosu (initech başka testte kullanılmaz) |

| Tenant | Host | Organization alias | Durum |
|---|---|---|---|
| acme | `acme.erp.test` | `acme` | ACTIVE |
| globex | `globex.erp.test` | `globex` | ACTIVE |
| initech | `initech.erp.test` | `initech` | ACTIVE (test geçici olarak SUSPENDED yapar) |
| — | `api.erp.test`, `unknown.erp.test` | — | kayıtsız (nötr API host'u / bilinmeyen host) |

---

### Task 1: Spike iskeleti, platform DB'si ve tenant dizini

**Files:**
- Modify: `pom.xml` (`spikes` profiline `<module>spikes/s2-identity</module>`), `spikes/README.md` (S2 satırı)
- Create: `spikes/s2-identity/pom.xml`, `.../SpikeApplication.java`
- Create (S1'den kopya, paket `com.smart.erp.spike.s2.kernel`): `TenantKey` (+ `implements Serializable`), `TenantContext`, `MissingTenantContextException`, `TenantSwitchInTransactionException`
- Create: `tenancy/PlatformDb`, `PlatformDataSourceProperties`, `PlatformDataSourceConfiguration`, `TenantStatus`, `TenantRecord`, `TenantDirectory`, `JdbcTenantDirectory`, `TenantHost`, `TenantDataSourceStandIn`
- Create: `src/main/resources/db/platform/V202610061200__tenant_registry.sql`, `V202610061201__spring_session.sql`
- Test: `support/SpikeDatabases`, `ModularityTests`, `ArchitectureRulesTests`, `SpikeDatabasesTests`, `kernel/TenantKeyTests`, `kernel/TenantContextTests` (S1'den kopya), `tenancy/TenantHostTests`, `tenancy/JdbcTenantDirectoryTests`, `tenancy/TenantDataSourceStandInTests`

**Interfaces:**
- Consumes: `erp-parent`, `com.smart.erp.platformtest.postgres.PostgresTestcontainer.newContainer()`, `ErpArchitectureRules`.
- Produces:
  - `record TenantKey(String value) implements Serializable` (S1 biçim kuralı `[a-z][a-z0-9_]{1,29}`); `TenantContext` (S1'deki kapsam API'si ve transaction koruması aynen).
  - `enum TenantStatus { PROVISIONING, ACTIVE, SUSPENDED, MAINTENANCE, ARCHIVED }`
  - `record TenantRecord(TenantKey key, TenantStatus status, String issuer, String organizationAlias)`
  - `interface TenantDirectory { Optional<TenantRecord> findByHost(String normalizedHost); Optional<TenantRecord> findByIdentity(String issuer, String organizationAlias); TenantRecord require(TenantKey key); }`. `require` kayıt yoksa `IllegalStateException` fırlatır. `JdbcTenantDirectory` platform DS üzerinde `JdbcClient` kullanır ve önbellek tutmaz (durum her aramada okunur).
  - `final class TenantHost { static Optional<String> normalize(@Nullable String serverName); }`
  - `@PlatformDb` qualifier; platform DS `@Bean(defaultCandidate = false) @PlatformDb` (S1 B8 düzeni; `erp.platform.datasource.url/username/password`).
  - `final class TenantDataSourceStandIn extends AbstractRoutingDataSource`: hiç bağlantı vermez, her denemede `requests()` sayacını artırıp `MissingTenantContextException` fırlatır. `unwrap/isWrapperFor` yönlendirme yapmaz ve sayılmaz (S1 B7). Varsayılan aday olan tek DataSource budur (`@Bean`, qualifier yok).
  - Test: `SpikeDatabases`: `platformDataSource()`, `platformDatabase()` (süper kullanıcı `JdbcClient`), `register(TenantRecord, String... hosts)` (idempotent), `setStatus(TenantKey, TenantStatus)`, `applicationProperties()`.

**Neden:** S2'nin tenant kaynağı sadece platform DB'sidir (§4.3); tenant DB'si gerekmez. Yer tutucu, S1'in açık maddesi 1'in (açılış izolasyonu web yığınıyla) oturum + güvenlik + actuator kısmını burada kanıtlamayı sağlar.

- [ ] **Step 1: İskelet.** `spikes/s2-identity/pom.xml`, S1 pom'unun düzenini izler (`erp-parent`, `artifactId` `erp-spike-s2-identity`, açıklama "Phase 0 S2 spike… Throwaway"). Bu görevdeki bağımlılıklar: `spring-boot-starter`, `spring-boot-starter-jdbc`, `org.postgresql:postgresql` (runtime). Test kapsamındakiler: `spring-boot-starter-test`, `spring-modulith-starter-test`, `erp-platform-test`, `flyway-core`, `flyway-database-postgresql`. Starter'lar ihtiyaç duyan görevde eklenir (S1 kararı 9). Surefire ayarı (Tasarım kararı 1):

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- SpikeBrowser reaches the app like Caddy does: original Host header (design decision 1). -->
                <argLine>-Djdk.httpclient.allowRestrictedHeaders=host</argLine>
            </configuration>
        </plugin>
    </plugins>
</build>
```

`spikes/README.md` tablosuna şu satır eklenir: `| S2 — Kimlik | [`s2-identity/`](s2-identity/) | [docs/spikes/s2-identity.md](../docs/spikes/s2-identity.md) |`. `kernel` sınıflarını ve `TenantKeyTests`/`TenantContextTests`'i S1'den kopyala, paket adını değiştir ve `TenantKey`'e `implements Serializable` ekle. Oturumda saklanan principal onu taşır (Task 3).

- [ ] **Step 2: Başarısız testleri yaz.**
  - `ModularityTests#modulesRespectTheirBoundaries`: `ApplicationModules.of(SpikeApplication.class).verify()`.
  - `ArchitectureRulesTests`: K10 `NO_LOCALE_LESS_CASE_CONVERSION`, K12 `NO_SPRING_SCHEDULING` (S1'deki gibi).
  - `TenantKeyTests#isSerializable`: serialize ve deserialize edildikten sonra eşit kalır.
  - `TenantHostTests` (birim; her satır bir test ya da `@ParameterizedTest`):

| Girdi | Beklenen | Neden |
|---|---|---|
| `acme.erp.test` | `acme.erp.test` | |
| `ACME.ERP.TEST` | `acme.erp.test` | Host büyük-küçük harfe duyarsız |
| `INITECH.erp.test`, varsayılan `Locale` `tr` iken | `initech.erp.test` | K10: `Locale.ROOT` (aksi halde `ınıtech`) |
| `acme.erp.test.` | `acme.erp.test` | Tam nitelikli adın sondaki noktası |
| `İnitech.erp.test` | boş | ASCII dışı |
| `ａcme.erp.test` (tam genişlik) | boş | ASCII dışı |
| `acme_tr.erp.test` | boş | DNS etiketi `_` içeremez, ama `TenantKey` içerebilir (bulgu adayı) |
| `-acme.erp.test`, `a..b`, 64 karakterlik etiket | boş | Geçersiz etiket |
| `127.0.0.1` | `127.0.0.1` | Etiketleri geçerli; dizinde kaydı olmadığı için 404 olur (normalleştirme IP'yi ayırmaz) |
| `[::1]` | boş | IPv6 literal geçersiz etiket |
| `null`, `""` | boş | |

  - `JdbcTenantDirectoryTests` (Spring'siz; `new JdbcTenantDirectory(SpikeDatabases.platformDataSource())`; kendi anahtarları `t1_alpha` ve `t1_beta`; issuer'lar `https://issuer-a.test/realms/erp` ve `https://issuer-b.test/realms/erp`): `findsTenantByHost`, `unknownHostFindsNothing`, `findsTenantByIssuerAndAlias`, `sameAliasUnderAnotherIssuerDoesNotResolve` (ADR-0005 ayrı realm istisnası), `requireFailsForUnknownTenant`, `statusIsReadOnEveryLookup` (`setStatus` → bir sonraki `findByHost` `SUSPENDED` döner).
  - `TenantDataSourceStandInTests`: `getConnection()` `MissingTenantContextException` fırlatır ve `requests()` 1 olur; `unwrap(DataSource.class)` kendini döner ve sayılmaz; `isWrapperFor(HikariDataSource.class)` `false` döner.
  - `SpikeDatabasesTests`: platform DB'de `tenant`, `tenant_domain`, `spring_session`, `spring_session_attributes` tabloları var; sahibi `erp_platform` rolü; `PUBLIC` için `CONNECT` kapalı.

- [ ] **Step 3: Çalıştır, derleme hatasıyla düştüğünü gör.** `./mvnw -Pspikes -pl spikes/s2-identity test`

- [ ] **Step 4: Uygula.** Migration'lar:

```sql
-- V202610061200__tenant_registry.sql: tenant registry and host mapping in the platform DB (doc §4.3).
-- The organization link lives on the tenant, not on the domain: a tenant may have several hosts, and a bearer request
-- has no meaningful host (S2 design decision 2). (issuer, alias) is the key, so a separate realm needs no resolver
-- change; the security chains still trust a single issuer (S2 finding).
create table tenant (
    tenant_key         text primary key check (tenant_key ~ '^[a-z][a-z0-9_]{1,29}$'),
    status             text not null check (status in ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'MAINTENANCE', 'ARCHIVED')),
    oidc_issuer        text not null,
    organization_alias text not null,
    unique (oidc_issuer, organization_alias)
);

create table tenant_domain (
    host       text primary key check (host = lower(host)),
    tenant_key text not null references tenant
);
```

`V202610061201__spring_session.sql`: Spring Session 4.1.1 jar'ındaki `org/springframework/session/jdbc/schema-postgresql.sql` aynen kopyalanır; dosyanın başına kaynak ve sürüm yorum satırı olarak eklenir. `SpikeDatabases` S1'inkinin sadeleştirilmiş halidir: tek konteyner, sadece `erp_platform` DB'si ve rolü (şifre `UUID.randomUUID()`), Flyway `classpath:db/platform`.

`TenantHost`:

```java
/**
 * Normalizes the request's server name before it is looked up in tenant_domain (doc §4.4 rule 1). Only plain ASCII
 * DNS names pass; anything else resolves to no tenant (404), never to a default one.
 */
public final class TenantHost {

    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private TenantHost() {}

    public static Optional<String> normalize(@Nullable String serverName) {
        if (serverName == null || serverName.isEmpty() || serverName.length() > 254) {
            return Optional.empty();
        }
        String host = serverName.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String label : host.split("\\.", -1)) {
            if (!LABEL.matcher(label).matches()) {
                return Optional.empty();
            }
        }
        return Optional.of(host);
    }
}
```

`JdbcTenantDirectory`: üç sorgu (`tenant_domain join tenant using (tenant_key) where host = ?`, `where oidc_issuer = ? and organization_alias = ?`, `where tenant_key = ?`). Önbellek yoktur; Faz 2'de Caffeine + LISTEN/NOTIFY gelir (ADR-0016). `PlatformDataSourceConfiguration`: platform DS (`HikariDataSource`, S1 düzeni), `JdbcTenantDirectory` bean'i ve `TenantDataSourceStandIn` bean'i.

- [ ] **Step 5: Çalıştır, geçtiğini gör.** `./mvnw -Pspikes -pl spikes/s2-identity verify` → `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add pom.xml spikes/README.md spikes/s2-identity/pom.xml spikes/s2-identity/src
git commit -m "feat(spike): add S2 identity spike module with platform tenant directory and host normalization

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Keycloak fixture'ı, test tarayıcısı ve Organizations öğrenme testleri

**Risk:** Sonraki bütün görevler Keycloak 26.8'in Organizations davranışına (iddianın şekli, ipucunun etkisi, üye olmayanın token'ı, sabit iddia eşleyicisi) dayanır. Bu davranış önce burada, uygulamadan bağımsız olarak sabitlenir.

**Files:**
- Create: `src/test/resources/keycloak/erp-realm.json`
- Create: `src/test/java/.../support/SpikeKeycloak.java`, `support/SpikeBrowser.java`
- Create: `src/main/java/.../identity/KeycloakOrganizations.java`
- Test: `identity/KeycloakOrganizationsTests.java`, `keycloak/KeycloakOrganizationsLearningTests.java`

**Interfaces:**
- Produces:
  - `KeycloakOrganizations`: `CLAIM = "organization"`, `static String scopeFor(String alias)` (→ `organization:<alias>`), `static Set<String> aliases(@Nullable Object claim)`. Liste, map (anahtarlar) ve tek string şekillerini kabul eder; biçimi bozuk iddia boş küme döner.
  - `SpikeKeycloak`: `IMAGE`, `issuer()`, `password(String username)`, `clientSecret(String clientId)`, `codeFlow(String username, String scope) → Tokens`, `clientCredentials(String clientId) → String`, `authorizationUrl(String clientId, String redirectUri, String scope) → String`, `createClient(String json) → int`, `createOrganization(String alias, String domain) → int`, `applicationProperties() → Map<String, String>`, `record Tokens(String idToken, String accessToken)`.
  - `SpikeBrowser`: `SpikeBrowser(int appPort)`, `record Page(URI url, int status, HttpHeaders headers, String body)` + `Optional<String> location()` (mantıksal URL'ye göre çözülmüş), `get(String url, String... headerPairs)`, `post(String url, Map<String, String> form, String... headerPairs)`, `login(String host, String username)`, `follow(Page, String username, @Nullable String stopBefore)`, `rewriteAuthorizationRequests(UnaryOperator<URI>)`, `cookie(String host, String name)`, `putCookie(String host, String name, String value)`, `sessionId(String host) → Optional<String>` (`__Host-SESSION` çerezinin Base64 çözülmüş hali, yani `spring_session.session_id`; paylaşılan platform DB'sinde testler kendi oturumlarını bununla hedefler, kullanıcı adıyla değil), `setCookieHeaders(String host) → List<String>`, `appResponses() → List<Page>`, `static MultiValueMap<String, String> query(String url)` (çözülmüş değerler), `static Map<String, Object> json(Page)`.

- [ ] **Step 1: Realm'i yaz.** Realm JSON'unda kullanıcı, organization ve sır yoktur; bunları fixture Admin API ile kurar.

```json
{
  "realm": "erp",
  "enabled": true,
  "organizationsEnabled": true,
  "sslRequired": "none",
  "clients": [
    {
      "clientId": "erp-web",
      "publicClient": false,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": false,
      "redirectUris": ["https://acme.erp.test/*", "https://globex.erp.test/*", "https://initech.erp.test/*"],
      "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+"}
    },
    {
      "clientId": "acme-integration",
      "publicClient": false,
      "standardFlowEnabled": false,
      "serviceAccountsEnabled": true,
      "protocolMappers": [
        {"name": "erp-api-audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
         "config": {"included.custom.audience": "erp-api", "access.token.claim": "true"}},
        {"name": "tenant", "protocol": "openid-connect", "protocolMapper": "oidc-hardcoded-claim-mapper",
         "config": {"claim.name": "organization", "claim.value": "[\"acme\"]", "jsonType.label": "JSON",
                    "access.token.claim": "true"}}
      ]
    }
  ]
}
```

Diğer entegrasyon istemcileri `acme-integration`'la aynı şablonla, sadece şu farklarla yazılır:

| clientId | `tenant` eşleyicisinin `claim.value`'su | `erp-api-audience` eşleyicisi | Kullanım (Task 6) |
|---|---|---|---|
| `initech-integration` | `["initech"]` | var | Askıdaki tenant |
| `multi-integration` | `["acme","globex"]` | var | İki organization |
| `unknown-integration` | `["hooli"]` | var | Kayıtsız organization |
| `noorg-integration` | eşleyici yok | var | İddia yok |
| `noaud-integration` | `["acme"]` | yok | Audience yok |

`sslRequired: none` sadece test realm'i içindir: testler Keycloak'a düz HTTP ile gider.

- [ ] **Step 2: `SpikeKeycloak`'ı yaz.** Konteyner şöyle kurulur:

```java
private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(IMAGE))
        .withCommand("start-dev", "--import-realm")
        .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
        .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD) // UUID per run (K9)
        .withEnv("KC_HEALTH_ENABLED", "true")
        .withCopyToContainer(MountableFile.forClasspathResource("keycloak/erp-realm.json"),
                "/opt/keycloak/data/import/erp-realm.json")
        .withExposedPorts(8080, 9000)
        .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)));
```

Statik başlatıcı, konteyner açıldıktan sonra Admin API ile (JDK `HttpClient`; JSON okuma `JsonParserFactory.getJsonParser()`; her çağrıda `master` realm'inde `admin-cli` ile yeni bir admin token alınır, çünkü admin token'ın ömrü 60 sn'dir) sırayla şunları yapar:
1. Kullanıcılar (`ayse`, `zeynep`, `mm`, `ipek`) için `POST /admin/realms/erp/users` çağrısı. Gövde: `username`, `email` (`<ad>@<org>.example`; `mm` için `mm@musavir.example`), `firstName`, `lastName`, `enabled: true`, `emailVerified: true`. Bu alanlar eksik olursa Keycloak girişte profil güncelleme sayfası açar. Ardından `PUT .../users/{id}/reset-password` ile rastgele ve kalıcı bir şifre verilir.
2. Organization'lar (`acme`, `globex`, `initech`) için `POST /admin/realms/erp/organizations` çağrısı: `{"name", "alias", "enabled": true, "domains": [{"name": "<alias>.example"}]}`. Üyelikler (kullanıcı tablosundaki gibi) `POST .../organizations/{id}/members` ile eklenir; gövde JSON string olarak kullanıcı kimliğidir.
3. `organization` client scope'u `erp-web`'e isteğe bağlı scope olarak bağlanır: `GET /client-scopes`'tan `name == "organization"` bulunur, ardından `PUT /clients/{uuid}/optional-client-scopes/{scopeId}` çağrılır. Bu adım idempotenttir; içe aktarma bağlamış olsa da yapılır.
4. Gizli istemcilerin secret'ları `GET /clients/{uuid}/client-secret` ile okunur ve önbelleğe alınır.

`issuer()` = `http://localhost:<eşlenen 8080>/realms/erp`. Uygulama da test tarayıcısı da Keycloak'a aynı adresle gittiği için issuer tutarlıdır. `applicationProperties()` şunları döner: `erp.identity.issuer-uri`, `erp.identity.web-client-secret` (= `clientSecret("erp-web")`). `codeFlow` testlerin uygulamasız token almasını sağlar:

```java
public static Tokens codeFlow(String username, String scope) {
    String redirectUri = "https://acme.erp.test/login/oauth2/code/keycloak"; // registered on erp-web; never followed
    String verifier = randomUrlSafe(32);
    String url = authorizationUrl("erp-web", redirectUri, scope) + "&code_challenge=" + s256(verifier)
            + "&code_challenge_method=S256&nonce=" + randomUrlSafe(16);
    SpikeBrowser browser = new SpikeBrowser(0);
    SpikeBrowser.Page callback = browser.follow(browser.get(url, "Accept", "text/html"), username, redirectUri);
    String code = callback.location()
            .filter(location -> location.startsWith(redirectUri))
            .map(location -> SpikeBrowser.query(location).getFirst("code"))
            // Keycloak refused before the callback (an error page, not a token): say what it showed, it is a finding.
            .orElseThrow(() -> new IllegalStateException("Keycloak did not reach the callback for " + username + " / "
                    + scope + ": " + callback.status() + " " + callback.url() + " "
                    + callback.body().substring(0, Math.min(500, callback.body().length()))));
    Map<String, Object> tokens = tokenRequest(Map.of(
            "grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri, "code_verifier", verifier,
            "client_id", "erp-web", "client_secret", clientSecret("erp-web")));
    return new Tokens((String) tokens.get("id_token"), (String) tokens.get("access_token"));
}
```

- [ ] **Step 3: `SpikeBrowser`'ı yaz.** Çekirdek kod:

```java
/**
 * Just enough browser for login flows across the app and Keycloak: one cookie jar per host, manual redirects and
 * Keycloak's login form. App hosts (*.erp.test) are reached the way Caddy reaches the app (design decision 1): plain
 * HTTP to 127.0.0.1 with the original Host and X-Forwarded-Proto: https. Every app response is recorded.
 */
public final class SpikeBrowser {

    private static final Pattern LOGIN_FORM = Pattern.compile("<form\\b[^>]*\\bid=\"kc-form-login\"[^>]*>");
    private static final Pattern ACTION = Pattern.compile("\\baction=\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final int appPort;
    private final Map<String, Map<String, String>> cookies = new HashMap<>();
    private final Map<String, List<String>> setCookieHeaders = new HashMap<>();
    private final List<Page> appResponses = new ArrayList<>();
    private UnaryOperator<URI> authorizationRewrite = UnaryOperator.identity();

    public record Page(URI url, int status, HttpHeaders headers, String body) {
        public Optional<String> location() {
            return headers.firstValue("Location").map(location -> url.resolve(location).toString());
        }
    }

    /** Starts a login on {@code host} and follows it to the end (the landing page or the first non-redirect). */
    public Page login(String host, String username) {
        return follow(get("https://" + host + "/oauth2/authorization/keycloak", "Accept", "text/html"), username, null);
    }

    /**
     * Follows redirects and fills Keycloak's login form (identity-first: username, then password). Stops before
     * requesting a URL that starts with {@code stopBefore} and returns the redirect pointing there.
     */
    public Page follow(Page page, String username, @Nullable String stopBefore) {
        Page current = page;
        for (int step = 0; step < 20; step++) {
            Optional<String> location = current.location();
            if (location.isPresent()) {
                URI next = URI.create(location.get());
                if (stopBefore != null && next.toString().startsWith(stopBefore)) {
                    return current;
                }
                if (next.getPath().endsWith("/protocol/openid-connect/auth")) {
                    next = authorizationRewrite.apply(next);
                }
                current = get(next.toString(), "Accept", "text/html");
            } else if (current.status() == 200 && LOGIN_FORM.matcher(current.body()).find()) {
                current = submitLoginForm(current, username);
            } else {
                return current;
            }
        }
        throw new IllegalStateException("Login did not settle; last page: " + current.url());
    }

    private Page submitLoginForm(Page page, String username) {
        Matcher form = LOGIN_FORM.matcher(page.body());
        Matcher action = ACTION.matcher(form.find() ? form.group() : "");
        if (!action.find()) {
            throw new IllegalStateException("Keycloak login form without an action at " + page.url());
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (page.body().contains("name=\"username\"")) {
            fields.put("username", username);
        }
        if (page.body().contains("name=\"password\"")) {
            fields.put("password", SpikeKeycloak.password(username));
        }
        String target = page.url().resolve(action.group(1).replace("&amp;", "&")).toString();
        return post(target, fields, "Accept", "text/html");
    }

    private Page send(URI url, String method, @Nullable String form, String... headerPairs) {
        boolean app = url.getHost().endsWith(".erp.test");
        URI target = app
                ? URI.create("http://127.0.0.1:" + appPort + url.getRawPath()
                        + (url.getRawQuery() == null ? "" : "?" + url.getRawQuery()))
                : url;
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .method(method, form == null ? BodyPublishers.noBody() : BodyPublishers.ofString(form));
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded");
        }
        if (app) {
            request.header("Host", url.getHost()).header("X-Forwarded-Proto", "https");
        }
        for (int i = 0; i < headerPairs.length; i += 2) {
            request.header(headerPairs[i], headerPairs[i + 1]);
        }
        String jar = url.getPort() == -1 ? url.getHost() : url.getHost() + ":" + url.getPort();
        String cookieHeader = cookies.getOrDefault(jar, Map.of()).entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
        if (!cookieHeader.isEmpty()) {
            request.header("Cookie", cookieHeader);
        }
        HttpResponse<String> response = exchange(request.build());
        response.headers().allValues("Set-Cookie").forEach(header -> store(jar, header));
        Page page = new Page(url, response.statusCode(), response.headers(), response.body());
        if (app) {
            appResponses.add(page);
        }
        return page;
    }
}
```

Kalan kısa metotlar: `get` ve `post` (`send`'i çağırır; form alanları `URLEncoder.encode(…, UTF_8)` ile kodlanır), `exchange` (`IOException` → `UncheckedIOException`; `InterruptedException` → interrupt bayrağını geri koy ve `IllegalStateException` fırlat), `store` (`name=value` ayrıştırılır; `Max-Age=0` silme demektir; ham başlık `setCookieHeaders`'a eklenir; path ve `Domain` jar'da yok sayılır, öznitelikler testlerde ham başlıktan doğrulanır), `cookie`, `putCookie`, `sessionId` (`Base64.getDecoder()`; Spring Session'ın `DefaultCookieSerializer`'ı değeri Base64 yazar), `rewriteAuthorizationRequests`, `query` (`UriComponentsBuilder.fromUriString(url).build().getQueryParams()` + değer başına `URLDecoder.decode(…, UTF_8)`), `json` (`JsonParserFactory.getJsonParser().parseMap(page.body())`).

- [ ] **Step 4: Başarısız testleri yaz.** `KeycloakOrganizationsTests` (birim): `["acme"]` → `{acme}`; `{"acme": {"id": "…"}}` → `{acme}`; `"acme"` → `{acme}`; `null` → `{}`; `[1]` → `{}`; `[""]` → `{}`; `scopeFor("acme")` → `organization:acme`. Öğrenme testleri:

```java
/**
 * Pins what Keycloak 26.8 does with Organizations before the application relies on it (doc §4.4, ADR-0005). Talks to
 * Keycloak only. A failure here is a finding: record the actual behaviour in docs/spikes/s2-identity.md and revisit the
 * tasks that assume it; do not bend the assertion to make it pass.
 */
class KeycloakOrganizationsLearningTests {

    @Test
    void scopeHintPutsOnlyTheRequestedOrganizationIntoBothTokens() throws ParseException {
        SpikeKeycloak.Tokens tokens = SpikeKeycloak.codeFlow("mm", "openid organization:globex");

        assertThat(organizations(tokens.idToken())).containsExactly("globex");
        assertThat(organizations(tokens.accessToken())).containsExactly("globex");
    }

    @Test
    void nonMemberAskingForAnOrganizationGetsNoClaimForIt() throws ParseException {
        SpikeKeycloak.Tokens tokens = SpikeKeycloak.codeFlow("zeynep", "openid organization:acme");

        assertThat(organizations(tokens.idToken())).doesNotContain("acme");
    }

    @Test
    void wildcardHintPutsEveryMembershipIntoTheClaim() throws ParseException {
        SpikeKeycloak.Tokens tokens = SpikeKeycloak.codeFlow("mm", "openid organization:*");

        assertThat(organizations(tokens.idToken())).containsExactlyInAnyOrder("acme", "globex");
    }

    @Test
    void hardcodedMapperGivesAServiceAccountItsTenantAndTheApiAudience() throws ParseException {
        JWTClaimsSet claims = SignedJWT.parse(SpikeKeycloak.clientCredentials("acme-integration")).getJWTClaimsSet();

        assertThat(KeycloakOrganizations.aliases(claims.getClaim(KeycloakOrganizations.CLAIM))).containsExactly("acme");
        assertThat(claims.getAudience()).contains("erp-api");
    }

    /** Provisioning must register every tenant host on erp-web (Phase 2): '*' matches only at the end of a path. */
    @Test
    void subdomainWildcardRedirectUriDoesNotCoverANewTenant() {
        assertThat(SpikeKeycloak.createClient("""
                {"clientId": "wildcard-probe", "publicClient": true, "redirectUris": ["https://*.erp.test/*"]}"""))
                .isEqualTo(201);

        SpikeBrowser.Page page = new SpikeBrowser(0).get(SpikeKeycloak.authorizationUrl(
                "wildcard-probe", "https://newco.erp.test/callback", "openid"));

        assertThat(page.status()).isEqualTo(400);
        assertThat(page.body()).contains("redirect_uri");
    }

    /** TenantKey allows '_' (S1); a DNS label does not (TenantHostTests). The organization alias must take the key. */
    @Test
    void organizationAliasAcceptsTheTenantKeyFormat() {
        assertThat(SpikeKeycloak.createOrganization("acme_tr", "acme-tr.example")).isEqualTo(201);
    }

    private static Set<String> organizations(String jwt) throws ParseException {
        return KeycloakOrganizations.aliases(SignedJWT.parse(jwt).getJWTClaimsSet().getClaim(KeycloakOrganizations.CLAIM));
    }
}
```

- [ ] **Step 5: Çalıştır, `KeycloakOrganizations` yokken derleme hatasıyla düştüğünü gör.**

- [ ] **Step 6: `KeycloakOrganizations`'ı yaz.**

```java
/**
 * The only Keycloak-specific knowledge in application code (ADR-0005): the organization scope hint and the shape of the
 * organization claim. Keycloak 26 emits ["acme"] by default and {"acme": {...}} when the organization's id or
 * attributes are mapped; both name the same organizations.
 */
public final class KeycloakOrganizations {

    public static final String CLAIM = "organization";

    private KeycloakOrganizations() {}

    public static String scopeFor(String alias) {
        return "organization:" + alias;
    }

    /** The organization aliases a claim names; a malformed claim names none. */
    public static Set<String> aliases(@Nullable Object claim) {
        Collection<?> values = switch (claim) {
            case null -> List.of();
            case String alias -> List.of(alias);
            case Collection<?> list -> list;
            case Map<?, ?> map -> map.keySet();
            default -> List.of(claim);
        };
        Set<String> aliases = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String alias) || alias.isBlank()) {
                return Set.of();
            }
            aliases.add(alias);
        }
        return Collections.unmodifiableSet(aliases);
    }
}
```

- [ ] **Step 7: Çalıştır.** `./mvnw -Pspikes -pl spikes/s2-identity test -Dtest='KeycloakOrganizations*'` → Beklenen: PASS. Bir öğrenme testi düşerse gerçek davranışı (ör. üye olmayan için Keycloak'ın hata sayfası göstermesi; `codeFlow`'un istisna mesajı son sayfayı gösterir) bir bulgu olarak not et ve öğrenme testini o davranışı sabitleyecek şekilde yeniden yaz; doğrulamayı geçsin diye bükme. Task 4'teki `assertRejected` iki yolu da kabul eder: değişmez kısmı "acme'de kimliği doğrulanmış oturum yok"tur, 403 + `tenant_mismatch` sadece callback uygulamaya ulaştığında beklenir.

- [ ] **Step 8: Commit**

```bash
git add spikes/s2-identity/src
git commit -m "test(spike): add Keycloak 26.8 fixture and pin Organizations behaviour with learning tests

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: BFF girişi: host'tan tenant, organization ipucu, platform DB'sinde `__Host-` oturumu

**Risk:** Güvenlik zinciri sırası, Spring Session'ın DataSource ve transaction kablolaması, Boot'un açılışta Keycloak'a gitmesi.

**Files:**
- Modify: `spikes/s2-identity/pom.xml` (+ `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`, `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-session-jdbc`), `tenancy/PlatformDataSourceConfiguration` (platform DS'ye `@SpringSessionDataSource`)
- Create: `identity/IdentityProperties`, `SecurityConfiguration`, `SessionConfiguration`, `BrowserTenantFilter`, `TenantFilterChain`, `TenantOidcUser`, `TenantOidcUserService`, `LazyClientRegistrationRepository`, `DiscardingAuthorizedClientRepository`; `sample/SampleController`, `WhoAmI`; `src/main/resources/application.yaml`
- Test: `support/SpikeEnvironment`, `SpikeTest`, `SpikeContexts`; `identity/BffLoginTests`, `identity/SessionWiringTests`, `identity/LazyClientRegistrationRepositoryTests`, `KeycloakUnavailableTests`, `StartupIsolationTests`

**Interfaces:**
- Consumes: Task 1 (`TenantDirectory`, `TenantHost`, `TenantContext`, `TenantDataSourceStandIn`, `@PlatformDb`), Task 2 (`KeycloakOrganizations`, `SpikeKeycloak`, `SpikeBrowser`).
- Produces:
  - `record IdentityProperties(URI issuerUri, String webClientId, String webClientSecret)` (`erp.identity`).
  - `public final class TenantOidcUser extends DefaultOidcUser`: `TenantKey tenant()`, `getName()` = `<tenant>:<sub>`.
  - `final class TenantFilterChain { static void proceed(TenantKey, FilterChain, ServletRequest, ServletResponse) throws IOException, ServletException; }`
  - `LazyClientRegistrationRepository.REGISTRATION_ID = "keycloak"`, `static Set<String> scopesWith(String scope)`; kurucular: `LazyClientRegistrationRepository(IdentityProperties)` (discovery = `ClientRegistrations::fromIssuerLocation`) ve testler için paket içi `LazyClientRegistrationRepository(IdentityProperties, Function<String, ClientRegistration.Builder> discovery)`.
  - Uç noktalar: `GET /` → `"ok"`; `GET /api/whoami` → `WhoAmI(String tenant, String name, String authentication)` (`authentication` = kimlik doğrulama sınıfının basit adı); `POST /api/echo` → `{"tenant": "<tenant>"}`.
  - Test: `@SpikeTest` = `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ContextConfiguration(initializers = SpikeEnvironment.class)`. `SpikeEnvironment` iki fixture'ı açar, tenant tablosundaki üç tenant'ı `SpikeKeycloak.issuer()` ile bir kez kaydeder ve özellikleri ekler. `SpikeContexts.start(String... args)` aynı ortamla `--server.port=0` üzerinde bir bağlam açar; port `local.server.port`'tan okunur.

- [ ] **Step 1: Başarısız testleri yaz.**

```java
@SpikeTest
class BffLoginTests {

    private static final String SESSION = "__Host-SESSION";

    @LocalServerPort
    int port;

    @Test
    void authorizationRequestCarriesTheOrganizationHintPkceAndTheTenantsRedirectUri() {
        SpikeBrowser.Page redirect =
                new SpikeBrowser(port).get("https://acme.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");

        assertThat(redirect.status()).isEqualTo(302);
        MultiValueMap<String, String> query = SpikeBrowser.query(redirect.location().orElseThrow());
        assertThat(query.getFirst("scope").split(" ")).contains("openid", "organization:acme");
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("https://acme.erp.test/login/oauth2/code/keycloak");
    }

    @Test
    void loginBindsANewSessionToTenantAndUserInThePlatformDatabase() {
        SpikeBrowser browser = new SpikeBrowser(port);
        SpikeBrowser.Page start = browser.get("https://acme.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");
        String beforeLogin = browser.cookie("acme.erp.test", SESSION).orElseThrow();

        assertThat(browser.follow(start, "ayse", null).body()).isEqualTo("ok");

        assertThat(browser.cookie("acme.erp.test", SESSION)).get().isNotEqualTo(beforeLogin);
        Map<String, Object> me = SpikeBrowser.json(
                browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json"));
        assertThat(me).containsEntry("tenant", "acme").containsEntry("authentication", "OAuth2AuthenticationToken");
        assertThat((String) me.get("name")).startsWith("acme:");
        assertThat(SpikeDatabases.platformDatabase().sql("select principal_name from spring_session")
                        .query(String.class).list())
                .contains((String) me.get("name"));
    }

    @Test
    void sessionCookieIsHostOnlySecureHttpOnlyAndLax() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login("acme.erp.test", "ayse");

        assertThat(browser.setCookieHeaders("acme.erp.test"))
                .filteredOn(header -> header.startsWith(SESSION + "="))
                .isNotEmpty()
                .allSatisfy(header -> assertThat(header)
                        .contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax")
                        .doesNotContainIgnoringCase("Domain="));
    }

    /** The browser gets no token at all; the session keeps only the ID token (RP-initiated logout), never access/refresh. */
    @Test
    void browserIsNeverGivenATokenAndTheSessionHoldsOnlyTheIdToken() throws ParseException {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login("acme.erp.test", "ayse");
        browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json");

        Pattern jwt = Pattern.compile("eyJ[\\w-]+\\.eyJ[\\w-]+\\.[\\w-]+");
        assertThat(browser.appResponses()).allSatisfy(page -> {
            assertThat(page.body()).doesNotContainPattern(jwt);
            assertThat(page.headers().map().toString()).doesNotContainPattern(jwt);
        });
        assertThat(browser.appResponses().stream()
                        .flatMap(page -> page.headers().allValues("Set-Cookie").stream())
                        .map(header -> header.substring(0, header.indexOf('='))))
                .containsOnly(SESSION);

        // This browser's session only: the platform DB is shared by every test.
        List<Map<String, Object>> attributes = SpikeDatabases.platformDatabase()
                .sql("""
                        select a.attribute_name, a.attribute_bytes from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id where s.session_id = ?""")
                .param(browser.sessionId("acme.erp.test").orElseThrow())
                .query().listOfRows();
        assertThat(attributes).extracting(row -> (String) row.get("attribute_name"))
                .noneMatch(name -> name.contains("AuthorizedClient"));
        List<String> typesInSession = new ArrayList<>();
        for (Map<String, Object> row : attributes) {
            Matcher token = jwt.matcher(new String((byte[]) row.get("attribute_bytes"), StandardCharsets.ISO_8859_1));
            while (token.find()) {
                typesInSession.add(SignedJWT.parse(token.group()).getJWTClaimsSet().getStringClaim("typ"));
            }
        }
        assertThat(typesInSession).isNotEmpty().containsOnly("ID"); // Keycloak: "ID", "Bearer", "Refresh"
    }

    /**
     * Pins design decision 1: from a trusted (internal) address, X-Forwarded-Host overrides Host, and the tenant follows
     * it. Caddy sends both with the same value; production narrows internal-proxies to Caddy (Phase 1/7).
     */
    @Test
    void forwardedHostFromATrustedProxyDecidesTheTenant() {
        SpikeBrowser.Page redirect = new SpikeBrowser(port).get(
                "https://acme.erp.test/oauth2/authorization/keycloak",
                "Accept", "text/html", "X-Forwarded-Host", "globex.erp.test");

        assertThat(redirect.status()).isEqualTo(302);
        MultiValueMap<String, String> query = SpikeBrowser.query(redirect.location().orElseThrow());
        assertThat(query.getFirst("scope").split(" ")).contains("organization:globex");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("https://globex.erp.test/login/oauth2/code/keycloak");
    }

    @Test
    void unknownHostIs404AndStartsNoLogin() {
        SpikeBrowser.Page page =
                new SpikeBrowser(port).get("https://unknown.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");

        assertThat(page.status()).isEqualTo(404);
        assertThat(page.location()).isEmpty();
    }

    @Test
    void withoutASessionTheApiAnswers401AndPagesRedirectToLogin() {
        SpikeBrowser browser = new SpikeBrowser(port);

        assertThat(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json").status())
                .isEqualTo(401);
        assertThat(browser.get("https://acme.erp.test/", "Accept", "text/html").location())
                .hasValueSatisfying(location -> assertThat(location).endsWith("/oauth2/authorization/keycloak"));
    }
}
```

`KeycloakUnavailableTests#startsAndIsReadyWithoutKeycloak`: `SpikeContexts.start("--erp.identity.issuer-uri=http://127.0.0.1:9/realms/erp")` ile bağlam açılır. `GET http://127.0.0.1:<port>/actuator/health/readiness` 200 döner; `GET https://acme.erp.test/oauth2/authorization/keycloak` 500–599 aralığında döner. Uygulama ayakta kalır ve readiness yine 200'dür.

`StartupIsolationTests`:

```java
/**
 * S2's share of S1's central claim (S1 open item 1): with the servlet stack, Spring Security, Spring Session and
 * Actuator in place, nothing asks the default (tenant) DataSource for a connection — not at start-up, not during a full
 * login, not at shutdown. Task 6 adds a bearer call.
 */
class StartupIsolationTests {

    @Test
    void sessionsSecurityAndActuatorNeverAskForTheTenantDataSource() {
        TenantDataSourceStandIn standIn;
        try (ConfigurableApplicationContext context = SpikeContexts.start()) {
            standIn = context.getBean(TenantDataSourceStandIn.class);
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            SpikeBrowser browser = new SpikeBrowser(port);

            assertThat(browser.get("http://127.0.0.1:" + port + "/actuator/health/readiness").status()).isEqualTo(200);
            assertThat(browser.login("acme.erp.test", "ayse").body()).isEqualTo("ok");
            assertThat(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json").status())
                    .isEqualTo(200);
            assertThat(standIn.requests()).as("tenant DataSource requests while running").isZero();
        }
        assertThat(standIn.requests()).as("tenant DataSource requests at shutdown").isZero();
    }
}
```

`SessionWiringTests` (`@SpikeTest`) Tasarım kararı 9'un ikinci tuzağını korur: `applicationTransactionsStayOnTheDefaultDataSource` niteleyicisiz enjekte edilen `TransactionOperations`'ın Boot'un `transactionTemplate`'i olduğunu ve yöneticisinin `DataSourceTransactionManager` olup DataSource'unun `TenantDataSourceStandIn` olduğunu doğrular. Step 5'ten önce de yeşildir; Step 5'te varsayılan aday bir `TransactionOperations` tanımlanırsa kırmızıya döner.

`LazyClientRegistrationRepositoryTests` (birim; sahte `discovery` fonksiyonu ve sayaç): `unknownRegistrationIdIsNullWithoutDiscovery`, `discoveryRunsOnceAndIsCached` (iki çağrı, bir discovery), `failedDiscoveryIsRetriedOnTheNextLogin` (ilk çağrı istisna fırlatır ve önbelleğe bir şey yazılmaz; ikinci çağrı kaydı döner, discovery iki kez çalışmıştır), `registrationRequiresPkce` (`getClientSettings().isRequireProofKey()` true).

- [ ] **Step 2: Çalıştır, düştüğünü gör** (sınıflar yok).

- [ ] **Step 3: Uygula** (`SessionConfiguration` hariç; onu Step 5 ekler).

`application.yaml`:

```yaml
# S2 identity spike (doc §15.3). Browser sessions live in the platform DB (ADR-0039); TLS ends at the proxy (ADR-0024).
spring:
  application.name: erp-spike-s2
  threads.virtual.enabled: true
  sql.init.mode: never
  session:
    timeout: 30m
    jdbc.initialize-schema: never # migrations are a separate step (doc §4.6)
    jdbc.cleanup-cron: "-" # K12: expired-session cleanup is a db-scheduler job in Phase 1, not a framework scheduler
server:
  # X-Forwarded-Proto/-Host are trusted from Boot's default internal-proxies (every private range, design decision 1);
  # production narrows them to Caddy (Phase 1/7).
  forward-headers-strategy: native
  servlet.session.cookie: # ADR-0006: host-only; with Secure and Path=/ a sibling subdomain cannot set it
    name: __Host-SESSION
    secure: true
    http-only: true
    same-site: lax
    path: /
management:
  endpoint.health.probes.enabled: true
  endpoint.health.group.readiness.include: readinessState,db
  health.db.ignore-routing-data-sources: true # readiness is the platform DB; the tenant stand-in is never probed
erp.identity.web-client-id: erp-web # issuer-uri and web-client-secret come from the environment (tests: SpikeKeycloak)
```

`SecurityConfiguration` (bu görevde actuator ve tarayıcı zinciri; bearer zinciri Task 6'da eklenir):

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfiguration {

    /** Health stays reachable on the internal address without a tenant host (readiness = platform DB). */
    @Bean
    @Order(0)
    SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/health/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** Path 1 of ADR-0040: the browser session (BFF). The tenant is the host's; login and session must agree with it. */
    @Bean
    @Order(2)
    SecurityFilterChain browserChain(
            HttpSecurity http, TenantDirectory tenants, OAuth2AuthorizationRequestResolver authorizationRequests)
            throws Exception {
        RequestMatcher apiCalls = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/bootstrap"));
        return http.addFilterBefore(new BrowserTenantFilter(tenants), CsrfFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        // The authorization endpoint is the login page: no generated page, and no eager discovery to
                        // list registrations (design decision 8).
                        .loginPage("/oauth2/authorization/" + LazyClientRegistrationRepository.REGISTRATION_ID)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(new TenantOidcUserService(tenants)))
                        // A redirect would restart login, Keycloak's SSO session would answer at once: a loop.
                        .failureHandler(SecurityConfiguration::loginFailed))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiCalls))
                .build();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(IdentityProperties properties) {
        return new LazyClientRegistrationRepository(properties);
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new DiscardingAuthorizedClientRepository();
    }

    /**
     * Adds the host tenant's organization hint (doc §4.4 rule 1). The hint is not a security boundary. PKCE comes from
     * the registration (requireProofKey, design decision 7).
     */
    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository registrations, TenantDirectory tenants) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(request -> request.scopes(LazyClientRegistrationRepository.scopesWith(
                KeycloakOrganizations.scopeFor(tenants.require(TenantContext.require()).organizationAlias()))));
        return resolver;
    }

    private static void loginFailed(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode()
                : "login_failed");
    }
}
```

`BrowserTenantFilter` (Task 4 buna oturum kontrolünü ekler):

```java
/**
 * Resolves the tenant from the host (doc §4.4 rule 1) and binds it for the rest of the chain. Runs before CSRF,
 * logout and OAuth2 login, so the organization hint and the login check see the host's tenant. Unknown host: 404;
 * tenant not ACTIVE: 503. Statuses are set directly: an error dispatch would re-enter the chain without a tenant.
 */
final class BrowserTenantFilter extends OncePerRequestFilter {

    private final TenantDirectory tenants;

    BrowserTenantFilter(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<TenantRecord> tenant = TenantHost.normalize(request.getServerName()).flatMap(tenants::findByHost);
        if (tenant.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (tenant.get().status() != TenantStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        TenantFilterChain.proceed(tenant.get().key(), chain, request, response);
    }
}
```

`TenantFilterChain.proceed`, `chain.doFilter`'ı `TenantContext.run(tenant, …)` içinde çalıştırır (kapsam API'si, S1 B11). Lambda içindeki `IOException`/`ServletException`, özel bir `RuntimeException` sarmalayıcısıyla dışarı taşınır ve türü korunarak yeniden fırlatılır.

`TenantOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser>` şu alanlara sahiptir: `delegate = new OidcUserService()` ve kurucudan gelen `TenantDirectory tenants` (Task 4 kullanır). Bu görevde `loadUser` sadece `new TenantOidcUser(TenantContext.require(), delegate.loadUser(request))` döner; iddia kontrolünü Task 4 ekler. `TenantOidcUser`:

```java
/**
 * The browser principal: an OIDC user bound to one tenant (doc §4.4: a session belongs to (user, tenant)). Its name
 * "<tenant>:<sub>" is what Spring Session indexes, so the sessions of (user, tenant) can be found without touching the
 * user's other tenants.
 */
public final class TenantOidcUser extends DefaultOidcUser {

    private static final long serialVersionUID = 1L;

    private final TenantKey tenant;

    TenantOidcUser(TenantKey tenant, OidcUser user) {
        super(user.getAuthorities(), user.getIdToken(), user.getUserInfo(), IdTokenClaimNames.SUB);
        this.tenant = tenant;
    }

    public TenantKey tenant() {
        return tenant;
    }

    @Override
    public String getName() {
        return tenant.value() + ":" + getSubject();
    }
}
```

`LazyClientRegistrationRepository` (Tasarım kararı 8): sadece `REGISTRATION_ID` için yanıt verir. İlk çağrıda `discovery.apply(issuerUri)` (üretimde `ClientRegistrations.fromIssuerLocation`) + `clientId`, `clientSecret`, `scope(SCOPES)` (`openid`, `profile`, `email`), `redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")`, `clientSettings(ClientSettings.builder().requireProofKey(true).build())` ile kaydı kurar. Sonucu `volatile` alanda çift kontrollü kilitle önbelleğe alır. Discovery başarısız olursa önbelleğe bir şey yazmaz; bir sonraki giriş yeniden dener. `static Set<String> scopesWith(String)`, `SCOPES`'a bir scope ekleyip döner; ipucu özelleştiricisi bunu kullanır, böylece scope listesinin tek kaynağı bu sınıftır.

`DiscardingAuthorizedClientRepository`: `loadAuthorizedClient` her zaman `null` döner, `saveAuthorizedClient` ve `removeAuthorizedClient` boştur. Javadoc'u Tasarım kararı 5'i anlatır. `SampleController`: `GET /` → `"ok"`; `GET /api/whoami` → `new WhoAmI(TenantContext.require().value(), authentication.getName(), authentication.getClass().getSimpleName())`; `POST /api/echo` → `Map.of("tenant", TenantContext.require().value())`. `PlatformDataSourceConfiguration`'da platform DS'ye `@SpringSessionDataSource` eklenir: §4.3'e göre oturumlar platform DB'sindedir.

- [ ] **Step 4: Çalıştır ve tuzağı gör.** `./mvnw -Pspikes -pl spikes/s2-identity test -Dtest='BffLoginTests,StartupIsolationTests,SessionWiringTests'` → Beklenen: `BffLoginTests` ve `StartupIsolationTests` FAIL, `SessionWiringTests` PASS. İlk oturum kaydı 500 döner ve `StartupIsolationTests`'te `requests()` ≥ 1 olur; yığında `JdbcIndexedSessionRepository` ve `DataSourceTransactionManager` görünür. Spring Session, JDBC işini platform DS'de yapar ama kanca verilmediği için transaction'ı bağlamdaki tek `PlatformTransactionManager`'la (`getIfUnique()`) açar. Boot'un bu bean'i varsayılan DataSource'tadır, yani Faz 1'de routing DataSource'tadır. Gözlenen yığını bulgu için not et. Kırmızı görülmezse bunu bulgu olarak yaz (kaynak okumasıyla çelişir, nedeni araştırılır) ve Step 5'i yine uygula: oturumların transaction sahibi açıkça belirtilmiş olur.

- [ ] **Step 5: `SessionConfiguration`'ı ekle.**

```java
@Configuration(proxyBeanMethods = false)
class SessionConfiguration {

    /**
     * Spring Session would otherwise open its transactions with the application's only transaction manager, which sits
     * on the default DataSource: the tenant-routing one from Phase 1 on (design decision 9, ADR-0039). Spring Session
     * finds this bean by name. It is not a default candidate: a default TransactionOperations would make Boot back off
     * its own transactionTemplate, and application code would get this platform one.
     */
    @Bean(name = "springSessionTransactionOperations", defaultCandidate = false)
    TransactionOperations springSessionTransactionOperations(@PlatformDb DataSource platform) {
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(platform));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); // Spring Session's default
        return transactions;
    }
}
```

- [ ] **Step 6: Çalıştır, geçtiğini gör.** `./mvnw -Pspikes -pl spikes/s2-identity verify` → `BUILD SUCCESS`; `SessionWiringTests` hâlâ yeşil (Boot'un `transactionTemplate`'i yerinde); `ModularityTests` yeşil (`identity → tenancy → kernel`, `sample → identity, kernel`). Bean adla bulunamazsa Spring Session tek TM'e döner ve `StartupIsolationTests` sayacı bunu yakalar (`defaultCandidate = false` bir bean'in `@Qualifier` adıyla enjekte edildiği varsayımı burada doğrulanır).

- [ ] **Step 7: Commit**

```bash
git add spikes/s2-identity/pom.xml spikes/s2-identity/src
git commit -m "feat(spike): log in through the BFF with the host's organization hint and a __Host- session in the platform DB

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Organization iddiası kontrolü ve her istekte host ↔ oturum tenant'ı

**Risk:** Bu, §4.4 madde 1'in güvenlik sınırıdır. İpucu istemcinin elindedir; sınırı ID token'daki iddia ve oturum principal'ının tenant'ı çizer.

**Files:**
- Modify: `identity/TenantOidcUserService` (iddia kontrolü), `identity/BrowserTenantFilter` (oturum kontrolü)
- Test: `identity/OrganizationClaimCheckTests`, `identity/SessionTenantBindingTests`

**Interfaces:**
- Consumes: Task 3.
- Produces: giriş hatası 403 döner ve gövdesi `tenant_mismatch` olur; başka tenant'ın oturumu 401 alır; askıdaki tenant 503 alır.

- [ ] **Step 1: Başarısız testleri yaz.**

```java
@SpikeTest
class OrganizationClaimCheckTests {

    @LocalServerPort
    int port;

    @Test
    void memberOfAnotherOrganizationCannotLogInHere() {
        SpikeBrowser browser = new SpikeBrowser(port);
        SpikeBrowser.Page result = browser.login("acme.erp.test", "zeynep");

        // Keycloak may refuse a non-member itself (an error page) or issue a token without acme (learning test, Task 2).
        // Either way no acme session exists; if the callback reached the application, the ID token check refused it.
        if (result.url().getHost().equals("acme.erp.test")) {
            assertRejectedByTheApplication(result);
        }
        assertNoSession(browser);
    }

    @Test
    void hintRewrittenToAnotherOrganizationIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.rewriteAuthorizationRequests(scope(scopes -> scopes.replace("organization:acme", "organization:globex")));
        assertRejected(browser, "mm");
    }

    @Test
    void wildcardHintIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.rewriteAuthorizationRequests(scope(scopes -> scopes.replace("organization:acme", "organization:*")));
        assertRejected(browser, "mm");
    }

    @Test
    void droppedHintIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.rewriteAuthorizationRequests(
                scope(scopes -> scopes.replace("organization:acme", "").replaceAll(" +", " ").strip()));
        assertRejected(browser, "mm");
    }

    /** The tampered hint names mm's own organizations (or none), so Keycloak issues a token: the app must refuse it. */
    private static void assertRejected(SpikeBrowser browser, String username) {
        assertRejectedByTheApplication(browser.login("acme.erp.test", username));
        assertNoSession(browser);
    }

    private static void assertRejectedByTheApplication(SpikeBrowser.Page result) {
        assertThat(result.status()).isEqualTo(403);
        assertThat(result.body()).isEqualTo("tenant_mismatch");
    }

    private static void assertNoSession(SpikeBrowser browser) {
        assertThat(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json").status())
                .isEqualTo(401);
    }

    /** Rewrites the decoded scope parameter of Keycloak's authorization URL, as a user could in the address bar. */
    private static UnaryOperator<URI> scope(UnaryOperator<String> change) {
        return uri -> {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri).replaceQuery(null);
            SpikeBrowser.query(uri.toString()).forEach((name, values) -> values.forEach(value ->
                    builder.queryParam(name, "scope".equals(name) ? change.apply(value) : value)));
            return builder.encode().build().toUri();
        };
    }
}
```

`SessionTenantBindingTests` (ne / kabul):

| Test | Senaryo | Kabul |
|---|---|---|
| `sessionCookieReplayedOnAnotherTenantsHostIs401` | `ayse` acme'de giriş yapar; acme'nin `__Host-SESSION` değeri `putCookie("globex.erp.test", …)` ile globex'e taşınır | globex `/api/whoami` 401; acme `/api/whoami` 200 (oturum silinmedi) |
| `oneUserGetsOneIndependentSessionPerTenant` | `mm` önce acme'de, sonra globex'te giriş yapar (ikincisi Keycloak SSO ile formsuz) | İki host'ta `whoami.name` `acme:<sub>` ve `globex:<sub>` olur (aynı `sub`); çerez değerleri farklıdır; acme çerezi globex'te 401 alır; `FindByIndexNameSessionRepository#findByPrincipalName("acme:" + sub)` bu tarayıcının acme oturumunu (`sessionId("acme.erp.test")`) içerir, globex oturumunu içermez. Oturum sayısı doğrulanmaz: platform DB'si testler arasında ortaktır ve `mm` başka testlerde de acme'ye giriş yapar |
| `callbackReplayedOnAnotherHostDoesNotLogIn` | `mm` acme'de girişe başlar; `follow(…, "mm", "https://acme.erp.test/login/oauth2/code/")` callback'ten önce durur; acme'nin giriş öncesi çerezi globex'e taşınır ve aynı callback yolu ile sorgusu globex host'unda istenir | Durum 403, gövde `tenant_mismatch` (onu iddia kontrolü durdurur, Spring değil: Tasarım kararı 7); globex `/api/whoami` 401 |
| `suspendedTenantRefusesSessionsAndLogins` | `ipek` initech'te giriş yapar; ardından `SpikeDatabases.setStatus(initech, SUSPENDED)` (`finally` bloğunda `ACTIVE`'e döner) | `/api/whoami` 503; `/oauth2/authorization/keycloak` 503 ve `Location` yok |

- [ ] **Step 2: Çalıştır, düştüğünü gör.** Beklenen: `OrganizationClaimCheckTests`'in mm'li üç testi düşer (giriş başarılı olur, durum 200); zeynep'li test, Keycloak token verdiyse düşer, kendisi reddettiyse geçer (Task 2'deki öğrenme testi hangisi olduğunu söyler). `sessionCookieReplayed…` ve `oneUser…` düşer (globex 200 döner, üstelik acme kimliğiyle). `callbackReplayed…` **düşer**: Spring Security 7.1.1 callback'te `redirect_uri`'yi karşılaştırmaz, kodu acme'nin `redirect_uri`'siyle takas eder ve globex'te acme üyesinin oturumu açılır. Bu, Task 3 sonunda bilinen bir tenant-arası açıktır ve sadece Step 3'teki iddia kontrolüyle kapanır; gözlenen sonucu bulgu için not et. `suspended…` geçebilir (Task 3'teki 503).

- [ ] **Step 3: İddia kontrolünü ekle** (`TenantOidcUserService#loadUser`):

```java
    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = delegate.loadUser(request);
        TenantRecord tenant = tenants.require(TenantContext.require());
        OidcIdToken idToken = user.getIdToken();
        boolean sameIssuer = tenant.issuer().equals(String.valueOf(idToken.getIssuer()));
        Set<String> organizations = KeycloakOrganizations.aliases(idToken.getClaims().get(KeycloakOrganizations.CLAIM));
        if (!sameIssuer || !organizations.equals(Set.of(tenant.organizationAlias()))) {
            // The hint is the user's to change; the signed ID token is not (doc §4.4 rule 1, design decision 7).
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "tenant_mismatch", "The ID token does not name exactly this host's organization", null));
        }
        return new TenantOidcUser(tenant.key(), user);
    }
```

- [ ] **Step 4: Oturum kontrolünü ekle** (`BrowserTenantFilter`; `ACTIVE` kontrolünden sonra, `proceed`'den önce):

```java
        TenantKey hostTenant = tenant.get().key();
        if (!sessionBelongsTo(hostTenant)) {
            LOG.warn("Session of another tenant presented on {}", tenant.get().key());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        TenantFilterChain.proceed(hostTenant, chain, request, response);
    }

    /** A session belongs to (user, tenant): on another tenant's host it is not a session at all (design decision 3). */
    private static boolean sessionBelongsTo(TenantKey hostTenant) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                || authentication.getPrincipal() instanceof TenantOidcUser user && user.tenant().equals(hostTenant);
    }
```

Kimlik doğrulaması olan ama principal'ı `TenantOidcUser` olmayan bir oturum da reddedilir (kapalı hata). `LOG` bu sınıfta bir SLF4J `Logger`'dır.

- [ ] **Step 5: Çalıştır, geçtiğini gör.** `./mvnw -Pspikes -pl spikes/s2-identity verify` → `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add spikes/s2-identity/src
git commit -m "feat(spike): enforce the organization claim at login and the host tenant on every session request

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: CSRF, `/bootstrap` ve çıkış

**Neden:** Tenant'lar alt alan adlarıdır ve aynı "site" sayılır; SameSite=Lax tenant'lar arasında koruma sağlamaz (§6.3.1). Durum değiştiren her istek, oturuma bağlı CSRF token'ı ister (Tasarım kararı 6). Çıkış hem uygulama oturumunu hem Keycloak SSO oturumunu bitirir.

**Files:**
- Modify: `sample/SampleController` (+ `GET /bootstrap`), `identity/SecurityConfiguration` (çıkış başarı işleyicisi)
- Create: `sample/Bootstrap`
- Test: `identity/CsrfTests`, `identity/LogoutTests`

**Interfaces:**
- Produces: `GET /bootstrap` → `Bootstrap(String tenant, String subject, String name, String csrfHeader, String csrfToken)` (sadece tarayıcı zinciri; oturum yoksa 401). Çıkış: `POST /logout` + CSRF → Keycloak `end_session_endpoint`'e 302; `post_logout_redirect_uri` = `https://<host>/`.

- [ ] **Step 1: Başarısız testleri yaz.** `CsrfTests` (`ayse` acme'de giriş yapar; token `/bootstrap`'tan okunur):

| Test | İstek | Kabul |
|---|---|---|
| `bootstrapGivesTheSpaTenantUserAndAMaskedCsrfToken` | `GET /bootstrap` iki kez | 200; `tenant` = `acme`; `csrfHeader` = `X-CSRF-TOKEN`; token'lar boş değil ve iki çağrıda farklı (XOR maskesi); ikisi de kabul ediliyor |
| `bootstrapWithoutASessionIs401` | Girişsiz yeni tarayıcıyla `GET /bootstrap` (`Accept: application/json`) | 401, `Location` yok (§9.7: SPA 401'i görüp girişe yönlendirir) |
| `stateChangingRequestWithoutTheTokenIs403` | `POST /api/echo` | 403 |
| `stateChangingRequestWithTheBootstrapTokenPasses` | `POST /api/echo` + `X-CSRF-TOKEN` | 200, `{"tenant":"acme"}` |
| `forgedDoubleSubmitCookieIsUseless` | `putCookie("acme.erp.test", "XSRF-TOKEN", "forged")` + `X-XSRF-TOKEN: forged` + form alanı `_csrf=forged` | 403 (Tasarım kararı 6'nın gerekçesi) |
| `tokenFromTheSameUsersOtherTenantSessionIsRejected` | `mm` acme ve globex'te giriş yapar; globex'in `/bootstrap` token'ıyla acme'ye `POST /api/echo` | 403 |

`LogoutTests`:

| Test | Senaryo | Kabul |
|---|---|---|
| `logoutWithoutTheTokenIs403AndKeepsTheSession` | `POST /logout` | 403; `/api/whoami` 200 |
| `logoutEndsTheSessionAndHandsOverToKeycloak` | `POST /logout` + token | 302; `Location` Keycloak'ın `…/protocol/openid-connect/logout` adresi, `id_token_hint` dolu, `post_logout_redirect_uri` = `https://acme.erp.test/`; `spring_session`'da bu oturumun satırı (çıkıştan önce okunan `sessionId("acme.erp.test")`) yok (kullanıcının başka testlerden kalan oturumları doğrulanmaz, platform DB'si ortaktır); eski çerezle `/api/whoami` 401; `Location` izlenince Keycloak `https://acme.erp.test/`'e döner |
| `logoutOnOneTenantLeavesTheOtherTenantsSessionAlone` | `mm` acme ve globex'te giriş yapar, acme'den çıkar | globex `/api/whoami` hâlâ 200. Bu davranış sabitlenir ve bulgu olur: Keycloak SSO bitti ama globex'teki uygulama oturumu kendi çıkışına ya da zaman aşımına kadar yaşar; back-channel logout kararı Faz 3'tedir. |

- [ ] **Step 2: Çalıştır, düştüğünü gör** (`/bootstrap` 404; çıkış Keycloak'a gitmez, `/oauth2/authorization/keycloak?logout` adresine yönlenir). `bootstrapWithoutASessionIs401` şimdiden geçebilir: tarayıcı zinciri Task 3'ten beri `/bootstrap`'a oturumsuz 401 verir.

- [ ] **Step 3: Uygula.**

```java
    @GetMapping("/bootstrap")
    Bootstrap bootstrap(@AuthenticationPrincipal TenantOidcUser user, CsrfToken csrf) {
        return new Bootstrap(
                user.tenant().value(), user.getSubject(), user.getFullName(), csrf.getHeaderName(), csrf.getToken());
    }
```

`SecurityConfiguration#browserChain`'e (`ClientRegistrationRepository registrations` parametresi eklenir):

```java
        OidcClientInitiatedLogoutSuccessHandler keycloakLogout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        keycloakLogout.setPostLogoutRedirectUri("{baseUrl}/");
        // ... .logout(logout -> logout.logoutSuccessHandler(keycloakLogout))
```

CSRF yapılandırması değişmez: Spring Security'nin varsayılanı (`HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler`) zaten Tasarım kararı 6'dır. Testler bu varsayılanı sabitler.

- [ ] **Step 4: Çalıştır, geçtiğini gör.** `./mvnw -Pspikes -pl spikes/s2-identity verify` → `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add spikes/s2-identity/src
git commit -m "feat(spike): expose the session CSRF token through /bootstrap and log out via Keycloak

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Bearer zinciri: sabit tenant iddiası

**Risk:** Zincir eşleştirme sırası (bearer isteği tarayıcı zincirine ya da tersi düşmemeli), durumsuzluk (bearer isteği oturum okumamalı ve yaratmamalı), tenant'ın token'dan çözülmesi.

**Files:**
- Modify: `spikes/s2-identity/pom.xml` (+ `spring-boot-starter-security-oauth2-resource-server`), `application.yaml`, `identity/SecurityConfiguration` (+ `bearerChain`), `StartupIsolationTests` (+ bir bearer çağrısı)
- Create: `identity/TenantJwtAuthentication`, `TenantJwtAuthenticationConverter`, `BearerTenantFilter`
- Test: `identity/BearerChainTests`, `identity/TenantJwtAuthenticationConverterTests`

**Interfaces:**
- Consumes: Task 3 (`TenantFilterChain`), Task 2 (`SpikeKeycloak.clientCredentials`).
- Produces: `public final class TenantJwtAuthentication extends JwtAuthenticationToken` (`TenantRecord tenant()`; adı `sub`); `/api/whoami`'nin `authentication` alanı `TenantJwtAuthentication` olur.

- [ ] **Step 1: Başarısız testleri yaz.** `TenantJwtAuthenticationConverterTests` (birim, bellek içi `TenantDirectory`): liste iddiası çözülür; map iddiası çözülür; iddia yoksa, iki organization varsa ya da `(issuer, alias)` kayıtsızsa `InvalidBearerTokenException` fırlatılır; aynı alias başka bir issuer altında çözülmez. `BearerChainTests` (aksi belirtilmedikçe istek `https://api.erp.test/api/whoami`'ye, `Authorization: Bearer <token>` ve `Accept: application/json` ile gider):

| Test | Token | Kabul |
|---|---|---|
| `integrationClientIsServedInItsTenant` | `acme-integration` | 200; `tenant` = `acme`, `authentication` = `TenantJwtAuthentication`; yanıtta `Set-Cookie` yok; `select count(*) from spring_session` değişmedi |
| `bearerPostNeedsNoCsrfToken` | `acme-integration`, `POST /api/echo` | 200 |
| `tokenWithoutOrganizationIs401` | `noorg-integration` | 401, `WWW-Authenticate` `invalid_token` içerir |
| `tokenWithTwoOrganizationsIs401` | `multi-integration` | 401 |
| `tokenForAnUnregisteredOrganizationIs401` | `unknown-integration` | 401 |
| `tokenWithoutTheApiAudienceIs401` | `noaud-integration` | 401 |
| `tokenSignedWithAForeignKeyIs401` | Nimbus ile üretilmiş RS256 token (`RSAKeyGenerator(2048)`), `iss` = `http://evil.test/realms/erp`, `aud` = `erp-api`, `organization` = `["acme"]`, 5 dk geçerli | 401. İmza (realm'in JWKS'inde olmayan anahtar) durdurur; issuer doğrulaması ayrıca kanıtlanmaz, ayrı realm konusu Tasarım kararı 2'dedir |
| `invalidBearerTokenDoesNotFallBackToTheSession` | `ayse` acme'de giriş yapar; aynı tarayıcıyla `Authorization: Bearer not-a-jwt` ile `https://acme.erp.test/api/whoami` | 401, `WWW-Authenticate` `invalid_token` içerir; istek tarayıcı zincirine düşüp oturumla 200 dönmez |
| `tokenOnAnotherTenantsHostIs403` | `acme-integration`; istek `https://globex.erp.test/api/whoami`'ye ve sonra `https://acme.erp.test/api/whoami`'ye | globex 403; acme 200 |
| `suspendedTenantsTokenIs503` | `initech-integration`; `setStatus(initech, SUSPENDED)` (`finally` bloğunda `ACTIVE`) | 503 |
| `bearerRequestIgnoresAnySessionCookie` | `ayse` acme'de giriş yapar; aynı tarayıcıyla `acme-integration` token'ı `https://acme.erp.test/api/whoami`'ye gönderilir | `authentication` = `TenantJwtAuthentication`; `name` `ayse`'nin `acme:<sub>` değeri değil; `Set-Cookie` yok |
| `bootstrapIsNotABearerEndpoint` | `acme-integration`, `GET /bootstrap` | 403 |

- [ ] **Step 2: Çalıştır, düştüğünü gör.** Bearer başlığı bu aşamada tarayıcı zincirine düşer: `api.erp.test` kayıtsız olduğu için 404 döner.

- [ ] **Step 3: Uygula.** `application.yaml`'a:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${erp.identity.issuer-uri} # Boot's decoder discovers lazily (SupplierJwtDecoder)
          audiences: erp-api # tokens issued to other clients of the realm are not API tokens
```

`SecurityConfiguration`'a:

```java
    /** Path 2 of ADR-0040: OIDC bearer (integrations; mobile in Phase 3). Stateless: no session read or made, no CSRF. */
    @Bean
    @Order(1)
    SecurityFilterChain bearerChain(HttpSecurity http, TenantDirectory tenants) throws Exception {
        return http.securityMatcher(SecurityConfiguration::hasBearerToken)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new TenantJwtAuthenticationConverter(tenants))))
                .addFilterAfter(new BearerTenantFilter(tenants), BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }
```

```java
/**
 * Resolves the tenant from the token's (iss, organization) (doc §4.4 rule 2). Integration clients get the claim from a
 * hardcoded mapper; a token that names no organization or several is not an API token (ADR-0040).
 */
final class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TenantDirectory tenants;

    TenantJwtAuthenticationConverter(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> organizations = KeycloakOrganizations.aliases(jwt.getClaims().get(KeycloakOrganizations.CLAIM));
        if (organizations.size() != 1) {
            throw new InvalidBearerTokenException("The token must name exactly one organization");
        }
        TenantRecord tenant = tenants.findByIdentity(String.valueOf(jwt.getIssuer()), organizations.iterator().next())
                .orElseThrow(() -> new InvalidBearerTokenException("The token's organization is not a tenant"));
        return new TenantJwtAuthentication(jwt, tenant);
    }
}
```

```java
/** Checks the token's tenant against its status and the host (design decision 3), then binds it for the request. */
final class BearerTenantFilter extends OncePerRequestFilter {

    private final TenantDirectory tenants;

    BearerTenantFilter(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof TenantJwtAuthentication authentication)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        TenantRecord tenant = authentication.tenant();
        if (tenant.status() != TenantStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        Optional<TenantKey> hostTenant = TenantHost.normalize(request.getServerName())
                .flatMap(tenants::findByHost)
                .map(TenantRecord::key);
        if (hostTenant.isPresent() && !hostTenant.get().equals(tenant.key())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        TenantFilterChain.proceed(tenant.key(), chain, request, response);
    }
}
```

`TenantJwtAuthentication`: `super(jwt, List.of(), jwt.getSubject())` + `TenantRecord tenant` alanı. Durumsuz zincirde serileştirilmez; bu Javadoc'ta yazar. `StartupIsolationTests`'e giriş adımından sonra şu eklenir: `browser.get("https://api.erp.test/api/whoami", "Authorization", "Bearer " + SpikeKeycloak.clientCredentials("acme-integration"))` → 200.

- [ ] **Step 4: Çalıştır, geçtiğini gör.** `./mvnw -Pspikes -pl spikes/s2-identity verify` → `BUILD SUCCESS`; `StartupIsolationTests` bearer çağrısıyla da yeşil.

- [ ] **Step 5: Commit**

```bash
git add spikes/s2-identity/pom.xml spikes/s2-identity/src
git commit -m "feat(spike): resolve bearer tokens to tenants by issuer and organization claim

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Bulgu raporu, ADR'ler ve çıkış kontrolü

**Files:**
- Create: `docs/spikes/s2-identity.md`
- Modify: `docs/adr/0005-keycloak-organizations.md`, `0006-bff-oturum-cerezi.md`, `0039-spring-session-jdbc.md`, `0040-uc-kimlik-yolu.md`, `0004-platform-veritabani.md`, `0024-onprem-linux-compose-erpctl.md`, `docs/adr/README.md`

**Interfaces:**
- Consumes: Task 1–6'nın yeşil test çalıştırması ve görevlerde not edilen gözlemler (Task 2 Step 7, Task 3 Step 4, Task 4 Step 2).
- Produces: S2 sonucu ADR'lerde; Faz 1/2/3 girdileri `docs/spikes/s2-identity.md`'de.

- [ ] **Step 1: Kanıtı topla.**

```bash
./mvnw -Pspikes -pl spikes/s2-identity verify 2>&1 | grep -E "Tests run:|BUILD"
git rev-parse --short HEAD
```

Beklenen: son `Tests run:` satırında `Failures: 0, Errors: 0`, `BUILD SUCCESS`. Toplam test sayısı ve kısa hash `<TEST_SAYISI>` ve `<COMMIT>` yerlerine yazılır.

- [ ] **Step 2: `docs/spikes/s2-identity.md`'yi yaz.** Düzen `s1-tenancy.md` ile aynıdır: başlık tablosu (Kapsam, Kod, Çalıştırma, Sonuç, İlgili ADR'ler), Kapsam dışı, Kurulum, Kanıt tablosu, Bulgular, Faz 1/2/3 girdileri.

*Kapsam dışı:* cihaz kimliği yolu (ADR-0040 yol 3, Faz 3), mobil kullanıcı token'ı (public client + PKCE, Faz 3), `user_account` kaydı ve `sub` bağlama (Faz 3), personel realm'i ve impersonation (ADR-0042, Faz 3), ayrı realm istisnasının uçtan uca kurulumu (sadece `(issuer, alias)` anahtarı test edildi; zincirlerin çoklu issuer ihtiyacı bulgu olarak yazılır), back-channel logout, oturumun mutlak ömrü ve IdP iptalinin oturuma yansıması (Faz 3), okunamayan oturum ve serileştirme uyumluluğu (Tasarım kararı 10, Faz 1), Keycloak'ın dış (front-channel) ve iç (back-channel) adresinin ayrışması (compose'da uygulama Keycloak'a iç adresle gider, issuer dış adrestir; spike iki tarafta aynı adresi kullanır, Faz 1 compose'unda çözülür), `internal-proxies`'in daraltılması (Faz 1/7), provisioning adaptörü (Faz 2), host araması için önbellek (Faz 2), gerçek TLS ve tarayıcı E2E'si (Faz 3).

*Kanıt tablosu* (her satır: iddia | test | sonuç):

| # | İddia | Test |
|---|---|---|
| 1 | Keycloak 26.8: `organization:<alias>` ipucu ID ve access token'a sadece o organization'ı koyar; üye olmayana iddia gelmez; `*` bütün üyelikleri koyar; sabit eşleyici service account'a tenant iddiası ve audience verir | `KeycloakOrganizationsLearningTests` |
| 2 | Giriş, host'un tenant'ının ipucu, PKCE (kayıttan) ve portsuz tenant `redirect_uri`'siyle başlar; kayıtsız host 404 döner; güvenilen adresten gelen `X-Forwarded-Host` tenant'ı belirler | `BffLoginTests` |
| 3 | Kurcalanmış, silinmiş ya da joker ipucuyla giriş 403 alır; üye olmayan kullanıcı acme'de oturum açamaz (Keycloak'ın mı uygulamanın mı reddettiği bulguda); oturum oluşmaz | `OrganizationClaimCheckTests` |
| 4 | Oturum `(kullanıcı, tenant)`'a bağlıdır: başka tenant'ın host'unda 401; mali müşavirin iki bağımsız oturumu olur; principal index'i tenant'a göre bulur; callback başka host'ta tekrar oynatılamaz (iddia kontrolüyle; Spring bunu durdurmaz); askıdaki tenant 503 alır | `SessionTenantBindingTests` |
| 5 | Çerez `__Host-`, `Secure`, `HttpOnly`, `SameSite=Lax`'tır, `Domain` yoktur; oturum kimliği girişte değişir; tarayıcıya token girmez; oturumda access ve refresh token yoktur, yalnızca ID token vardır | `BffLoginTests` |
| 6 | Oturumlar platform DB'sinde, platform transaction'ıyla tutulur; uygulamanın `TransactionTemplate`'i varsayılan DataSource'ta kalır | `BffLoginTests`, `SessionWiringTests`, `StartupIsolationTests` |
| 7 | CSRF: token'sız ve başka oturumun token'ıyla 403; basılmış double-submit çerezi işe yaramaz; oturumsuz `/bootstrap` 401 | `CsrfTests` |
| 8 | Çıkış CSRF ister, oturumu siler ve Keycloak'a devreder; diğer tenant'ın oturumu yaşar | `LogoutTests` |
| 9 | Bearer: `(iss, organization)` → tenant; iddiası yok, iki organization, kayıtsız organization, audience yok ya da yabancı anahtarla imzalı → 401; geçersiz token oturum çerezine düşmez (401); başka tenant host'u → 403; askıda → 503; oturum yaratılmaz ve kimlik oturumdan alınmaz; CSRF yok | `BearerChainTests`, `TenantJwtAuthenticationConverterTests` |
| 10 | Açılış, giriş, bearer çağrısı ve kapanış boyunca tenant DataSource'una 0 istek; Keycloak yokken uygulama açılır ve hazır olur; başarısız discovery sonraki girişte yeniden denenir | `StartupIsolationTests`, `KeycloakUnavailableTests`, `LazyClientRegistrationRepositoryTests` |

*Bulgular:* her biri gözlenen davranış + karar + kanıt olarak yazılır. En az şu konular ele alınır:
- Öğrenme testlerinde gözlenen gerçek davranış: iddianın şekli, ID ve access token'daki yeri, üye olmayan için ne olduğu.
- Spring Session'ın transaction yöneticisi tuzağı (Task 3 Step 4'te kırmızı görüldü mü, yığın) ve ikinci tuzak: varsayılan aday bir `TransactionOperations` Boot'un `transactionTemplate`'ini bastırır (Tasarım kararı 9).
- Callback'in başka host'ta tekrar oynatılmasına karşı tek savunmanın ID token iddia kontrolü olması (Task 4 Step 2'de kırmızı görüldü mü; Tasarım kararı 7). Faz 1: kontrol ve regresyon testi zorunlu.
- Ayrı realm istisnası: çözümleyici değişmez, zincirler değişir (bearer'da `JwtIssuerAuthenticationManagerResolver`, tarayıcıda tenant'ın issuer'ına göre registration; Tasarım kararı 2). ADR-0005'in "kod değişmez" cümlesinin düzeltilmesi.
- Oturumda yalnızca ID token (e-posta ve adla birlikte) durması; RP-initiated logout'un buna bağlı olması (Tasarım kararı 5).
- Oturum ömrünün IdP'den kopuk olması: refresh yok, `spring.session.timeout` sadece boşta kalma süresi, mutlak ömür yok; kullanıcının devre dışı bırakılması oturuma yansımaz; principal adı `<tenant>:<sub>` olduğu için bir kullanıcının bütün tenant'lardaki oturumları index'le tek seferde bulunamaz (Faz 3 kararı: `auth_time` ile mutlak ömür, `sub`'a göre oturum bulma, back-channel logout).
- İstek başına platform DB maliyeti: host araması (önbelleksiz), oturum okuma ve `JdbcSession.setLastAccessedTime`'ın her istekte işaretlediği `UPDATE spring_session`; her giriş başlangıcı kimliği doğrulanmamış bir oturum satırı yazar (Faz 1 temizlik işi, Faz 2 host önbelleği, Faz 7 `/oauth2/authorization/*` oran sınırı). ADR-0039'un "Olumsuz" satırına girer.
- Oturum serileştirmesi: Spring Security 7.1.1 UID'leri sabit (620L), minör geçiş oturumu bozmaz; kendi sınıflarımız için Faz 1 notu (Tasarım kararı 10).
- Boot'un `issuer-uri` ile açılışta discovery yapması (Tasarım kararı 8); `SupplierJwtDecoder` ve `LazyClientRegistrationRepository` başarısız discovery'yi önbelleğe almaz.
- PKCE: Spring Security 7.1.1 gizli istemcide PKCE'yi varsayılan olarak eklemez; `requireProofKey(true)` (Tasarım kararı 7).
- Önyükleme yolu: §8.1 `GET /api/v1/bootstrap` diyor ve Faz 3'teki mobil (bearer) istemci de önyükleme isteyecek; spike `/bootstrap`'ı CSRF alanı yüzünden tarayıcıya özel tuttu. Faz 3'te yol §8.1'e alınır; bearer'da CSRF alanları olmadan açılıp açılmayacağı açık sorudur.
- Keycloak'ın alt alan adı jokeri kabul etmemesi: her tenant host'u `erp-web`'in redirect ve post-logout URI'lerine provisioning ile eklenmeli (Faz 2). Yüzlerce tenant'ta istemci yapılandırmasının büyümesi bir izleme konusudur.
- Organization bağının `tenant`'ta durması (§4.3'e doküman önerisi).
- `TenantKey`'in `_` içerebilmesine karşın DNS etiketinin içerememesi: Faz 2'de ya `TenantKey`'den `_` çıkar ya da host anahtardan türetilmez.
- Çıkışın diğer tenant oturumlarını bitirmemesi (Faz 3: back-channel logout ya da principal index'iyle silme).
- `InMemoryOAuth2AuthorizedClientService` varsayılanı (Tasarım kararı 5).
- `forward-headers-strategy: native`: Boot 4.1.1 varsayılanlarıyla `X-Forwarded-Proto` ve `X-Forwarded-Host`'a bütün özel ağ aralıklarından güvenilir; tenant `X-Forwarded-Host`'u izler. On-prem LAN'daki her makine güvenilen proxy sayılır; Faz 1/7'de `internal-proxies` Caddy'ye daraltılır ve uygulama portu dışarı açılmaz (ADR-0024, Tasarım kararı 1).
- CSRF seçimi ve cookie tossing (Tasarım kararı 6).
- `cleanup-cron` kapalı: süresi dolan oturumları db-scheduler işi silmeli (Faz 1).
- S1 açık madde 1'in kısmi kanıtı: web, güvenlik, oturum ve actuator tenant'sız bağlantı istemiyor; JPA, jOOQ ve Modulith ile birlikte servlet açılışı Faz 1'de kalıyor.

*Faz 1 girdileri:* BFF yapılandırması (yukarıdaki YAML parçaları), `TenantOidcUser`, `TenantFilterChain`, iki zincir + actuator zinciri, `SessionConfiguration` (`springSessionTransactionOperations`, `defaultCandidate = false`) ve `SessionWiringTests`, iddia kontrolü ile callback tekrar oynatma regresyon testi, açılış izolasyon testinin web sürümü, `internal-proxies`'in Caddy'ye daraltılması, Keycloak'ın dış ve iç adresi (compose), süresi dolan oturum temizliği, oturum serileştirme notu (Tasarım kararı 10). *Faz 2:* provisioning'de redirect ve post-logout URI kaydı, host araması önbelleği, `tenant_domain` şeması. *Faz 3:* cihaz zinciri, mobil token, organization'dan çıkarmada oturum silme, back-channel logout, oturumun mutlak ömrü, `/api/v1/bootstrap` ve bearer'daki durumu, tarayıcıda token olmadığını doğrulayan Playwright testi. *İlk ayrı realm müşterisiyle:* zincirlerde çoklu issuer.

- [ ] **Step 3: ADR'leri güncelle.** Kural, ADR README'deki "Durum sözlüğü"dür ve S1 Task 10'daki gibi uygulanır: doğrulama kararı olduğu gibi desteklediyse **Kabul edildi**, karar metni değişmek zorundaysa **Değişti**. "Karar" metnine dokunulmaz; sadece "Durum" satırı değişir ve "Doğrulama"nın sonuna `**S2 sonucu (<TARİH>, `<COMMIT>`):** …` paragrafı eklenir. **Değişti** durumunda bu paragraf neyin değiştiğini ve nedenini yazar (README: "ne değiştiği ve nedeni `Doğrulama` bölümündedir"). Task 1–6 yeşilse ve öğrenme testleri beklendiği gibi geçtiyse geçerli durumlar şunlardır:

| ADR | Durum | S2 paragrafının içeriği |
|---|---|---|
| 0005 | **Değişti** | Değişen: "Çözümleyici `(issuer, organization) → tenant` eşlemesini kullandığı için kod değişmez" cümlesi. Çözümleyici değişmez, ama zincirler tek issuer'a güvenir; ayrı realm istisnası bearer'da çoklu issuer çözümleyicisi (`JwtIssuerAuthenticationManagerResolver`, güvenilen issuer'lar = `tenant.oidc_issuer`) ve tarayıcıda tenant'ın issuer'ına göre client registration ister; bu iş ilk ayrı realm müşterisiyle gelir. Kararın geri kalanı doğrulandı: öğrenme testleri 1–4'ün sonucu; kontrolün yeri (ID token, `iss` + tam organization kümesi) ve callback tekrar oynatmaya karşı tek savunma olması; redirect ve post-logout URI'lerinin tenant host'u başına kaydı; alias biçimi; `(issuer, alias)` anahtarı. Ayrıntı için bulgulara bağlantı. |
| 0006 | Kabul edildi | Çerez öznitelikleri; tarayıcıda token olmaması, oturumda yalnızca ID token (access/refresh yok); her istekte host ↔ principal tenant kontrolü (401); CSRF'in oturum token'ı olması ve double-submit'in neden seçilmediği; oturumun mutlak ömrünün Faz 3 kararı olarak açık kaldığı. |
| 0039 | Kabul edildi | `@SpringSessionDataSource` + `springSessionTransactionOperations` (`defaultCandidate = false`, `REQUIRES_NEW`) zorunluluğu (gözlenen tuzak ve Boot'un `transactionTemplate`'ini bastırma tuzağı); principal index'i `<tenant>:<sub>`; `cleanup-cron` kapalı ve temizliğin db-scheduler'a geçmesi; olumsuz sonuç: her istekte platform DB'sine `UPDATE spring_session` ve her giriş başlangıcında kimliksiz oturum satırı. |
| 0040 | Önerildi kalır | Yol 1 ve 2 doğrulandı (iki zincir, bearer'da `(iss, organization)`, audience, tenant host kuralı, geçersiz bearer'ın çereze düşmemesi); yol 3 (cihaz) Faz 3'te. |
| 0004 | Önerildi kalır | Not: organization bağı `tenant`'ta, `tenant_domain` sadece host eşlemesi; oturumlar platform DB'sinde doğrulandı. |
| 0024 | Önerildi kalır | Not: `__Host-` + `Secure` için uygulama `X-Forwarded-Proto`'ya Tomcat `RemoteIpValve` üzerinden güvenir (`server.forward-headers-strategy: native`); Boot varsayılanında güvenilen aralık bütün özel ağlardır ve tenant `X-Forwarded-Host`'u izler, bu yüzden üretimde `internal-proxies` Caddy'ye daraltılır ve uygulama portu dışarı açılmaz. Keycloak'ın dış ve iç adresinin ayrışması S2'de doğrulanmadı (Faz 1 compose). |

Bir öğrenme testi ya da Task 3 Step 4 beklenenden farklı sonuç verdiyse, ilgili ADR'nin durumunu bu kurala göre yeniden seç ve nedeni paragrafta yaz. `docs/adr/README.md` dizinindeki "Durum" hücrelerini güncelle.

- [ ] **Step 4: Ürün reaktörünü ve sır taramasını doğrula.**

```bash
./mvnw verify
gitleaks git --config .gitleaks.toml --redact .
gitleaks dir --config .gitleaks.toml --redact spikes docs
```

Beklenen: `BUILD SUCCESS` (spike ürün reaktöründe değil); iki gitleaks komutu da `no leaks found`.

- [ ] **Step 5: Commit**

```bash
git add docs/spikes/s2-identity.md docs/adr/0004-platform-veritabani.md docs/adr/0005-keycloak-organizations.md docs/adr/0006-bff-oturum-cerezi.md docs/adr/0024-onprem-linux-compose-erpctl.md docs/adr/0039-spring-session-jdbc.md docs/adr/0040-uc-kimlik-yolu.md docs/adr/README.md
git commit -m "docs(adr): record S2 identity spike results, accept ADR-0006 and 0039, change ADR-0005

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Plan 0C çıkış kontrolü

- [ ] `./mvnw verify` yeşil (ürün reaktörü; spike dahil değil).
- [ ] `./mvnw -Pspikes -pl spikes/s2-identity verify` yeşil (testler, Error Prone, Spotless, Modulith, ArchUnit).
- [ ] `StartupIsolationTests` yeşil: açılış, giriş, bearer çağrısı ve kapanış boyunca `TenantDataSourceStandIn.requests() == 0`.
- [ ] `KeycloakOrganizationsLearningTests` yeşil ya da her sapma `docs/spikes/s2-identity.md`'de bulgu olarak yazılı.
- [ ] `gitleaks git --config .gitleaks.toml --redact .` → `no leaks found`.
- [ ] `docs/spikes/s2-identity.md` commit'li. ADR-0006 ve 0039 `Kabul edildi`, ADR-0005 gerekçeli `Değişti` (beklenmeyen sonuçta durumlar Task 7 Step 3'teki kurala göre yeniden seçilir); ADR-0040, 0004, 0024 S2 notuyla `Önerildi`; `docs/adr/README.md` ve `spikes/README.md` güncel.
- [ ] Spike kodu `spikes/s2-identity/` altında; silinmesi plan 0E'nin işi.

## Sonraki planlar

| Plan | Durum |
|---|---|
| 0D — S3 Metadata ve belge dilimi | S2'den bağımsızdır; kimliği taklit edebilir (tek tenant, sabit kullanıcı). S1'in routing DataSource + jOOQ + flush dinleyicisi düzenini varsayar. |
| 0E — Teyit listesi ve kapanış | `spikes/` klasörünün (s1-tenancy, s2-identity) ve kök pom'daki `spikes` profilinin silinmesi, Flyway/Liquibase lisansı, jOOQ ticari lisans kararı (ADR-0011), ADR durumlarının son denetimi. S2'nin Faz 1/2/3'e devrettikleri bu fazların plan girdisidir. |
