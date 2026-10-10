# ERP v4 Geliştirici Kılavuzu

Bu kılavuz, erp-v4 deposunun bugünkü halini anlatan başvuru kaynağıdır. Her faz sonunda koda göre güncellenir; geçmiş yalnızca "Faz geçmişi" bölümünde tutulur. Mimari kararların gerekçesi burada yeniden üretilmez; ilgili ADR'ye (`docs/adr/`) ya da mimari dokümanın bölümüne (`docs/architecture/v4-platform.md`, ör. §4.1) atıf yapılır. Kılavuz en son 2026-10-10 tarihinde `main` dalında (HEAD `6187a6e`) koda göre güncellenmiştir. Bu güncellemede derleme, test, sır taraması, frontend ve duman testi zincirleri, ürün uygulamasının sağlık ve bilgi uçları ile S2 spike'ına ait bütün komut ve alıştırmalar yeniden çalıştırılmıştır. Ürün koduna ve S1 spike'ına ait "kendin dene" alıştırmaları en son Faz 0A ve 0B'de çalıştırılmıştır; ilgili dosyalar o tarihten beri değişmemiştir. Doğrulanamayanlar ayrıca işaretlenmiştir.

> **Kaynak önceliği:** Kod ve yapılandırma, plan dosyaları (`docs/superpowers/plans/2026-10-05-faz-0a-zemin.md`, `docs/superpowers/plans/2026-10-05-faz-0b-s1-tenancy.md`, `docs/superpowers/plans/2026-10-06-faz-0c-s2-kimlik.md`), mimari doküman, ADR'ler ve git geçmişi bu sırayla esas alınır. Kılavuz ile kod çelişirse kod doğrudur ve kılavuz düzeltilmelidir.

## 1. Sisteme genel bakış ve temel felsefe

Depo bugün üç parçadan oluşur:

- **Ürün kodu** (Faz 0A, "Zemin", yürüyen iskelet): boş bir Spring Boot uygulaması, SPA iskeleti, kalite kapıları, tek imaj, compose profilleri, duman testi ve CI tanımı. Ürün kodunda iş modülü, tenant, kimlik doğrulama ve veritabanı şeması henüz yoktur (bkz. Bölüm 10).
- **S1 tenancy spike'ı** (Faz 0B): `spikes/s1-tenancy/` altında, varsayılan Maven reaktörünün dışında duran ve yalnızca `-Pspikes` ile derlenen atılacak bir Spring Boot uygulaması. Tenant başına veritabanı, tenant'a yönlendiren DataSource, Hibernate 7, jOOQ, Spring Modulith olay kaydı ve db-scheduler yığınını otomatik testlerle sınar. Sonuçları `docs/spikes/s1-tenancy.md`'de ve ADR'lerin "Doğrulama" bölümlerindedir.
- **S2 kimlik spike'ı** (Faz 0C): `spikes/s2-identity/` altında, yine yalnızca `-Pspikes` ile derlenen atılacak bir Spring Boot uygulaması. Keycloak 26.8 Organizations ile BFF girişini, tenant'ın alt alan adından seçilmesini, oturumun `(kullanıcı, tenant)` çiftine bağlanıp Spring Session JDBC ile platform DB'sinde tutulmasını, kardeş alt alan adlarına karşı CSRF korumasını ve tenant'ı token'dan çözen bearer zincirini gerçek Keycloak ve PostgreSQL konteynerleriyle sınar. Sonuçları `docs/spikes/s2-identity.md`'dedir.

İki spike'ın kodu Faz 0 kapanışında (plan 0E) silinir (`spikes/README.md`).

44 ADR'den yedisi (ADR-0003, ADR-0006, ADR-0012, ADR-0013, ADR-0014, ADR-0015, ADR-0039) spike sonuçlarıyla "Kabul edildi", biri (ADR-0005) "Değişti" durumundadır; diğer 36'sı "Önerildi" durumundadır (`docs/adr/README.md`). Durumların anlamı aynı dosyadaki "Durum sözlüğü"nde tanımlanmıştır.

Bulgu numaraları spike raporlarına aittir ve iki raporda da B1'den başlar. Kılavuzda S1 bulguları önek olmadan (B12, `docs/spikes/s1-tenancy.md`), S2 bulguları `S2` önekiyle (S2 B7, `docs/spikes/s2-identity.md`) anılır.

> ⚠️ Spike kodu ürün koduna kopyalanmaz; Faz 1 ve Faz 2 kodu bulgulara göre yeniden yazılır (`spikes/README.md`). Bu kılavuzda spike'tan söz edilen her yer "bugün nasıl sınandığını" anlatır, ürünün nasıl yazılacağını değil.

### 1.1 Tek kod tabanı, tek imaj, iki kurulum şekli

Gerekçe ADR-0002 ve §4.1'dedir. Kodda bugünkü karşılığı aşağıdaki tabloda verilmiştir.

| Konu | Bugün kodda | Henüz karşılanmayan |
|---|---|---|
| Çalışma modu ayarı | `erp.deployment.mode`, `DeploymentProperties` record'una bağlanır; değerler `DeploymentMode` enum'u (`SAAS`, `ONPREM`) | Modun tenant kaynağına ve operasyon varsayılanlarına etkisi (Bölüm 10) |
| Varsayılan mod | `app/src/main/resources/application.yaml` içinde `onprem` | SaaS çok tenant'lı çalışma |
| Modun dışa açılması | `/api/v1/system/info` yanıtındaki `deploymentMode` alanı | `/api/v1/bootstrap` (§8.1, Faz 3) |
| Tek imaj | `deploy/docker/app.Dockerfile`: SPA gömülü tek OCI imajı | İmza (cosign) yalnızca CI tanımında; on-prem paketi ve `erpctl` yok |
| Müşteriye özel build yok (K8) | Süreç kuralı; imajda müşteri parametresi yoktur | — |

Mod değeri esnek bağlanır: `saas`, `SaaS`, `onprem`, `on-prem` kabul edilir. Bilinmeyen (`cloud`), boş ya da eksik değerde uygulama açılmayı reddeder ve hata metni özelliğin adını (`erp.deployment.mode`) içerir. Bu davranış `DeploymentPropertiesTests` ile kanıtlanır. Enum'un `key()` metodu API'de küçük harfli anahtarı (`saas`, `onprem`) `Locale.ROOT` ile üretir.

> İş kodu modu bilmemelidir (§4.1). `DeploymentMode` sınıfının Javadoc'u da bunu söyler: moda yalnızca tenant kaynağı ve operasyon varsayılanları bağlı olabilir.

### 1.2 Modüler monolit ve Spring Modulith

Gerekçe ADR-0001, katmanlar §5.1, modül anatomisi §5.3'tedir. Hedef katmanlar platform, iş modülleri, sektör paketleri ve müşteri uzantılarıdır; bağımlılık yönü her zaman üstten alta doğrudur.

Bugün kodda tek bir Spring Modulith uygulama modülü vardır: `com.smart.erp.system`. Spring Modulith, `com.smart.erp` altındaki her üst paketi bir modül sayar; modülün kök paketi açık, alt paketleri (ör. `com.smart.erp.system.web`) iç pakettir. Yapı doğrulaması `app/src/test/java/com/smart/erp/ModularityTests.java` içinde `ApplicationModules.of(...).verify()` ile her `./mvnw verify` çalışmasında yapılır (K1).

> ⚠️ §5.3'e göre her modül ayrı bir Maven modülüdür. Bugünkü `system` modülü ise `app` Maven modülünün içindedir; `SystemInfoController` Javadoc'una göre bu uç nokta Faz 3'te `/api/v1/bootstrap` ile değiştirilecek bir iskelet ucudur. Platform modülleri (`kernel`, `tenancy` …) henüz yoktur.

`platform/platform-test` bir Maven modülüdür ama Modulith uygulama modülü değildir: paylaşılan test altyapısıdır ve `ModularityTests` içinde `com.smart.erp.platformtest..` paketi doğrulamanın dışında tutulur.

S1 spike'ı ayrı bir Spring Boot uygulamasıdır (`com.smart.erp.spike.s1.SpikeApplication`) ve dört Modulith modülü içerir: `kernel`, `tenancy`, `jobs`, `sample` (Bölüm 7.8–7.11). Bu modüller §6.1 (`kernel`), §4.3–§4.5 (`tenancy`) ve §6.12'deki (`jobs`) platform modüllerinin deneme sürümleridir.

S2 spike'ı da ayrı bir uygulamadır (`com.smart.erp.spike.s2.SpikeApplication`) ve dört Modulith modülü içerir: `kernel` (S1'den kopya), `tenancy`, `identity`, `sample` (Bölüm 7.12–7.15). `identity`, §6.3.1'deki kimlik doğrulama katmanının deneme sürümüdür. Her spike'ın modül yapısı kendi `ModularityTests` sınıfıyla doğrulanır. Ürün uygulamasının modül listesi Faz 0B ve 0C'de değişmemiştir.

### 1.3 SPA'nın jar içine gömülmesi ve aynı origin

Gerekçe K8, §9.2 ("Tek build"), §9.7 ve §11.1'dedir. `app/pom.xml` içindeki `copy-spa` yürütmesi, `process-resources` aşamasında `web/apps/web/dist` klasörünü `target/classes/static` altına kopyalar; Spring Boot bu klasörü statik içerik olarak sunar. `dist` yoksa kopyalama atlanır ve jar SPA'sız üretilir.

```xml
<!-- app/pom.xml:72-93 (kısaltıldı) -->
<id>copy-spa</id>
<phase>process-resources</phase>
<outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
<directory>${project.basedir}/../web/apps/web/dist</directory>
```

Sonuçları:

- SPA ve API aynı origin'den sunulur; CORS yapılandırması yoktur ve gerekmez.
- `@erp/core` içindeki `getJson` göreli yol (`/api/v1/...`) ve `credentials: 'same-origin'` kullanır.
- Geliştirmede Vite dev sunucusu (5173) `/api` ve `/actuator` isteklerini 8080'e yönlendirerek aynı origin'i taklit eder (`web/apps/web/vite.config.ts`).
- SPA'nın jar'a girmesi için `pnpm build`, Maven `package`'tan önce çalışmalıdır. İmajda bu sıra `app.Dockerfile`'ın `web` aşamasıyla sağlanır.
- BFF oturum çerezi (ADR-0006) bu aynı origin düzenine dayanır: SPA, CSRF token'ını aynı origin'deki önyükleme yanıtından okur ve kardeş alt alan adları (başka tenant'lar) bu yanıtı okuyamaz. S2 bu düzeni `/bootstrap` ile sınamıştır (Bölüm 7.15); kimlik bilgili (credentialed) CORS açılırsa koruma çöker (`docs/spikes/s2-identity.md`, "Korunacak kısıtlar"). Ürün uygulamasında oturum ve CSRF henüz yoktur.

### 1.4 Bir isteğin tarayıcıdan veritabanına yolculuğu

Bugün ürün uygulamasında kimlik doğrulama ve tenant çözümleme yoktur; istekler doğrudan Spring MVC'ye ulaşır. Faz 0B ve 0C bu yolu değiştirmemiştir; tenant'a bağlı veri erişimi (Bölüm 1.5) ile kimlik doğrulama ve tenant çözümleme (Bölüm 1.6) yalnızca spike'larda vardır. Uygulama veritabanına yalnızca sağlık denetimi (`db` göstergesi) için bağlanır; `/api/v1/system/info` veritabanına dokunmaz.

```mermaid
sequenceDiagram
participant B as Tarayıcı (SPA)
participant O as İzleyici (ör. e2e global-setup)
participant M as Spring MVC
participant C as SystemInfoController
participant A as Actuator
participant P as PostgreSQL
B->>M: GET / (static/index.html, JS)
M-->>B: 200 text/html
B->>M: GET /api/v1/system/info
M->>C: info()
C-->>M: SystemInfoResponse
M-->>B: 200 JSON (version, deploymentMode)
O->>M: GET /actuator/health
M->>A: sağlık göstergeleri (db dahil)
A->>P: bağlantı al ve doğrula
P-->>A: başarılı ya da hata
A-->>M: UP ya da DOWN
M-->>O: 200 ya da 503
```

Sürüm bilgisi `spring-boot-maven-plugin`'in `build-info` hedefinin ürettiği `META-INF/build-info.properties` dosyasından gelir; bu dosya sınıf yolunda yoksa `SystemInfoController.DEV_VERSION` (`0.0.0-dev`) döner.

### 1.5 Tenant bağlamında bir yazma işleminin yolculuğu (S1 spike'ı)

Kurallar §4.4 ve §4.5'te, kararlar ADR-0003 ve ADR-0015'tedir. S1 spike'ında HTTP ucu yoktur; tenant'ı test kodu `TenantContext.run/call` ile bağlar. HTTP isteğinden tenant'ı çözümleyip aynı kapsamı açan katman S2 spike'ında sınanmıştır (Bölüm 1.6). İki parçanın birleştiği uçtan uca yol (tarayıcı ya da bearer isteği → tenant DB'si) Faz 1'de kurulacaktır.

```mermaid
sequenceDiagram
participant T as Çağıran (test)
participant C as TenantContext
participant S as SampleService
participant R as TenantRoutingDataSource
participant D as JdbcTenantDirectory
participant P as PostgreSQL
T->>C: call(acme, işlem)
C->>C: transaction kapsamında mı? (koruma)
C->>S: record("x") @Transactional
S->>R: getConnection()
R->>D: find(acme) (havuz yoksa)
D->>P: tenant tablosu (erp_platform)
P-->>D: ACTIVE, erp_t_acme
R->>P: tenant-acme havuzundan bağlantı
S->>P: insert sample_record ve event_publication
S-->>C: commit
C-->>T: sonuç, bağlama kaldırılır
```

Akışın kuralları:

- Tenant transaction'dan önce bağlanır. `TenantContext.call`, transaction ya da transaction senkronizasyonu açıkken başka bir tenant'a geçişi `TenantSwitchInTransactionException` ile reddeder (§4.4 madde 7, bulgu B12).
- `TenantRoutingDataSource`, bağlı tenant yoksa `MissingTenantContextException` fırlatır ve `rejectedWithoutTenant` sayacını artırır; varsayılan tenant yoktur.
- Tenant kaydı yalnızca platform DataSource'u (`@PlatformDb`) üzerinden okunur; havuz yalnızca `ACTIVE` tenant için ve ilk istekte açılır (`minimumIdle=0`).
- Olay kaydı (`platform_events.event_publication`) iş verisiyle aynı tenant transaction'ında yazılır (ADR-0012). Olayın ardından gelen asenkron dinleyici ve iş akışı Bölüm 7.10'dadır.

### 1.6 Kimlikli bir isteğin yolculuğu (S2 spike'ı)

Kurallar §4.4 madde 1–2 ve §6.3.1'de, kararlar ADR-0005, ADR-0006, ADR-0039 ve ADR-0040'tadır. Spike'ta üç `SecurityFilterChain` vardır; istek, sırası en küçük olandan başlayarak eşleştiği ilk zincirde işlenir (`identity/SecurityConfiguration.java`).

| Sıra | Zincir | Eşleşen istek | Ne yapar |
|---|---|---|---|
| 0 | `actuatorChain` | `/actuator/health/**` | Kimliksiz ve durumsuz; tenant host'u istemez. Readiness yalnızca platform DB'sine bakar |
| 1 | `bearerChain` | `Authorization` başlığı `Bearer ` ile başlar (büyük-küçük harf duyarsız) | ADR-0040 yol 2: JWT doğrulanır, tenant `(iss, organization)`'dan çözülür; oturum ve CSRF yoktur |
| 2 | `browserChain` | Diğer her istek | ADR-0040 yol 1: tenant host'tan çözülür; `oauth2Login`, platform DB'sinde oturum, CSRF |

Bearer başlığı taşıyan istek, token geçersiz olsa bile bearer zincirinde kalır; böylece geçersiz token oturum çerezine düşmez (S2 B21). Tarayıcı girişinin akışı:

```mermaid
sequenceDiagram
participant B as Tarayıcı
participant F as BrowserTenantFilter
participant S as Spring Security
participant K as Keycloak
participant U as TenantOidcUserService
participant P as Platform DB
B->>F: GET /oauth2/authorization/keycloak
F->>P: host araması (tenant_domain, tenant)
F->>S: TenantContext.run(acme, zincir)
S-->>B: 302 Keycloak (organization:acme, PKCE)
B->>K: kullanıcı adı ve şifre
K-->>B: 302 callback (code, state)
B->>F: GET /login/oauth2/code/keycloak
F->>S: TenantContext.run(acme, zincir)
S->>K: kod takası
S->>U: loadUser(ID token)
U-->>S: iss ve organization yalnızca acme mi?
S->>P: oturum, yeni kimlik, principal acme:sub
S-->>B: 302 /, Set-Cookie __Host-SESSION
```

Akışın kuralları:

- Tenant her istekte host'tan çözülür: `TenantHost.normalize(request.getServerName())` ve `TenantDirectory.findByHost`. Kayıtsız host 404, `ACTIVE` olmayan tenant 503 alır; giriş başlamaz. Varsayılan tenant yoktur. Uygulama TLS'i sonlandıran proxy'nin arkasındadır ve host'u `X-Forwarded-Host`'tan öğrenir (`server.forward-headers-strategy: native`, S2 B17).
- Organization ipucu (`organization:<alias>`) bir güvenlik sınırı değildir. Asıl kontrol callback'te, imzalı ID token üzerinde yapılır: `iss` tenant'ın issuer'ına, `organization` kümesi tam olarak tenant'ın alias'ına eşit olmalıdır. Uymayan giriş 403 `tenant_mismatch` alır ve oturum açılmaz. Bu kontrol, callback'in başka bir tenant host'unda tekrar oynatılmasına karşı tek savunmadır (S2 B2, B3).
- Oturum Spring Session JDBC ile platform DB'sine, kendi transaction'ıyla yazılır; tenant DataSource'una hiçbir istek gitmez (S2 B7). Giriş başlangıcı, authorization isteğini saklamak için kimliği doğrulanmamış bir oturum satırı yazar (S2 B11); oturum kimliği girişte değişir. Oturumda access ve refresh token yoktur; principal (`TenantOidcUser`) yalnızca ID token'ı taşır (S2 B8).
- Oturumlu sonraki her istekte `BrowserTenantFilter`, principal'ın tenant'ını host'un tenant'ıyla karşılaştırır; farklıysa 401 döner ve tenant bağlamı kurulmaz (S2 B14). Eşleşen istekte tenant, `TenantFilterChain.proceed` ile zincirin geri kalanı ve controller için bağlanır; controller tenant'ı `TenantContext.require()` ile okur.
- Bearer isteğinde `SessionlessRequestFilter` oturumu istekten gizler, Boot'un JWT decoder'ı imzayı, `iss`'i ve `erp-api` audience'ını doğrular, `TenantJwtAuthenticationConverter` tenant'ı `(iss, organization)` ile çözer, `BearerTenantFilter` durumu ve host'u denetleyip tenant'ı bağlar (Bölüm 7.14).

## 2. Dizin anatomisi ve modül haritası

Hedef depo yerleşimi §14'tedir. §14'teki diğer üst klasörler ilk içerikleri geldiği fazda açılır; boş klasör açılmaz (plan, "Dosya haritası").

### 2.1 Kök dizin ve araç dosyaları

| Yol | Görevi | Ürettiği | Bağlı olduğu |
|---|---|---|---|
| `mise.toml` | Araç zincirini sabitler: `java = "temurin-25"`, `node = "24.21.0"`, `pnpm = "12.9.1"`, `gitleaks = "8.30.1"` | `mise install` ile kurulan araçlar | mise |
| `pom.xml` | `erp-parent`: Spring Boot 4.1.1 parent, Modulith 2.1.1 BOM, ArchUnit 1.5.1, enforcer, Error Prone 2.50.0, Spotless 3.10.3; `spikes` profili | Maven reaktörü (`platform`, `app`); `-Pspikes` ile ayrıca `spikes/s1-tenancy` ve `spikes/s2-identity` | `spring-boot-starter-parent` |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Maven wrapper (Maven 3.9.16) | — | İnternet erişimi (ilk indirme) |
| `.mvn/jvm.config` | Error Prone'un javac iç API'lerine erişmesi için `--add-exports`/`--add-opens` | — | JDK 25 |
| `.editorconfig`, `.gitattributes` | Satır sonu LF, UTF-8; Java ve XML 4, diğerleri 2 boşluk girinti | — | Editör, git |
| `.gitignore` | `target/`, `node_modules/`, `dist/`, `.env*` (`!.env.example` hariç), duman testi çıktıları | — | git |
| `.dockerignore` | `.git`, derleme çıktıları, `docs`, `**/.env*` imaj bağlamına girmez | — | Docker |
| `.gitleaks.toml` | Varsayılan gitleaks kuralları + `pnpm-lock.yaml` istisnası | — | gitleaks |
| `renovate.json` | Bağımlılık güncelleme kuralları | Renovate PR'ları (bot henüz kurulu değil) | Renovate |
| `.gitlab-ci.yml` | CI hattı (5 aşama, 6 iş) | Test raporları, imaj, imza | GitLab, dind runner |
| `.gitlab/merge_request_templates/Default.md` | MR şablonu ve kontrol listesi | — | GitLab |
| `README.md`, `CONTRIBUTING.md` | Hızlı başlangıç, dal ve commit akışı | — | — |
| `scripts/check-guide.mjs`, `scripts/check-guide.allow` | Bu kılavuzun DocBuilder biçim kurallarına ve depoya göre denetimi (Bölüm 4.2); `.allow` bilerek depoda olmayan yolların gerekçeli listesidir | Hata ve uyarı listesi, çıkış kodu | Node, git, `npx` ile `@mermaid-js/mermaid-cli@9.1.7` |

### 2.2 Backend

| Yol | Görevi | Ürettiği | Bağlı olduğu |
|---|---|---|---|
| `platform/pom.xml` | `erp-platform`: platform modüllerinin toplayıcısı ve parent'ı | — | `erp-parent` |
| `platform/platform-test` | `erp-platform-test`: `ErpArchitectureRules` (K10, K12) ve `PostgresTestcontainer` (`newContainer()` dahil) | Test kapsamında kullanılan jar | ArchUnit, Testcontainers PostgreSQL, `spring-boot-testcontainers` |
| `app/pom.xml` | `erp-app`: tek Spring Boot uygulaması; build-info, SPA kopyalama, CycloneDX SBOM, lisans denetimi | `app/target/erp-app.jar` | `erp-platform-test` (test), `web/apps/web/dist` |
| `app/src/main/java/com/smart/erp/ErpApplication.java` | Giriş noktası; `@ConfigurationPropertiesScan` | — | Spring Boot |
| `app/src/main/java/com/smart/erp/system` | `system` modülü: `DeploymentMode`, `DeploymentProperties` ve `web` alt paketindeki `SystemInfoController` | `/api/v1/system/info` | — |
| `app/src/main/resources/application.yaml` | Varsayılan yapılandırma: datasource URL ve kullanıcı, sanal thread'ler, graceful shutdown, actuator, `erp.deployment.mode` | — | — |
| `app/src/main/resources/application-dev.yaml` | `dev` profili: `deploy/compose/.env` dosyasını içe aktarır, DB şifresini oradan alır | — | `deploy/compose/.env` |
| `app/src/test/java/com/smart/erp` | Uygulama testleri (Bölüm 4 ve 6) | Surefire raporları | Docker (Testcontainers) |

Jar içeriği doğrulanmıştır: `BOOT-INF/classes/static/index.html`, `META-INF/build-info.properties` ve `META-INF/sbom/application.cdx.json` bulunur.

### 2.3 Frontend

| Yol | Görevi | Ürettiği | Bağlı olduğu |
|---|---|---|---|
| `web/package.json` | Kök betikler: `lint`, `format`, `format:check`, `typecheck`, `test`, `build`, `licenses:check`; `packageManager: pnpm@12.9.1` | — | Kök geliştirme araçları |
| `web/pnpm-workspace.yaml` | Paket listesi, sürüm kataloğu (`catalog:`), `allowBuilds` | — | pnpm 12 |
| `web/pnpm-lock.yaml` | Kilit dosyası (`--frozen-lockfile` ile kullanılır) | — | — |
| `web/eslint.config.js` | Vue + TypeScript kuralları, `vue/no-v-html`, `script setup` zorunluluğu, Prettier ile çakışan kuralların kapatılması | — | ESLint 10 |
| `web/.prettierrc.json`, `web/.prettierignore` | Biçim: noktalı virgül yok, tek tırnak, 100 karakter | — | Prettier |
| `web/tsconfig.base.json` | Ortak TypeScript ayarları (`strict`, `noUncheckedIndexedAccess`) | — | TypeScript 6.0.3 |
| `web/packages/ui` | `@erp/ui`: Vuetify yapılandırması (`createErpVuetify`), temalar | — | Vuetify, `@mdi/js` |
| `web/packages/core` | `@erp/core`: HTTP istemcisi (`getJson`, `ApiError`), `useSystemInfo` | — | TanStack Query |
| `web/packages/shell` | `@erp/shell`: `ErpShell` uygulama iskeleti ve i18n metinleri | — | `@erp/core`, `@erp/ui`, vue-i18n |
| `web/apps/web` | `@erp/web`: SPA giriş noktası, Vite yapılandırması | `web/apps/web/dist` | `@erp/shell`, `@erp/core`, `@erp/ui` |
| `web/e2e` | `@erp/e2e`: Playwright duman testi | HTML ve JUnit raporları (konteynerde üretilir, `smoke.sh` dışarı kopyalar) | `@playwright/test` |
| `web/scripts/check-licenses.mjs`, `web/scripts/license-expression.mjs` | Üretim bağımlılıklarında lisans izinli listesi | — | `pnpm licenses list` |

### 2.4 Dağıtım ve dokümantasyon

| Yol | Görevi | Ürettiği | Bağlı olduğu |
|---|---|---|---|
| `deploy/compose/compose.yaml` | Compose projesi `erp`: `postgres` (profilsiz), `keycloak` ve `mailpit` (`dev`), `app` (`ci`), `e2e` (`e2e`) | Konteynerler, `postgres-data` volume'u | `deploy/compose/.env` |
| `deploy/compose/compose.smoke.yaml` | Duman testi için host portlarını kaldırır | — | `compose.yaml` |
| `deploy/compose/init-env.sh` | `.env` dosyasını rastgele sırlarla üretir; var olana dokunmaz | `deploy/compose/.env` (izin `600`) | `/dev/urandom` |
| `deploy/compose/.env.example` | Değişken adlarının listesi (değersiz) | — | — |
| `deploy/compose/postgres/Dockerfile` | `postgres:18.6` + init betikleri gömülü imaj | `erp-postgres:local` | — |
| `deploy/compose/postgres/initdb/01-create-databases.sh` | İlk açılışta `erp_platform` ve `keycloak` veritabanlarını ve sahip rollerini açar | — | `ERP_PLATFORM_DB_PASSWORD`, `KEYCLOAK_DB_PASSWORD` |
| `deploy/compose/smoke.sh` | Tek imaj + compose + Playwright duman testi | `deploy/compose/smoke-logs/` | Docker |
| `deploy/docker/app.Dockerfile` | Üç aşamalı uygulama imajı | `erp-app:local` | Node ve Temurin imajları |
| `deploy/docker/e2e.Dockerfile` | Playwright koşucu imajı | `erp-smoke-e2e` | `mcr.microsoft.com/playwright:v1.63.0-noble` |
| `docs/architecture/v4-platform.md` | Mimari ve yol haritası (onaylı, 2026-10-05) | — | — |
| `docs/adr/` | `0000-template.md`, ADR-0001…0044, dizin `README.md` (durum sütunu) | — | — |
| `docs/guides/` | `coding-rules.md`, `local-development.md`, `ci.md` ve bu kılavuz | — | — |
| `docs/spikes/s1-tenancy.md` | S1 spike'ının sonuç raporu: kanıt tablosu, bulgular B1–B20, Faz 1 girdileri ve açık maddeleri, Faz 2'ye devredilenler | — | Spike testleri |
| `docs/spikes/s2-identity.md` | S2 spike'ının sonuç raporu: kurulum, kanıt tablosu, bulgular B1–B28, Faz 1 girdileri ve 11 açık madde, Faz 2, 3 ve 7'ye devredilenler, spec'e işlenen düzeltmeler | — | Spike testleri |
| `docs/open-questions.md` | Tasarım incelemelerinden doğan, henüz karara bağlanmamış sorular (Bölüm 10.4); karar verilen soru buradan silinir | — | — |
| `docs/superpowers/plans/2026-10-05-faz-0a-zemin.md` | Faz 0A uygulama planı | — | — |
| `docs/superpowers/plans/2026-10-05-faz-0b-s1-tenancy.md` | Faz 0B (S1 spike'ı) uygulama planı | — | — |
| `docs/superpowers/plans/2026-10-06-faz-0c-s2-kimlik.md` | Faz 0C (S2 spike'ı) uygulama planı | — | — |

Git'e girmeyen ama yerelde oluşan yollar: `deploy/compose/.env` (sırlar), `web/apps/web/dist` (SPA derlemesi), `app/target/`, `spikes/s1-tenancy/target/` ve `spikes/s2-identity/target/` (Maven çıktısı), `deploy/compose/smoke-logs/` (duman testi kayıtları), `docs/guides/gelistirici-kilavuzu.pdf` (bu kılavuzun DocBuilder çıktısı; `.gitignore`'da değildir ama commit'e alınmaz, plan 0C "Global Constraints").

### 2.5 Modül bağımlılıkları

Maven reaktörü (oklar "içerir" ya da "kullanır" ilişkisini gösterir):

```mermaid
flowchart TD
P[erp-parent<br>pom.xml] -->|modül| PL[erp-platform<br>platform/pom.xml]
P -->|modül| APP[erp-app<br>app/pom.xml]
PL -->|modül| PT[erp-platform-test<br>platform/platform-test]
APP -.->|test kapsamı| PT
DIST[SPA derlemesi<br>web/apps/web/dist] -.->|process-resources| APP
P -.->|spikes profili| SP[erp-spike-s1-tenancy<br>spikes/s1-tenancy]
SP -.->|test kapsamı| PT
P -.->|spikes profili| SP2[erp-spike-s2-identity<br>spikes/s2-identity]
SP2 -.->|test kapsamı| PT
```

Kesikli `spikes profili` oku, modülün yalnızca `-Pspikes` verildiğinde reaktöre girdiğini gösterir. Spike'lar birbirine bağlı değildir. Her spike, `erp-parent`'ı `relativePath` ile, `erp-platform-test`'i ise yerel Maven deposundan çözer; bu yüzden önce `./mvnw -q install -DskipTests` çalıştırılmalıdır (Bölüm 3.10).

pnpm workspace (oklar `package.json` bağımlılığını gösterir):

```mermaid
flowchart TD
WEB[erp/web<br>apps/web] --> SHELL[erp/shell<br>packages/shell]
WEB --> CORE[erp/core<br>packages/core]
WEB --> UI[erp/ui<br>packages/ui]
SHELL --> CORE
SHELL --> UI
E2E[erp/e2e<br>e2e] -.->|HTTP| WEB
```

Diyagramda paket adlarının başındaki `@` işareti atılmıştır; Mermaid 9.1.7 düğüm metnindeki `@` ile çizim hatası vermektedir. `@erp/core` bugün `@erp/ui`'a bağlı değildir; §9.2'deki yön (`apps → modules → shell → meta-renderer → core → ui`) yalnızca izin verilen yönü tanımlar. `@erp/e2e` hiçbir workspace paketini import etmez, uygulamaya HTTP ile erişir. Bir paket yalnızca `package.json`'unda bildirdiği paketi çözebilir; bildirilmemiş bir workspace paketini import etmek `vue-tsc` denetiminde `TS2307` hatası verir (Bölüm 4.5'teki alıştırma).

### 2.6 Spike'lar (`spikes/`)

Spike'lar §15.3'teki riskli kararları gerçek kodla sınayan atılacak projelerdir (`spikes/README.md`). Varsayılan reaktörde değildir; `./mvnw verify`, imaj derlemesi ve CI onları derlemez.

| Yol | Görevi | Ürettiği | Bağlı olduğu |
|---|---|---|---|
| `spikes/README.md` | Spike'ların ne olduğu, nasıl çalıştırıldığı, ne zaman silineceği | — | — |
| `spikes/s1-tenancy/pom.xml` | `erp-spike-s1-tenancy`: JPA, jOOQ, Modulith JDBC, db-scheduler 16.12.0, Actuator, Web MVC; test kapsamında Flyway ve `erp-platform-test` | `spikes/s1-tenancy/target/` | `erp-parent`, yerel depodaki `erp-platform-test` |
| `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1` | `SpikeApplication` ve `kernel`, `tenancy`, `jobs`, `sample` modülleri (Bölüm 7.8–7.11) | — | — |
| `spikes/s1-tenancy/src/main/resources/application.yaml` | Açılışta tenant'sız bağlantı istenmesini önleyen zorunlu ayarlar (Bölüm 4.7) | — | — |
| `spikes/s1-tenancy/src/main/resources/db/platform` | Platform DB migration'ları: `tenant`, `scheduled_tasks` | — | Flyway (yalnızca test fixture'ı) |
| `spikes/s1-tenancy/src/main/resources/db/tenant` | Tenant DB migration'ları: `platform_events.event_publication`, `sample.sample_record` | — | Flyway (yalnızca test fixture'ı) |
| `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support` | Çok veritabanlı fixture (`SpikeDatabases`), test ayarları, `@SpikeTest`, bağlamı önbellek dışında açan `SpikeContexts`, `SpikeAssertions` | — | Testcontainers, Flyway |
| `spikes/s2-identity/pom.xml` | `erp-spike-s2-identity`: Web MVC, JDBC, Actuator, `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-session-jdbc`; test kapsamında Flyway, Modulith test ve `erp-platform-test`; Surefire `argLine` `-Djdk.httpclient.allowRestrictedHeaders=host` | `spikes/s2-identity/target/` | `erp-parent`, yerel depodaki `erp-platform-test` |
| `spikes/s2-identity/src/main/java/com/smart/erp/spike/s2` | `SpikeApplication` ve `kernel`, `tenancy`, `identity`, `sample` modülleri (Bölüm 7.12–7.15) | — | — |
| `spikes/s2-identity/src/main/resources/application.yaml` | Bearer decoder'ın issuer ve audience'ı, oturum (`timeout`, `initialize-schema: never`, `cleanup-cron: "-"`), `__Host-SESSION` çerezi, `forward-headers-strategy: native`, readiness | — | — |
| `spikes/s2-identity/src/main/resources/db/platform` | Platform DB migration'ları: `tenant`, `tenant_domain`, Spring Session tabloları | — | Flyway (yalnızca test fixture'ı) |
| `spikes/s2-identity/src/test/java/com/smart/erp/spike/s2/support` | PostgreSQL fixture'ı (`SpikeDatabases`), Keycloak fixture'ı (`SpikeKeycloak`), test tarayıcısı (`SpikeBrowser`), ortam (`SpikeEnvironment`), `@SpikeTest`, `SpikeContexts` | — | Testcontainers, Flyway, Keycloak Admin API |
| `spikes/s2-identity/src/test/resources/keycloak/erp-realm.json` | Test realm'i `erp`: Organizations açık, `erp-web` ve altı entegrasyon istemcisi; kullanıcı, organization ve sır içermez | — | Keycloak `--import-realm` |

S1 spike'ının `application.yaml` dosyasında bağlantı ayarı yoktur. Altı bağlantı özelliği (`erp.platform.datasource.*` ve `erp.tenancy.datasource.*`) testlerde `SpikeDatabases.applicationProperties()` tarafından üretilir.

S2 spike'ının `application.yaml` dosyasında da bağlantı adresi ve sır yoktur. `erp.platform.datasource.*` değerlerini `SpikeDatabases.applicationProperties()`, `erp.identity.issuer-uri` ve `erp.identity.web-client-secret` değerlerini `SpikeKeycloak.applicationProperties()` üretir; `SpikeEnvironment` bunları bağlama ekler ve üç test tenant'ını platform DB'sine kaydeder. Spike'ları testler dışında çalıştırmanın tanımlı bir yolu yoktur.

## 3. Günlük çalışma döngüsü

Komutlar depo kökünden (`erp-v4/`) çalıştırılır; aksi belirtilmişse `cd` adımı gösterilir. Bu bölümdeki komutlar macOS (Apple Silicon), Docker Desktop (sunucu 29.5.3) ve Docker Compose v5.1.4 ile doğrulanmıştır.

### 3.1 Araç kurulumu

Java, Node, pnpm ve gitleaks yalnızca mise üzerinden kullanılır. Sistemde başka sürümler kurulu olabilir (doğrulama yapılan makinede sistem `java` 17, sistem `node` 18'dir ve global `pnpm` yoktur); Maven enforcer Java 17'yi reddeder (Bölüm 4.4).

```bash
brew install mise
mise trust
mise install
```

Doğrulama makinesinde `brew install mise` "already installed and up-to-date" uyarısı, `mise install` ise "all tools are installed" çıktısı vermiştir; temiz makinede ilk kurulum çıktısı doğrulanmamıştır (bu makinede zaten kurulu).

mise araçları iki yoldan biriyle kullanılır:

| Yol | Nasıl | Ne zaman |
|---|---|---|
| Kabuk entegrasyonu | `~/.zshrc` dosyasına `eval "$(mise activate zsh)"` satırı eklenir, yeni terminal açılır | Günlük etkileşimli çalışma |
| Önek | Her komutun başına `mise x --` yazılır, ör. `mise x -- ./mvnw verify` | Kabuk entegrasyonu yoksa, betikler ve ajanlar |

> ⚠️ Doğrulama makinesinde `~/.zshrc` dosyasına mise satırı eklenmemiştir; bu nedenle kılavuzdaki doğrulamalar `mise x --` önekiyle yapılmıştır. Kabuk entegrasyonu `zsh -c` içinde `mise activate` ve `mise hook-env` ile denenmiş, `java` 25, `node` 24.21.0 ve `pnpm` 12.9.1 görülmüştür. Kılavuzun geri kalanındaki komutlar etkin bir kabuk varsayar.

Sürümlerin kontrolü:

```bash
java -version      # openjdk version "25.0.4.1" ... Temurin
node -v            # v24.21.0
pnpm -v            # 12.9.1
gitleaks version   # 8.30.1
docker info --format '{{.ServerVersion}} {{.OSType}}/{{.Architecture}}'
```

Maven kurulmaz; `./mvnw` wrapper'ı Maven 3.9.16'yı indirir. Docker Desktop çalışıyor olmalıdır: Testcontainers testleri ve compose buna bağlıdır.

### 3.2 Sırların üretimi

```bash
deploy/compose/init-env.sh
```

Betik `deploy/compose/.env` dosyasını `umask 077` ile (izin `-rw-------`) dört rastgele değerle üretir: `POSTGRES_PASSWORD`, `ERP_PLATFORM_DB_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`. Dosya varsa dokunmaz ve şu satırı yazar:

```text
deploy/compose/.env already exists; leaving it untouched.
```

Dosya `.gitignore` (`.env*`) ve `.dockerignore` (`**/.env*`) ile git'e ve imaj bağlamına girmez (K9). İsteğe bağlı geçersiz kılmalar (`POSTGRES_PORT`, `APP_PORT`, `ERP_APP_IMAGE`) `.env.example` içinde yorum olarak listelenmiştir.

> `.env` içeriği hiçbir yere kopyalanmamalı, ekran görüntüsüne ya da MR açıklamasına konmamalıdır. Kaybolursa yeni değerler üretmek için dosya silinip betik yeniden çalıştırılır; ancak mevcut PostgreSQL volume'u eski şifrelerle açılmıştır ve sıfırlanması gerekir (Bölüm 3.9).

### 3.3 Yerel yığını kaldırma

```bash
docker compose -f deploy/compose/compose.yaml --profile dev up -d
docker compose -f deploy/compose/compose.yaml --profile dev ps
```

Beklenen durum: `postgres` (`erp-postgres:local`) `healthy`, `keycloak` (`quay.io/keycloak/keycloak:26.8.0`) `Up`, `mailpit` (`axllent/mailpit:v1.31.4`) `healthy`. PostgreSQL ilk açılışta (boş volume) `deploy/compose/postgres/initdb/01-create-databases.sh` betiğini çalıştırır.

| Servis | Adres | Not |
|---|---|---|
| PostgreSQL | `127.0.0.1:5432` | Veritabanları `erp_platform`, `keycloak` |
| Keycloak | `http://localhost:8180` | Yönetici `admin`, şifre `.env` içindeki `KEYCLOAK_ADMIN_PASSWORD`; ürün uygulaması henüz kullanmıyor, S2 spike'ı kendi Keycloak konteynerini açar (Bölüm 3.10) |
| Mailpit | `http://localhost:8025` (SMTP 1025) | Uygulama henüz kullanmıyor |
| Uygulama | `http://localhost:8080` | Bölüm 3.4 |
| SPA dev sunucusu | `http://localhost:5173` | Bölüm 3.5 |

Bütün portlar yalnızca `127.0.0.1`'e bağlanır. Yığının ayakta olduğunu kanıtlayan komutlar:

```bash
docker compose -f deploy/compose/compose.yaml exec postgres \
  psql -U postgres -tAc "select datname, datlocprovider, datlocale \
  from pg_database where datname in ('erp_platform','keycloak') order by 1"
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8180/realms/master
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8025/
```

Beklenen çıktı:

```text
erp_platform|b|C.UTF-8
keycloak|b|C.UTF-8
200
200
```

### 3.4 Backend'i derleme ve çalıştırma

Tam doğrulama (derleme, Error Prone, testler, Modulith, ArchUnit, Spotless, lisans, SBOM):

```bash
./mvnw clean verify
```

Beklenen: `Tests run: 11` (`erp-platform-test`), `Tests run: 20` (`erp-app`), `BUILD SUCCESS`. Doğrulama makinesinde süre Faz 0A'da 1 dakika 17 saniye, Faz 0C'de 19 saniye ölçülmüştür (ikisinde de Docker imajları önbellekteyken; sürenin çoğu Testcontainers ile açılan veritabanlarıdır). Bu komut spike'ları derlemez; spike'lar için Bölüm 3.10'a bakılır.

> `clean` her zaman eklenmelidir. VS Code ve Antigravity'nin Java dil sunucuları `target/` altına bozuk sınıf dosyaları yazabilir; Faz 0A'da bu yaşanmıştır (Bölüm 9).

Uygulamayı `dev` profiliyle çalıştırmanın üç yolu vardır; üçü de compose yığınındaki PostgreSQL'e bağlanır:

```bash
# 1. Paketlenmiş jar ile
./mvnw -q package -DskipTests
java -jar app/target/erp-app.jar --spring.profiles.active=dev

# 2. Maven eklentisiyle (önce reaktör yerel depoya kurulur)
./mvnw -q install -DskipTests
./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev
```

3. IDE'de `ErpApplication` sınıfı, aktif profil `dev` olarak çalıştırılır (doğrulanmadı).

> Faz 0B ve 0C doğrulamalarında 8080 portu çalışan bir compose `app` servisi (`ci` profili) tarafından kullanılıyordu. Jar yolu bu yüzden `--server.port=18081` eklenerek denenmiş, aşağıdaki iki yanıt aynen alınmıştır; 8080 doluysa aynı yöntem kullanılır.

`dev` profili `application-dev.yaml` üzerinden `deploy/compose/.env` dosyasını hem depo kökünden hem `app/` klasöründen arar ve `spring.datasource.password` değerini `ERP_PLATFORM_DB_PASSWORD`'den alır. Profil verilmezse şifre boştur; uygulama yine açılır ama `readiness` `DOWN` olur (Bölüm 6.2).

Çalıştığını kanıtlayan komutlar ve beklenen yanıtlar:

```bash
curl -s http://127.0.0.1:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
curl -s http://127.0.0.1:8080/api/v1/system/info
# {"version":"0.1.0-SNAPSHOT","deploymentMode":"onprem"}
```

`/actuator/info` derleme bilgisini (`build.artifact`, `build.version` …) döner. Actuator'da yalnızca `health` ve `info` açıktır.

### 3.5 Frontend'i çalıştırma

```bash
cd web
pnpm install --frozen-lockfile
pnpm --filter @erp/web dev
```

Vite `http://localhost:5173/` adresinde açılır. Kök kontroller `web/` içinde çalışır: `pnpm format:check`, `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`, `pnpm licenses:check`.

### 3.6 Backend ile frontend arasındaki bağlantı

`web/apps/web/vite.config.ts` dev sunucusunda `/api` ve `/actuator` önekli istekleri `http://localhost:8080`'e iletir. Backend ile Vite birlikte çalışırken doğrulama:

```bash
curl -s http://localhost:5173/api/v1/system/info
# {"version":"0.1.0-SNAPSHOT","deploymentMode":"onprem"}
```

Backend kapalıyken aynı istek Vite'tan `502` döner, Vite günlüğüne `http proxy error: /api/v1/system/info` ve `ECONNREFUSED` yazılır; tarayıcıda shell "Sunucuya ulaşılamıyor" uyarısını gösterir (Bölüm 6.2). Üretimde proxy yoktur; SPA jar'dan, API ile aynı origin'den sunulur.

### 3.7 Hata ayıklama

| İhtiyaç | Yöntem |
|---|---|
| Backend'e debugger bağlamak | `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 -jar app/target/erp-app.jar --spring.profiles.active=dev`; IDE'den 5005'e "Remote JVM Debug" bağlanır |
| Uygulama sağlığı | `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness` |
| Konteyner günlükleri | `docker compose -f deploy/compose/compose.yaml logs postgres` (ya da `keycloak`, `mailpit`) |
| Veritabanına bağlanmak | `docker compose -f deploy/compose/compose.yaml exec postgres psql -U postgres -d erp_platform` |
| Frontend ağ hataları | Tarayıcı geliştirici araçları; Vite günlüğündeki `http proxy error` satırları |
| Duman testi hatası | `deploy/compose/smoke-logs/compose.log` ve `deploy/compose/smoke-logs/playwright-report/index.html` |
| Test raporları | `app/target/surefire-reports/` |

JDWP satırı doğrulanmıştır (`Listening for transport dt_socket at address: 5005`); IDE bağlantısı doğrulanmamıştır.

### 3.8 Test verisi ve örnek tenant

Ürün uygulamasında henüz yoktur: tablo, migration ve tenant kavramı yoktur; örnek tenant ve veri üretici sonraki fazlara aittir (Bölüm 10). Ürün testleri gerçek PostgreSQL 18 konteyneriyle (Testcontainers) çalışır ve kendi veritabanını açar; compose yığınına ihtiyaç duymaz.

Örnek tenant'lar bugün yalnızca S1 spike'ının test fixture'ında vardır. `SpikeDatabases` (`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeDatabases.java`) test çalışması başına bir PostgreSQL 18.6 konteyneri açar ve §7.1'deki düzeni kurar:

| Tenant | Kayıttaki durum | Veritabanı | Amacı |
|---|---|---|---|
| `acme` | `ACTIVE` | `erp_t_acme` (migration'lı) | Olağan tenant |
| `globex` | `ACTIVE` | `erp_t_globex` (migration'lı) | İzolasyon karşılaştırması için ikinci tenant |
| `initech` | `SUSPENDED` | `erp_t_initech` (migration'lı) | Askıdaki tenant: havuz açılmamalı |
| `ghost` | `ACTIVE` | Yok | Erişilemeyen tenant: hata raporlanmalı, diğerleri devam etmeli |

Platform DB'si `erp_platform`'dur. Platform rolü `erp_platform`, tenant rolü `erp_tenant`'tır; şifreler her çalıştırmada `UUID.randomUUID()` ile üretilir, depoda şifre yoktur (K9). Her veritabanının `CONNECT` yetkisi `PUBLIC`'ten alınmıştır; tenant rolü platform DB'sine bağlanamaz (`SpikeDatabasesTests`). Migration'ları uygulama değil fixture çalıştırır (`erpctl migrate` rolü, §4.6). Testler, uygulamanın yönlendirmesini atlayan süper kullanıcı sorgularıyla (`SpikeDatabases.tenantDatabase(...)`) satırın hangi veritabanına yazıldığını bağımsız olarak doğrular.

S2 spike'ının fixture'ı iki konteyner açar. `SpikeDatabases` (`spikes/s2-identity/src/test/java/com/smart/erp/spike/s2/support/SpikeDatabases.java`) PostgreSQL 18.6'da yalnızca `erp_platform`'u kurar; tenant DB'si yoktur. `SpikeKeycloak` aynı dizinde Keycloak 26.8.0'ı `start-dev --import-realm` ile açar, kullanıcıları, organization'ları ve üyelikleri Admin API ile kurar. Kullanıcı şifreleri, yönetici şifresi ve DB rol şifresi her çalıştırmada üretilir; client secret'lar Keycloak'tan okunur (K9). Tenant'lar `SpikeEnvironment` tarafından fixture'ın issuer'ıyla kaydedilir:

| Tenant | Host | Organization alias | Durum |
|---|---|---|---|
| `acme` | `acme.erp.test` | `acme` | `ACTIVE` |
| `globex` | `globex.erp.test` | `globex` | `ACTIVE` |
| `initech` | `initech.erp.test` | `initech` | `ACTIVE` (askıya alma testi geçici olarak `SUSPENDED` yapar) |
| — | `api.erp.test`, `unknown.erp.test` | — | Kayıtsız: nötr API host'u ve bilinmeyen host |

| Kullanıcı | Organization üyeliği | Kullanım |
|---|---|---|
| `ayse` | acme | Tek tenant'lı kullanıcı |
| `zeynep` | globex | acme'de "üye değil" senaryosu |
| `mm` | acme, globex | Mali müşavir: iki tenant, iki bağımsız oturum |
| `ipek` | initech | Askıya alma senaryosu |

Entegrasyon istemcileri realm dosyasındadır: `acme-integration`, `initech-integration`, `multi-integration` (iki organization), `unknown-integration` (kayıtsız organization), `noorg-integration` (tenant eşleyicisi yok), `noaud-integration` (`erp-api` audience'ı yok). Test realm'ine özgü iki ayar üretime taşınmaz: `sslRequired: none` ve kullanıcı adı alt sınırının 2'ye indirilmesi (S2 B26).

### 3.9 Yığını durdurma ve sıfırlama

```bash
# Durdur (veri korunur)
docker compose -f deploy/compose/compose.yaml --profile dev down

# Sıfırla: volume dahil her şey silinir, init betikleri yeniden çalışır
docker compose -f deploy/compose/compose.yaml --profile dev down --volumes
```

> ⚠️ `down --volumes` geliştirici veritabanını (`erp_postgres-data` volume'u) kalıcı olarak siler. Durdurma ve sıfırlama komutları kılavuz hazırlanırken dev yığınında çalıştırılmamış, aynı compose dosyasıyla ayrı bir proje adı ve port üzerinde (`POSTGRES_PORT=55432`, `-p erp-guidecheck`) doğrulanmıştır: `down` volume'u korumuş, `down --volumes` yalnızca `erp-guidecheck_postgres-data` volume'unu silmiş, `erp_postgres-data` her iki durumda da korunmuştur.

Durdurma ve sıfırlama komutları commit `86779d0`'dan beri `--profile dev` ile yazılır; böylece `dev` profilindeki `keycloak` ve `mailpit` de komutun kapsamına girer. Profil verilmeden çalıştırıldığında dev servislerinin ne olacağı doğrulanmamıştır.

### 3.10 Spike'ları çalıştırma

Spike'lar compose yığınına ihtiyaç duymaz; veritabanlarını ve S2'nin Keycloak'ını Testcontainers ile kendileri açar. Docker Desktop çalışıyor olmalıdır.

#### S1 tenancy spike'ı

```bash
# Bir kez ve platform/ ya da kök pom.xml her değiştiğinde
./mvnw -q install -DskipTests

# Spike'ın tamamı: testler, Error Prone, Spotless, Modulith, ArchUnit
./mvnw -Pspikes -pl spikes/s1-tenancy verify
```

Beklenen: `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0` ve `BUILD SUCCESS`. Süre Faz 0B'de 45 saniye (yaklaşık 32 saniyesi fixture'ın konteyneri açıp veritabanlarını kurması), Faz 0C doğrulamasında 14 saniye ölçülmüştür. Fixture maliyetini onu ilk kullanan test sınıfı taşır.

Tek bir test sınıfı ve biçimlendirme:

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=TenantRoutingTests
# Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
```

`-Pspikes` unutulursa Maven modülü bulamaz:

```text
[ERROR] Could not find the selected project in the reactor:
spikes/s1-tenancy -> [Help 1]
```

Hata ayıklama için test raporları `spikes/s1-tenancy/target/surefire-reports/` altındadır. Testler bağlam önbelleğini paylaşır (`@SpikeTest` tek bir bağlam açar); açılış ve kapanışı gözlemleyen testler (`StartupIsolationTests`) bağlamı önbellek dışında `SpikeContexts.start(...)` ile açar ve ayarları komut satırı argümanı olarak verir, çünkü `SpringApplicationBuilder.properties()` ile verilen değerleri `application.yaml` ezer (Bölüm 9).

#### S2 kimlik spike'ı

İlk çalıştırma `quay.io/keycloak/keycloak:26.8.0` imajını çeker. Keycloak ve PostgreSQL konteynerleri test çalışması başına bir kez açılır.

```bash
./mvnw -q install -DskipTests
./mvnw -Pspikes -pl spikes/s2-identity verify
```

Beklenen: `Tests run: 127, Failures: 0, Errors: 0, Skipped: 0` ve `BUILD SUCCESS`. Faz 0C doğrulamasında süre 24 saniyedir (imajlar önbellekteyken). Fixture'ları ilk kullanan test sınıfı konteynerlerin açılışını ve Keycloak kurulumunu taşır; doğrulama çalışmasında bu `SessionWiringTests` olmuş ve 15,6 saniye sürmüştür.

```bash
./mvnw -Pspikes -pl spikes/s2-identity test -Dtest=CsrfTests
# Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
./mvnw -Pspikes -pl spikes/s2-identity spotless:apply
```

`-Pspikes` unutulursa hata S1'deki gibidir (`Could not find the selected project in the reactor: spikes/s2-identity`).

Testler uygulamaya gerçek HTTP ile gider. Test tarayıcısı `SpikeBrowser`, uygulamaya Caddy'nin yaptığı gibi `http://127.0.0.1:<port>` adresine `Host: <tenant>.erp.test` ve `X-Forwarded-Proto: https` başlıklarıyla, Keycloak'a ise doğrudan bağlanır; Keycloak'ın giriş formunu doldurur ve host başına çerez kavanozu tutar. JDK HTTP istemcisi `Host` başlığını varsayılan olarak yasakladığından Surefire'a `-Djdk.httpclient.allowRestrictedHeaders=host` verilir (`spikes/s2-identity/pom.xml`). Açılış ve kapanışı gözlemleyen testler (`StartupIsolationTests`, `KeycloakUnavailableTests`) S1'deki gibi `SpikeContexts.start(...)` kullanır. Test raporları `spikes/s2-identity/target/surefire-reports/` altındadır.

## 4. Kalite kapıları: build'i ne kırar

Kırmızı çizgilerin (K1–K15) tanımı §3.2'dedir; denetim durumunun depo içi özeti `docs/guides/coding-rules.md`'dedir. Bu bölüm her kuralın kodda nerede zorunlu kılındığını ve ihlalde hangi hatanın görüldüğünü gösterir.

> ⚠️ Bugün hiçbir kapı kendiliğinden çalışmaz: git hook'u yoktur ve GitLab CI hattı henüz hiç çalışmamıştır (Bölüm 5.3). Kapılar yalnızca geliştirici `./mvnw verify`, frontend zinciri, `gitleaks` ve `smoke.sh` komutlarını çalıştırdığında devreye girer. Bu nedenle Bölüm 8.5'teki komut zinciri her commit öncesi çalıştırılmalıdır.

### 4.1 Zorunlu kırmızı çizgiler ve kurallar

Faz 0A deponun ilk fazı olduğu için aşağıdaki kuralların hepsi o fazda zorunlu hale gelmiştir (✅). Faz 0B ve Faz 0C ürün build'ine yeni bir kural eklememiştir; bu fazlarda zorunlu hale gelenler yalnızca `-Pspikes` ile çalışan spike kapılarıdır (Bölüm 4.7 ve 4.8). Faz 0C ile birlikte ayrıca kılavuz denetimi betiği eklenmiştir (Bölüm 4.2).

| Kural | Neden | Nerede zorunlu | İhlalde görülen hata | Faz 0A |
|---|---|---|---|---|
| K1: modüller arası erişim yalnızca açık paketler, olaylar ve view'lar üzerinden | §3.2, §5.4, ADR-0001 | `app/src/test/java/com/smart/erp/ModularityTests.java` (Spring Modulith `verify()`) | `Module 'x' depends on non-exposed type ... within module 'system'!` | ✅ |
| K9: repoda sır yok | §3.2, §10.2 | `.gitignore` (`.env*`), `.dockerignore`, gitleaks (`.gitleaks.toml`, CI işi `secrets:gitleaks`) | `leaks found: 1`, çıkış kodu 1 | ✅ |
| K10: harf dönüşümü `Locale.ROOT` ile | §3.2, §7.8, ADR-0027 | Kök `pom.xml` Error Prone `-Xep:StringCaseLocaleUsage:ERROR`; ArchUnit `ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION` (`ArchitectureRulesTests.k10NoLocaleLessCaseConversion`) | `[StringCaseLocaleUsage] Specify a Locale ...` ya da `Architecture Violation ... references method <java.lang.String.toUpperCase()>` | ✅ |
| K11: nesneler `==` ile karşılaştırılmaz | §3.2 | Kök `pom.xml` Error Prone `-Xep:ReferenceEquality:ERROR`, `-Xep:BoxedPrimitiveEquality:ERROR` | `[ReferenceEquality] Comparison using reference equality instead of value equality` | ✅ |
| K12: `@Scheduled` yasak | §3.2, §6.12, ADR-0014 | ArchUnit `ErpArchitectureRules.NO_SPRING_SCHEDULING` (`ArchitectureRulesTests.k12NoSpringScheduling`) | `Method ... is meta-annotated with @Scheduled` | ✅ |
| Backend üretim bağımlılığı lisansı | §3.1 madde 6, §12.3 | `app/pom.xml` `license-maven-plugin` yürütmesi `third-party-license-check` (`verify` aşaması) | `There are some forbidden licenses used, please check your dependencies.` | ✅ |
| Frontend üretim bağımlılığı lisansı | §3.1 madde 6, §12.3 | `web/scripts/check-licenses.mjs` (`pnpm licenses:check`) | `Disallowed licenses in production dependencies:` | ✅ |
| `v-html` yasak | §10.1 | `web/eslint.config.js` kuralı `vue/no-v-html` | `'v-html' directive can lead to XSS attack  vue/no-v-html` | ✅ |
| Vue bileşeni `script setup lang="ts"` | `docs/guides/coding-rules.md` | `web/eslint.config.js` kuralları `vue/block-lang`, `vue/component-api-style` | ESLint hatası (kural adıyla; alıştırması yapılmadı) | ✅ |

Ayrıntılar:

- K10 iki katmanlıdır. Error Prone doğrudan çağrıyı (`value.toUpperCase()`) derleme hatası yapar ama `String::toUpperCase` metot referansını yakalamaz; metot referansını ArchUnit kuralı yakalar. Bu açık Faz 0A içinde kapatılmıştır (commit `6bdfc02`); kural çağrıları ve referansları "erişim" olarak eşler.
- K12 meta-anotasyonla eşler: `@Scheduled`, `@Schedules` ve `@EnableScheduling`'i taşıyan birleşik anotasyonlar da yakalanır (commit `6bdfc02`).
- Her ArchUnit kuralı negatif bir fixture testiyle birlikte eklenir (`docs/guides/coding-rules.md`; süreç kuralıdır, kodla zorlanmaz): `platform/platform-test/src/test/java/com/smart/erp/platformtest/architecture/ErpArchitectureRulesTests.java` ve aynı klasördeki `fixtures/` sınıfları.
- Lisans denetimleri, çift lisanslı bir bağımlılığı alternatiflerinden biri izinliyse geçirir. Backend'de bu `license-maven-plugin`'in davranışıdır (ör. `logback-classic`, `LGPL-2.1-only` yerine `EPL-2.0` üzerinden geçer); frontend'de `web/scripts/license-expression.mjs` SPDX ifadesini değerlendirir: `OR` için alternatiflerden biri, `AND` için her bileşen izinli olmalıdır (commit `6ce92ce`).

### 4.2 Araç kapıları

| Kapı | Ne denetler | Nerede tanımlı | Komut | İhlalde görülen hata |
|---|---|---|---|---|
| Maven enforcer | Java 25 ve üstü, Maven 3.9.0 ve üstü, POM'da çift bağımlılık sürümü yok | Kök `pom.xml`, `enforce-toolchain` | `./mvnw verify` | `JDK 25 is required. Run mise install ...` |
| Error Prone 2.50.0 | Varsayılan hata desenleri ve K10/K11 | Kök `pom.xml`, `maven-compiler-plugin`; `.mvn/jvm.config` | `./mvnw compile` | `[DesenAdı] ...` derleme hatası |
| Spotless 3.10.3 + palantir-java-format 2.101.0 | Java biçimi, kullanılmayan import | Kök `pom.xml` (`check`, `verify` aşaması) | `./mvnw verify`; düzeltme `./mvnw spotless:apply` | `The following files had format violations:` |
| ArchUnit 1.5.1 | K10, K12 | `ErpArchitectureRules`, `ArchitectureRulesTests` | `./mvnw verify` | `Architecture Violation [Priority: MEDIUM] ...` |
| Spring Modulith 2.1.1 | Modül yapısı (K1) | `ModularityTests` | `./mvnw verify` | `org.springframework.modulith.core.Violations` |
| JUnit + Testcontainers | Davranış testleri, PostgreSQL 18.6 ile | `app/src/test`, `platform/platform-test/src/test` | `./mvnw verify` | `Tests run: ..., Failures: ...` |
| Prettier 3.9.9 | Frontend biçimi | `web/.prettierrc.json` | `pnpm format:check`; düzeltme `pnpm format` | `Code style issues found in the above file.` |
| ESLint 10.12.0 | Doğruluk kuralları (biçim kuralları `skip-formatting` ile kapalı) | `web/eslint.config.js` | `pnpm lint` | `✖ 1 problem (1 error, 0 warnings)` |
| vue-tsc 3.3.12 / tsc | Tip denetimi | Paketlerin `tsconfig.json` dosyaları | `pnpm typecheck` | `error TS2322: ...` |
| Vitest 5.0.3 ve `node --test` | Birim ve bileşen testleri, lisans ifadesi testleri | Paketlerin `vitest.config.ts` dosyaları, `web/scripts/license-expression.test.mjs` | `pnpm test` | `FAIL ... AssertionError` |
| Vite 8.3.2 | Üretim derlemesi | `web/apps/web/vite.config.ts` | `pnpm build` | Derleme hatası |
| Lisans (iki taraf) | Bkz. 4.1 | `app/pom.xml`, `web/scripts/check-licenses.mjs` | `./mvnw verify`, `pnpm licenses:check` | Bkz. 4.1 |
| gitleaks 8.30.1 | Tüm git geçmişinde sır | `.gitleaks.toml` | `gitleaks git --config .gitleaks.toml --redact .` | `leaks found: N` |
| pnpm `allowBuilds` | Derleme betiği olan bağımlılık için açık karar | `web/pnpm-workspace.yaml` | `pnpm install` | `ERR_PNPM_IGNORED_BUILDS` |
| Compose zorunlu değişkenleri | `.env` olmadan yığın başlamaz | `deploy/compose/compose.yaml` (`${VAR:?...}`) | `docker compose ... up` | `required variable POSTGRES_PASSWORD is missing a value` |
| Duman testi | Tek imaj + compose + tarayıcı | `deploy/compose/smoke.sh` | `deploy/compose/smoke.sh` | `Smoke test FAILED (exit N). Logs: ...` |
| Kılavuz denetimi | Bu kılavuzda DocBuilder kuralları (80 karakter, dil etiketi, H5, kod dışı HTML, emoji, tablo sütunu, Mermaid 9.1.7 ile çizim, ASCII sınıf ve ER adları) ve satır içi koddaki yolların depoda bulunması | `scripts/check-guide.mjs`, `scripts/check-guide.allow` | `mise x -- node scripts/check-guide.mjs` | `HATA  docs/guides/gelistirici-kilavuzu.md:<satır> [missing-path] Depoda yok: ...`, çıkış kodu 1 |

Kılavuz denetimi dışındaki kapıların tamamı Faz 0A'da eklenmiştir. Kılavuz denetimi Faz 0B ile 0C arasında `main`'e eklenmiştir (commit `e8d84c8`); yalnızca bu kılavuz değiştiğinde elle çalıştırılır, CI'da yoktur. Kodda bulunamayan CamelCase adları hata değil uyarı olarak listeler; bilerek depoda olmayan bir yol (ör. "kendin dene" dosyası) gerekçesiyle `scripts/check-guide.allow`'a eklenir. Testcontainers testleri çalışan bir Docker gerektirir.

### 4.3 Henüz zorunlu olmayan K kuralları

| Kural | Özet (§3.2) | Planlanan denetim | Ne zaman |
|---|---|---|---|
| K1 (ek) | Modulith dışındaki ek denetimler | ArchUnit ek kuralları; jOOQ codegen kapsamı (§3.2) | Faz 1 (ArchUnit) |
| K2 | Jenerik modülde sektör kavramı yok | Kod incelemesi, paket isim kuralları | Süreç |
| K3 | Bir iş işlemi = tek komut + tek transaction | ArchUnit (`@Transactional` application katmanında) | Faz 1 |
| K4 | Kesinleşmiş belge ve ledger içerik kolonları değişmez | DB trigger + test | Faz 5 |
| K5 | Para, miktar, oran ve kur için `float`/`double` yok | ArchUnit | Faz 1 |
| K6 | Çekirdek iş verisi jsonb'de değil | Metadata doğrulayıcı | Faz 4 |
| K7 | Her tabloda `@Version`, güncellemede If-Match | Kernel temel sınıfı + ArchUnit | Faz 1 |
| K8 | Müşteriye özel build, dal, fork yok | Süreç | Süreç |
| K13 | Liste uç noktaları her zaman sayfalı | ArchUnit + API testleri | Faz 1 |
| K14 | Entity'ler API'de serileştirilmez | ArchUnit | Faz 1 |
| K15 | Kişisel veri alanları işaretli, logda yok | Metadata doğrulayıcı, log maskeleme | Faz 4 |

Faz sütunu `docs/guides/coding-rules.md`'den alınmıştır. K9'un "entegrasyon kimlik bilgileri şifreli saklanır" kısmının uygulanacağı bir entegrasyon henüz yoktur.

S1 spike'ı K3 ve K7'nin kalıbını örnek kodla göstermiştir (`SampleService.record`: tek komut, tek transaction; `SampleRecord`: `@Version Long`), ancak bunları denetleyen bir kural yazmamıştır. Bulgu raporu Faz 1 için ayrıca şu kapıları önerir (`docs/spikes/s1-tenancy.md`, "Faz 1 için girdiler"): açılış ve kapanış izolasyon testinin kalıcı kalite kapısı olması (B9, web katmanı dahil) ve `@GeneratedValue`/`@SequenceGenerator` kullanımını yasaklayan bir ArchUnit kuralı (§4.5'teki havuzlu sequence optimizer tuzağı, ADR-0015).

S2 bulgu raporu Faz 1 için şu kapıları ekler (`docs/spikes/s2-identity.md`, "Faz 1 için girdiler"): uygulamanın `TransactionTemplate`'inin varsayılan DataSource'ta kaldığını doğrulayan `SessionWiringTests` (S2 B7); giriş iddia kontrolü ve callback tekrar oynatma regresyon testi (S2 B3); tam giriş ve bearer çağrısını içeren, JPA, jOOQ ve Modulith'le birlikte servlet açılış izolasyon testi (S2 B25, S1 açık madde 1); host ile oturum kontrolünün testi (S2 B14); bearer zincirinin oturuma dokunmadığını `last_access_time` ve `Set-Cookie` üzerinden doğrulayan test (S2 B22). Rapora göre bu kapılar statü kodunun yanında ayırt edici bir sinyal de doğrulamalıdır: başka tenant'ın oturumuyla gelen isteğin 401'i oturumsuz isteğin 401'iyle aynı yanıttır.

### 4.4 Kendin dene: backend

Her alıştırma bir kuralı bilerek ihlal eder, beklenen hatayı gösterir ve değişikliği geri alır. Alıştırma sonunda `git status --short` boş olmalıdır. Hata çıktıları satır kırılarak ve `...` ile kısaltılarak alıntılanmıştır. Komutlar `clean` içerir (Bölüm 9).

#### K10: parametresiz harf dönüşümü (Error Prone)

`app/src/main/java/com/smart/erp/system/GuideCheck.java` dosyası oluşturulur:

```java
package com.smart.erp.system;

class GuideCheck {

    String upper(String value) {
        return value.toUpperCase();
    }
}
```

```bash
./mvnw -q clean compile -pl app -am
```

```text
[ERROR] .../GuideCheck.java:[6,33] [StringCaseLocaleUsage] Specify a
`Locale` when calling `String#to{Lower,Upper}Case`. ...
```

Geri alma: `rm app/src/main/java/com/smart/erp/system/GuideCheck.java`

#### K10 ve K12: metot referansı ve `@Scheduled` (ArchUnit)

Aynı yola şu sınıf yazılır. Metot referansı Error Prone'dan geçer, ArchUnit'e takılır:

```java
package com.smart.erp.system;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;

class GuideCheck {

    List<String> upper(List<String> values) {
        return values.stream().map(String::toUpperCase).toList();
    }

    @Scheduled(fixedDelay = 1000)
    void tick() {}
}
```

```bash
./mvnw clean test -pl app -am -Dtest=ArchitectureRulesTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

```text
Method <com.smart.erp.system.GuideCheck.tick()> is meta-annotated with
@Scheduled in (GuideCheck.java:...)
Method <com.smart.erp.system.GuideCheck.upper(java.util.List)> references
method <java.lang.String.toUpperCase()> in (GuideCheck.java:...)
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
```

Geri alma: `rm app/src/main/java/com/smart/erp/system/GuideCheck.java`

#### K11: referans eşitliği (Error Prone)

```java
package com.smart.erp.system;

import java.math.BigDecimal;

class GuideCheck {

    boolean sameCount(Integer a, Integer b) {
        return a == b;
    }

    boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == b;
    }
}
```

```bash
./mvnw -q clean compile -pl app -am
```

```text
[ERROR] .../GuideCheck.java:[8,18] [BoxedPrimitiveEquality] Comparison
using reference equality instead of value equality. ...
[ERROR] .../GuideCheck.java:[8,18] [ReferenceEquality] Comparison using
reference equality instead of value equality
[ERROR] .../GuideCheck.java:[12,18] [ReferenceEquality] Comparison using
reference equality instead of value equality
```

`Integer` karşılaştırması (satır 8) iki desene birden takılır; toplam üç hata görülür.

Geri alma: `rm app/src/main/java/com/smart/erp/system/GuideCheck.java`

#### K1: başka modülün iç paketine erişim (Spring Modulith)

Yeni bir modül paketi açılır ve `system` modülünün iç paketindeki bir tipi kullanır. Dosya: `app/src/main/java/com/smart/erp/guidecheck/GuideCheck.java`

```java
package com.smart.erp.guidecheck;

import com.smart.erp.system.web.SystemInfoResponse;

public class GuideCheck {

    public SystemInfoResponse peek() {
        return new SystemInfoResponse("x", "onprem");
    }
}
```

```bash
./mvnw clean test -pl app -am -Dtest=ModularityTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

```text
org.springframework.modulith.core.Violations:
- Module 'guidecheck' depends on non-exposed type
  com.smart.erp.system.web.SystemInfoResponse within module 'system'!
```

Geri alma: `rm -r app/src/main/java/com/smart/erp/guidecheck`

#### Spotless: biçim ihlali

`app/src/main/java/com/smart/erp/system/DeploymentMode.java` içinde `    SAAS,` satırının girintisi silinir.

```bash
./mvnw -q spotless:check -pl app
```

```text
[ERROR] Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:
3.10.3:check ... The following files had format violations:
[ERROR]     src/main/java/com/smart/erp/system/DeploymentMode.java
```

Geri alma: `./mvnw -q spotless:apply -pl app` (dosyayı düzeltir; `git status --short` boş kalır).

#### Enforcer: yanlış JDK

mise etkin olmayan bir kabukta (sistem `java` 17 iken) çalıştırılır:

```bash
./mvnw validate
```

```text
[ERROR] Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion
failed with message:
[ERROR] JDK 25 is required. Run `mise install`
(docs/guides/local-development.md).
```

Geri alma gerekmez; komut mise ile ya da `mise x --` önekiyle çalıştırılır.

#### Lisans izinli listesi (backend)

`app/pom.xml` içindeki `<includedLicenses>` değerinden `MIT|` silinir (izinli listeden bir lisans ailesinin çıkması, izinsiz bir bağımlılık eklenmesiyle aynı sonucu verir).

```bash
./mvnw clean verify -pl app -am -DskipTests
```

```text
[WARNING] There are 1 forbidden licenses used:
[WARNING] License: 'MIT' used by 1 dependencies:
[ERROR] Failed to execute goal org.codehaus.mojo:license-maven-plugin:2.7.1:
add-third-party (third-party-license-check) on project erp-app: There are
some forbidden licenses used, please check your dependencies.
```

Geri alma: `git checkout -- app/pom.xml`

### 4.5 Kendin dene: frontend

Komutlar `web/` klasöründe çalıştırılır.

#### ESLint: `v-html`

`web/apps/web/src/App.vue` içindeki başlık satırı şu hale getirilir:

```vue
<h1 class="text-h5" v-html="$t('shell.welcome')"></h1>
```

```bash
pnpm lint
```

```text
  8:27  error  'v-html' directive can lead to XSS attack  vue/no-v-html
✖ 1 problem (1 error, 0 warnings)
```

Geri alma: `git checkout -- apps/web/src/App.vue`

#### Prettier: biçim

`web/packages/core/src/system.ts` içindeki `from './http'` ifadesi çift tırnağa çevrilir (`from "./http"`).

```bash
pnpm format:check
```

```text
[warn] packages/core/src/system.ts
[warn] Code style issues found in the above file. Run Prettier with --write
to fix.
```

Geri alma: `pnpm format` (ya da `git checkout -- packages/core/src/system.ts`).

#### TypeScript: tip hatası

`web/packages/core/src/system.ts` sonuna (20 satırlık dosyanın ardından, boş satır bırakmadan) eklenir:

```typescript
export const broken: number = 'x'
```

```bash
pnpm typecheck
```

```text
packages/core typecheck: src/system.ts(21,14): error TS2322: Type 'string'
is not assignable to type 'number'.
```

Araya boş satır bırakılırsa satır numarası `22` olur.

Geri alma: `git checkout -- packages/core/src/system.ts`

#### Vitest: Türkçe karakter

`web/packages/shell/src/i18n/tr.json` içinde `"Sürüm"` değeri `"Surum"` yapılır.

```bash
pnpm --filter @erp/shell test
```

```text
 FAIL  src/ErpShell.test.ts > ErpShell > shows the backend version ...
AssertionError: expected 'ERPSurum: 1.2.3içerik' to contain 'Sürüm'
```

Geri alma: `git checkout -- packages/shell/src/i18n/tr.json`

#### Lisans izinli listesi (frontend)

`web/scripts/check-licenses.mjs` içindeki `ALLOWED` kümesinden `'MIT',` satırı silinir.

```bash
pnpm licenses:check
```

```text
Disallowed licenses in production dependencies:
  @babel/helper-string-parser@7.29.7 (MIT)
  ...
```

Geri alma: `git checkout -- scripts/check-licenses.mjs`

#### Paket sınırı: bildirilmemiş bağımlılık

`web/packages/ui/src/guidecheck.ts` oluşturulur (`@erp/ui`, `@erp/core`'u bildirmemiştir):

```typescript
import { getJson } from '@erp/core'
export const g = getJson
```

```bash
pnpm --filter @erp/ui typecheck
```

```text
src/guidecheck.ts(1,25): error TS2307: Cannot find module '@erp/core' or
its corresponding type declarations.
```

Geri alma: `rm packages/ui/src/guidecheck.ts`

#### pnpm 12 `allowBuilds`

`web/pnpm-workspace.yaml` içindeki `vue-demi: false` satırı silinir.

```bash
pnpm install --frozen-lockfile
```

```text
Error: ERR_PNPM_IGNORED_BUILDS
  × installing dependencies
  ╰─▶ Ignored build scripts: vue-demi@0.14.10
```

Geri alma: `git checkout -- pnpm-workspace.yaml`, ardından `pnpm install --frozen-lockfile`.

### 4.6 Kendin dene: sır ve yapılandırma

#### gitleaks: sahte token

Depo dışında geçici bir klasörde rastgele, sahte bir GitHub token biçimi üretilir (dokümana gerçek görünümlü token yazılmaz):

```bash
D=$(mktemp -d)
T=$(LC_ALL=C tr -dc 'a-zA-Z0-9' </dev/urandom | head -c 36)
printf 'token = "ghp_%s"\n' "$T" > "$D/fake.txt"
gitleaks dir --redact --no-banner "$D"; echo "exit=$?"
```

```text
WRN leaks found: 1
exit=1
```

`-v` eklenirse `RuleID: github-pat` görülür. Geri alma: `rm -rf "$D"`

#### Compose: `.env` olmadan başlamama

`.env` silinmeden, compose'a boş bir env dosyası verilerek denenir:

```bash
docker compose -f deploy/compose/compose.yaml --env-file /dev/null config
```

```text
error while interpolating services.postgres.environment.POSTGRES_PASSWORD:
required variable POSTGRES_PASSWORD is missing a value: run
deploy/compose/init-env.sh first
```

Çıkış kodu 1'dir; geri alma gerekmez. Docker Compose v5.1.4 eksik değişkenlerden ilk rastladığını raporlar ve bu sıra çalıştırmadan çalıştırmaya değişir: mesajda `POSTGRES_PASSWORD` yerine `ERP_PLATFORM_DB_PASSWORD` ya da `KEYCLOAK_DB_PASSWORD` (farklı servis yollarıyla) da görülebilir.

### 4.7 S1 spike'ının kapıları

Spike modülü `erp-parent`'tan miras aldığı için Error Prone (K10, K11), Spotless ve enforcer onda da çalışır. Bunlara ek olarak spike'ın kendi testleri aşağıdaki kapıları kurar. Hepsi Faz 0B'de eklenmiştir ve yalnızca `./mvnw -Pspikes -pl spikes/s1-tenancy verify` ile çalışır; ürün build'i ve CI bunları çalıştırmaz.

| Kapı | Neden | Nerede | İhlalde görülen hata | Faz 0B |
|---|---|---|---|---|
| Açılış ve kapanışta tenant'sız bağlantı isteği yok | §4.4 madde 4, §4.5, ADR-0015; bulgu B9 | `StartupIsolationTests` (`rejectedWithoutTenant()` sıfır, açık havuz yok) | `MissingTenantContextException` ile açılış hatası ya da sayaç sıfır değil | ✅ |
| Sağlık uçları tenant'a dokunmaz | B8 | `HealthTests` | Sayaç artar ya da `components.db.status` `UP` değil | ✅ |
| Transaction içinde tenant değiştirilemez | §4.4 madde 7, B12 | `TenantContextTests`, `TransactionGuardTests` | `Expecting code to raise a throwable.` | ✅ |
| K1: spike modül yapısı | §5.4 | Spike `ModularityTests` | `org.springframework.modulith.core.Violations` | ✅ |
| K10, K12 (ArchUnit) | §3.2 | Spike `ArchitectureRulesTests` (`ErpArchitectureRules` kuralları) | `Architecture Violation ...` | ✅ |
| İzolasyon: satır yalnızca bağlı tenant'ın DB'sine gider | ADR-0003 | `TenantRoutingTests`, `SamplePersistenceTests`, `TransactionGuardTests` (süper kullanıcı tanığıyla); `JooqReadTests` | Tanık sorgusunda ya da okumada beklenmeyen satır | ✅ |

`StartupIsolationTests` ayrıca bir negatif kontrol içerir: `hibernate.boot.allow_jdbc_metadata_access=true` ile açılışın `Unable to determine Dialect without JDBC metadata` hatasıyla durduğunu doğrular (B6).

> ⚠️ `StartupIsolationTests` bağlamı web sunucusu olmadan (`WebApplicationType.NONE`) açar; Tomcat ve Web MVC açılışının tenant'sız bağlantı istemediği bu yüzden kanıtlanmamıştır. Bu, Faz 1'de kapatılacak açık maddedir (Bölüm 10.1).

#### Kendin dene: jOOQ diyalektini kaldırmak

`spikes/s1-tenancy/src/main/resources/application.yaml` içindeki `sql-dialect: POSTGRES` satırı silinir.

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=StartupIsolationTests
```

```text
[ERROR] Tests run: 3, Failures: 0, Errors: 2, Skipped: 0
... Error creating bean with name 'jooqConfiguration' ...
Factory method 'jooqConfiguration' threw exception with message:
No tenant is bound to this thread; tenant database access must run
inside TenantContext.run/call (doc §4.4)
```

Boot, diyalekt verilmezse açılışta bağlantı açıp diyalekti sorar (B5); routing DataSource tenant'sız isteği reddeder ve açılış durur. Geri alma: `git checkout -- spikes/s1-tenancy/src/main/resources/application.yaml`

#### Kendin dene: transaction korumasını gevşetmek

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantContext.java` içindeki `inTransactionScope()` yalnızca gerçek transaction'a bakacak hale getirilir (`|| TransactionSynchronizationManager.isSynchronizationActive()` kısmı silinir).

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test \
  -Dtest='TenantContextTests,TransactionGuardTests'
```

```text
TransactionGuardTests.switchingTenantInsideASupportsScope
  FailsInsteadOfWritingToTheFirstTenant ... <<< FAILURE!
Expecting code to raise a throwable.
TransactionGuardTests.jooqCannotReadAnotherTenantThrough
  ANotSupportedScope ... <<< FAILURE!
TenantContextTests.switchingTenantInsideASynchronizationOnlyScope
  IsRejected ... <<< FAILURE!
[ERROR] Tests run: 14, Failures: 3, Errors: 0, Skipped: 0
```

Bu, son incelemede bulunan hatanın kendisidir: `PROPAGATION_SUPPORTS` ve `NOT_SUPPORTED` kapsamlarında gerçek transaction olmadığı halde ilk bağlantı kapsama bağlanır ve korumasız sürümde ikinci tenant'ın yazması sessizce ilk tenant'a gider (B12, commit `117f78c`). Geri alma: `git checkout -- spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantContext.java`

### 4.8 S2 spike'ının kapıları

S2 modülü de `erp-parent`'tan Error Prone (K10, K11), Spotless ve enforcer'ı miras alır. Aşağıdaki kapılar Faz 0C'de eklenmiştir ve yalnızca `./mvnw -Pspikes -pl spikes/s2-identity verify` ile çalışır; ürün build'i ve CI bunları çalıştırmaz. Test sınıfları `spikes/s2-identity/src/test/java/com/smart/erp/spike/s2/` altındadır.

| Kapı | Neden | Nerede | İhlalde görülen hata | Faz 0C |
|---|---|---|---|---|
| Açılış, giriş, oturumlu ve bearer çağrısı, kapanış boyunca tenant DataSource'una istek yok | §4.3, ADR-0039; S2 B7, B25 | `StartupIsolationTests` (`TenantDataSourceStandIn.requests()` sıfır) | Giriş 500 ya da sayaç sıfır değil | ✅ |
| Spring Session'ın transaction kancası uygulamanınkini bastırmaz | S2 B7 | `SessionWiringTests` | `No bean named 'transactionTemplate' available` | ✅ |
| Girişte ID token'ın `iss`'i ve tam organization kümesi denetlenir | ADR-0005; S2 B2, B3 | `OrganizationClaimCheckTests`, `SessionTenantBindingTests` | `expected: 403 but was: 200` (ya da `302`) | ✅ |
| Başka tenant'ın oturumu bu host'ta geçmez | ADR-0006; S2 B14 | `SessionTenantBindingTests` | `expected: 401 but was: 200` | ✅ |
| Bearer isteği oturumu okumaz, yaratmaz, kimliğini döndürmez | ADR-0040; S2 B21–B23 | `BearerChainTests` | `Expecting empty but was: ["__Host-SESSION=...` | ✅ |
| CSRF oturuma bağlıdır; double-submit çerezi işe yaramaz | ADR-0006; S2 B15 | `CsrfTests`, `LogoutTests` | Beklenen 403 yerine 200 | ✅ |
| Keycloak yokken uygulama açılır ve hazırdır | S2 B6 | `KeycloakUnavailableTests`, `LazyClientRegistrationRepositoryTests` | Açılış hatası ya da readiness 503 | ✅ |
| K1, K10, K12 | §3.2 | Spike `ModularityTests`, `ArchitectureRulesTests` | `org.springframework.modulith.core.Violations`, `Architecture Violation ...` | ✅ |

Aşağıdaki alıştırmalar kılavuz hazırlanırken çalıştırılmış ve her biri `git checkout` ile geri alınmıştır. Komutların her biri Keycloak ve PostgreSQL konteynerlerini açtığı için doğrulama makinesinde 18–20 saniye sürmüştür. Dosya yolları `spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/identity/` altındadır.

#### Kendin dene: Spring Session'ın transaction kancasını kaldırmak

`SessionConfiguration.java` içindeki `@Bean(name = "springSessionTransactionOperations", defaultCandidate = false)` satırı silinir.

```bash
./mvnw -Pspikes -pl spikes/s2-identity test \
  -Dtest='StartupIsolationTests,SessionWiringTests'
```

```text
Caused by: com.smart.erp.spike.s2.kernel.MissingTenantContextException:
No tenant is bound to this thread; ...
StartupIsolationTests.sessionsSecurityAndActuatorNeverAskForThe
  TenantDataSource:31
expected: "ok"
 but was: "<html><body><h1>Whitelabel Error Page</h1>... status=500 ...
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
```

Kanca yokken Spring Session bağlamdaki tek `PlatformTransactionManager`'ı, yani Boot'un varsayılan (tenant) DataSource üzerindeki yöneticisini alır ve ilk oturum kaydı tenant DataSource'una gider. Yer tutucu bağlantıyı reddettiği için hata burada görünür; Faz 1'in gerçek routing DataSource'unda aynı hata sessiz kalır (S2 B7). `SessionWiringTests` bu durumda geçer: Boot'un `transactionTemplate`'i yerindedir. Geri alma: `git checkout -- spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/identity/SessionConfiguration.java`

#### Kendin dene: kancayı varsayılan aday yapmak

Aynı satırdaki `, defaultCandidate = false` kısmı silinir.

```bash
./mvnw -Pspikes -pl spikes/s2-identity test -Dtest=SessionWiringTests
```

```text
[ERROR]   SessionWiringTests.applicationTransactionsStayOnTheDefault
  DataSource:31 » NoSuchBeanDefinition No bean named
  'transactionTemplate' available
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
```

Boot `transactionTemplate`'i `@ConditionalOnMissingBean(TransactionOperations.class)` ile kurar; varsayılan aday bir `TransactionOperations` onu bastırır ve uygulama kodu platform DB'sinin şablonunu alır. Geri alma: `git checkout -- spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/identity/SessionConfiguration.java`

#### Kendin dene: girişte yalnızca issuer'ı denetlemek

`TenantOidcUserService.java` içindeki koşul `if (!sameIssuer)` haline getirilir (organization kümesi karşılaştırması silinir).

```bash
./mvnw -Pspikes -pl spikes/s2-identity test \
  -Dtest='OrganizationClaimCheckTests,SessionTenantBindingTests'
```

```text
SessionTenantBindingTests.callbackReplayedOnAnotherHostDoesNotLogIn
expected: 403
 but was: 302
OrganizationClaimCheckTests.memberOfAnotherOrganizationCannotLogInHere
expected: 403
 but was: 200
[ERROR] Tests run: 8, Failures: 5, Errors: 0, Skipped: 0
```

Dört `OrganizationClaimCheckTests` testinin hepsi (kurcalanmış, joker ve silinmiş ipucu, üye olmayan kullanıcı) ile callback tekrar oynatma testi kırılır: kontrol yokken Keycloak bu girişleri reddetmez, globex üyesi acme'de oturum açar (S2 B1–B3). Geri alma: `git checkout -- spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/identity/TenantOidcUserService.java`

#### Kendin dene: bearer zincirinden `SessionlessRequestFilter`'ı çıkarmak

`SecurityConfiguration.java` içindeki `.addFilterBefore(new SessionlessRequestFilter(), DisableEncodeUrlFilter.class)` satırı silinir.

```bash
./mvnw -Pspikes -pl spikes/s2-identity test -Dtest=BearerChainTests
```

```text
BearerChainTests.bearerRequestIgnoresAnySessionCookie:186 [Set-Cookie]
Expecting empty but was: ["__Host-SESSION=...; Path=/; Secure;
HttpOnly; SameSite=Lax"]
BearerChainTests.invalidBearerTokenDoesNotFallBackToTheSession:142
[the session was not read]
[ERROR] Tests run: 13, Failures: 3, Errors: 0, Skipped: 0
```

Üçüncü hata `errorDispatchIsServedWithoutTheSession` testidir. `SessionCreationPolicy.STATELESS` Spring Session arkasında durumsuzluk vermez: çerezdeki oturum yüklenir, boşta kalma süresi uzar ve geçerli token'da oturum kimliği döndürülür (S2 B22). Geri alma: `git checkout -- spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/identity/SecurityConfiguration.java`

#### Kendin dene: host ile oturum kontrolünü kaldırmak

`BrowserTenantFilter.java` içindeki `sessionBelongsTo` metodundan `&& user.tenant().equals(hostTenant)` kısmı silinir.

```bash
./mvnw -Pspikes -pl spikes/s2-identity test \
  -Dtest=SessionTenantBindingTests
```

```text
SessionTenantBindingTests.oneUserGetsOneIndependentSessionPerTenant:67
expected: 401
 but was: 200
SessionTenantBindingTests.sessionCookieReplayedOnAnotherTenantsHostIs401
expected: 401
 but was: 200
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
```

Gerçek tarayıcı `__Host-` çerezini başka host'a göndermez; bu kontrol derinlemesine savunmadır ve test ancak çerez kavanozu taşınarak kurulabilir (S2 B14). Geri alma: `git checkout -- spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/identity/BrowserTenantFilter.java`

## 5. Paketleme, CI, dağıtım ve duman testi

Paketleme ilkeleri §11.1, yürüyen iskelet yaklaşımı §11 girişindedir: Faz 0'dan itibaren her commit on-prem ile aynı compose paketiyle ayağa kaldırılır.

### 5.1 Uygulama imajının derleme aşamaları

`deploy/docker/app.Dockerfile` depo kökünden derlenir:

```bash
docker build -f deploy/docker/app.Dockerfile -t erp-app:local .
```

| Aşama | Taban imaj | Ne yapar | Çıktı |
|---|---|---|---|
| `web` | `node:24.21.0-bookworm-slim` | `corepack enable`; `web/` kopyalanır; `pnpm install --frozen-lockfile --filter "@erp/web..."` (pnpm deposu önbellek mount'unda); `pnpm --filter @erp/web build` | `web/apps/web/dist` |
| `backend` | `eclipse-temurin:25-jdk-noble` | `mvnw`, `pom.xml`, `.mvn`, `platform`, `app` ve SPA derlemesi kopyalanır; `./mvnw -B -ntp -pl app -am package -DskipTests` (Maven deposu önbellek mount'unda); `java -Djarmode=tools -jar ... extract --layers --launcher` | `/layers` (katmanlı jar) |
| Çalışma zamanı | `eclipse-temurin:25-jre-noble` | `erp` kullanıcısı (uid/gid 10001); katmanlar `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application` sırasıyla kopyalanır; `USER 10001:10001`, `EXPOSE 8080` | `erp-app:local` |

Giriş komutu `java -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError org.springframework.boot.loader.launch.JarLauncher`'dır. `ARG VERSION` (varsayılan `dev`) yalnızca `org.opencontainers.image.version` etiketine yazılır; uygulamanın bildirdiği sürüm Maven `build-info`'dan gelir (`0.1.0-SNAPSHOT`).

> ⚠️ İmaj derlemesinde testler atlanır (`-DskipTests`); testler CI'da `backend:verify` işinde çalışır. Yeni bir üst düzey Maven klasörü açıldığında (§14'teki modül, paket ve müşteri klasörleri) `backend` aşamasındaki `COPY` satırlarına eklenmelidir; Dockerfile'daki yorum bunu hatırlatır. `spikes/` klasörü bilerek kopyalanmaz: kök `pom.xml` ona yalnızca `spikes` profilinde atıf yapar ve imaj bu profil olmadan derlenir (Faz 0B ve 0C doğrulamalarında `smoke.sh` imajı yeniden derleyerek geçmiştir).

Doğrulama:

```bash
docker run --rm --entrypoint id erp-app:local
# uid=10001(erp) gid=10001(erp) groups=10001(erp)
F='{{.Config.User}} '
F+='{{index .Config.Labels "org.opencontainers.image.version"}}'
docker image inspect erp-app:local --format "$F"
# 10001:10001 dev
```

İmaj çalışma zamanında şu ortam değişkenleriyle yapılandırılır (compose `app` servisi): `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `ERP_DEPLOYMENT_MODE`.

### 5.2 Compose profilleri

| Profil | Servisler | Kullanım |
|---|---|---|
| (yok) | `postgres` | Yalnızca veritabanı |
| `dev` | `postgres`, `keycloak`, `mailpit` | Yerel geliştirme; uygulama IDE'den ya da `./mvnw` ile çalışır |
| `ci` | `postgres`, `app` (tek imaj) | Yürüyen iskelet; `smoke.sh` kullanır |
| `e2e` | `e2e` (Playwright koşucusu) | Yalnızca `smoke.sh` kullanır |

`app` servisi compose ağında `erp-app` takma adını da taşır. Chromium'un HSTS preload listesi `app` üst düzey alan adını kapsar ve `http://app` adresini zorla HTTPS'e çevirir; bu yüzden e2e koşucusu uygulamaya `http://erp-app:8080` ile ulaşır (`compose.yaml` yorumu, `docs/guides/ci.md`).

PostgreSQL servisi `erp-postgres:local` imajını kullanır: init betikleri bind mount yerine imaja gömülüdür, çünkü bind mount kaynağı Docker daemon'unun dosya sisteminde çözülür ve uzak bir daemon'da (CI'daki docker:dind) depo yoktur (commit `4587692`, `deploy/compose/postgres/Dockerfile`).

### 5.3 CI hattı

Tanım `.gitlab-ci.yml`'dedir; iş başına açıklama ve yerel karşılıklar `docs/guides/ci.md`'dedir.

> ⚠️ GitLab CI hattı hiç çalışmamıştır. Deponun uzak adresi (`origin`) GitHub'dır; CI sağlayıcısı §20 soru 5 ile açıktır (ADR-0028). Aşağıdaki işlerin yerel karşılıkları doğrulanmıştır; CI'ın kendisi doğrulanmamıştır. Faz 0 çıkış kriterinin "CI yeşil" maddesi bir GitLab projesi ve ayrıcalıklı (privileged) docker:dind runner'ı bekler.

```mermaid
flowchart LR
A[check<br>secrets:gitleaks] --> B[verify<br>backend:verify]
A --> C[verify<br>frontend:verify]
B --> D[package<br>image:build]
C --> D
D --> E[smoke<br>smoke:compose]
E --> F[sign<br>image:sign]
```

| Aşama | İş | Ne doğrular | Yerel karşılığı | Yerelde |
|---|---|---|---|---|
| check | `secrets:gitleaks` | Tüm git geçmişinde sır (`GIT_DEPTH: "0"`) | `gitleaks git --config .gitleaks.toml --redact .` | ✅ `no leaks found` (58 commit) |
| verify | `backend:verify` | Derleme, Error Prone, testler (Testcontainers, dind), Modulith, ArchUnit, Spotless, lisans; SBOM ve `THIRD-PARTY.txt` artifact | `./mvnw verify` | ✅ `BUILD SUCCESS` |
| verify | `frontend:verify` | Biçim, lint, tip, test, build, lisans | Bölüm 8.5'teki frontend zinciri | ✅ |
| package | `image:build` | Tek imaj; buildx ile SBOM ve provenance attestation'ı; digest `APP_IMAGE_DIGEST` olarak dotenv artifact'ına | `docker build -f deploy/docker/app.Dockerfile -t erp-app:local .` | ✅ (buildx, attestation ve push doğrulanmadı) |
| smoke | `smoke:compose` | `SKIP_BUILD=1` ile, etiketi değil digest'i (`$CI_REGISTRY_IMAGE/app@$APP_IMAGE_DIGEST`) sınar; digest boşsa hemen başarısız | `deploy/compose/smoke.sh` | ✅ `Smoke test passed.` |
| sign | `image:sign` | cosign imzası; yalnızca varsayılan dal ve tag | — | ❌ doğrulanmadı |

Hat kuralları: merge request pipeline'ları çalışır; açık MR'ı olan dal için ayrıca dal pipeline'ı çalışmaz; dal ve tag pipeline'ları çalışır. `DOCKER_HOST: tcp://docker:2375` ile TLS'siz dind kullanılır; TLS'e geçiş `docs/guides/ci.md`'dedir. CI değişkenleri `COSIGN_PRIVATE_KEY` (protected) ve `COSIGN_PASSWORD` (protected, masked) henüz tanımlı değildir; anahtar üretimi de `ci.md`'dedir.

Digest'in sınanması kararı commit `f1ba3c3` ile gelmiştir: imzalanan bayt dizisi ile duman testinden geçen bayt dizisinin aynı olması için `smoke:compose` değişebilir etiketi değil `image:build`'in ürettiği digest'i çalıştırır.

`.gitlab-ci.yml` spike'ları çalıştırmaz: `backend:verify` işi `-Pspikes` vermez. Spike testleri yalnızca yerelde, Bölüm 3.10'daki komutlarla çalışır; bu, plan 0B ve 0C'nin "spike varsayılan reaktörde değildir" kısıtıdır.

### 5.4 Duman ve E2E testi

`deploy/compose/smoke.sh` akışı:

```mermaid
flowchart TD
A[init-env.sh<br>.env yoksa üret] --> B[build postgres<br>gerekirse build app]
B --> C[up -d --no-build<br>postgres app]
C --> D[docker build<br>erp-smoke-e2e]
D --> E[compose run --no-deps<br>e2e]
E --> F[cleanup: günlükler,<br>raporlar, down --volumes]
```

- Ayrı compose projesi (`--project-name erp-smoke`) ve `compose.smoke.yaml` (host portu yok) kullanılır; bu yüzden çalışan dev yığınıyla port çakışmaz ve dev volume'una dokunmaz. Temizlikteki `down --volumes` yalnızca `erp-smoke` projesinin volume'unu siler.
- e2e imajı compose ile değil doğrudan `docker build` ile ve compose'un vereceği adla (`erp-smoke-e2e`) üretilir; `compose build e2e`, `depends_on` nedeniyle `app`'i de yeniden derleyip `$ERP_APP_IMAGE`'ın üzerine yazardı. Aynı nedenle e2e `run --no-deps` ile çalışır (commit `4587692`, `smoke.sh` yorumları).
- Playwright koşucusu `mcr.microsoft.com/playwright:v1.63.0-noble` imajıdır; etiket `web/pnpm-workspace.yaml` içindeki `@playwright/test` sürümüyle aynı olmalıdır (Renovate ikisini birlikte günceller).
- `web/e2e/global-setup.ts`, `/actuator/health` `UP` olana kadar en fazla 180 saniye bekler.
- `web/e2e/tests/smoke.spec.ts` iki test içerir: sağlık `UP`; imajdan sunulan SPA'da başlık `ERP`, `Sürüm` metni (Türkçe karakterlerin derleme → jar → imaj → tarayıcı yolunda bozulmadığının kanıtı), API'deki sürümle aynı sürüm ve `backend-unavailable` uyarısının yokluğu. Tarayıcı yereli `tr-TR`'dir.
- Çıktılar `deploy/compose/smoke-logs/` altına yazılır: `compose.log`, `playwright-report/`, `reports/junit.xml`, `test-results/` (ör. `deploy/compose/smoke-logs/reports/junit.xml`).

```bash
deploy/compose/smoke.sh
# Running 2 tests using 1 worker
#   2 passed
# Smoke test passed.

# Önceden derlenmiş bir imajı sınamak (CI biçimi)
ERP_APP_IMAGE=erp-app:local SKIP_BUILD=1 deploy/compose/smoke.sh
```

Her iki biçim de Faz 0A'da doğrulanmıştır (`SKIP_BUILD=1` ile yalnızca `erp-postgres:local` derlenir); Faz 0B ve 0C'de ilk biçim yeniden çalıştırılmış ve geçmiştir. Doğrulama makinesinde Docker katman önbelleği doluyken süre 20–30 saniyedir (Faz 0B'de 21, Faz 0C'de 30 saniye); ilk çalıştırma imaj indirme ve derleme nedeniyle daha uzun sürer (ölçülmedi). Registry'deki bir digest ile çalıştırma (CI biçimi) yerelde doğrulanmamıştır.

### 5.5 Migration ve yükseltme akışı

Ürün uygulamasında henüz yoktur. Uygulamada Flyway, migration dosyası ve şema yoktur; `erp_platform` veritabanı boştur. Migration kuralları §4.6 ve §7.9'da, orkestratör ADR-0038'dedir. On-prem güncelleme (`erpctl update`, §11.2) Faz 7'ye aittir.

S1 spike'ı §4.6'daki "migration ayrı adımdır" ilkesini sınamıştır:

- Migration dosyaları iki konumdadır: `db/platform` (platform DB) ve `db/tenant` (her tenant DB'si). Adlar `V<yyyyMMddHHmm>__<açıklama>.sql` biçimindedir (ör. `V202610051200__tenant_registry.sql`).
- Migration'ları uygulama açılışı değil, test fixture'ı (`SpikeDatabases.migrate`) Flyway API'siyle çalıştırır; bu, `erpctl migrate`'in rolüdür.
- Açılışta şema kuran her şey kapalıdır: `spring.sql.init.mode: never`, `spring.jpa.hibernate.ddl-auto: none`, `spring.modulith.events.jdbc.schema-initialization.enabled: false` (B3, B4).
- Spike'ta `flyway-core` yalnızca test kapsamındadır. Boot 4'te Flyway otomatik yapılandırması yalnızca `spring-boot-flyway` sınıf yolundayken çalışır; Faz 2 orkestratörü Flyway API'sini doğrudan kullanır, `spring-boot-starter-flyway` eklenmez (B17).
- Flyway 12.4.0 (OSS) PostgreSQL 18.6'ya migration'ları sorunsuz uygulamıştır. Flyway/Liquibase lisans teyidi plan 0E'dedir.

S2 spike'ı aynı ilkeyi platform DB'si için izler: `db/platform` altındaki `V202610061200__tenant_registry.sql` (`tenant`, `tenant_domain`) ve `V202610061201__spring_session.sql` fixture tarafından uygulanır. İkincisi spring-session-jdbc 4.1.1'in PostgreSQL şema betiğinin (jar içindeki schema-postgresql.sql) birebir kopyasıdır ve Spring Session yükseltildiğinde yeniden kopyalanır. Spring Session'ın şema kurulumu kapalıdır (`spring.session.jdbc.initialize-schema: never`).

### 5.6 Sistemin ayakta olduğunu kanıtlayan komutlar

| Komut | Beklenen yanıt |
|---|---|
| `curl -s http://127.0.0.1:8080/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` (HTTP 200) |
| `curl -s http://127.0.0.1:8080/actuator/health/readiness` | `{"status":"UP"}` (veritabanı yoksa HTTP 503 ve `DOWN`) |
| `curl -s http://127.0.0.1:8080/actuator/health/liveness` | `{"status":"UP"}` |
| `curl -s http://127.0.0.1:8080/api/v1/system/info` | `{"version":"0.1.0-SNAPSHOT","deploymentMode":"onprem"}` |
| `curl -s http://127.0.0.1:8080/` | `index.html`, içinde `<title>ERP</title>` |
| `docker compose -f deploy/compose/compose.yaml --profile dev ps` | `postgres` `healthy`, `keycloak` `Up`, `mailpit` `healthy` |

## 6. Çalışma zamanı davranışı ve dayanıklılık

### 6.1 Hata modeli (RFC 9457 Problem Details)

Hedef hata modeli §8.3'tedir: `application/problem+json`, `type`, `title`, `status`, `code`, `detail`, `traceId` ve alan hataları (`errors`).

**Backend'de üretim: henüz yok.** Uygulamada Problem Details üreten bir yapılandırma ya da `@ControllerAdvice` yoktur; kernel'deki `ProblemDetail` desteği Faz 1 kapsamındadır (§15.3). Bugün hatalar Spring Boot'un varsayılan hata gövdesiyle döner:

```bash
curl -s -i -H 'Accept: application/json' \
  http://127.0.0.1:8080/api/v1/nope
```

```text
HTTP/1.1 404
Content-Type: application/json
{"timestamp":"...","status":404,"error":"Not Found","path":"/api/v1/nope"}
```

`Accept: application/problem+json` ile istendiğinde `Content-Type` `application/problem+json` olur ama gövde yine aynı varsayılan alanları (`timestamp`, `status`, `error`, `path`) taşır; `title`, `code` ve `traceId` yoktur.

**Önyüzde ayrıştırma: var.** `web/packages/core/src/http.ts` her başarısız çağrıyı `ApiError` olarak fırlatır:

| Durum | `ApiError.status` | `ApiError.problem` | `message` |
|---|---|---|---|
| Sunucuya ulaşılamadı (ağ hatası) | `0` | `undefined`; asıl hata `cause` içinde | `Network error` |
| 2xx olmayan yanıt, gövde JSON | HTTP kodu | Ayrıştırılan gövde (`ProblemDetail` olarak) | `problem.title`, yoksa `HTTP <kod>` |
| 2xx olmayan yanıt, gövde JSON değil (ör. proxy'nin HTML 502 sayfası) ya da bozuk JSON | HTTP kodu | `undefined` | `HTTP <kod>` |
| 2xx yanıt ama gövde JSON değil | HTTP kodu (ör. 200) | `undefined`; `cause` bir `SyntaxError` | `HTTP <kod>` |

Son satır Faz 0A içinde eklenmiştir (commit `e0306bc`): önceden 2xx bir HTML gövdesi ham `SyntaxError` fırlatıyordu. Bugünkü varsayılan Spring Boot gövdesinde `title` olmadığı için mesaj `HTTP 404` biçimindedir.

```typescript
// web/packages/core/src/http.ts:29-47 (satırlar kırıldı)
export async function getJson<T>(
  path: string, fetchFn: FetchFn = browserFetch): Promise<T> {
  let response: Response
  try {
    response = await fetchFn(path, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
  } catch (cause) {
    throw new ApiError(0, undefined, { cause })
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  try {
    return (await response.json()) as T
  } catch (cause) {
    throw new ApiError(response.status, undefined, { cause })
  }
}
```

Shell bu hatayı kullanıcıya şöyle yansıtır: `useSystemInfo()` sorgusu hata durumuna geçerse `ErpShell.vue` `data-testid="backend-unavailable"` olan bir `v-alert` ile "Sunucuya ulaşılamıyor. Lütfen biraz sonra tekrar deneyin." metnini gösterir ve sayfa içeriğini (`slot`) çizmeye devam eder. `web/apps/web/src/main.ts` içindeki `QueryClient` sorguları bir kez yeniden dener (`retry: 1`) ve pencere odağında yenilemez.

### 6.2 Bağımlılıklar kapalıyken ya da bozukken

| Bağımlılık ve durum | Uygulamanın davranışı | Kullanıcının gördüğü | Kanıt |
|---|---|---|---|
| PostgreSQL erişilemez | Uygulama açılır; `/actuator/health` ve `/readiness` 503 `DOWN`, `/liveness` 200, `/api/v1/system/info` 200 | Shell sürümü gösterir, uyarı yok | `DatabaseUnavailableTests`; imaj veritabanısız çalıştırılarak doğrulandı |
| PostgreSQL şifresi yanlış | Aynı | Aynı | `DatabaseWrongPasswordTests` |
| `dev` profili verilmeden yerel çalıştırma | Şifre boş; `readiness` 503 `DOWN`, `info` 200 | Aynı | Elle doğrulandı (Bölüm 3.4) |
| Keycloak kapalı | Etkisi yok; ürün uygulaması henüz Keycloak'a bağlanmıyor (S2 spike'ındaki davranış Bölüm 6.5'tedir) | — | Uygulama yapılandırmasında Keycloak yok |
| Mailpit kapalı | Etkisi yok; uygulama henüz posta göndermiyor | — | — |
| Backend erişilemez (ağ hatası) | `ApiError` (`status` 0) | "Sunucuya ulaşılamıyor" uyarısı, sayfa içeriği çizilir | `http.test.ts`, `ErpShell.test.ts` |
| Proxy HTML 502 sayfası ya da JSON olmayan gövde | `ApiError` (`problem` yok) | Aynı uyarı | `http.test.ts`, `ErpShell.test.ts`; Vite proxy'nin 502'si elle doğrulandı |
| Geçersiz `erp.deployment.mode` | Uygulama açılmaz: `APPLICATION FAILED TO START`, `Failed to bind properties under 'erp.deployment.mode'` | — | `DeploymentPropertiesTests`; imajda `ERP_DEPLOYMENT_MODE=cloud` ile doğrulandı |

Bu davranışı sağlayan yapılandırma `application.yaml`'dadır: sağlık probları açıktır (`management.endpoint.health.probes.enabled: true`) ve `readiness` grubu `readinessState` ile `db` göstergelerini içerir. `liveness` veritabanını içermez; böylece bir orkestratör veritabanı kesintisinde uygulamayı yeniden başlatmaz, yalnızca trafikten çıkarır. `server.shutdown: graceful` ile kapanışta süren istekler tamamlanır.

```yaml
# app/src/main/resources/application.yaml:16-27
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
```

Veritabanısız çalıştırmanın kanıtı (port 18080, dev yığınından bağımsız; `curl` komutlarından önce uygulamanın açılması için birkaç saniye beklenir):

```bash
docker run --rm -d --name erp-nodb -p 127.0.0.1:18080:8080 erp-app:local
curl -s -w ' [%{http_code}]\n' http://127.0.0.1:18080/actuator/health
# {"groups":["liveness","readiness"],"status":"DOWN"} [503]
curl -s -w ' [%{http_code}]\n' \
  http://127.0.0.1:18080/actuator/health/liveness
# {"status":"UP"} [200]
docker stop erp-nodb
```

Compose'da `app` servisi `postgres` servisinin `service_healthy` olmasını bekler. PostgreSQL sağlık denetimi TCP üzerinden (`pg_isready -h 127.0.0.1`) yapılır; init sırasındaki geçici sunucu yalnızca soket dinlediği için servis init betikleri bitmeden `healthy` olmaz (`compose.yaml` yorumu).

### 6.3 Review Focus maddeleri

Plan dosyalarındaki "Review Focus" maddeleri ve bugünkü karşılıkları.

#### Plan 0A (Zemin)

| # | Beklenen davranış | Sağlayan kod | Kanıtlayan test ya da doğrulama |
|---|---|---|---|
| 1 | Veritabanı yokken ya da şifre yanlışken uygulama açılır; `health` ve `readiness` 503 `DOWN`, `liveness` 200, `/api/v1/system/info` 200; SPA sürümü gösterir | `application.yaml` (probes, `readiness` grubu); `SystemInfoController` veritabanı kullanmaz | `DatabaseUnavailableTests`, `DatabaseWrongPasswordTests`; SPA tarafı `ErpShell.test.ts` (sürüm gösterimi). Veritabanısız uçtan uca bir E2E testi yoktur |
| 2 | Backend erişilemezken shell çökmez, "Sunucuya ulaşılamıyor" uyarısını gösterir, içerik çizilir | `http.ts` (`getJson`, `readProblem`), `ErpShell.vue` (`isError` → `v-alert`, `slot`) | `http.test.ts` (ağ hatası, HTML 502, bozuk JSON, 2xx JSON olmayan gövde), `ErpShell.test.ts` |
| 3 | `erp.deployment.mode` `saas`, `SaaS`, `onprem`, `on-prem` ile kabul edilir; bilinmeyen ya da boş değerde açık bir hatayla açılmaz | `DeploymentProperties` (`@Validated`, `@NotNull`), `DeploymentMode` enum bağlaması | `DeploymentPropertiesTests` (bilinen dört yazım; `cloud`, boş, yalnızca boşluk, eksik) |
| 4 | `.env` yokken `init-env.sh` rastgele sır üretir, var olana dokunmaz; compose eksik değişkenle başlamaz; duman testi dev volume'unu silmez ve port çakışmasına girmez | `init-env.sh` (`-f .env` kontrolü), `compose.yaml` (`${VAR:?...}`), `smoke.sh` (`--project-name erp-smoke`), `compose.smoke.yaml` (`ports: !reset []`) | Otomatik test yok; plan doğrulama adımları. Kılavuz hazırlanırken elle doğrulandı (Bölüm 3.2, 4.6, 5.4) |
| 5 | Kazara sır commit'i yakalanır; `.env` git'te yok sayılır; `Sürüm` imajdan sunulan SPA'da bozulmadan görünür | `.gitignore` (`.env*`), `.gitleaks.toml`, CI `secrets:gitleaks`; `smoke.spec.ts` | `git check-ignore -v deploy/compose/.env` → `.gitignore:23:.env*`; sahte token alıştırması (Bölüm 4.6); `smoke.spec.ts` `getByText('Sürüm')` |

#### Plan 0B (S1 tenancy spike'ı)

Kod yolları `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/` altındadır; test sınıfları aynı paket yapısıyla `src/test/java` altındadır.

| # | Beklenen davranış | Sağlayan kod | Kanıtlayan test | Durum |
|---|---|---|---|---|
| 1 | Tam açılış, `/actuator/health` ve kapanış boyunca routing DataSource'tan tenant'sız bağlantı istenmez (`rejectedWithoutTenant() == 0`); hatayı yutan çerçeve kodu da sayılır | `TenantRoutingDataSource` (sayaç; `unwrap`/`isWrapperFor` yönlendirmez), `application.yaml` açılış ayarları, `ModulithTenancySupport`, `TenancyConfiguration` (`defaultCandidate = false`) | `StartupIsolationTests`, `HealthTests`, `TenantRoutingDataSourceTests.wrapperIntrospectionIsAnsweredWithoutRouting` | ⚠️ Web sunucusuz açılışla kanıtlandı (Bölüm 4.7) |
| 2 | Biçimi bozuk tenant anahtarı DB araması ya da havuz açılmadan reddedilir; bilinmeyen ve askıdaki tenant'a havuz açılmaz | `TenantKey` (`[a-z][a-z0-9_]{1,29}`), `TenantRoutingDataSource.determineTargetDataSource`; `tenant` tablosundaki `check` kısıtı | `TenantKeyTests` (`Acme`, `ışık`, `acme;drop`, boş, 31 karakter dahil 11 geçersiz değer), `TenantRoutingDataSourceTests` (`anUnknownTenant...`, `aSuspendedTenant...`) | ✅ |
| 3 | Açık transaction içinde tenant değiştirmek hata verir, ilk tenant'a yazılmaz, işlem geri alınır | `TenantContext.call` ve `inTransactionScope()` | `TenantContextTests` (4 transaction testi), `TransactionGuardTests` (`REQUIRED`, `SUPPORTS`, `NOT_SUPPORTED`) | ⚠️ İstisna yutulursa geri alma garanti değil (B12, Bölüm 10.1) |
| 4 | Askıdaki ya da DB'si erişilemeyen tenant'ın işi görünür biçimde başarısız olur ve tekrar denenir; diğer tenant'lar devam eder; veri başka yere yazılmaz | `TenantExecutionInterceptor`, routing reddi, db-scheduler'ın `OneTimeTask` yeniden deneme davranışı; `TenantEventRepublisher` (tenant başına `try/catch`, `RepublishReport`) | `TenantJobTests.aJobForASuspendedTenantFailsVisiblyAndWaitsForARetry`, `TenantEventRepublisherTests.anUnreachableTenantIsReportedAndDoesNotStopTheOthers` | ✅ |
| 5 | Olayın ikinci teslimi işi ikinci kez planlamaz; havuzdaki thread önceki görevin tenant'ını taşımaz | `SampleRecordedListener` (`scheduleIfNotExists`, iş örneği kimliği = `eventId`), `TenantTaskDecorator`, `TenantContext` (kapsam bitince önceki değer) | `TenantJobTests` (`theEventIdIsTheJobInstanceId...`, `aRedeliveryAfterTheJobCompleted...`), `TenantTaskDecoratorTests.theWorkerDoesNotKeepTheTenantForTheNextTask` | ⚠️ Tekilleştirme yalnızca iş satırı dururken; iş tamamlandıktan sonraki teslim işi yeniden çalıştırır, işler idempotent olmalı (B15) |

#### Plan 0C (S2 kimlik spike'ı)

Kod yolları `spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/` altındadır; test sınıfları aynı paket yapısıyla `src/test/java` altındadır.

| # | Beklenen davranış | Sağlayan kod | Kanıtlayan test | Durum |
|---|---|---|---|---|
| 1 | İpucu başka bir organization'la ya da `organization:*` ile değiştirilir, silinir ya da kullanıcı üye değildir: giriş 403, kimliği doğrulanmış oturum oluşmaz | `TenantOidcUserService` (ID token'da `iss` ve tam organization kümesi), `SecurityConfiguration` (`loginFailed`: yönlendirme yerine 403) | `OrganizationClaimCheckTests` (4 test) | ✅ Reddi Keycloak değil uygulama verir (S2 B1, B2) |
| 2 | Çerez başka host'a taşınır, callback başka host'ta tekrar oynatılır ya da mali müşavirin iki oturumu karışır: 401 ya da 403, diğer tenant'ın bağlamı kurulmaz | `BrowserTenantFilter` (`sessionBelongsTo`), `TenantOidcUser` (`<tenant>:<sub>`), `TenantOidcUserService` | `SessionTenantBindingTests` (`sessionCookieReplayed...`, `callbackReplayedOnAnotherHostDoesNotLogIn`, `oneUserGetsOneIndependentSessionPerTenant`) | ✅ Callback tekrarını Spring değil iddia kontrolü durdurur (S2 B3) |
| 3 | Host varyantları (büyük harf, Türkçe `İ`, sondaki nokta, `_`, tam genişlikli karakter, IP literal, boş, kayıtsız host) normalleşir ya da 404 alır; varsayılan tenant yoktur; `X-Forwarded-Host`'un etkisi sabittir | `TenantHost.normalize`, `BrowserTenantFilter`, `server.forward-headers-strategy: native` | `TenantHostTests`, `BffLoginTests` (`unknownHostIs404AndStartsNoLogin`, `forwardedHostFromATrustedProxyDecidesTheTenant`) | ⚠️ Boot varsayılanında her özel ağ adresi güvenilen proxy'dir; `internal-proxies` Faz 1/7'de daraltılır (S2 B17) |
| 4 | Kardeş alt alan adından CSRF: basılmış double-submit çerezi, başka oturumun token'ı, token'sız çıkış 403 alır | Spring Security varsayılanı (`HttpSessionCsrfTokenRepository`, XOR maskesi), `/bootstrap` | `CsrfTests`, `LogoutTests.logoutWithoutTheTokenIs403AndKeepsTheSession` | ✅ |
| 5 | Geçerli ama yanlış bearer token: iddiasız, iki organization'lı, kayıtsız organization'lı, audience'sız, yabancı anahtarlı token 401; başka tenant'ın host'u 403; askıdaki tenant 503; geçersiz token oturum çerezine düşmez, oturum oluşmaz | `bearerChain` (`hasBearerToken`), `TenantJwtAuthenticationConverter`, `BearerTenantFilter`, `SessionlessRequestFilter`, `audiences: erp-api` | `BearerChainTests` (13 test), `TenantJwtAuthenticationConverterTests` | ⚠️ Token'sız çıplak `Authorization: Bearer` başlığı tarayıcı zincirine düşer (S2 B24, Faz 1) |

### 6.4 Tenant'a bağlı hata durumları (S1 spike'ı)

Ürün uygulamasında tenant olmadığı için bu davranışlar bugün yalnızca spike'ta vardır.

| Durum | Davranış | Kanıt |
|---|---|---|
| Bağlı tenant yokken tenant DB erişimi (JDBC, JPA, jOOQ) | `MissingTenantContextException` (unchecked, `IllegalStateException`); `rejectedWithoutTenant` sayacı artar | `TenantRoutingDataSourceTests`, `TenantRoutingTests.accessWithoutATenantFails`, `SamplePersistenceTests.repositoryAccessWithoutATenantFails`, `JooqReadTests.jooqWithoutATenantFails` |
| Kayıtta olmayan tenant | `TenantNotAvailableException` (`unknown tenant`), havuz açılmaz | `TenantRoutingDataSourceTests`, `TenantRoutingTests.anUnknownTenantIsRefused` |
| `ACTIVE` olmayan tenant (ör. `SUSPENDED`) | `TenantNotAvailableException` (durum adıyla), havuz açılmaz; işi başarısız sayılır, `last_failure` dolar, `consecutive_failures=1`, 5 dakika sonraya planlanır | `TenantRoutingTests.aSuspendedTenantIsRefused`, `TenantJobTests` |
| `ACTIVE` ama veritabanı yok ya da erişilemez | Bağlantı `connection-timeout` sonunda düşer (varsayılan 10 s, testlerde 2 s); yeniden yayın o tenant'ı `RepublishReport.failed` içinde raporlar, `WARN` günlüğü yazar ve diğerlerine devam eder | `TenantEventRepublisherTests.anUnreachableTenantIsReportedAndDoesNotStopTheOthers` |
| Transaction içinde başka tenant'a geçiş | `TenantSwitchInTransactionException`; istisna transaction sınırına kadar yayılırsa işlem geri alınır | `TransactionGuardTests` |
| Dinleyici başarısız ya da kapanışta tamamlanmamış yayın | Yayın tenant DB'sinde kalır; tenant dolaşan yeniden yayın onu kendi tenant'ında yeniden gönderir; kapanışta kayıt defteri sorgu yapmaz | `TenantEventRepublisherTests.aFailedPublicationIsResubmittedInItsOwnTenant`, `StartupIsolationTests` (kapanışta sayaç sıfır) |
| Geri alınan transaction | Olay yayınlanmaz, yayın kaydı yazılmaz | `EventPublicationTests.aRolledBackTransactionPublishesNothing` |

Sağlık: spike'ta `readiness` grubu `readinessState` ve `db` göstergelerini içerir; `management.health.db.ignore-routing-data-sources: true` ile `db` göstergesi yalnızca platform DB'sini denetler. Tenant DB'leri readiness konusu değildir (B8). Platform DB'si kapalıyken davranış spike'ta denenmemiştir.

### 6.5 Kimliğe bağlı hata durumları (S2 spike'ı)

Spike'ta Problem Details yoktur: tenant ve oturum kararları gövdesiz statü kodlarıyla, giriş hatası `text/plain` bir hata koduyla, bearer hataları Spring Security'nin `WWW-Authenticate` başlığıyla döner. `BrowserTenantFilter` statüleri doğrudan yazar, çünkü hata dispatch'i zincire tenant'sız yeniden girer (S2 B23).

| Durum | Tarayıcı zinciri | Bearer zinciri | Kanıt |
|---|---|---|---|
| Kayıtsız host (ör. `unknown.erp.test`) | 404, giriş başlamaz | Tenant'ı token belirler (nötr host, ör. `api.erp.test`) | `BffLoginTests.unknownHostIs404AndStartsNoLogin`, `BearerChainTests.integrationClientIsServedInItsTenant` |
| Tenant `ACTIVE` değil | 503; giriş de başlamaz | 503 | `SessionTenantBindingTests.suspendedTenantRefusesSessionsAndLogins`, `BearerChainTests.suspendedTenantsTokenIs503` |
| Oturum yok | `/api/**` ve `/bootstrap` 401 (yönlendirme yok, SPA `fetch` ile görür); sayfalar giriş ucuna 302 | — | `BffLoginTests.withoutASessionTheApiAnswers401AndPagesRedirectToLogin`, `CsrfTests.bootstrapWithoutASessionIs401` |
| Başka tenant'ın oturum çerezi | 401, WARN logu; oturum silinmez | — | `SessionTenantBindingTests.sessionCookieReplayedOnAnotherTenantsHostIs401` |
| Kurcalanmış ipucu, üye olmayan kullanıcı, başka host'ta callback | 403, gövde `tenant_mismatch`; oturum açılmaz | — | `OrganizationClaimCheckTests`, `SessionTenantBindingTests.callbackReplayedOnAnotherHostDoesNotLogIn` |
| CSRF token'ı yok ya da başka oturumun | 403 | CSRF denetlenmez | `CsrfTests`, `BearerChainTests.bearerPostNeedsNoCsrfToken` |
| Token geçersiz: imza, audience, organization sayısı, kayıtsız organization | — | 401, `WWW-Authenticate` içinde `invalid_token` | `BearerChainTests`, `TenantJwtAuthenticationConverterTests` |
| Token başka tenant'ın host'unda | — | 403, gövdesiz | `BearerChainTests.tokenOnAnotherTenantsHostIs403` |
| Bearer ile `/api/**` dışı yol (ör. `/bootstrap`) | — | 403 (rapora göre `insufficient_scope`) | `BearerChainTests.bootstrapIsNotABearerEndpoint` |
| Keycloak erişilemez | Uygulama açılır, readiness 200; giriş 5xx (whitelabel 500) ve ERROR seviyesinde yığın logu; sonraki giriş discovery'yi yeniden dener | Boot'un decoder'ı tembeldir (yeniden deneme testle sınanmadı) | `KeycloakUnavailableTests`, `LazyClientRegistrationRepositoryTests.failedDiscoveryIsRetriedOnTheNextLogin` |

> ⚠️ Keycloak kesintisinde kullanıcının gördüğü whitelabel 500 sayfası ve her denemede ERROR seviyesinde yazılan tam yığın Faz 1 açık maddesidir (S2 B6, açık madde 1). Güvenlik olaylarının (girişte `tenant_mismatch`, bearer host ile tenant uyuşmazlığı) loglanması da eksiktir; bugün yalnızca başka tenant'ın oturumu için oran sınırsız bir WARN satırı yazılır (S2 B28, açık madde 11).

Sağlık: actuator zinciri `/actuator/health/**`'ı tenant host'u olmadan karşılar; readiness yalnızca platform DB'sidir (`ignore-routing-data-sources: true`). İç adreste `/actuator/health/**` dışındaki her yol, `/actuator/info` dahil, `BrowserTenantFilter`'dan 404 alır (S2 B25).

## 7. Modül rehberleri

Ürün kodunda bir Spring Modulith uygulama modülü (`system`), bir platform test modülü (`platform-test`) ve beş frontend paketi vardır. İş modülü yoktur. Ürün modüllerinin tablosu, yayınladığı ya da dinlediği olayı ve SPI'ı yoktur. Bölüm 7.8–7.11 S1 spike'ının, Bölüm 7.12–7.15 S2 spike'ının dört modülünü anlatır. Tablo ve olay yalnızca spike modüllerinde vardır; ER diyagramları Bölüm 7.11 ve 7.14'tedir.

### 7.1 `system` (backend, `com.smart.erp.system`)

| Konu | İçerik |
|---|---|
| Amacı | Yürüyen iskeletin SPA → API bağlantısını kanıtlamak; çalışma modunu ve sürümü bildirmek |
| Açık tipler (modül kök paketi) | `DeploymentMode` (enum, `key()`), `DeploymentProperties` (record, `erp.deployment` öneki) |
| İç paket | `com.smart.erp.system.web`: `SystemInfoController` (paket görünürlüğünde), `SystemInfoResponse` (record) |
| REST | `GET /api/v1/system/info` → `{"version": "...", "deploymentMode": "saas" ya da "onprem"}` |
| Tablolar ve olaylar | Yok |
| Bağımlı olduğu modüller | Yok; Spring Boot `BuildProperties` (isteğe bağlı, `ObjectProvider` ile) |
| Uzatma noktası | Yok |
| Kritik kurallar | Mod esnek bağlanır, bilinmeyen değerde açılış reddedilir; sürüm yoksa `0.0.0-dev` |
| Testler | `DeploymentPropertiesTests` (`ApplicationContextRunner` ile birim), `SystemInfoControllerTests` (`@ErpIntegrationTest`) |

```mermaid
classDiagram
class ErpApplication {
  +main(String[] args)
}
class DeploymentProperties {
  +DeploymentMode mode
}
class DeploymentMode {
  SAAS
  ONPREM
  +key() String
}
class SystemInfoController {
  -String version
  +info() SystemInfoResponse
}
class SystemInfoResponse {
  +String version
  +String deploymentMode
}
DeploymentProperties --> DeploymentMode
SystemInfoController --> DeploymentProperties
SystemInfoController ..> SystemInfoResponse
```

`ErpApplication` `@ConfigurationPropertiesScan` taşıdığı için `DeploymentProperties` ayrıca kaydedilmez. Uç noktanın Faz 3'te `/api/v1/bootstrap` ile değiştirilmesi öngörülür (`SystemInfoController` Javadoc'u, §8.1).

### 7.2 `platform-test` (backend, `com.smart.erp.platformtest`)

| Konu | İçerik |
|---|---|
| Amacı | Paylaşılan test altyapısı (§14): mimari kurallar ve PostgreSQL Testcontainer'ı |
| Açık API | `ErpArchitectureRules.NO_SPRING_SCHEDULING`, `ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION`; `PostgresTestcontainer` (`IMAGE = "postgres:18.6"`, `@ServiceConnection` bean'i, `newContainer()`) |
| Kullananlar | `erp-app` test kapsamında: `ArchitectureRulesTests`, `ErpIntegrationTest`, `DatabaseWrongPasswordTests`; iki spike'ta da `ArchitectureRulesTests` ve `SpikeDatabases` |
| Kritik kurallar | Test veritabanı compose ile aynı initdb argümanlarıyla açılır (`--encoding=UTF8 --locale-provider=builtin --builtin-locale=C.UTF-8`); gömülü veritabanı (H2 vb.) kullanılmaz |
| Testler | `ErpArchitectureRulesTests` ve `fixtures/` altındaki bilerek kural ihlal eden sınıflar; `PostgresTestcontainerTests` |

`PostgresTestcontainer.newContainer()` Faz 0B'de eklenmiştir: aynı imaj ve initdb argümanlarıyla, başlatılmamış bir konteyner döner. Kendi rolünü ve veritabanlarını kuran fixture'lar (bugün iki spike'ın `SpikeDatabases` sınıfları) bunu kullanır; `@ServiceConnection` bean'i de artık bu metottan üretilir. `PostgresTestcontainerTests`, dönen konteynerin imajını ve `POSTGRES_INITDB_ARGS` değerini doğrular.

`PostgresTestcontainer.IMAGE` ile `deploy/compose/postgres/Dockerfile` içindeki `FROM postgres:18.6` aynı sürümde tutulur; `renovate.json`'daki özel yöneticiler ve "PostgreSQL image" grubu ikisini tek PR'da günceller (commit `2dbc596`). `TestDatabaseLocaleTests` test veritabanının `UTF8`, `builtin` sağlayıcı (`b`) ve `datlocale = C.UTF-8` ile açıldığını doğrular.

`app/src/test/java/com/smart/erp/ErpIntegrationTest.java` bileşik bir test anotasyonudur: `@SpringBootTest`, `@AutoConfigureMockMvc` ve `@Import(PostgresTestcontainer.class)`. Gerçek veritabanı gereken her uygulama testi bunu kullanır; konteyner aynı bağlamı paylaşan test sınıfları arasında yeniden kullanılır.

### 7.3 `@erp/ui` (`web/packages/ui`)

| Konu | İçerik |
|---|---|
| Amacı | Vuetify'ın tek yapılandırma noktası (ADR-0020, §9.4) |
| Dışa açık | `createErpVuetify({ locale?, dark? })`, `ErpLocale` (`'tr'` ya da `'en'`), `ErpVuetifyOptions`, `erpLight`, `erpDark`; ayrıca `@erp/ui/styles` (Vuetify global stilleri) |
| Kritik kurallar | Varsayılan dil `tr`, yedek `en`; varsayılan tema `erpLight`; ikonlar `mdi-svg` |
| Testler | `vuetify.test.ts` |

Sarmalanmış bileşenler (`ErpTextField`, `ErpGrid` …) henüz yoktur.

### 7.4 `@erp/core` (`web/packages/core`)

| Konu | İçerik |
|---|---|
| Amacı | HTTP istemcisi, hata modeli, sunucu durumu sorguları |
| Dışa açık | `getJson`, `ApiError`, `ProblemDetail`, `FetchFn`, `getSystemInfo`, `useSystemInfo`, `SystemInfo` |
| Kritik kurallar | Her başarısız çağrı `ApiError`'dır (Bölüm 6.1); `useSystemInfo` sorgu anahtarı `['system', 'info']`, `staleTime: Infinity` |
| Testler | `http.test.ts` (7 test), `system.test.ts` (1 test) |

`FetchFn` parametresi testlerde sahte `fetch` vermek içindir; üretimde `globalThis.fetch` kullanılır.

### 7.5 `@erp/shell` (`web/packages/shell`)

| Konu | İçerik |
|---|---|
| Amacı | Uygulama iskeleti (§9.3); bugün yalnızca üst çubuk, sürüm ve sunucu uyarısı |
| Dışa açık | `ErpShell` bileşeni (varsayılan `slot`), `shellMessages` (`{ tr, en }`) |
| i18n anahtarları | `shell.title`, `shell.version`, `shell.backendUnavailable`, `shell.welcome` |
| E2E sözleşmesi (`data-testid`) | `shell-title`, `system-version`, `backend-unavailable` |
| Testler | `ErpShell.test.ts`; `vitest.setup.ts` jsdom'da eksik `ResizeObserver` ve `matchMedia` için yer tutucu sağlar |

### 7.6 `@erp/web` (`web/apps/web`)

`main.ts` sırasıyla `@erp/ui/styles`'ı yükler, `createErpVuetify()`, `VueQueryPlugin` (`QueryClient`: `retry: 1`, `refetchOnWindowFocus: false`) ve `i18n`'i (`web/apps/web/src/i18n.ts`: `legacy: false`, `locale: 'tr'`, `fallbackLocale: 'en'`, mesajlar `shellMessages`) kurar ve `App.vue`'yu bağlar. `App.vue`, `ErpShell` içinde `shell.welcome` başlığını gösterir. Router, Pinia ve modül manifestleri yoktur.

### 7.7 `@erp/e2e` (`web/e2e`)

Playwright 1.63.0 duman testleri (Bölüm 5.4). Kurgulanan çalıştırma yolu `smoke.sh`'in başlattığı konteynerdir; yerel tarayıcıyla çalıştırma doğrulanmamıştır. `baseURL` `E2E_BASE_URL` ortam değişkeninden gelir, yoksa `http://localhost:8080`'dir. Kök `pnpm test` bu paketi dışarıda bırakır (`--filter "!@erp/e2e"`).

### 7.8 Spike: `kernel` (`com.smart.erp.spike.s1.kernel`)

Bölüm 7.8–7.11'deki sınıflar `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/` altındadır. Modüller arası bağımlılık yönü `sample → jobs → tenancy → kernel` biçimindedir (`sample` ayrıca `kernel`'i, `jobs` ayrıca `kernel`'i kullanır); spike `ModularityTests` bunu doğrular.

| Konu | İçerik |
|---|---|
| Amacı | §6.1'deki kernel'in tenant ve kimlik parçalarının deneme sürümü |
| Açık tipler | `TenantKey` (record), `TenantContext` (`current`, `require`, `run`, `call`), `MissingTenantContextException`, `TenantSwitchInTransactionException`, `UuidV7` (`next`) |
| Tablolar ve olaylar | Yok |
| Bağımlılıklar | Spike modülü yok; `spring-tx` (`TransactionSynchronizationManager`) |
| Kritik kurallar | Tenant anahtarı `[a-z][a-z0-9_]{1,29}`; bağlama yalnızca kapsamla (setter ve varsayılan tenant yok); transaction ya da senkronizasyon kapsamında başka tenant'a geçiş yok; `UuidV7` tek üreteçte kesin artan |
| Testler | `TenantKeyTests`, `TenantContextTests`, `UuidV7Tests`, `HibernateUuidV7Tests` (öğrenme testi) |

`TenantContext` ThreadLocal tutar ama dışarıya yalnızca kapsam API'si açar; kapsam bitince önceki değer geri konduğu için havuzdaki bir thread bayat tenant taşıyamaz (B11). Koruma kapsam girişindedir:

```java
// kernel/TenantContext.java:46-59
TenantKey previous = CURRENT.get();
if (inTransactionScope() && !tenant.equals(previous)) {
    throw new TenantSwitchInTransactionException(previous, tenant);
}
CURRENT.set(tenant);
try {
    return action.get();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
```

`inTransactionScope()`, `isActualTransactionActive() || isSynchronizationActive()` döner. Yalnızca gerçek transaction'a bakmak yetmez: `SUPPORTS`, `NOT_SUPPORTED` ve `NEVER` kapsamlarında da ilk bağlantı kapsama bağlanır (B12, Bölüm 4.7'deki alıştırma). Neden ThreadLocal seçildiği (Java 25 `ScopedValue` yerine) B11'dedir.

`UuidV7` RFC 9562'nin 1. yöntemini uygular: 48 bit milisaniye, 12 bit sayaç, 62 bit rastgele. Saat durduğunda ya da geri gittiğinde sayaç ilerler, sayaç taşarsa zaman damgası bir milisaniye öne alınır. Kimlik nesne kurulurken bilinir; neden Hibernate'in `@UuidGenerator(style = VERSION_7)`'si yerine uygulama üreteci seçildiği ADR-0013 Doğrulama bölümünde ve B14'tedir.

### 7.9 Spike: `tenancy` (`com.smart.erp.spike.s1.tenancy`)

| Konu | İçerik |
|---|---|
| Amacı | §4.3–§4.5: platform DataSource'u, tenant'a yönlendiren DataSource, tenant kaydı, bağlam yayılımı; Modulith ve jOOQ uyum parçaları |
| Açık tipler | `PlatformDb` (qualifier), `PlatformDataSourceProperties`, `TenantDataSourceProperties`, `TenantStatus`, `TenantDescriptor`, `TenantDirectory`, `TenantNotAvailableException`, `TenantDataSourceFactory`, `TenantRoutingDataSource`, `TenantTaskDecorator` |
| İç tipler | `TenancyConfiguration`, `JdbcTenantDirectory`, `FlushBeforeJooqQueryListener`, `JooqListenerConfiguration`, `ModulithTenancySupport`, `TenantSafeEventPublicationRegistry` |
| Tablolar | Platform DB'sinde `tenant` (okur; şemayı migration kurar) |
| Testler | `TenantDataSourceFactoryTests`, `TenantRoutingDataSourceTests`, `TenantTaskDecoratorTests`, `JdbcTenantDirectoryTests`, `TenantRoutingTests`, `HibernateBootstrapTests`, `JooqReadTests`, `StartupIsolationTests`, `HealthTests` |

```mermaid
classDiagram
class TenantContext {
  +current() Optional
  +require() TenantKey
  +run(TenantKey, Runnable)
  +call(TenantKey, Supplier) T
}
class TenantKey {
  +String value
}
class TenantRoutingDataSource {
  #determineTargetDataSource() DataSource
  +unwrap(Class) T
  +rejectedWithoutTenant() long
  +openPools() Set
  +close()
}
class TenantDirectory {
  <<interface>>
  +find(TenantKey) Optional
  +activeTenants() List
}
class JdbcTenantDirectory
class TenantDataSourceFactory {
  +create(TenantDescriptor) HikariDataSource
}
class TenantDescriptor {
  +TenantKey key
  +String databaseName
  +TenantStatus status
}
class TenantTaskDecorator {
  +decorate(Runnable) Runnable
}
TenantRoutingDataSource --> TenantDirectory
TenantRoutingDataSource --> TenantDataSourceFactory
TenantRoutingDataSource ..> TenantContext
JdbcTenantDirectory ..|> TenantDirectory
TenantDirectory ..> TenantDescriptor
TenantTaskDecorator ..> TenantContext
TenantContext ..> TenantKey
```

Bean'ler `TenancyConfiguration`'dadır:

| Bean | Ne yapar | Neden böyle |
|---|---|---|
| `platformDataSource` | Platform DB'sine Hikari havuzu (`erp.platform.datasource.*`) | `@Bean(defaultCandidate = false)` + `@PlatformDb`: JPA, jOOQ, `JdbcClient` ve Modulith tek aday olarak routing DataSource'u alır, `@Primary` gerekmez (B8, ADR-0015) |
| `tenantDirectory` | `JdbcTenantDirectory`, kaydı platform DB'sinden okur | Kayıt hiçbir zaman routing DataSource'tan okunmaz |
| `tenantDataSource` | `TenantRoutingDataSource` | Statik hedef haritası ve varsayılan hedef yok; havuz tembel ve yalnızca `ACTIVE` tenant için |
| `modulithTenancySupport` | Modulith'in `databaseType` bean'ini bağlantısız `POSTGRES` ile, kayıt defterini `TenantSafeEventPublicationRegistry` ile karşılar | `static`: bir `BeanPostProcessor`, yakaladığı bean'lerden önce var olmalıdır (B1, B2) |
| `tenantTaskDecorator` | Gönderenin tenant'ını işçi thread'ine taşır | Boot her `TaskDecorator` bean'ini `applicationTaskExecutor`'a bileştirir (B10) |

`JooqListenerConfiguration`, `FlushBeforeJooqQueryListener`'ı bir `DefaultExecuteListenerProvider` bean'i olarak kaydeder: okuma-yazma bir JPA transaction'ında her jOOQ sorgusundan önce bağlı `EntityManager` flush edilir (§7.7, B13). Sınıf adı `JooqConfiguration` olamaz; Boot'un aynı adlı bean'iyle çakışır (Bölüm 9).

Tenant havuzu ayarları (`TenantDataSourceProperties`, önek `erp.tenancy.datasource`): `url-template` (`{database}` içermek zorunda), `username`, `password`, `maximum-pool-size` (varsayılan 4), `idle-timeout` (5m), `connection-timeout` (10s). `TenantDataSourceFactory` ayrıca `minimumIdle=0` verir; havuz oluşturmak bağlantı açmaz.

> ⚠️ `ModulithTenancySupport` Modulith 2.1.1'in iç bean adlarına (`databaseType`, `eventPublicationRegistry`) ve sınıf adına bağlıdır. Modulith yükseltmesi bunları değiştirirse `StartupIsolationTests` kırılır. Kayıt defteri `postProcessBeforeInstantiation`'dan döndüğü için Spring'in normal oluşturma yolunu atlar ve kapanış geri çağrısı hiç kaydedilmez; asıl çözüm bu atlamadır, `TenantSafeEventPublicationRegistry#destroy()` çağrılmaz (Faz 1 açık madde 3, Bölüm 10.1).

### 7.10 Spike: `jobs` (`com.smart.erp.spike.s1.jobs`)

| Konu | İçerik |
|---|---|
| Amacı | §6.12: db-scheduler'ın platform DB'sinde çalışması, tenant işleri, tenant dolaşan olay yeniden yayını (ADR-0012, ADR-0014) |
| Açık tipler | `TenantScopedTaskData`, `TenantEventRepublisher` (`republishAll(Duration)`), `RepublishReport`, `JobsProperties`, `EventRepublishProperties` |
| İç tipler | `JobsConfiguration`, `TenantExecutionInterceptor`, `JsonTaskDataSerializer`, `DelayedFixedDelay` |
| Tablolar | Platform DB'sinde `scheduled_tasks` (db-scheduler 16.12.0 şeması) |
| Platform işi | `platform.event-republisher` (`RecurringTask`, ilk çalışma bir aralık sonra) |
| Testler | `TenantExecutionInterceptorTests`, `TenantEventRepublisherTests`; ayrıca `sample` altındaki `TenantJobTests` |

`JobsConfiguration` üç bean kurar:

- `scheduler`: `Scheduler.create(platformDataSource, ...)`; iş verisi Jackson 3 ile JSON (`JsonTaskDataSerializer`), `TenantExecutionInterceptor` iş verisi `TenantScopedTaskData` ise tenant'ı işin süresince bağlar. Ayarlar `spike.jobs.*`: `polling-interval` (1s), `threads` (4), `shutdown-max-wait` (30s).
- `schedulerLifecycle`: zamanlayıcı bağlam ayağa kalktıktan sonra başlar, DataSource'lar kapanmadan durur. `isPauseable()` `false` döner: db-scheduler `stop()`'tan sonra yeniden başlatılamaz, Spring 7 ise önbellekteki test bağlamlarını duraklatıp yeniden başlatır (B20).
- `eventRepublisherTask`: `DelayedFixedDelay` ile ilk çalışma bir aralık sonradır (`spike.events.republish.interval`, varsayılan 1m; `min-age` 5m). db-scheduler'ın `FixedDelay`'i açılışta hemen çalışır ve her tenant için havuz açardı (B16).

Bir olaydan tenant işine giden akış:

```mermaid
flowchart TD
A[SampleService.record<br>kayıt + olay aynı tx]
B[Modulith async dinleyici<br>TenantTaskDecorator]
A --> B
R[TenantEventRepublisher<br>tamamlanmamış yayın] -.-> B
B --> C[SampleRecordedListener<br>scheduleIfNotExists]
C --> D[scheduled_tasks<br>platform DB]
D --> E[TenantExecutionInterceptor<br>tenant bağlanır]
E --> F[SampleProcessor.process<br>idempotent]
```

> ⚠️ İş örneği kimliği olayın `eventId`'sidir; `scheduleIfNotExists` ikinci teslimi yalnızca iş satırı dururken reddeder. `OneTimeTask` tamamlanınca satırı silindiği için tamamlanmadan sonra gelen tekrar teslim işi yeniden çalıştırır. Gerçek garanti idempotent iştir (B15). Spec §6.12'deki "iş bir kez planlanır" cümlesinin bu pencereyle sınırlanması bekleyen bir karardır (Bölüm 10.1).

`TenantEventRepublisher` her `ACTIVE` tenant için `TenantContext.run` içinde Modulith'in `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(minAge)` metodunu çağırır; hata veren tenant `RepublishReport.failed` içine yazılır, diğerleri devam eder. Her çalışmada her aktif tenant için havuz açması spike basitleştirmesidir; §6.12'nin istediği etkinlik göstergesi Faz 2'dedir (B16).

### 7.11 Spike: `sample` (`com.smart.erp.spike.s1.sample`)

| Konu | İçerik |
|---|---|
| Amacı | Yığının tamamını tek bir örnek iş akışıyla sınamak: JPA yazma, jOOQ okuma, olay, dinleyici, iş |
| Açık tipler | `SampleRecord` (entity), `SampleRecordRepository`, `SampleQueries` (jOOQ, codegen yok), `SampleRecorded` (olay), `SampleService`, `SampleProcessor`, `ProcessSampleData` (iş verisi) |
| İç tipler | `SampleJobs` (`sample.process-sample` işi), `SampleRecordedListener` |
| Tablolar | Tenant DB'sinde `sample.sample_record` |
| Yayınladığı olay | `SampleRecorded(eventId, tenantKey, sampleId, occurredAt)` |
| Dinlediği olay | `SampleRecorded` (`@ApplicationModuleListener`, commit sonrası, asenkron) |
| Testler | `HibernateBootstrapTests`, `SamplePersistenceTests`, `TransactionGuardTests`, `JooqReadTests`, `EventPublicationTests`, `TenantJobTests` |

Kritik kurallar: kimlik `SampleRecord` kurulurken `UuidV7.next()` ile atanır; `@Version` alanı `Long` (sarmalayıcı) olduğu için Spring Data atanmış kimlikli yeni kaydı `merge` değil `persist` eder (B14); `markProcessed` idempotenttir (tekrar çalışan iş zaman damgasını değiştirmez); dinleyici olayın tenant'ının bağlı tenant ile aynı olduğunu denetler.

Spike'ın bütün tabloları aşağıdadır (platform DB'sinde `tenant` ve `scheduled_tasks`; her tenant DB'sinde `platform_events.event_publication` ve `sample.sample_record`). Tablolar arasında yabancı anahtar yoktur: iş satırı tenant ve örnek kimliğini JSON iş verisinde (`task_data`), yayın kaydı olayı serileştirilmiş metin olarak (`serialized_event`) taşır. `scheduled_tasks` ve `event_publication` için seçilmiş altı kolon gösterilmiştir; tam tanım migration dosyalarındadır.

```mermaid
erDiagram
tenant {
  text tenant_key
  text database_name
  text status
}
scheduled_tasks {
  text task_name
  text task_instance
  bytea task_data
  timestamptz execution_time
  timestamptz last_failure
  int consecutive_failures
}
event_publication {
  uuid id
  text listener_id
  text event_type
  text serialized_event
  timestamptz publication_date
  timestamptz completion_date
}
sample_record {
  uuid id
  bigint version
  text name
  timestamptz processed_at
}
```

### 7.12 Spike: S2 `kernel` (`com.smart.erp.spike.s2.kernel`)

Bölüm 7.12–7.15'teki sınıflar `spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/` altındadır. Modüller arası bağımlılık yönü `sample → identity → tenancy → kernel` biçimindedir (`sample` ve `identity` ayrıca `kernel`'i kullanır); spike `ModularityTests` bunu doğrular.

S1'deki `kernel`'in kopyasıdır (Bölüm 7.8): `TenantKey`, `TenantContext`, `MissingTenantContextException`, `TenantSwitchInTransactionException`; `UuidV7` yoktur. Tek fark, `TenantKey`'in `Serializable` olmasıdır: oturumdaki principal (`TenantOidcUser`) onu taşır ve oturum Java serileştirmesiyle platform DB'sine yazılır. `TenantKey` bir record olduğu için okunurken kanonik kurucudan geçer; biçim kuralı daralırsa eski oturumlar okunamaz (S2 B13, B20). Testler: `TenantKeyTests`, `TenantContextTests`.

### 7.13 Spike: S2 `tenancy` (`com.smart.erp.spike.s2.tenancy`)

| Konu | İçerik |
|---|---|
| Amacı | §4.3: platform DB'si, tenant kaydı ve host eşlemesi; Faz 1'in routing DataSource'unun yer tutucusu |
| Açık tipler | `PlatformDb`, `PlatformDataSourceProperties`, `TenantStatus`, `TenantRecord` (`key`, `status`, `issuer`, `organizationAlias`), `TenantDirectory` (`findByHost`, `findByIdentity`, `require`), `TenantHost` (`normalize`), `TenantDataSourceStandIn` (`requests()`) |
| İç tipler | `PlatformDataSourceConfiguration`, `JdbcTenantDirectory` |
| Tablolar | Platform DB'sinde `tenant` ve `tenant_domain` (okur; şemayı migration kurar) |
| Testler | `TenantHostTests`, `JdbcTenantDirectoryTests`, `TenantDataSourceStandInTests`, `SpikeDatabasesTests` |

`PlatformDataSourceConfiguration` üç bean kurar:

| Bean | Ne yapar | Neden böyle |
|---|---|---|
| `platformDataSource` | Platform DB'sine Hikari havuzu (`erp.platform.datasource.*`) | `@Bean(defaultCandidate = false)` + `@PlatformDb` + `@SpringSessionDataSource`: niteliksiz tüketiciler onu almaz, Spring Session kendi niteleyicisiyle bulur (ADR-0015, ADR-0039) |
| `tenantDirectory` | `JdbcTenantDirectory`; kaydı platform DB'sinden önbelleksiz okur, durum her aramada okunur | Önbellek (Caffeine + LISTEN/NOTIFY, ADR-0016) Faz 2'dedir (S2 B11) |
| `tenantDataSource` | `TenantDataSourceStandIn`: tek varsayılan aday DataSource | Her bağlantı isteğini sayar ve `MissingTenantContextException` ile reddeder; böylece "bu bileşen tenant DB'sine dokundu" testlerde bir sayıya dönüşür |

Yer tutucu `AbstractRoutingDataSource`'u genişletir; bu sayede `management.health.db.ignore-routing-data-sources: true` onu sağlık denetiminin dışında tutar. `unwrap` ve `isWrapperFor` yönlendirmez ve sayılmaz, çünkü Boot'un metrik ve sağlık bağlaması onları tenant'sız çağırıp hatayı yutar.

Tenant anahtarı ile host ayrı kavramlardır. Organization bağı `tenant` satırındadır (`oidc_issuer`, `organization_alias`; ikisi birlikte tekil); `tenant_domain` yalnızca host → tenant eşlemesidir. Bir tenant'ın birden çok host'u olabilir ve bearer isteğinin anlamlı bir host'u yoktur (S2 B19, §4.3). `TenantHost.normalize` host'u aramadan önce sadeleştirir:

```java
// tenancy/TenantHost.java:14, 18-31 (kısaltıldı, satırlar kırıldı)
Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
if (serverName == null || serverName.isEmpty()
        || serverName.length() > 254) {
    return Optional.empty();
}
String host = serverName.toLowerCase(Locale.ROOT);
if (host.endsWith(".")) {
    host = host.substring(0, host.length() - 1);
}
// her etiket LABEL'a uymalı, yoksa Optional.empty()
```

Yalnızca düz ASCII DNS adları geçer; `_`, Türkçe `İ` ve tam genişlikli karakter içeren ad hiçbir tenant'a eşlenmez (404). `TenantKey` `_` içerebildiği halde DNS etiketi içeremez; bu uyumsuzluk Faz 2 kararıdır (S2 B20).

### 7.14 Spike: S2 `identity` (`com.smart.erp.spike.s2.identity`)

| Konu | İçerik |
|---|---|
| Amacı | §6.3.1 ve ADR-0005, ADR-0006, ADR-0039, ADR-0040: BFF girişi, platform DB'sinde oturum, CSRF, bearer zinciri, tenant'ın her istekte bağlanması |
| Açık tipler | `KeycloakOrganizations` (`CLAIM`, `scopeFor`, `aliases`), `TenantOidcUser` (`tenant()`, adı `<tenant>:<sub>`), `TenantJwtAuthentication` (`tenant()`), `IdentityProperties` (`erp.identity.*`) |
| İç tipler | `SecurityConfiguration`, `SessionConfiguration`, `BrowserTenantFilter`, `BearerTenantFilter`, `TenantFilterChain`, `SessionlessRequestFilter`, `TenantOidcUserService`, `TenantJwtAuthenticationConverter`, `LazyClientRegistrationRepository`, `DiscardingAuthorizedClientRepository` |
| Tablolar | Platform DB'sinde `spring_session` ve `spring_session_attributes` (Spring Session yazar) |
| Bağımlılıklar | `tenancy` (`TenantDirectory`, `TenantHost`, `TenantRecord`, `TenantStatus`, `PlatformDb`), `kernel` (`TenantContext`, `TenantKey`) |
| Testler | `BffLoginTests`, `OrganizationClaimCheckTests`, `SessionTenantBindingTests`, `CsrfTests`, `LogoutTests`, `BearerChainTests`, `SessionWiringTests`, `TenantJwtAuthenticationConverterTests`, `KeycloakOrganizationsTests`, `LazyClientRegistrationRepositoryTests`; ayrıca kök paketteki `StartupIsolationTests`, `KeycloakUnavailableTests` ve `keycloak` altındaki öğrenme testleri |

Tarayıcı tarafının sınıfları:

```mermaid
classDiagram
class BrowserTenantFilter {
  #doFilterInternal(request, response, chain)
  -sessionBelongsTo(TenantKey) boolean
}
class TenantFilterChain {
  +proceed(TenantKey, FilterChain, request, response)$
}
class TenantOidcUserService {
  +loadUser(OidcUserRequest) OidcUser
}
class TenantOidcUser {
  +tenant() TenantKey
  +getName() String
}
class KeycloakOrganizations {
  +String CLAIM$
  +scopeFor(String) String$
  +aliases(Object) Set$
}
class LazyClientRegistrationRepository {
  +findByRegistrationId(String) ClientRegistration
}
class DiscardingAuthorizedClientRepository {
  +saveAuthorizedClient() void
}
class TenantDirectory {
  <<interface>>
  +findByHost(String) Optional
  +findByIdentity(String, String) Optional
  +require(TenantKey) TenantRecord
}
BrowserTenantFilter --> TenantDirectory
BrowserTenantFilter ..> TenantFilterChain
BrowserTenantFilter ..> TenantOidcUser
TenantOidcUserService --> TenantDirectory
TenantOidcUserService ..> KeycloakOrganizations
TenantOidcUserService ..> TenantOidcUser
```

Bearer zincirinde filtrelerin sırası:

```mermaid
flowchart LR
A[SessionlessRequestFilter<br>oturum gizlenir]
B[BearerTokenAuthenticationFilter<br>imza, iss, aud]
C[TenantJwtAuthenticationConverter<br>iss + organization ile tenant]
D[BearerTenantFilter<br>durum ve host]
E[TenantFilterChain<br>TenantContext.run]
F[Controller<br>TenantContext.require]
A --> B
B --> C
C --> D
D --> E
E --> F
```

Bileşenlerin görevleri:

| Bileşen | Ne yapar | Neden böyle |
|---|---|---|
| `KeycloakOrganizations` | İpucu biçimi `organization:<alias>` ve iddianın şekli (`["acme"]` ya da `{"acme": {...}}`) | Uygulama kodundaki Keycloak'a özgü tek bilgi (ADR-0005); biçimi bozuk iddia hiçbir organization'ı adlandırmaz |
| `LazyClientRegistrationRepository` | Tek client registration'ı (`keycloak`) ilk girişte keşfeder; yalnızca başarılı sonucu saklar | Boot'un `issuer-uri`'li kaydı açılışta Keycloak'a gider ve uygulamayı düşürür (S2 B6). `registrationId("keycloak")` açıkça verilir, `requireProofKey(true)` ile PKCE gizli istemcide de eklenir (S2 B5) |
| `DiscardingAuthorizedClientRepository` | Girişin access ve refresh token'ını atar | BFF aşağı akışta API çağırmaz; Boot'un bellek içi deposu token'ları sınırsız biriktirirdi (S2 B8) |
| `TenantOidcUserService` | Callback'te ID token'ın `iss`'ini ve organization kümesini host'un tenant'ıyla karşılaştırır, `TenantOidcUser` döner | İpucu kullanıcının elindedir, imzalı ID token değildir; callback tekrarına karşı tek savunma (S2 B2, B3) |
| `BrowserTenantFilter` | Host → tenant; 404, 503, 401; CSRF, logout ve OAuth2 filtrelerinden önce çalışır | İpucu ve giriş kontrolü host'un tenant'ını görmelidir (S2 B14) |
| `SessionlessRequestFilter` | Bearer isteğinde oturumu her dispatch tipinde (istek, hata, async) gizler | `STATELESS` Spring Session arkasında durumsuzluk vermez (S2 B22, B23) |
| `TenantJwtAuthenticationConverter`, `BearerTenantFilter` | Tam olarak bir organization ister, `(iss, alias)` ile tenant'ı bulur; durum ve host'u denetler | Tenant token'dan çözülür, host'un tenant'ı varsa onunla uyuşmalıdır (S2 B21) |
| `TenantFilterChain` | Zincirin geri kalanını `TenantContext.run` içinde çalıştırır | Bağlama istekle biter, havuzdaki thread bayat tenant taşımaz |

> ⚠️ Tenant ve oturum filtreleri bean değildir; zincirlere `new` ile eklenir. `@Component` olsalardı Boot onları her isteğe uygulanan global servlet filtresi olarak kaydeder, tarayıcı oturumları ve nötr host'taki bearer çağrıları bozulurdu (`docs/spikes/s2-identity.md`, "Korunacak kısıtlar").

Spring Session'ın transaction kancası `SessionConfiguration`'dadır. Kanca olmadan Spring Session bağlamdaki tek transaction yöneticisini, yani varsayılan (tenant) DataSource üzerindekini alır (S2 B7, Bölüm 4.8'deki alıştırmalar):

```java
// identity/SessionConfiguration.java:21-26 (satırlar kırıldı)
@Bean(name = "springSessionTransactionOperations",
        defaultCandidate = false)
TransactionOperations springSessionTransactionOperations(
        @PlatformDb DataSource platform) {
    TransactionTemplate transactions = new TransactionTemplate(
            new DataSourceTransactionManager(platform));
    transactions.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transactions;
}
```

Oturum, çerez ve proxy ayarları `application.yaml`'dadır:

```yaml
# spikes/s2-identity/src/main/resources/application.yaml:2, 6-22
# (yorumlar atıldı)
spring:
  security.oauth2.resourceserver.jwt:
    issuer-uri: ${erp.identity.issuer-uri}
    audiences: erp-api
  session:
    timeout: 30m
    jdbc.initialize-schema: never
    jdbc.cleanup-cron: "-"
server:
  forward-headers-strategy: native
  servlet.session.cookie:
    name: __Host-SESSION
    secure: true
    http-only: true
    same-site: lax
    path: /
```

Kritik kurallar: oturumda access ve refresh token yoktur, yalnızca principal'ın ID token'ı vardır; RP-initiated logout `id_token_hint`'i buradan alır ve ID token tarayıcıdan bir kez, Keycloak'a giden 302'nin `Location`'ında geçer (belgelenmiş istisna, S2 B9). `spring.session.timeout` yalnızca boşta kalma süresidir; oturumun mutlak ömrü yoktur ve Keycloak'taki iptal oturuma yansımaz (S2 B10, `docs/open-questions.md` soru 3). `cleanup-cron: "-"` ile Spring Session'ın zamanlayıcısı kapalıdır (K12); süresi dolan oturumları Faz 1'de bir db-scheduler işi silecektir (S2 B12). Çıkış (`POST /logout`) CSRF token'ı ister, oturumu siler ve Keycloak SSO oturumunu bitirir; aynı kullanıcının başka tenant'taki oturumu yaşamaya devam eder.

Modülün tabloları (Spring Session 4.1.1 şeması; `SPRING_SESSION` için seçilmiş altı kolon gösterilmiştir) ve `tenancy`'nin okuduğu tablolar:

```mermaid
erDiagram
tenant ||--o{ tenant_domain : "host eşlemesi"
SPRING_SESSION ||--o{ SPRING_SESSION_ATTRIBUTES : "öznitelikler"
tenant {
  text tenant_key
  text status
  text oidc_issuer
  text organization_alias
}
tenant_domain {
  text host
  text tenant_key
}
SPRING_SESSION {
  char PRIMARY_ID
  char SESSION_ID
  bigint LAST_ACCESS_TIME
  int MAX_INACTIVE_INTERVAL
  bigint EXPIRY_TIME
  varchar PRINCIPAL_NAME
}
SPRING_SESSION_ATTRIBUTES {
  char SESSION_PRIMARY_ID
  varchar ATTRIBUTE_NAME
  bytea ATTRIBUTE_BYTES
}
```

`PRINCIPAL_NAME`, `TenantOidcUser`'ın adını (`<tenant>:<sub>`) taşır; Spring Session'ın principal index'i bir `(kullanıcı, tenant)` çiftinin oturumlarını bulur, bir kullanıcının bütün tenant'lardaki oturumlarını tek seferde bulamaz (S2 B10). Oturum tablosu ile tenant tabloları arasında yabancı anahtar yoktur.

### 7.15 Spike: S2 `sample` (`com.smart.erp.spike.s2.sample`)

Güvenlik zincirlerinin arkasındaki uygulama: kimin hangi tenant'ta girdiğini göstermeye yetecek kadar uç içerir (`SampleController`).

| Uç | Zincir | Yanıt |
|---|---|---|
| `GET /` | Tarayıcı | `ok` (girişten sonraki açılış sayfası) |
| `GET /bootstrap` | Yalnızca tarayıcı | `Bootstrap`: `tenant`, `subject`, `name`, `csrfHeader` (`X-CSRF-TOKEN`), `csrfToken` (her yanıtta farklı XOR maskeli değer) |
| `GET /api/whoami` | Tarayıcı ve bearer | `WhoAmI`: `tenant`, `name`, `authentication` (`OAuth2AuthenticationToken` ya da `TenantJwtAuthentication`) |
| `POST /api/echo` | Tarayıcı ve bearer | `{"tenant": "acme"}`; tarayıcıda CSRF token'ı ister |

`/bootstrap`, §8.1'deki `GET /api/v1/bootstrap`'ın deneme sürümüdür ve CSRF alanları yüzünden tarayıcıya özeldir; bearer zincirinde 403 alır. Bearer istemcilere açılıp açılmayacağı açık sorudur (S2 B16, `docs/open-questions.md` soru 2). Controller tenant'ı `TenantContext.require()` ile okur; tablo ve olay yoktur.

## 8. Uygulamalı el kitabı

Tarifler depodaki gerçek konvansiyonları izler. Bölüm 8.1 ve 8.2'deki örnekler kılavuz hazırlanırken depoya geçici olarak eklenmiş, tüm kontrollerden geçtiği görülmüş ve geri alınmıştır; depoda bulunmazlar. Bölüm 8.1 Faz 0B'de yeniden denenmiştir; Bölüm 8.2 Faz 0A'da denenmiştir ve ilgili önyüz dosyaları o tarihten beri değişmemiştir. Bölüm 8.6, S1 spike'ındaki mevcut örnek akışı adım adım açıklar. Bölüm 8.7'deki örnek Faz 0C'de S2 spike'ına geçici olarak eklenmiş, `verify`'dan geçtiği görülmüş ve geri alınmıştır.

### 8.1 Yeni backend REST ucu

Örnek: `system` modülüne `GET /api/v1/system/deployment-mode` eklemek.

1. Uç nokta, modülün `web` alt paketine (iç paket, §5.3) yazılır. Controller ve yöntemleri paket görünürlüğündedir; bağımlılıklar constructor ile alınır (`docs/guides/coding-rules.md`). Yol `/api/v1/<modül>/...` biçimindedir (§8.1).

```java
// app/src/main/java/com/smart/erp/system/web/DeploymentModeController.java
package com.smart.erp.system.web;

import com.smart.erp.system.DeploymentProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class DeploymentModeController {

    private final DeploymentProperties deployment;

    DeploymentModeController(DeploymentProperties deployment) {
        this.deployment = deployment;
    }

    @GetMapping("/deployment-mode")
    DeploymentModeResponse mode() {
        return new DeploymentModeResponse(deployment.mode().key());
    }
}
```

2. Yanıt DTO'su bir `record`'dur. Entity hiçbir zaman döndürülmez (K14, henüz otomatik denetlenmiyor).

```java
// app/src/main/java/com/smart/erp/system/web/DeploymentModeResponse.java
package com.smart.erp.system.web;

public record DeploymentModeResponse(String mode) {}
```

3. Hata modeli: Problem Details üretimi kernel ile Faz 1'de gelir (§8.3, §15.3). O zamana kadar uç noktadaki hatalar Spring Boot'un varsayılan gövdesiyle döner ve önyüzde `ApiError` olarak ayrıştırılır (Bölüm 6.1).

4. Entegrasyon testi `@ErpIntegrationTest` (gerçek PostgreSQL 18.6, tam bağlam, `MockMvcTester`) ile yazılır. Test sınıfı adı `*Tests` ile biter.

```java
// app/src/test/java/com/smart/erp/system/web/DeploymentModeControllerTests.java
package com.smart.erp.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.ErpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ErpIntegrationTest
class DeploymentModeControllerTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void returnsTheConfiguredMode() {
        assertThat(mvc.get().uri("/api/v1/system/deployment-mode"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"mode": "onprem"}
                        """);
    }
}
```

5. Spring bağlamı gerektirmeyen mantık için birim testi yazılır; yapılandırma bağlaması `ApplicationContextRunner` ile denenir (örnek: `DeploymentPropertiesTests`).

6. Mimari kurallara uyum ayrıca kayıt gerektirmez: `ArchitectureRulesTests` ve `ModularityTests` `com.smart.erp` altındaki bütün üretim sınıflarını tarar. Başka bir modülün yalnızca kök paketindeki tipleri kullanılabilir (K1).

7. Doğrulama ve biçim:

```bash
./mvnw -q spotless:apply -pl app
./mvnw clean verify
```

Beklenen: `erp-app` için `Tests run: 21` (yeni test dahil), `BUILD SUCCESS`.

8. Önyüz istemcisi gerekiyorsa `@erp/core`'a `getSystemInfo` örneğindeki gibi bir fonksiyon ve TanStack Query sorgusu eklenir (`web/packages/core/src/system.ts`).

### 8.2 Yeni önyüz bileşeni, ekranı ve i18n metni

Örnek: çalışma modunu gösteren bir `SystemModeChip` bileşeni `@erp/shell`'e eklenir ve ana ekranda kullanılır.

1. Kullanıcıya görünen metin önce `tr` ve `en` dosyalarına eklenir; anahtar düzeni `<modül>.<kaynak>.<alan>`'dır (§9.8). `web/packages/shell/src/i18n/tr.json` içinde `shell` nesnesine:

```json
"mode": {
  "saas": "Bulut (SaaS)",
  "onprem": "Kurum içi"
}
```

`web/packages/shell/src/i18n/en.json` içine aynı anahtarlar: `"saas": "Cloud (SaaS)"`, `"onprem": "On-premises"`.

2. Bileşen `script setup lang="ts"` ile yazılır (ESLint zorlar); Vuetify bileşenleri `vite-plugin-vuetify` ile otomatik içe aktarılır. E2E kancası `data-testid` ile verilir.

```vue
<!-- web/packages/shell/src/SystemModeChip.vue -->
<script setup lang="ts">
import { useSystemInfo } from '@erp/core'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { data: systemInfo } = useSystemInfo()
</script>

<template>
  <v-chip v-if="systemInfo" size="small" data-testid="system-mode">
    {{ t(`shell.mode.${systemInfo.deploymentMode}`) }}
  </v-chip>
</template>
```

3. Paket dışına açılır: `web/packages/shell/src/index.ts` dosyasına `export { default as SystemModeChip } from './SystemModeChip.vue'` satırı eklenir.

4. Ekranda kullanılır (`web/apps/web/src/App.vue`):

```vue
<script setup lang="ts">
import { ErpShell, SystemModeChip } from '@erp/shell'
</script>

<template>
  <ErpShell>
    <v-container>
      <h1 class="text-h5">{{ $t('shell.welcome') }}</h1>
      <SystemModeChip />
    </v-container>
  </ErpShell>
</template>
```

5. Bileşen testi `ErpShell.test.ts` desenini izler: sahte `fetch`, `retry: false` ile `QueryClient`, Türkçe `i18n`.

```typescript
// web/packages/shell/src/SystemModeChip.test.ts
import { createErpVuetify } from '@erp/ui'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import SystemModeChip from './SystemModeChip.vue'
import { shellMessages } from './i18n'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('SystemModeChip', () => {
  it('shows the deployment mode in Turkish', async () => {
    const body = '{"version":"1.2.3","deploymentMode":"onprem"}'
    const headers = { 'content-type': 'application/json' }
    const ok = async () => new Response(body, { status: 200, headers })
    vi.stubGlobal('fetch', vi.fn(ok))

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const i18n = createI18n({
      legacy: false,
      locale: 'tr',
      messages: shellMessages,
    })
    const wrapper = mount(SystemModeChip, {
      global: {
        plugins: [createErpVuetify(), [VueQueryPlugin, { queryClient }], i18n],
      },
    })

    await vi.waitFor(async () => {
      await flushPromises()
      const chip = wrapper.get('[data-testid="system-mode"]')
      expect(chip.text()).toBe('Kurum içi')
    })
  })
})
```

6. Doğrulama (`web/` içinde):

```bash
pnpm format:check && pnpm lint && pnpm typecheck
pnpm test && pnpm build
```

Beklenen: `packages/shell test: Tests 3 passed (3)` ve diğer kontroller hatasız.

> Vuetify yalnızca `@erp/ui` üzerinden yapılandırılır; bir paket yalnızca `package.json`'unda bildirdiği paketi import edebilir (Bölüm 4.5). Yeni bir modül paketi (`@erp/mod-...`, §9.2) bugün yoktur; ilk modül paketi geldiğinde bu tarif genişletilmelidir.

### 8.3 Yeni veritabanı migration'ı

Ürün kodunda henüz yoktur: ürün uygulamasında Flyway bağımlılığı ve migration klasörü bulunmaz. Kuralları §7.9 ve `docs/guides/coding-rules.md` tanımlar: modül klasöründe `V<yyyyMMddHHmm>__<açıklama>.sql` adı, genişlet/daralt kuralı (§4.6), `snake_case` ve tekil tablo adları (§7.2). Ürün tarifi, migration orkestratörünün (ADR-0038) geldiği fazda yazılacaktır.

S1 spike'ında migration eklemek (yalnızca spike'ın kendi testleri için):

1. Dosya hedef veritabanına göre `spikes/s1-tenancy/src/main/resources/db/platform/` ya da `.../db/tenant/` altına `V<yyyyMMddHHmm>__<açıklama>.sql` adıyla yazılır. Tenant tabloları modül şemasında açılır (`create schema sample;`, `sample.sample_record`); kimlik `uuid`, sürüm `bigint not null` kolonudur (§7.2).
2. Uygulama açılışı şema kurmaz; fixture (`SpikeDatabases`) bir sonraki test çalışmasında Flyway API'siyle dosyayı her tenant DB'sine (ya da platform DB'sine) uygular. Ek bir kayıt gerekmez.
3. Entity eklendiyse `spring.jpa.hibernate.ddl-auto: none` olduğu için Hibernate şemayı doğrulamaz; eşleşmeyi testler kanıtlar.
4. Yeni tablonun her tenant DB'sinde açıldığı, `SpikeDatabasesTests.migratesEveryTenantDatabase`'teki gibi süper kullanıcı bağlantısıyla `select to_regclass('<şema>.<tablo>')::text` sorgusu eklenerek doğrulanır; ardından `./mvnw -Pspikes -pl spikes/s1-tenancy verify` çalıştırılır.

Bu tarif spike'taki mevcut iki tenant migration'ının (`V202610051200__platform_events.sql`, `V202610051201__sample.sql`) izlediği yoldur; kılavuz hazırlanırken yeni bir migration eklenerek ayrıca denenmemiştir (doğrulanmadı).

S2 spike'ında yalnızca platform DB'si vardır; migration'lar `spikes/s2-identity/src/main/resources/db/platform/` altına aynı adlandırmayla yazılır ve `SpikeDatabases` tarafından uygulanır. Üçüncü taraf bir kütüphanenin şeması (bugün Spring Session) elle yazılmaz: kütüphanenin kendi PostgreSQL betiği, kaynağı ve sürümü dosyanın başına yorum olarak yazılıp birebir kopyalanır (`V202610061201__spring_session.sql`).

### 8.4 Bağımlılık sürümü yükseltmek

Sürüm politikası ADR-0025 ve §11.8'dedir; major yükseltmeler Renovate'te onay ister (`renovate.json`). Bir sürümün değiştiği yerler:

| Bileşen | Sürümün yeri | Birlikte değişmesi gerekenler |
|---|---|---|
| Spring Boot (ve yönettiği Testcontainers, JUnit, PostgreSQL JDBC; S1'in kullandığı Hibernate, jOOQ, Flyway, HikariCP; S2'nin kullandığı Spring Security 7.1.1, Spring Session 4.1.1) | Kök `pom.xml` `<parent>` sürümü | İki spike yeniden çalıştırılır (Bölüm 3.10): S1'in açılış ayarları B4–B6 ve S2'nin bulguları (S2 B5–B8, B22) bu sürümlere bağlıdır; Spring Session değiştiyse `V202610061201__spring_session.sql` yeniden kopyalanır |
| Spring Modulith, ArchUnit, Error Prone, Spotless, palantir-java-format, license-maven-plugin | Kök `pom.xml` `<properties>` || Modulith yükseltmesinde spike yeniden çalıştırılır: `ModulithTenancySupport` Modulith'in iç bean adlarına bağlıdır |
| Maven | `.mvn/wrapper/maven-wrapper.properties` | — |
| Java | `mise.toml` (`temurin-25`), kök `pom.xml` (`java.version`, enforcer) | `deploy/docker/app.Dockerfile` (`eclipse-temurin:25-*`), `.gitlab-ci.yml` (`backend:verify` imajı) |
| Node | `mise.toml` | `deploy/docker/app.Dockerfile`, `.gitlab-ci.yml` (`frontend:verify` imajı); Renovate 24 hattında tutar |
| pnpm | `mise.toml` | `web/package.json` (`packageManager`) |
| Frontend kütüphaneleri | `web/pnpm-workspace.yaml` → `catalog:` | `web/pnpm-lock.yaml` (`pnpm install` ile) |
| TypeScript | `catalog:` | 6.1'in altında kalır (typescript-eslint 8.71 sınırı, ADR-0029) |
| Playwright | `catalog:` (`@playwright/test`) | `deploy/docker/e2e.Dockerfile` imaj etiketi |
| PostgreSQL | `deploy/compose/postgres/Dockerfile` | `PostgresTestcontainer.IMAGE`; major yükseltme Renovate'te kapalıdır (§11.2) |
| Keycloak | `deploy/compose/compose.yaml` | S2 fixture'ındaki `SpikeKeycloak.IMAGE`; Renovate bu sabiti eşlemez, elle güncellenir. Öğrenme testleri (`KeycloakOrganizationsLearningTests`) yeniden çalıştırılır |
| Mailpit | `deploy/compose/compose.yaml` | — |
| gitleaks | `mise.toml` | `.gitlab-ci.yml` (`secrets:gitleaks` imajı) |
| db-scheduler (yalnızca spike) | `spikes/s1-tenancy/pom.xml` (`db-scheduler.version`) | `db/platform/V202610051201__scheduled_tasks.sql` (tablo, kullanılan sürümün PostgreSQL şemasından alınmıştır) |

Adımlar:

1. Sürüm yukarıdaki yerlerde değiştirilir; frontend için `web/` içinde `pnpm install` çalıştırılarak kilit dosyası güncellenir.
2. Yeni bir bağımlılığın lisansı izinli listede olmalıdır; liste dışı bir lisans ailesi ADR gerektirir (`CONTRIBUTING.md`).
3. Derleme betiği olan yeni bir frontend bağımlılığı için `web/pnpm-workspace.yaml` → `allowBuilds` altına gerekçeli yorumla `true` ya da `false` yazılır.
4. Bölüm 8.5'teki zincirin tamamı çalıştırılır; Java, Node veya PostgreSQL imajı değiştiyse `deploy/compose/smoke.sh` zorunludur.

> Bu tarif kılavuz hazırlanırken gerçek bir sürüm yükseltmesiyle denenmemiştir (doğrulanmadı); dosya yerleri depoda kontrol edilmiştir.

### 8.5 Commit ve MR öncesi komut zinciri

CI'daki işlerin yerel karşılıkları, aynı sırayla (depo kökünden):

```bash
gitleaks git --config .gitleaks.toml --redact .
./mvnw clean verify
(cd web && pnpm install --frozen-lockfile && pnpm format:check \
  && pnpm lint && pnpm typecheck && pnpm test && pnpm build \
  && pnpm licenses:check)
deploy/compose/smoke.sh
```

Beklenen son satırlar: `no leaks found`; `BUILD SUCCESS`; `License check passed: 37 production packages.`; `Smoke test passed.` Bu zincir Faz 0C doğrulamasında aynen geçmiştir (gitleaks 58 commit tarar).

Değişiklik `spikes/`, `platform/` ya da kök `pom.xml`'e dokunuyorsa zincire spike'lar eklenir (CI bunu yapmaz, Bölüm 5.3):

```bash
./mvnw -q install -DskipTests
./mvnw -Pspikes -pl spikes/s1-tenancy verify
./mvnw -Pspikes -pl spikes/s2-identity verify
```

Bu kılavuz değiştiyse `mise x -- node scripts/check-guide.mjs` çalıştırılır; son satır `0 hata` ile başlamalıdır (Bölüm 4.2).

Çalışma ağacındaki dosyalar (ör. commit'lenmemiş belgeler) gitleaks ile taranacaksa `gitleaks dir` her yol için ayrı çağrılır:

```bash
gitleaks dir --config .gitleaks.toml --redact spikes
gitleaks dir --config .gitleaks.toml --redact docs
```

gitleaks 8.30.1'de `dir` tek yol alır. İki yol verildiğinde (`... --redact spikes docs`) çalışma ağacının tamamını tarar ve git'in yok saydığı yerel `deploy/compose/.env`'yi bildirir; Faz 0C doğrulamasında bu çağrı `leaks found: 4` vermiş, yukarıdaki iki çağrı `no leaks found` vermiştir. Plan 0B ve 0C'nin doğrulama adımlarındaki iki yollu komut bu yüzden hatalıdır (`docs/spikes/s2-identity.md`, "Plan 0E için girdi").

**Commit biçimi** Conventional Commits'tir (§14, `CONTRIBUTING.md`). Kapsam modül ya da alan adıdır; depodaki örnekler: `feat(app): ...`, `fix(web): ...`, `build(app): ...`, `test(e2e): ...`, `ci: ...`, `docs(adr): ...`; spike commit'lerinde kapsam `spike`'tır (`feat(spike): ...`, `fix(spike): ...`, `test(spike): ...`, `docs(spike): ...`). Kırıcı değişiklik `feat(api)!:` ve gövdede `BREAKING CHANGE:` ile işaretlenir. Dallar `main`'den açılır: `feat/<konu>`, `fix/<konu>`, `chore/<konu>`, `docs/<konu>`; müşteri için dal açılmaz (K8).

**MR şablonu** `.gitlab/merge_request_templates/Default.md`'dir ve üç bölüm içerir: "Ne ve neden" (ilgili ADR ve doküman bölümü), "Nasıl doğrulandı" ve "Kontrol listesi". Kontrol listesi şunları sorar: Conventional Commits, K1–K15 ihlali yok, yeni davranışın testi ve yerelde yeşil kontroller, gerekiyorsa ADR, kırıcı değişiklik yok ya da ADR'li, yeni bağımlılığın lisansı ve sürümünün BOM/catalog'da olması, sır ve kişisel veri eklenmemesi, metinlerin `tr` ve `en` i18n dosyalarında olması. Platform değişikliklerinde platform sahibinin onayı gerekir (§13.2). Pipeline yeşil olmadan merge edilmez (`CONTRIBUTING.md`); hat henüz çalışmadığı için bu kural bugün yerel zincirle karşılanır.

### 8.6 Tenant bağlamında yazma, olay ve iş zinciri (S1 spike kalıbı)

Bu tarif, spike'taki `sample` akışının hangi adımlardan oluştuğunu ve yeni bir benzerinin nasıl kurulacağını anlatır. Kurallar §4.4, §6.12 ve §7.7'de, kararlar ADR-0012 ve ADR-0014'tedir. Ürün kodunda bu altyapı yoktur; Faz 1 ve Faz 2'de bulgulara göre yeniden yazılacaktır. Aşağıdaki parçalar depodaki dosyalardan alıntıdır (yollar `spikes/s1-tenancy/src/` altına göredir) ve `TenantJobTests` ile doğrulanır.

1. Uygulama servisi tek komut, tek transaction'dır (K3). Olay, kaydı yazan aynı tenant transaction'ında yayınlanır; Modulith yayın kaydını o tenant'ın DB'sine yazar. Olay kendi `eventId`'sini (UUIDv7) ve `tenantKey`'i taşır (§5.4).

```java
// main/java/.../sample/SampleService.java:25-31 (satırlar kırıldı)
@Transactional
public UUID record(String name) {
    SampleRecord record = records.save(new SampleRecord(name));
    events.publishEvent(new SampleRecorded(
            UuidV7.next(), TenantContext.require(),
            record.getId(), clock.instant()));
    return record.getId();
}
```

2. Servis her zaman bağlı bir tenant içinde çağrılır. Tenant transaction başlamadan bağlanır; servis içinden başka tenant'a geçilmez (Bölüm 1.5).

```java
// test/java/.../sample/TenantJobTests.java:43 (satır kırıldı)
UUID sampleId = TenantContext.call(ACME,
        () -> samples.record("job-" + UUID.randomUUID()));
```

3. İş verisi `TenantScopedTaskData`'yı uygulayan bir record'dur; JSON'a `tenantKey` ve iş kimlikleri yazılır, `tenant()` bir bean özelliği değildir.

```java
// main/java/.../sample/ProcessSampleData.java (Javadoc atıldı, satır kırıldı)
public record ProcessSampleData(String tenantKey, UUID sampleId)
        implements TenantScopedTaskData {
    @Override
    public TenantKey tenant() {
        return new TenantKey(tenantKey);
    }
}
```

4. İş bir `OneTimeTask` bean'i olarak tanımlanır; `JobsConfiguration` bütün `OneTimeTask` bean'lerini zamanlayıcıya kaydeder. İş adı `<modül>.<iş>` biçimindedir (`sample.process-sample`).

```java
// main/java/.../sample/SampleJobs.java:14-19
@Bean
OneTimeTask<ProcessSampleData> processSampleTask(SampleProcessor processor) {
    return Tasks.oneTime(PROCESS_SAMPLE, ProcessSampleData.class)
            .execute((instance, context) ->
                    processor.process(instance.getData().sampleId()));
}
```

5. Dinleyici `@ApplicationModuleListener` ile commit sonrası ve asenkron çalışır; `TenantTaskDecorator` sayesinde yayıncının tenant'ı bağlıdır. İşi iş örneği kimliği `eventId` olacak şekilde `scheduleIfNotExists` ile kuyruğa alır.

```java
// main/java/.../sample/SampleRecordedListener.java:31-45 (kısaltıldı)
@ApplicationModuleListener
void on(SampleRecorded event) {
    // olayın tenant'ı bağlı tenant ile aynı mı? (denetim)
    boolean scheduled = scheduler.scheduleIfNotExists(
            processSample.instance(
                    event.eventId().toString(),
                    new ProcessSampleData(
                            event.tenantKey().value(),
                            event.sampleId())),
            clock.instant());
}
```

6. İşi yapan servis kendi transaction'ını açar; tenant'ı `TenantExecutionInterceptor` bağlar. İş idempotent yazılır, çünkü iş tamamlandıktan sonra gelen bir tekrar teslim onu yeniden çalıştırır (B15). Örnekte `SampleRecord.markProcessed` zaman damgası boşsa yazar.

7. Test `@SpikeTest` ile yazılır: işlem bir tenant'ta yapılır, sonuç `await()` ile beklenir, başka tenant'a hiçbir şey yazılmadığı süper kullanıcı tanığıyla (`SpikeDatabases.tenantDatabase(GLOBEX)`) doğrulanır. Örnek: `TenantJobTests.recordingASampleRunsItsJobInTheSameTenant`.

8. `./mvnw -Pspikes -pl spikes/s1-tenancy verify` çalıştırılır. `StartupIsolationTests` yeni bean'lerin açılışta tenant'sız bağlantı istemediğini, `ModularityTests` modül sınırlarını denetler.

> ⚠️ Bir zamanlanmış işi `@Scheduled` ile yazmak K12'yi ihlal eder; tekrarlayan işler `RecurringTask` bean'i olarak yazılır (`JobsConfiguration.eventRepublisherTask`). Tekrarlayan bir platform işi tenant'ları kendisi dolaşacaksa ilk çalışmasını açılışa koymamalıdır (`DelayedFixedDelay`, B16).

### 8.7 Kimlik zincirlerinin arkasına uç nokta eklemek (S2 spike kalıbı)

Bu tarif, S2 spike'ında hem tarayıcı oturumuyla hem entegrasyon istemcisinin bearer token'ıyla çağrılan, tenant'a bağlı bir uç noktanın nasıl ekleneceğini ve sınanacağını anlatır. Kurallar §4.4 ve §6.3.1'de, kararlar ADR-0006 ve ADR-0040'tadır. Ürün kodunda kimlik katmanı yoktur; Faz 1'de bulgulara göre yeniden yazılacaktır. Örnek: `POST /api/notes`.

1. Uç nokta `/api/**` altına yazılır. Bu yol iki zincirde de kimlik ister; bearer zinciri `/api/**` dışındaki her yolu reddeder (`anyRequest().denyAll()`). Controller tenant'ı `TenantContext.require()` ile okur. Tenant'ı zincirdeki filtre bağlamıştır; controller host'a ya da token'a bakmaz.

```java
// spikes/s2-identity/src/main/java/com/smart/erp/spike/s2/sample/
//   NoteController.java
package com.smart.erp.spike.s2.sample;

import com.smart.erp.spike.s2.kernel.TenantContext;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class NoteController {

    @PostMapping("/api/notes")
    Map<String, String> add(Authentication authentication) {
        return Map.of(
                "tenant", TenantContext.require().value(),
                "by", authentication.getName());
    }
}
```

2. Ek güvenlik yapılandırması gerekmez. Tarayıcıda durum değiştiren istek oturumun CSRF token'ını ister; SPA token'ı `/bootstrap` yanıtından alır ve `X-CSRF-TOKEN` başlığıyla gönderir. Bearer zincirinde CSRF yoktur. Başka bir tenant'ın token'ı, kayıtlı bir tenant host'unda `BearerTenantFilter`'dan 403 alır.

3. Test `@SpikeTest` ile, uygulamaya gerçek HTTP üzerinden giden `SpikeBrowser` ve token üreten `SpikeKeycloak` ile yazılır. Test tarafında `sample` paketi bugün yoktur; klasör açılır.

```java
// spikes/s2-identity/src/test/java/com/smart/erp/spike/s2/sample/
//   NoteControllerTests.java (satırlar kırıldı)
package com.smart.erp.spike.s2.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeKeycloak;
import com.smart.erp.spike.s2.support.SpikeTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpikeTest
class NoteControllerTests {

    private static final String ACME = "https://acme.erp.test";

    @LocalServerPort
    int port;

    @Test
    void theBrowserNeedsTheSessionsCsrfToken() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login("acme.erp.test", "ayse");
        String url = ACME + "/api/notes";

        assertThat(browser.post(url, Map.of(), "Accept", "application/json")
                        .status())
                .isEqualTo(403);

        SpikeBrowser.Page bootstrap =
                browser.get(ACME + "/bootstrap", "Accept", "application/json");
        String token = (String) SpikeBrowser.json(bootstrap).get("csrfToken");
        SpikeBrowser.Page page = browser.post(
                url, Map.of(), "Accept", "application/json",
                "X-CSRF-TOKEN", token);

        assertThat(page.status()).isEqualTo(200);
        assertThat(SpikeBrowser.json(page)).containsEntry("tenant", "acme");
    }

    @Test
    void anIntegrationClientIsServedInItsTenantWithoutCsrf() {
        String token = SpikeKeycloak.clientCredentials("acme-integration");
        SpikeBrowser.Page page = new SpikeBrowser(port).post(
                "https://api.erp.test/api/notes", Map.of(),
                "Authorization", "Bearer " + token,
                "Accept", "application/json");

        assertThat(page.status()).isEqualTo(200);
        assertThat(SpikeBrowser.json(page)).containsEntry("tenant", "acme");
    }

    @Test
    void anotherTenantsTokenIsRefusedOnThisHost() {
        String token =
                SpikeKeycloak.clientCredentials("initech-integration");
        SpikeBrowser.Page page = new SpikeBrowser(port).post(
                ACME + "/api/notes", Map.of(),
                "Authorization", "Bearer " + token);

        assertThat(page.status()).isEqualTo(403);
    }
}
```

4. Biçim ve doğrulama:

```bash
./mvnw -Pspikes -pl spikes/s2-identity spotless:apply
./mvnw -Pspikes -pl spikes/s2-identity verify
```

Beklenen: `Tests run: 130, Failures: 0, Errors: 0, Skipped: 0` (yeni üç test dahil) ve `BUILD SUCCESS`. `StartupIsolationTests` yeni bean'in tenant DataSource'una dokunmadığını, `ModularityTests` `sample → kernel` bağımlılığının izinli olduğunu denetler. Spotless, kılavuzdaki 80 karakterlik satırları depodaki 120 karakterlik biçime göre yeniden düzenler.

5. Yeni bir test entegrasyon istemcisi gerekiyorsa `spikes/s2-identity/src/test/resources/keycloak/erp-realm.json` içine mevcut istemcilerin kalıbıyla eklenir: service account açık, `erp-api` audience eşleyicisi ve tenant'ı sabit iddia olarak veren eşleyici. Client secret dosyaya yazılmaz; `SpikeKeycloak` onu Keycloak'tan okur (K9). Bu adım yeni bir istemci eklenerek denenmemiştir (doğrulanmadı).

```json
{"name": "tenant", "protocol": "openid-connect",
 "protocolMapper": "oidc-hardcoded-claim-mapper",
 "config": {"claim.name": "organization",
            "claim.value": "[\"acme\"]", "jsonType.label": "JSON",
            "access.token.claim": "true"}}
```

Kaynak: `erp-realm.json:23-25` (satırlar kırıldı). Sabit iddia, üyelik iddiasıyla aynı şekli (`["acme"]`) verir (S2 B21).

> ⚠️ Faz 1'de tenant DB'sine yazan bir uç noktada bu kalıp Bölüm 8.6'daki kurallarla birleşir: tenant zincirde bağlanır, transaction controller'ın çağırdığı uygulama servisinde açılır (K3). Async uç nokta (`Callable`, `DeferredResult`) açılmamalıdır: inceleme çıkarımına göre `OncePerRequestFilter` tabanlı tenant filtreleri ASYNC dispatch'ini atlar ve async iş `TenantContext` olmadan çalışır (S2 B23, Faz 1 açık madde 4).

## 9. Sorun giderme

"Kaynak" sütunu sorunun Faz 0A, 0B ya da 0C'de gerçekten yaşandığını (commit, plan ya da bulgu raporu) ya da beklenen bir sorun olduğunu gösterir.

| Belirti | Neden | Çözüm | Kaynak |
|---|---|---|---|
| `JDK 25 is required. Run mise install ...` | Kabukta mise etkin değil; sistem `java` (ör. 17) kullanılıyor | Kabuk entegrasyonu ya da `mise x --` öneki (Bölüm 3.1) | Doğrulandı |
| `command not found: pnpm` ya da `node -v` 18 gösteriyor | Aynı neden | Aynı çözüm | Doğrulandı |
| Kaynakla uyuşmayan derleme ya da test hataları, açıklanamayan sınıf hataları | VS Code ve Antigravity'nin Java dil sunucuları `target/` altına bozuk sınıf dosyaları yazabiliyor | Maven her zaman `clean` ile çalıştırılır: `./mvnw clean verify` | Faz 0A'da yaşandı |
| `Non-parseable POM ... in comment after two dashes (--) next character must be >` | XML yorumunda `--` geçiyor (ör. `pnpm --filter ...` yazmak) | XML yorumlarında `--` kullanılmaz; `app/pom.xml`'deki yorum bu yüzden "`pnpm build` of @erp/web" biçimindedir | Plan ile kod farkı; hata doğrulandı |
| e2e: `ERR_SSL_PROTOCOL_ERROR` ya da `net::ERR_...` | Chromium HSTS preload listesi `app` TLD'sini kapsar, `http://app` zorla HTTPS'e çevrilir | Uygulamaya `erp-app` ağ takma adıyla erişilir; takma ad preload listesinde olmayan bir ad kalmalıdır | Faz 0A'da yaşandı (`compose.yaml`, `ci.md`) |
| `initdb/` betiğindeki değişiklik etkisiz | Betikler `erp-postgres:local` imajına gömülüdür ve yalnızca boş volume'da çalışır | `docker compose -f deploy/compose/compose.yaml build postgres`, ardından dev volume'u sıfırlanır (veri silinir, Bölüm 3.9) | `local-development.md` |
| Uzak Docker daemon'unda (CI dind) init betikleri bulunamıyor | Bind mount kaynağı daemon'un dosya sisteminde çözülür | Betikler imaja gömülür (bugünkü durum) | Faz 0A'da yaşandı (commit `4587692`) |
| `SKIP_BUILD=1` iken sınanan uygulama imajı değişiyor | `compose build e2e` ya da `run --build`, `depends_on` nedeniyle `app`'i yeniden derler | e2e imajı `docker build` ile, e2e `run --no-deps` ile çalıştırılır (bugünkü `smoke.sh`) | Faz 0A'da yaşandı (commit `4587692`) |
| ESLint ile Prettier birbirinin biçimini hata sayıyor | ESLint Vue biçim kuralları Prettier ile çakışır | `@vue/eslint-config-prettier/skip-formatting` `eslint.config.js`'de en sonda kalır; biçim için `pnpm format` | Faz 0A'da yaşandı (commit `c53186b`) |
| `ERR_PNPM_IGNORED_BUILDS` | pnpm 12 derleme betiği olan bağımlılık için açık karar ister | `web/pnpm-workspace.yaml` → `allowBuilds` altına gerekçeli `true`/`false` (`vue-demi: false` örneği) | Faz 0A'da yaşandı; hata doğrulandı |
| `datcollate` sütunu `en_US.utf8` gösteriyor, `C.UTF-8` bekleniyordu | `builtin` sağlayıcıda yerel ayar `datlocale` sütunundadır; `datcollate` libc değerini taşır | `datlocale` sorgulanır (`TestDatabaseLocaleTests` gibi) | Plan (Task 8) ile kod farkı; doğrulandı |
| Shell, 2xx HTML gövdesinde ham `SyntaxError` ile düşüyordu | `response.json()` hatası sarılmıyordu | Bugün `ApiError` olarak sarılır | Faz 0A'da yaşandı (commit `e0306bc`) |
| `String::toUpperCase` build'den geçiyordu | Error Prone metot referansını yakalamaz; ArchUnit kuralı yalnızca çağrıya bakıyordu | Kural erişimleri (çağrı ve referans) eşler; birleşik `@Scheduled` da yakalanır | Faz 0A'da yaşandı (commit `6bdfc02`) |
| Frontend lisans denetimi `A AND B` ifadesini yanlış geçiriyordu | `AND` ifadesi `OR` gibi değerlendiriliyordu | `license-expression.mjs` SPDX önceliğiyle değerlendirir | Faz 0A'da yaşandı (commit `6ce92ce`) |
| `required variable POSTGRES_PASSWORD is missing a value` | `deploy/compose/.env` yok | `deploy/compose/init-env.sh` | Doğrulandı |
| 5432 portu dolu | Başka bir PostgreSQL çalışıyor | `.env`'e (ya da kabuk ortamına) `POSTGRES_PORT=5433`; backend `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/erp_platform` ile çalıştırılır | Kabuk ortamı ve 55432 portuyla doğrulandı |
| `Could not find a valid Docker environment` | Docker Desktop kapalı | Docker Desktop başlatılır | Beklenen; doğrulanmadı |
| Yerelde `readiness` `DOWN`, `/api/v1/system/info` çalışıyor | `dev` profili verilmedi, `.env` yok ya da `.env` yeniden üretildi ve volume eski şifreyi taşıyor | `--spring.profiles.active=dev`; `.env` yeniden üretildiyse dev volume'u sıfırlanır | Beklenen; profil durumu doğrulandı |
| Vite'ta "Sunucuya ulaşılamıyor", günlükte `http proxy error ... ECONNREFUSED` | Backend 8080'de çalışmıyor | Backend başlatılır (Bölüm 3.4) | Doğrulandı |
| Backend lisans denetimi başarısız | İzinli listede olmayan ya da farklı yazılmış bir lisans adı | `app/target/generated-sources/license/THIRD-PARTY.txt` incelenir; yazım farkıysa `app/pom.xml` `licenseMerges`, değilse bağımlılık değişir ya da ADR açılır | `ci.md` |
| e2e imajında Playwright tarayıcısı ile paket uyumsuz | `e2e.Dockerfile` etiketi ile `@playwright/test` sürümü farklı | İkisi birlikte güncellenir | `e2e.Dockerfile` yorumu |
| TypeScript 7 yükseltmesinde lint kırılıyor | `typescript-eslint` 8.71 peer aralığı `<6.1.0` | TypeScript 6.0.3'te kalır (ADR-0029, `renovate.json`) | Plan |
| `spotless:check` başarısız | Java biçimi palantir'e uymuyor | `./mvnw spotless:apply` | Doğrulandı |
| `Could not find the selected project in the reactor: spikes/s1-tenancy` | `-Pspikes` verilmedi; spike varsayılan reaktörde değil | Komut `./mvnw -Pspikes -pl spikes/s1-tenancy ...` biçiminde yazılır | Doğrulandı |
| Spike derlemesi `erp-parent` ya da `erp-platform-test`'i bulamıyor | Ürün modülleri yerel Maven deposuna kurulmadı ya da `platform/` değişti | `./mvnw -q install -DskipTests` | Beklenen; hata metni doğrulanmadı |
| Spike açılışında `MissingTenantContextException` (ör. `jooqConfiguration` bean'inde) | Bir çerçeve bileşeni açılışta tenant'sız bağlantı istiyor: jOOQ diyalekti, `ddl-auto`, Modulith şema kurulumu ya da sağlık ayarı eksik | `application.yaml`'daki "start-up" yorumlu ayarlar geri konur (B3–B8); `StartupIsolationTests` çalıştırılır | Faz 0B'de yaşandı; jOOQ durumu doğrulandı (Bölüm 4.7) |
| `Unable to determine Dialect without JDBC metadata` | Hibernate 7.4.5'te metadata erişimi açık; reddedilen bağlantıyı yutup diyalekti seçemiyor | `hibernate.boot.allow_jdbc_metadata_access=false` + `jakarta.persistence.database-product-name` + `database-major-version` (B6) | Faz 0B'de yaşandı; negatif kontrol testi |
| `BeanDefinitionOverrideException` (`jooqConfiguration`) | Yapılandırma sınıfının adı `JooqConfiguration`; Boot'un jOOQ bean'iyle aynı ad | Sınıf `JooqListenerConfiguration` adını alır (B13) | Faz 0B'de yaşandı (Task 6) |
| CGLIB `Cannot subclass final class` (kayıt defteri alt sınıfı) | Modulith'in transactional metotları proxy ister | `TenantSafeEventPublicationRegistry` `final` değildir | Faz 0B'de yaşandı |
| Spring Data repository bean'i oluşmuyor | Repository arayüzü başka bir sınıfın içinde (iç içe) tanımlı; varsayılan tarama iç içe repository'leri almaz | Repository üst düzey arayüz olarak yazılır (`SampleRecordRepository`) | Faz 0B prototipinde yaşandı |
| Testte verilen ayar etkisiz kalıyor | `SpringApplicationBuilder.properties()` varsayılan özellik verir, `application.yaml` onu ezer | Ayarlar komut satırı argümanı olarak verilir (`SpikeContexts.start`) | Faz 0B prototipinde yaşandı |
| Test sırasına bağlı `Failed to start bean 'schedulerLifecycle'`, `RejectedExecutionException` | db-scheduler `stop()`'tan sonra başlatılamaz; Spring 7 önbellekteki test bağlamını duraklatıp yeniden başlatır | Yaşam döngüsü `isPauseable() = false` (B20) | Faz 0B'de yaşandı (commit `117f78c`) |
| Testte beklenen istisna yerine Spring'in sarmaladığı istisna geliyor | Spring reddi `DataSourceUtils.getConnection`'da sarar, jOOQ'nun kullandığı `doGetConnection`'da sarmaz | En içteki neden doğrulanır (`SpikeAssertions.assertRootCause`, B9) | Faz 0B'de yaşandı |
| İkinci tenant'a yazılan satır ilk tenant'ın DB'sinde | `SUPPORTS`/`NOT_SUPPORTED` kapsamında koruma yalnızca gerçek transaction'a bakıyordu | Koruma senkronizasyona da bakar (B12) | Faz 0B'de yaşandı (commit `117f78c`) |
| jOOQ, aynı transaction'daki JPA yazmasını görmüyor | Hibernate atanmış kimlikli INSERT'ü flush'a erteler | `FlushBeforeJooqQueryListener` (B13); dinleyicisiz `DSLContext` kullanılmaz | Faz 0B'de gösterildi (`JooqReadTests`) |
| Tekrarlayan platform işi açılışta her tenant'a havuz açıyor | db-scheduler `FixedDelay` ilk çalışmayı hemen yapar | `DelayedFixedDelay` (B16) | Plan 0B tasarım kararı 8; commit `dafa8dc` |
| Spike derlemesinde `[NotJavadoc]` uyarısı (`JobsConfiguration.java:[67,13]`) | Anonim sınıf içindeki metotta Javadoc yorumu | Uyarıdır, build'i kırmaz; düz yoruma çevrilebilir | Doğrulandı |
| `Could not find the selected project in the reactor: spikes/s2-identity` | `-Pspikes` verilmedi | `./mvnw -Pspikes -pl spikes/s2-identity ...` | Doğrulandı |
| S2 girişinde whitelabel 500, yığında `JdbcSession.save` → `DataSourceTransactionManager.doBegin` → `TenantDataSourceStandIn` | Spring Session'a kendi transaction kancası verilmedi; varsayılan (tenant) DataSource'un yöneticisini aldı. Yer tutucunun `No tenant is bound` mesajı burada yanıltıcıdır | `springSessionTransactionOperations` bean'i, `defaultCandidate = false` (Bölüm 7.14) | Faz 0C'de yaşandı (S2 B7); Bölüm 4.8 alıştırması |
| `No bean named 'transactionTemplate' available` | Spring Session kancası varsayılan aday yapıldı; Boot `transactionTemplate`'i kurmadı | `defaultCandidate = false` geri konur | Kaynak okuması (S2 B7); Bölüm 4.8 alıştırmasıyla doğrulandı |
| Keycloak callback'i `error=invalid_request` (`Missing parameter: code_challenge_method`) ile dönüyor | Spring Security 7.1.1 gizli istemcide PKCE'yi kendiliğinden eklemez, Keycloak istemcisi PKCE ister | Kayıtta `requireProofKey(true)` | Faz 0C'de gözlendi (S2 B5) |
| Redirect URI'sinde `{registrationId}` `keycloak` yerine issuer'ın host adına açılıyor | `ClientRegistrations.fromIssuerLocation` kaydı issuer'ın host'uyla adlandırır | `registrationId("keycloak")` açıkça verilir | Faz 0C'de gözlendi (S2 B5) |
| Test kullanıcısı `mm` oluşturulamıyor: 400 `error-invalid-length` | Keycloak 26.8 user profile'ı kullanıcı adı için en az 3 karakter ister | Fixture yalnızca test realm'inde alt sınırı 2'ye indirir; üretim realm'ine taşınmaz | Faz 0C'de yaşandı (S2 B26) |
| Çıkıştan sonra yeni giriş formsuz tamamlanıyor, tarayıcı `?logout` adresine dönüyor | Spring'in varsayılan çıkışı yalnızca uygulama oturumunu bitirir, Keycloak SSO oturumu yaşar | `OidcClientInitiatedLogoutSuccessHandler`; dönüş adresi `{baseUrl}` kökü | Faz 0C'de yaşandı (S2 B9) |
| Keycloak yeni tenant host'unda `Invalid parameter: redirect_uri` (400) | `https://*.erp.test/*` gibi alt alan adı jokeri eşleşmez; `*` yalnızca yolun sonunda geçerlidir | Her tenant host'u `erp-web`'in redirect ve post-logout URI'lerine ayrıca eklenir (Faz 2 provisioning) | Faz 0C'de gözlendi; öğrenme testiyle sabit (S2 B18) |
| Oturum çerezi taşıyan bearer isteği `Set-Cookie: __Host-SESSION=...` alıyor, `last_access_time` ilerliyor | `SessionCreationPolicy.STATELESS` Spring Session arkasında oturum okumayı engellemez | Bearer zincirinin ilk filtresi `SessionlessRequestFilter` | Faz 0C'de yaşandı (S2 B22); Bölüm 4.8 alıştırması |
| Bearer ile bilinmeyen `/api` yolu 404 yerine 403 alıyor | `anyRequest().denyAll()` `/error` dispatch'ine de uygulanıyordu | Zincirin ilk kuralı `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` | Faz 0C'de yaşandı (S2 B23, commit `bde87e6`) |
| Geçersiz bearer token oturum çereziyle 200 alıyor | Bearer zinciri yokken istek tarayıcı zincirine düşüyordu | Zincir `Authorization: Bearer` başlığıyla eşleşir (`hasBearerToken`) | Faz 0C'de yaşandı (S2 B21) |
| `gitleaks dir ... spikes docs` yerel `deploy/compose/.env`'yi bildiriyor | gitleaks 8.30.1'de `dir` tek yol alır | Her yol için ayrı `gitleaks dir` (Bölüm 8.5) | Faz 0C'de yaşandı; doğrulandı |
| S2 testlerinde JDK `restricted header name` hatası (beklenen) | JDK HTTP istemcisi `Host` başlığını yasaklar | Surefire `argLine` `-Djdk.httpclient.allowRestrictedHeaders=host` (`spikes/s2-identity/pom.xml`) yerinde kalmalıdır | Beklenen; hata metni doğrulanmadı |
| Test çıktısında `WARNING: A Java agent has been loaded dynamically` ve `Mockito is currently self-attaching ...` | Mockito kendini çalışma anında JDK 25'e agent olarak yükler | Uyarıdır, build'i kırmaz; repo genelinde Surefire `argLine` kararıdır | S1 ve S2 çıktısında doğrulandı (S2 B27) |

## 10. Henüz yok ve açık kararlar

### 10.1 Bilerek sonraki fazlara bırakılanlar

| Konu | Bugünkü durum | Nerede planlı |
|---|---|---|
| S3 metadata ve belge dilimi (YAML kaynak, AG Grid satır ızgarası, kesinleştirme) | Yok | Plan 0D |
| Teyit listesi (Flyway/Liquibase lisansı, JasperReports + Java 25, springdoc, jOOQ ticari lisans), ADR durumlarının son denetimi, `spikes/` klasörünün ve kök `pom.xml`'deki `spikes` profilinin silinmesi, plan çıkış kontrollerindeki `gitleaks dir` komutunun düzeltilmesi | Yok; S1 ve S2 spike'ları `spikes/` altında duruyor | Plan 0E |
| İlk yeşil GitLab pipeline'ı, cosign anahtarları, Renovate botu | CI tanımı var, hiç çalışmadı | §20 soru 5, `docs/guides/ci.md` |
| Kernel: BaseEntity, UUIDv7, `@Version`, Money/Quantity, Clock, ProblemDetail, TurkishText | Ürün kodunda yok; `TenantKey`, `TenantContext` ve `UuidV7`'nin deneme sürümleri yalnızca spike'ta. K10 kural mesajı `TurkishText.fold()`'u anar ama sınıf yoktur | Faz 1 (§15.3) |
| ArchUnit kuralları K3, K5, K7, K13, K14; `@GeneratedValue`/`@SequenceGenerator` yasağı | Yok | Faz 1 |
| Tek tenant'lı routing DataSource, `TenantContext`, açılış izolasyon testi | Ürün kodunda yok; spike'ta kanıtlandı, "Faz 1 için girdiler" listesi ve zorunlu yapılandırma `docs/spikes/s1-tenancy.md`'de | Faz 1 |
| Migration orkestratörü, ürün Flyway kurulumu | Yok; spike'ta fixture Flyway API'siyle migration çalıştırıyor | Faz 2 (ADR-0038) |
| Giriş (BFF), oturum, CSRF, bearer zinciri | Ürün kodunda yok, tüm uçlar kimliksiz açık; S2 spike'ında kanıtlandı, yeniden yazılacak sınıflar, zincirler, kalite kapıları ve zorunlu yapılandırma `docs/spikes/s2-identity.md`, "Faz 1 için girdiler"de | Faz 1 |
| Rol ve kapsam kontrolü, `user_account` kaydı, cihaz kimliği yolu (ADR-0040 yol 3), mobil kullanıcı token'ı, `/api/v1/bootstrap` | Yok | Faz 3 |
| Metadata ve CRUD motoru | Yok | Faz 1 (v0), Faz 4 |
| Router, Pinia, vee-validate + Zod, openapi-typescript/openapi-fetch, AG Grid, meta-renderer, modül paketleri | Yok | Faz 1 ve sonrası (§9) |
| OpenAPI üretimi ve japicmp kırıcı değişiklik denetimi | Yok | §5.4, §8.7 |
| Örnek tenant ve test verisi üretici | Ürün kodunda yok; spike fixture'ında dört test tenant'ı (Bölüm 3.8) | Faz 2 ve sonrası |
| Compose `onprem` ve `monitoring` profilleri, `erpctl`, Caddy ile TLS, appliance imajı | Yok; S2'ye göre kurulum `internal-proxies` ayarını ve uygulama portunun dışarı açılmadığını denetlemelidir (S2 B17) | Faz 7 (§11, §14) |
| §14'teki modül, paket, müşteri, araç, appliance ve runbook klasörleri | Açılmadı (boş klasör açılmaz) | İlk içerikleri geldiğinde |

#### S1 spike'ından Faz 1'e kalan açık maddeler

`docs/spikes/s1-tenancy.md`, "Faz 1'de kapatılacak açık maddeler" bölümünden. Spike'ın sonucunu değiştirmezler; Faz 1 planına girmeleri gerekir.

| # | Madde | Faz 1'de yapılacak |
|---|---|---|
| 1 | Açılış izolasyon testi web sunucusu olmadan açılıyor (`WebApplicationType.NONE`) | Kalite kapısı servlet açılışıyla (`server.port=0`) `rejectedWithoutTenant == 0` doğrular |
| 2 | `spring.modulith.events.staleness.*` verilirse Modulith tenant'sız sorgu çalıştırır | Özellikler boş bırakılır; açılış testine "staleness bean'i yok" doğrulaması eklenir |
| 3 | B2'nin anlatımı eksik: kayıt defteri Spring'in oluşturma yolunu atladığı için kapanış geri çağrısı hiç kaydedilmez, `destroy()` çağrılmaz | Bypass bilinçli ve testli kurulur; B2 metni düzeltilir |
| 4 | ADR-0015'in "havuzlu optimizer yok" maddesi tasarım gereği karşılanıyor (UUIDv7) ama kanıtlanmadı | `@GeneratedValue`/`@SequenceGenerator` yasaklayan ArchUnit kuralı; ADR-0015 Doğrulama'sına not |
| 5 | Tenant geçiş koruması transaction'ı rollback-only işaretlemiyor; istisnayı yutan çağıran ilk tenant'a yazdıklarını commit edebilir | Koruma transaction'ı işaretler ya da bu istisnayı yakalamak kuralla yasaklanır; "yutulan istisna sonrası commit yok" testi |

#### Faz 2'ye devredilenler ve bekleyen kararlar

- Faz 2'ye devredilenler (`docs/spikes/s1-tenancy.md`): LRU havuz sınırı ve aktif bağlantısı olan havuzu kapatmama kuralı, tenant durum değişikliğinde havuz kapatma, tenant başına havuz metrikleri (B18); etkinlik göstergesiyle yeniden yayın, tenant başına iş örnekleri ve kotalar (B16); `scheduled_tasks`'ta tenant'a göre temizlik (B15); platform tabloları için modül şemaları (B19); 3 tenant'lı izolasyon paketi (ADR-0003).
- Spring Modulith'e bildirilecek iki konu (`databaseType` bean'i için koşul ya da özellik; `DefaultEventPublicationRegistry#destroy()`'un DB'ye gitmemesi) henüz açılmamıştır; bulgu raporuna göre Faz 1 başlamadan açılacaktır.
- Spec §6.12'deki "Olay iki kez teslim edilse bile iş bir kez planlanır" cümlesi, tekilleştirmenin iş satırı silinene kadar sürdüğü (B15) gerçeğiyle sınırlanmayı bekler. Mimari doküman onaylı kaynak olduğu için değişiklik proje sahibinin kararıdır.

#### S2 spike'ından Faz 1'e kalan açık maddeler

`docs/spikes/s2-identity.md`, "Faz 1'de kapatılacak açık maddeler" bölümünden. Spike'ın sonucunu değiştirmezler; Faz 1 planına girmeleri gerekir.

| # | Madde | Faz 1'de yapılacak |
|---|---|---|
| 1 | Keycloak kesintisinde giriş whitelabel 500 ve ERROR yığını veriyor; discovery bir kilit altında 30 sn bağlantı ve 30 sn okuma zaman aşımıyla bekleyebiliyor (bytecode okuması, ölçülmedi; S2 B6) | 503 sayfası ve tek satırlık log; negatif önbellek ya da geri çekilme, tek uçuş, kısa zaman aşımı |
| 2 | Kimliksiz oturum satırları platform DB'sinde birikiyor, temizlik yok (S2 B11, B12) | Süresi dolan oturumları silen db-scheduler işi; istek önbelleğinin kapsamı |
| 3 | Bearer zincirinin kenarları (S2 B24) | Sıkı `Bearer` matcher'ı, `.logout(disable)`, `resource_metadata` |
| 4 | Async dispatch'te tenant bağlamı yok (S2 B23) | Async uç nokta açılmadan tenant'ın async dispatch'e taşınması |
| 5 | Boot `InMemoryOAuth2AuthorizedClientService` bean'ini yine kuruyor (S2 B8) | Bastırılır ya da boş kaldığı testle sabitlenir |
| 6 | `IdentityProperties.toString` client secret'ı açık metinle içeriyor (S2 B27) | `toString` override ya da ayrı sır tipi |
| 7 | İç uç noktalar (ör. Prometheus) tarayıcı zincirinden 404 alıyor (S2 B25) | Ayrı zincir |
| 8 | Oturum serileştirmesi (S2 B13) | Kendi sınıflarda sabit `serialVersionUID`, gerekirse toleranslı deserializer |
| 9 | Güvenilen proxy aralığı bütün özel ağlar (S2 B17) | `internal-proxies` Caddy'ye daraltılır, uygulama portu dışarı açılmaz |
| 10 | Keycloak'ın dış ve iç adresi ayrışmıyor (S2 B17) | Compose'da issuer dış adres, discovery ve JWK erişimi iç adres |
| 11 | Güvenlik olayları eksik loglanıyor (S2 B28) | Hangi olayların, nereye (platform DB'si ya da yapılandırılmış log), hangi oran sınırıyla yazılacağı |

#### S2'den sonraki fazlara devredilenler

- Faz 2: provisioning adaptörü (organization, sabit tenant iddialı ve `erp-api` audience'lı entegrasyon istemcisi, her tenant host'unun `erp-web` redirect ve post-logout URI'lerine kaydı ve silinmesi), host araması önbelleği (Caffeine + LISTEN/NOTIFY), `tenant_domain` şeması ve organization bağının `tenant`'ta durması, `TenantKey` ile DNS etiketi uyumu, istemci yapılandırmasının büyümesinin izlenmesi (S2 B11, B18–B21).
- Faz 3: cihaz zinciri, mobil kullanıcı token'ı, `user_account` ve `sub` bağlama, organization'dan çıkarmada o tenant'ın oturumlarını silme, back-channel logout, oturumun mutlak ömrü, `/api/v1/bootstrap` ve bearer'daki durumu, tarayıcıda token olmadığını ve çerez özniteliklerini doğrulayan Playwright testi (S2 B9, B10, B16).
- Faz 7: `/oauth2/authorization/*` ve kimliksiz sayfa istekleri için oran sınırı, kurulumda `internal-proxies` ve uygulama portu denetimi (S2 B11, B17).
- İlk ayrı realm müşterisiyle: bearer'da `JwtIssuerAuthenticationManagerResolver` (güvenilen issuer'lar = `tenant.oidc_issuer`), tarayıcıda tenant'ın issuer'ına göre client registration ve çıkış uç noktası (S2 B4, ADR-0005 "Değişti").

### 10.2 Geliştirmeyi en çok etkileyen "Önerildi" ADR'ler

44 ADR'den 36'sı "Önerildi" durumundadır (`docs/adr/README.md`). Faz 0B'de ADR-0003, ADR-0012, ADR-0013, ADR-0014 ve ADR-0015, Faz 0C'de ADR-0006 ve ADR-0039 "Kabul edildi" durumuna geçmiştir. ADR-0005 Faz 0C'de "Değişti" durumuna geçmiştir: ayrı realm istisnasında çözümleyici değişmez ama güvenlik zincirleri tek issuer'a güvendiği için çoklu issuer yapılandırması gerekir (S2 B4). Bu ADR'ler aşağıdaki tablodan çıkarılmıştır (Bölüm 11). Kod yazarken en çok bunlar etkiler:

| ADR | Karar | Neden önemli | Doğrulanacağı yer |
|---|---|---|---|
| ADR-0011 | Yazma JPA, okuma jOOQ; ticari jOOQ lisansı | Repository ve sorgu kalıpları | Yazma/okuma ayrımı S1'de doğrulandı; lisans kararı Plan 0E |
| ADR-0004, ADR-0038 | Platform DB ayrımı; migration orkestratörü | Platform tabloları ve migration akışı | Faz 2 (platform DataSource düzeni S1'de, platform DB'sindeki oturum ve tenant kaydı S2'de denendi) |
| ADR-0040 | Üç kimlik yolu, her biri ayrı `SecurityFilterChain` | Her uç noktanın güvenlik ve tenant bağlamı | Yol 1 ve 2 S2'de doğrulandı; yol 3 (cihaz) Faz 3 |
| ADR-0024 | On-prem Linux + compose + `erpctl`; TLS Caddy'de | `forward-headers-strategy`, `internal-proxies`, uygulama portunun kapalılığı (S2 B17) | Faz 7 (S2 notu ADR'de) |
| ADR-0007, ADR-0018, ADR-0034, ADR-0037 | Metadata YAML, belge durum eksenleri, satır modeli, ledger | İş modüllerinin şekli | Plan 0D |
| ADR-0020, ADR-0021 | Vuetify 4 + `@erp/ui`; AG Grid Community | Önyüz bileşen katmanı | Plan 0D |
| ADR-0028 | Monorepo, GitLab CI | CI sağlayıcısı | §20 soru 5 |
| ADR-0029 | Başlangıç sürümleri | Bu kılavuzdaki sürümler | Plan 0E |

### 10.3 Mimari dokümandaki açık sorular

§20'deki sorulardan Faz 0'a ya da hemen yanıt bekleyenler:

| # | Soru (§20) | Ne zamana kadar |
|---|---|---|
| 1 | Ekip büyüklüğü ve rol dağılımı | Faz 0 öncesi |
| 2 | v1 müşterileri v4'e geçecek mi, v1 ne zaman dondurulacak | Faz 0 |
| 3 | v1'de sızmış sırlar iptal edildi mi | Hemen |
| 5 | GitLab ile mi devam edilecek | Faz 0 |
| 7 | Ürün adı ve paket kökü (`com.smart.erp` bugün kullanılıyor) | Faz 0 |
| 11 | Nötr referans modül mü, gerçek `catalog` + minimal `inventory` mı (ADR-0044) | Faz 0 |
| 13 | jOOQ ticari lisansı bütçelenecek mi (ADR-0011) | Faz 0 |

Diğer sorular (4, 6, 8, 9, 10, 12) sonraki fazlara aittir.

### 10.4 İnceleme kaynaklı açık sorular

`docs/open-questions.md`, tasarım incelemelerinden doğan ve henüz karara bağlanmamış soruları tutar; karar verilen soru, kararın yazıldığı yere bağlantıyla birlikte oradan silinir. Faz 0C'de açılan 1 ve 5 numaralı sorular S2 sonrası spec'e işlenerek kapanmıştır (§4.3, §6.3.1; commit `4719795`). Açık kalanlar:

| # | Soru | Güncel öneri | Ne zamana kadar |
|---|---|---|---|
| 2 | Önyükleme (`/api/v1/bootstrap`, §8.1) bearer istemcilere de açık mı? | Açık; bearer yanıtında CSRF alanları olmaz. Spike `/bootstrap`'ı tarayıcıya özel tutar | Faz 3 |
| 3 | BFF oturumunun mutlak ömrü ne olacak, Keycloak'taki iptal oturuma nasıl yansıyacak? | ID token'ın `auth_time`'ı ile mutlak ömür; organization'dan çıkarmada principal index'iyle oturum silme; `sub`'a göre bulma ya da back-channel logout | Faz 3 |
| 4 | Tenant'ı belirleyen başlık `X-Forwarded-Host` mı, yalnızca `Host` mu? | Boot varsayılanı kalır; `internal-proxies` Caddy'ye daraltılır, uygulama portu dışarı açılmaz | Faz 1 |

## 11. Faz geçmişi

### Faz 0A — Zemin (2026-10-05)

Plan: `docs/superpowers/plans/2026-10-05-faz-0a-zemin.md`. Commit'ler `1dce60f` ile `07cfd09` arasındadır (ikisi dahil 25 commit, dal `feat/faz-0a-zemin`).

**Eklenenler**

- Modüller: Maven reaktörü (`erp-parent`, `erp-platform`, `erp-platform-test`, `erp-app`); Spring Modulith modülü `system`; pnpm paketleri `@erp/ui`, `@erp/core`, `@erp/shell`, `@erp/web`, `@erp/e2e`.
- Uçlar: `GET /api/v1/system/info`, `/actuator/health` (liveness ve readiness grupları ile), `/actuator/info`.
- Kurallar: K1 (Modulith), K9 (gitleaks, `.gitignore`), K10 (Error Prone + ArchUnit), K11 (Error Prone), K12 (ArchUnit), backend ve frontend lisans izinli listeleri, `vue/no-v-html`.
- Araçlar: mise araç zinciri, Maven wrapper 3.9.16, enforcer, Error Prone 2.50.0, Spotless 3.10.3, ArchUnit 1.5.1, Testcontainers, CycloneDX SBOM, license-maven-plugin 2.7.1, Prettier, ESLint, vue-tsc, Vitest, Playwright, gitleaks, Renovate yapılandırması, GitLab CI tanımı, MR şablonu.
- Dağıtım: tek uygulama imajı (`app.Dockerfile`), `erp-postgres:local` imajı, compose `dev`/`ci`/`e2e` profilleri, `init-env.sh`, `smoke.sh`.
- Dokümanlar: `CONTRIBUTING.md`, `docs/guides/coding-rules.md`, `docs/guides/local-development.md`, `docs/guides/ci.md`, ADR şablonu ve dizini.

**ADR'ler ve spike'lar**

- ADR-0001…ADR-0044 "Önerildi" durumunda açıldı (commit `d605ab6`); kabul edilen ya da değişen ADR yoktur.
- Bu fazda spike yapılmadı (plan: "Bu plan spike içermez"); S1, S2, S3 ve teyit listesi Plan 0B–0E'ye bırakıldı.
- Mimari dokümanın durumu "Onaylandı (2026-10-05). Faz 0 başladı." olarak güncellendi.

**Faz içinde davranışı değişenler ve geliştiriciye etkisi**

| Commit | Değişiklik | Geliştiriciye etkisi |
|---|---|---|
| `e0306bc` | 2xx JSON olmayan gövde `ApiError` olarak sarılır | Önyüz hata işleyicileri tek tip hata alır |
| `c53186b` | ESLint biçim kuralları Prettier'e bırakıldı | Biçim yalnızca `pnpm format` ile düzeltilir |
| `4587692` | PostgreSQL init betikleri imaja gömüldü; e2e imajı ayrı derlenir, `--no-deps` ile çalışır | Init betiği değişikliği imaj derlemesi ve volume sıfırlaması ister |
| `86779d0` | Durdurma ve sıfırlama komutlarına `--profile dev` eklendi | Komutlar profil ile yazılır |
| `6bdfc02` | K10 metot referanslarını, K12 birleşik anotasyonları yakalar | `String::toUpperCase` ve birleşik `@Scheduled` build'i kırar |
| `f1ba3c3` | CI duman testi digest'i sınar | İmzalanan ve sınanan imaj aynıdır |
| `e66e7ef` | Boş/boşluk mod değeri ve yanlış DB şifresi testleri | Review Focus 1 ve 3'ün test kapsamı genişledi |
| `2dbc596` | Test PostgreSQL'i compose ile aynı initdb argümanlarıyla açılır; imaj sürümü Renovate ile eşlenir | Test ve dev veritabanı aynı yerel ayarı taşır |
| `8c4136d` | `.gitignore` `.env` yerine `.env*`, `.dockerignore` `**/.env*` | Her `.env` türevi git'e ve imaja girmez |
| `6ce92ce` | Lisans ifadelerinde `AND` doğru değerlendirilir | `A AND B` ancak ikisi de izinliyse geçer |
| `416be16` | CI rehberi, kodlama kuralları ve ADR metinleri dokümana hizalandı | — |

**Kılavuzda güncellenen bölümler**

Kılavuz bu fazda ilk kez oluşturuldu; Bölüm 1–11'in tamamı yazıldı.

### Faz 0B — S1 tenancy spike'ı (2026-10-05)

Plan: `docs/superpowers/plans/2026-10-05-faz-0b-s1-tenancy.md`. Dal `feat/faz-0b-s1-tenancy`, `feat/faz-0a-zemin`'in `e138193` commit'inden (Faz 0A kılavuzunun eklendiği commit) açılmıştır. Faz commit'leri `e8e3590` (plan) ile `4e1f6d6` arasındadır (ikisi dahil 14 commit). Spike sonuç raporu: `docs/spikes/s1-tenancy.md`.

**Eklenenler**

- Spike: `spikes/s1-tenancy/` (`erp-spike-s1-tenancy`), dört Modulith modülü (`kernel`, `tenancy`, `jobs`, `sample`), dört migration, 90 test; `spikes/README.md`.
- Build: kök `pom.xml`'de `spikes` profili; ürün reaktörü değişmedi.
- Paylaşılan test altyapısı: `PostgresTestcontainer.newContainer()` ve `PostgresTestcontainerTests` (`erp-platform-test` test sayısı 10'dan 11'e çıktı).
- Kurallar ve kapılar (yalnızca `-Pspikes`): açılış ve kapanış izolasyon testi, sağlık testi, transaction koruması testleri, spike'ın kendi K1, K10 ve K12 testleri (Bölüm 4.7). Ürün build'ine yeni kural eklenmedi.
- Uçlar: ürün uygulamasına yeni uç eklenmedi. Spike'ta HTTP ucu yoktur; yalnızca Actuator (`/actuator/health`) testlerde kullanılır.
- Araçlar: db-scheduler 16.12.0 (yalnızca spike); Boot 4.1.1'in yönettiği Hibernate 7.4.5.Final, jOOQ 3.21.7, Flyway 12.4.0, HikariCP 7.0.2 spike'ta ilk kez kullanıldı.
- Dokümanlar: `docs/spikes/s1-tenancy.md` (kanıt tablosu, B1–B20, Faz 1 girdileri ve açık maddeleri, Faz 2'ye devredilenler, upstream konuları).

**ADR'ler ve spike'lar**

- S1 spike'ı yapıldı; bütün iddiaları otomatik testlerle kanıtlandı (rapordaki kanıt tablosu).
- "Kabul edildi" durumuna geçenler: ADR-0003 (tenant başına veritabanı), ADR-0012 (olay kaydı tenant DB'sinde), ADR-0013 (UUIDv7, uygulama tarafı üreteç), ADR-0014 (db-scheduler), ADR-0015 (routing DataSource). Her birinin "Doğrulama" bölümüne "S1 sonucu" paragrafı eklendi; `docs/adr/README.md` dizini güncellendi.
- ADR-0011 "Önerildi" durumunda kaldı: yazma/okuma ayrımı doğrulandı, ticari jOOQ lisansı kararı plan 0E'dedir.
- Son incelemeden sonra ADR-0012, ADR-0014 ve ADR-0015'teki iddialar düzeltildi (commit `2cd2548`): transaction koruması senkronizasyonu da kapsar; iş tekilleştirmesi yalnızca iş satırı dururken geçerlidir; yeniden yayının ilk çalışması bir aralık sonradır.
- Kararlara etkisi: Faz 1 kernel'i kapsam API'li `TenantContext`, uygulama tarafı `UuidV7`, `@Version Long` ve jOOQ öncesi otomatik flush ile yazılacak; zorunlu yapılandırma bloğu ve açılış izolasyon testi Faz 1'in girdisidir (`docs/spikes/s1-tenancy.md`).

**Faz içinde davranışı değişenler ve geliştiriciye etkisi**

| Commit | Değişiklik | Geliştiriciye etkisi |
|---|---|---|
| `117f78c` | Tenant geçiş koruması senkronizasyon kapsamlarını da kapsar; zamanlayıcı yaşam döngüsü duraklatılamaz | `SUPPORTS`/`NOT_SUPPORTED` içinde tenant değiştirmek de hata verir; spike testleri sıra bağımsız çalışır |
| `117f78c` | Yeniden yayın testinin ilk çalışma kontrolü yeniden yazıldı (eski sürüm `FixedDelay` ile de geçiyordu) | `DelayedFixedDelay` gerçekten testle korunur |
| `2cd2548` | ADR-0012, ADR-0014, ADR-0015 düzeltmeleri | Tekilleştirmeye güvenilmez, işler idempotent yazılır |
| `4e1f6d6` | Ertelenen beş inceleme maddesi Faz 1 açık maddesi olarak kaydedildi | Faz 1 planı bu maddeleri içermelidir (Bölüm 10.1) |

**Kılavuzda güncellenen bölümler**

- Bölüm 1: giriş (ürün kodu ve spike ayrımı, ADR durumları), 1.2 (spike modülleri), 1.4 (yolun değişmediği notu); yeni 1.5 (tenant bağlamında yazma akışı).
- Bölüm 2: 2.1, 2.2, 2.4 tabloları, 2.5 Maven diyagramı; yeni 2.6 (spike'lar).
- Bölüm 3: 3.4 (test sayıları, süre, 8080 notu), 3.8 (spike test tenant'ları); yeni 3.10 (spike'ı çalıştırma).
- Bölüm 4: 4.1 ve 4.3 notları; 4.4'te K11 beklenen çıktısı (üç hata), 4.5'te TypeScript satır numarası, 4.6'da compose hata metni yeniden doğrulamaya göre düzeltildi; yeni 4.7 (spike kapıları ve iki alıştırma).
- Bölüm 5: 5.1 (`spikes/` imaja girmez), 5.3 (gitleaks commit sayısı, CI spike'ı çalıştırmaz), 5.4 (süre), 5.5 (spike'taki migration akışı).
- Bölüm 6: 6.3'e Plan 0B Review Focus tablosu; yeni 6.4 (tenant'a bağlı hata durumları).
- Bölüm 7: giriş, 7.2 (`newContainer()`); yeni 7.8–7.11 (spike modülleri, sınıf, akış ve ER diyagramları).
- Bölüm 8: giriş, 8.3 (spike migration tarifi), 8.4 (spike'ı etkileyen sürümler), 8.5 (spike adımı, commit kapsamı `spike`); yeni 8.6 (tenant bağlamında yazma, olay ve iş zinciri).
- Bölüm 9: Faz 0B'de yaşanan hatalar eklendi.
- Bölüm 10: S1 satırı çıkarıldı; Faz 1 açık maddeleri, Faz 2'ye devredilenler ve bekleyen kararlar eklendi; 10.2'den kabul edilen ADR'ler çıkarıldı.

### Faz 0C — S2 kimlik spike'ı (2026-10-06 – 2026-10-10)

Plan: `docs/superpowers/plans/2026-10-06-faz-0c-s2-kimlik.md`. Dal `feat/faz-0c-s2-kimlik`, `main`'in `e8d84c8` commit'inden açılmıştır. Faz commit'leri `7ceff7c` (plan) ile `7fc7be8` arasındadır (ikisi dahil 13 commit) ve merge commit `14f239b` ile `main`'e alınmıştır. Spec düzeltmeleri `docs/s2-spec-duzeltmeleri` dalındaki `4719795` commit'iyle gelmiş, merge commit `6187a6e` ile alınmıştır. Spike sonuç raporu: `docs/spikes/s2-identity.md`.

**Eklenenler**

- Spike: `spikes/s2-identity/` (`erp-spike-s2-identity`), dört Modulith modülü (`kernel`, `tenancy`, `identity`, `sample`), iki platform migration'ı, Keycloak test realm'i, 127 test; `spikes/README.md`'ye S2 satırı.
- Build: kök `pom.xml`'deki `spikes` profiline `spikes/s2-identity` eklendi; ürün reaktörü değişmedi. Yeni üçüncü taraf bağımlılık yoktur: eklenen starter'lar Boot 4.1.1 BOM'undandır (plan 0C, "Global Constraints").
- Kurallar ve kapılar (yalnızca `-Pspikes`): açılış ve çalışma boyunca tenant DataSource izolasyonu, oturum transaction kancasının kablolaması, giriş iddia kontrolü, host ile oturum kontrolü, bearer zincirinin oturuma dokunmaması, CSRF, Keycloak'sız açılış, spike'ın kendi K1, K10 ve K12 testleri (Bölüm 4.8). Ürün build'ine yeni kural eklenmedi.
- Araçlar: kılavuz denetimi betiği `scripts/check-guide.mjs` ve `scripts/check-guide.allow` (commit `e8d84c8`, faz dalı açılmadan önce `main`'e); Boot 4.1.1'in yönettiği Spring Security 7.1.1 ve Spring Session 4.1.1 ile Keycloak 26.8.0 konteyneri spike'ta ilk kez kullanıldı.
- Uçlar: ürün uygulamasına yeni uç eklenmedi. Spike'ta `GET /`, `GET /bootstrap`, `GET /api/whoami`, `POST /api/echo`, Spring Security'nin giriş (`/oauth2/authorization/keycloak`, `/login/oauth2/code/keycloak`) ve çıkış (`POST /logout`) uçları ile `/actuator/health/**` vardır.
- Dokümanlar: `docs/spikes/s2-identity.md` (kanıt tablosu, B1–B28, Faz 1 girdileri ve 11 açık madde), `docs/open-questions.md`.

**ADR'ler ve spike'lar**

- S2 spike'ı yapıldı; kanıt tablosundaki on iddianın hepsi otomatik testlerle kanıtlandı. Öğrenme testleri yazıldığı gibi geçti; fixture'daki tek sapma test kullanıcısı `mm`'nin Keycloak'ın kullanıcı adı alt sınırına takılmasıdır (S2 B26).
- "Kabul edildi" durumuna geçenler: ADR-0006 (BFF + `__Host-` oturum çerezi; tarayıcıda token yok), ADR-0039 (Spring Session JDBC, platform DB). `docs/adr/README.md` dizini güncellendi.
- "Değişti" durumuna geçen: ADR-0005. İstisna maddesindeki "kod değişmez" cümlesi doğru değildir: çözümleyici değişmez, ama ayrı realm için bearer ve tarayıcı zincirleri çoklu issuer'a göre yapılandırılmalıdır (S2 B4).
- "Önerildi" kalıp S2 notu alanlar: ADR-0040 (yol 1 ve 2 doğrulandı, yol 3 Faz 3'te), ADR-0004 (organization bağı `tenant`'ta, oturumlar platform DB'sinde), ADR-0024 (`forward-headers-strategy: native`, `internal-proxies`'in daraltılması).
- Spec'e işlenen düzeltmeler (commit `4719795`): §4.3'te Keycloak bağı `tenant` satırına taşındı, `tenant_domain` yalnızca host eşlemesi oldu (S2 B19); §6.3.1 "İstisna" satırı ADR-0005'e göre düzeltildi (S2 B4). `docs/open-questions.md`'deki 1 ve 5 numaralı sorular kapandı.
- Kararlara etkisi: Faz 1 kimlik katmanı rapordaki "Faz 1 için girdiler"e göre yeniden yazılacak: üç zincir (actuator 0, bearer 1, tarayıcı 2), ID token'da `iss` ve tam organization kümesi kontrolü, Spring Session'a platform DB üzerinde varsayılan aday olmayan transaction kancası, bearer zincirinde oturumun istekten gizlenmesi, zorunlu yapılandırma bloğu; 11 açık madde Faz 1 planına girer (Bölüm 10.1).

**Faz içinde davranışı değişenler ve geliştiriciye etkisi**

| Commit | Değişiklik | Geliştiriciye etkisi |
|---|---|---|
| `67cc7f8` | Plan Spring Security 7.1.1 ve Spring Session 4.1.1'e göre gözden geçirildi: transaction kancası varsayılan aday olmayan `springSessionTransactionOperations` bean'i oldu, PKCE kayıttan alındı, callback tekrarına karşı tek savunmanın iddia kontrolü olduğu yazıldı, `docs/open-questions.md` açıldı | Faz 1'de aynı tuzaklar (S2 B5, B7) baştan bilinir |
| `3a388ed` | Çıkış testi Keycloak'ın çıkışını izleyip yeni girişte formu bekleyecek şekilde düzeltildi (eski test SSO'nun bittiğini kanıtlamıyordu) | Çıkışın Keycloak SSO'sunu bitirdiği testle korunur |
| `bde87e6` | Bearer zinciri hata dispatch'ine izin verir; `SessionlessRequestFilter` her dispatch tipinde çalışır | Bilinmeyen `/api` yolu bearer'da 404 alır, `/error` dispatch'i oturuma dokunmaz (S2 B23) |
| `c18bc2f`, `7fc7be8` | Raporda kaynak ve bytecode okumasına dayanan iddialar işaretlendi; güvenlik olayı loglaması (S2 B28) ve ID token istisnasının sınırları (S2 B9) eklendi | Rapordaki her iddianın kanıt türü ayırt edilir; açık madde 11 Faz 1'e girer |
| `4719795` | Spec §4.3 ve §6.3.1 düzeltildi | Platform tablosu tasarımı organization bağını `tenant`'ta tutar |

**Kılavuzda güncellenen bölümler**

- Giriş: doğrulama notu, kaynak listesine plan 0C.
- Bölüm 1: giriş (S2 spike'ı, ADR durumları, bulgu numaralarının öneki), 1.2 (S2 modülleri), 1.3 (CSRF ve aynı origin), 1.4 ve 1.5 (yolun değişmediği notu); yeni 1.6 (kimlikli bir isteğin yolculuğu, zincir tablosu ve giriş akışı).
- Bölüm 2: 2.1 (`spikes` profili, kılavuz denetimi betiği), 2.4 (S2 raporu, açık sorular, plan 0C, Git'e girmeyen yollar), 2.5 Maven diyagramı, 2.6 (S2 dosyaları ve ayarları).
- Bölüm 3: 3.3 (Keycloak notu), 3.4 (süre, 8080 notu), 3.8 (S2 test tenant'ları, kullanıcıları ve istemcileri); 3.10 "Spike'ları çalıştırma" olarak genişletildi.
- Bölüm 4: 4.1 notu, 4.2'ye kılavuz denetimi, 4.3'e S2'nin önerdiği Faz 1 kapıları; yeni 4.8 (S2 kapıları ve beş alıştırma).
- Bölüm 5: 5.1, 5.3 (gitleaks commit sayısı, spike'lar CI'da yok), 5.4 (süre), 5.5 (S2 migration'ları).
- Bölüm 6: 6.2 Keycloak satırı, 6.3'e Plan 0C Review Focus tablosu; yeni 6.5 (kimliğe bağlı hata durumları).
- Bölüm 7: giriş, 7.2 (kullananlar); yeni 7.12–7.15 (S2 modülleri, sınıf, akış ve ER diyagramları).
- Bölüm 8: giriş, 8.3 (S2 migration'ları), 8.4 (Spring Security, Spring Session ve Keycloak sürümleri), 8.5 (S2 adımı, kılavuz denetimi, `gitleaks dir`); yeni 8.7 (kimlik zincirlerinin arkasına uç nokta).
- Bölüm 9: Faz 0C'de yaşanan hatalar eklendi.
- Bölüm 10: S2 satırı çıkarıldı; kimlik satırları ve teyit listesi güncellendi; S2'nin Faz 1 açık maddeleri ve sonraki fazlara devredilenler eklendi; 10.2'den ADR-0005, ADR-0006 ve ADR-0039 çıkarıldı, ADR-0040 ve ADR-0024 satırları eklendi; yeni 10.4 (`docs/open-questions.md`).
