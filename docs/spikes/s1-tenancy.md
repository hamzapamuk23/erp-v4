# S1 — Tenancy yığını spike'ı: sonuçlar

| | |
|---|---|
| **Kapsam** | v4-platform.md §15.3 S1: 2+ tenant DB ile routing DataSource + Hibernate 7 + jOOQ + Modulith olay kaydı + db-scheduler; bağlam yayılımı, tenant dolaşan yeniden yayın, §4.5'teki Hibernate tuzakları |
| **Kod** | `spikes/s1-tenancy/` (commit `dafa8dc`), Faz 0 kapanışında silinir |
| **Çalıştırma** | `./mvnw -q install -DskipTests && ./mvnw -Pspikes -pl spikes/s1-tenancy verify` (Docker gerekir) |
| **Sonuç** | 84 test, hepsi yeşil (2026-10-05). Doküman kararları ayakta; uygulama kuralları aşağıda. |
| **İlgili ADR'ler** | 0003, 0011, 0012, 0013, 0014, 0015 |

## Kapsam dışı

Alt alan adıyla tenant çözümleme ve BFF (S2), LRU havuz sınırı ve tenant durum değişikliğinde havuz kapatma (Faz 2), migration orkestratörü (Faz 2, ADR-0038), tenant başına iş kotaları ve `job_run` (Faz 2), 3 tenant'lı tam izolasyon paketi (Faz 2 çıkış kriteri).

## Kurulum

Tek PostgreSQL 18.6 sunucusu: `erp_platform` (`erp_platform` rolü), `erp_t_acme`, `erp_t_globex` (aktif), `erp_t_initech` (askıda) (`erp_tenant` rolü); `ghost` kayıtta aktif ama DB'si yok. Her DB `PUBLIC`'e kapalı. Migration'ları test fixture'ı Flyway API'siyle çalıştırır; uygulama açılışta şema kurmaz.

## Kanıt tablosu

| # | İddia | Test | Sonuç |
|---|---|---|---|
| 1 | Açılış, sağlık/metrik bağlama ve kapanış boyunca tenant'sız bağlantı isteği **0**, açılışta tenant havuzu açılmaz | `StartupIsolationTests`, `HealthTests` | ✅ |
| 2 | Yazmalar (JDBC, JPA) sadece bağlı tenant'ın DB'sine düşer; okumalar (JDBC, jOOQ) sadece onun satırlarını görür | `TenantRoutingTests`, `SamplePersistenceTests`, `JooqReadTests` | ✅ |
| 3 | Bağlamsız erişim, bilinmeyen ve askıdaki tenant hata verir; havuz açılmaz | `TenantRoutingDataSourceTests`, `TenantRoutingTests`, `SamplePersistenceTests`, `JooqReadTests` | ✅ |
| 4 | Transaction içinde tenant değiştirmek hata verir ve geri alır; tenant'sız transaction başlamaz | `TenantContextTests`, `TransactionGuardTests` | ✅ |
| 5 | Hibernate diyalekti (PostgreSQL 18) bağlantısız, yapılandırmadan gelir; metadata erişimi açılırsa açılış durur | `HibernateBootstrapTests`, `StartupIsolationTests#hibernateMetadataAccessWouldNeedATenantAtStartup` | ✅ |
| 6 | jOOQ, flush edilmemiş JPA yazımını görmez; flush dinleyicisi bunu otomatik çözer | `JooqReadTests` | ✅ |
| 7 | Olay yayını tenant'ın DB'sinde tutulur, async dinleyici yayıncının tenant'ında çalışır, tamamlanınca silinir; geri alınan transaction yayın yapmaz | `EventPublicationTests` | ✅ |
| 8 | db-scheduler işi kendi tenant'ında çalışır; `eventId` = iş örneği kimliği, tekrar teslim yeni iş üretmez; askıdaki tenant'ın işi görünür biçimde düşer ve sonra tekrar denenir | `TenantJobTests` | ✅ |
| 9 | Başarısız yayın tenant dolaşan işle kendi tenant'ında yeniden gönderilir; erişilemeyen tenant raporlanır, diğerleri devam eder; tekrarlayan iş açılışta değil bir aralık sonra çalışır | `TenantEventRepublisherTests` | ✅ |
| 10 | Bağlam havuzdaki thread'e sızmaz | `TenantTaskDecoratorTests` | ✅ |
| 11 | UUIDv7: uygulama üreteci geçerli, kesin artan (saat donsa da geri gitse de), thread'ler arası benzersiz; atanmış kimlikli yeni kayıt `persist` edilir | `UuidV7Tests`, `SamplePersistenceTests`, `HibernateUuidV7Tests` | ✅ |

Her tuzak, yapılandırması eklenmeden önce kırmızıya düşürülerek gözlendi: JPA eklenince açılış `EmbeddedDatabaseConnection.isEmbedded` içinde (B4), jOOQ eklenince `SqlDialectLookup.getDialect` içinde (B5), Modulith eklenince `JdbcEventPublicationAutoConfiguration.databaseType` içinde (B1) `MissingTenantContextException` ile durdu; kayıt defteri değişikliği kaldırılınca kapanış sayacı 1 oldu (B2).

## Bulgular

**B1 — Modulith 2.1.1, açılışta olay deposunun DataSource'unu sorgular.** `JdbcEventPublicationAutoConfiguration#databaseType` koşulsuz bir bean'dir ve `JdbcUtils.extractDatabaseMetaData` ile bağlantı açar; tipi (`DatabaseType`) paket içidir, `@ConditionalOnMissingBean` yoktur. Çözüm: `ModulithTenancySupport` (`InstantiationAwareBeanPostProcessor`) bean'i bağlantısız `POSTGRES` ile karşılar.

**B2 — Modulith 2.1.1, kapanışta da sorgular.** `DefaultEventPublicationRegistry#destroy()` tamamlanmamış yayınları listeler. Çözüm: `TenantSafeEventPublicationRegistry` (sorgusuz `destroy()`); tamamlanmamış yayınların raporu tenant dolaşan işin işidir.

**B3 — Modulith şema kurulumu özellik yokken açıktır.** `spring.modulith.events.jdbc.schema-initialization.enabled` yoksa koşul `matchIfMissing = true` ile eşleşir ve açılışta DDL çalışır (kaynak kod okuması; özelliğin belgelenmiş varsayılanı `false` olsa da). Özellik açıkça `false` yapılır; tablo migration'la (`platform_events.event_publication`, v2 düzeni) kurulur. `spring.modulith.events.jdbc.schema` tablo adına önek olarak eklenir, `search_path`'e dokunmaz.

**B4 — Boot, `ddl-auto` verilmezse açılışta bağlantı ister** (`HibernateDefaultDdlAutoProvider` → `EmbeddedDatabaseConnection.isEmbedded`). `spring.jpa.hibernate.ddl-auto: none` zorunlu.

**B5 — Boot, jOOQ diyalekti verilmezse açılışta bağlantı ister** (`SqlDialectLookup`). `spring.jooq.sql-dialect: POSTGRES` zorunlu.

**B6 — Hibernate 7.4.5:** metadata erişimi açıkken açılışta bağlantı ister, reddi yutar (`catch (Exception)`) ve sonra açıkça verilmiş `jakarta.persistence.database-product-name`'e rağmen "Unable to determine Dialect without JDBC metadata" ile durur. Doğru ayar: `hibernate.boot.allow_jdbc_metadata_access=false` + `jakarta.persistence.database-product-name=PostgreSQL` + `jakarta.persistence.database-major-version=18` → `PostgreSQLDialect`, sürüm 18.

**B7 — `AbstractRoutingDataSource.unwrap/isWrapperFor` o anki hedefe yönlendirir.** Boot'un metrik ve sağlık kodu (`DataSourceUnwrapper#safeUnwrap`) bunları tenant'sız çağırır ve her hatayı yutar. Routing DataSource bu iki metodu yönlendirmeden yanıtlar.

**B8 — İki DataSource'un Boot'la kablolanması:** platform DataSource `@Bean(defaultCandidate = false)` + `@PlatformDb` ile işaretlenince JPA, jOOQ, `JdbcClient`, `TransactionTemplate` ve Modulith routing DataSource'u tek aday olarak alır (`@Primary` gerekmez); Boot'un sağlık kodu platform DB'sini yine `db` bileşeni olarak denetler. `management.health.db.ignore-routing-data-sources=true` ile readiness = platform DB; tenant DB'leri readiness konusu değildir.

**B9 — Ret istisnası unchecked olmalı.** `MissingTenantContextException extends IllegalStateException`: sadece `SQLException` yakalayan kod (Boot'un gömülü DB yoklaması, jOOQ diyalekt tespiti) onu yutamaz, açılış gürültüyle durur. `Exception` yakalayan kod (Hibernate, Boot'un unwrapper'ı) için tek güvence `rejectedWithoutTenant` sayacı ve onu sıfırda tutan açılış testidir. Faz 1'de bu test (`StartupIsolationTests` eşdeğeri) kalıcı kalite kapısıdır. Spring, reddedilen bağlantıyı `DataSourceUtils.getConnection`'da sarar ama jOOQ'nun transaction-aware proxy'sinin kullandığı `doGetConnection`'da sarmaz; testler istisnayı "en içteki neden" üzerinden doğrulamalı.

**B10 — Modulith'in async dinleyicisi tenant ister.** Kayıt defterinin `markProcessing/markCompleted` çağrıları kendi transaction'larını (`REQUIRES_NEW`) dinleyici thread'inde açar. Boot 4.1, her `TaskDecorator` bean'ini `applicationTaskExecutor`'a (sanal thread'li) bileştirir; `TenantTaskDecorator` yeterli. Tamamlanma kaydı dinleyicinin transaction'ından sonra ayrı yazılır: dinleyici işini commit edip tamamlanma kaydı düşmeden çökerse olay yeniden teslim edilir. Dinleyiciler idempotent olmalı (B15).

**B11 — `TenantContext` için ThreadLocal + sadece kapsam API'si.** `run/call` dışında bağlama yolu yok; kapsam bitince önceki değer geri konur. `ScopedValue` (Java 25) aynı garantiyi verir, ama `TaskDecorator` zinciri, MDC, Spring Security ve Micrometer bağlam yayılımı ThreadLocal temellidir. Micrometer context-propagation `ScopedValue` desteği verince yeniden değerlendirilir.

**B12 — Transaction koruması kapsam girişinde yeterli.** JPA transaction'ı başlarken bağlantıyı aldığı için tenant'sız transaction başlayamaz; transaction içinde başka tenant'a geçiş `TenantContext.call`'da reddedilir. Bu koruma olmadan jOOQ ve Modulith, transaction'a bağlı bağlantıyı kullanmaya devam eder ve **sessizce** ilk tenant'a yazar: korumanın varlık nedeni budur.

**B13 — jOOQ öncesi flush'ı altyapı yapmalı.** Tuzak gerçek (atanmış UUID ile Hibernate INSERT'ü flush'a erteler). `FlushBeforeJooqQueryListener` (jOOQ `ExecuteListener`) aktif ve salt-okunur olmayan JPA transaction'ında her jOOQ sorgusundan önce flush eder; Hibernate'in native sorgu öncesi auto-flush'ının karşılığı. Öneri: Faz 1 kernel'inde varsayılan olsun (doğruluk > performans, §1.3); "CRUD motoru ve belge çatısı flush etmeyi hatırlar" kuralı yerine. Not: bu dinleyiciyi kaydeden yapılandırma sınıfı `JooqConfiguration` adını alamaz; Boot'un jOOQ otomatik yapılandırmasında `jooqConfiguration` adlı bir bean zaten var.

**B14 — UUIDv7: uygulama tarafı üreteç.** `kernel.UuidV7` (RFC 9562 method 1: 12 bit sayaç + 62 bit rastgele; saat donsa da geri gitse de kesin artan). Hibernate 7.4.5'in `@UuidGenerator(style = VERSION_7)`'si geçerli ve monoton v7 üretiyor, ama `@Incubating`, saati `Instant.now()`'dan alıyor (`Clock` enjekte edilemez) ve kimliği ancak `persist` anında veriyor. Uygulama üreteciyle kimlik kurulumda bilinir (olay, idempotency anahtarı, aynı transaction'da bağlama). `@Version Long` (sarmalayıcı) sayesinde Spring Data atanmış kimlikli yeni kaydı `persist` eder; `long` olsaydı `merge` eder ve önce SELECT atardı.

**B15 — db-scheduler:** platform DB'sinde, `ExecutionInterceptor` iş verisindeki tenant'ı (`TenantScopedTaskData`) bağlar ve iş bitince kaldırır. İş verisi Jackson 3 ile JSON (`{"tenantKey":"globex","sampleId":"…"}`), platform DB'sinde okunur. İş örneği kimliği = `eventId`, `scheduleIfNotExists` ikinci denemede `false` döner. Askıdaki tenant'ın işi `TenantNotAvailableException` ile düşer, `consecutive_failures=1`, 5 dakika sonra tekrar denenir. Faz 2: geri yükleme el kitabının 5. adımı (§4.7.1, "o tenant'a ait işler temizlenir") için `scheduled_tasks`'ta tenant'a göre sorgu gerekir. Seçenekler: iş örneği kimliğine tenant öneki (`<tenant>:<eventId>`) ya da JSON'dan üretilmiş (generated) bir kolon + index.

**B16 — Tenant dolaşan yeniden yayının maliyeti.** Her çalışmada her ACTIVE tenant için havuz açar; §6.12'nin "boşta duran tenant'lar için havuz açılmaz" ilkesine aykırı. Spike'ta ilk çalışma bir aralık ertelendi (`DelayedFixedDelay`), böylece açılış havuz açmaz. Faz 2: platform DB'sinde bir etkinlik göstergesi (son aktivite / bekleyen yayın işareti) ile sadece işi olan tenant'lar dolaşılır. Erişilemeyen tenant (DB yok) diğerlerini durdurmaz, raporlanır; her çalışmada yeniden denenir (Faz 2: geri çekilme).

**B17 — Boot 4 modüler otomatik yapılandırma:** Flyway otomatik yapılandırması sadece `spring-boot-flyway` sınıf yolundaysa çalışır. Spike `flyway-core`'u yalnızca fixture'da kullandı; açılışta migration yok. Faz 2 orkestratörü Flyway API'sini doğrudan kullanır, `spring-boot-starter-flyway` eklenmez. Flyway 12.4 (OSS) PostgreSQL 18.6'ya sorunsuz migration uyguladı.

**B18 — Havuz yaşam döngüsü (Faz 2'ye):** Havuz tenant başına bir kez oluşur ve kayıttaki durum değişikliğini (SUSPENDED, MAINTENANCE) görmez; durum değişikliği havuzu kapatmalıdır. LRU sınırında HikariCP `close()` kullanımdaki bağlantıları iptal eder (abort); sadece aktif bağlantısı olmayan havuz kapatılmalı. Tenant havuzları Boot'un Hikari metriklerine girmez (bean değiller); tenant başına `MeterBinder` gerekir.

**B19 — Platform tabloları spike'ta `public` şemasında.** Faz 2'de platform modülleri için de modül başına şema (`tenancy.tenant`, `jobs.scheduled_tasks`) kararı verilmeli; db-scheduler tablo adı şema önekiyle verilebilir.

## Faz 1 için girdiler

- `kernel`: `TenantKey` (biçim kuralı), `TenantContext` (kapsam API'si + transaction koruması), `MissingTenantContextException`, `TenantSwitchInTransactionException`, `UuidV7`; `BaseEntity`'de kimlik kurulumda atanır, `@Version Long`.
- `tenancy` (Faz 1'de tek tenant, aynı soyutlamalar): `TenantRoutingDataSource` (statik harita yok, `unwrap/isWrapperFor` yönlendirmez, ret sayacı), platform DataSource `defaultCandidate=false` + qualifier, `TenantTaskDecorator`, `FlushBeforeJooqQueryListener`, `ModulithTenancySupport`.
- Kalite kapısı: açılış/kapanış izolasyon testi (B9).
- Zorunlu yapılandırma (gerekçeleri B3–B8):

```yaml
spring:
  sql.init.mode: never
  jpa:
    open-in-view: false
    hibernate.ddl-auto: none
    properties:
      hibernate.boot.allow_jdbc_metadata_access: false
      jakarta.persistence.database-product-name: PostgreSQL
      jakarta.persistence.database-major-version: 18
      hibernate.cache.use_second_level_cache: false
  jooq.sql-dialect: POSTGRES
  modulith.events:
    jdbc:
      schema-initialization.enabled: false
      schema: platform_events
    republish-outstanding-events-on-restart: false
    completion-mode: delete
management.health.db.ignore-routing-data-sources: true
```

## Faz 2'ye devredilenler

LRU havuz sınırı ve "aktif bağlantısı olan havuzu kapatma" kuralı (B18); tenant durum değişikliğinde havuz kapatma (B18); tenant başına havuz metrikleri (B18); etkinlik göstergesiyle yeniden yayın ve tenant başına iş örnekleri, kotalar (B16, ADR-0014); `scheduled_tasks`'ta tenant'a göre temizlik (B15); platform şemaları (B19); 3 tenant'lı izolasyon paketi (ADR-0003).

## Upstream'e bildirilecekler

Spring Modulith için iki konu (Faz 1 başlamadan açılır, bağlantılar buraya eklenir): (1) `JdbcEventPublicationAutoConfiguration#databaseType` için `@ConditionalOnMissingBean` ya da bir `spring.modulith.events.jdbc.database-type` özelliği; (2) `DefaultEventPublicationRegistry#destroy()`'un DB'ye gitmemesi ya da kapatılabilmesi. İkisi de routing DataSource ile çok kiracılı kullanımı engelliyor.
