# Faz 0B — S1 Tenancy spike'ı Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** §15.3'teki S1 spike'ını yapmak: iki (ve daha fazla) tenant veritabanı üzerinde routing DataSource + Hibernate 7 + jOOQ + Spring Modulith olay kaydı + db-scheduler yığınının, açılışta/kapanışta tenant'sız hiçbir DB erişimi olmadan, bağlam yayılımıyla ve izolasyonla çalıştığını otomatik testlerle kanıtlamak; sonucu ADR-0003/0011/0012/0013/0014/0015'e işlemek.

**Architecture:** Spike, varsayılan reaktörün dışında `spikes/s1-tenancy/` altında (`-Pspikes` profiliyle derlenen) tek bir Spring Boot uygulamasıdır; dört Modulith modülü vardır: `kernel` (TenantKey, TenantContext, UuidV7), `tenancy` (platform DataSource, tenant'a yönlendiren DataSource, tenant dizini, TaskDecorator, Modulith ve jOOQ uyum parçaları), `jobs` (db-scheduler, tenant ExecutionInterceptor, tenant dolaşan yeniden yayın), `sample` (entity, jOOQ sorgusu, olay, dinleyici, iş). Testler tek bir PostgreSQL 18 konteynerinde platform DB + 3 tenant DB kuran bir fixture'a karşı çalışır; migration'ları fixture çalıştırır (`erpctl migrate` rolü), uygulama açılışta asla şema kurmaz. Merkezdeki kanıt, routing DataSource'un "tenant'sız istendim" sayacıdır: tam açılış, sağlık kontrolü ve kapanış boyunca sıfır kalmalıdır.

**Tech Stack:** Java 25, Spring Boot 4.1.1 (yönettiği: Hibernate ORM 7.4.5.Final, jOOQ 3.21.7 OSS, Flyway 12.4.0, HikariCP 7.0.2, PostgreSQL JDBC 42.7.13, Jackson 3.1.5, Spring Framework 7.0.9), Spring Modulith 2.1.1, db-scheduler 16.12.0, Testcontainers 2.0.5 (`postgres:18.6`), Awaitility (Boot BOM), JUnit 5, AssertJ.

**Spec:** [docs/architecture/v4-platform.md](../../architecture/v4-platform.md) — özellikle §4.2–§4.6 (tenant izolasyonu, platform DB, bağlam yayılımı, bağlantı yönetimi, migration), §6.1 (kernel), §6.12 (işler), §7.1 ve §7.7 (DB düzeni, transaction kuralları), §15.3 Faz 0 S1, §18 (ADR listesi). İlgili ADR'ler: [0003](../../adr/0003-tenant-basina-veritabani.md), [0011](../../adr/0011-jpa-yazma-jooq-okuma.md), [0012](../../adr/0012-olay-kaydi-tenant-db.md), [0013](../../adr/0013-uuidv7.md), [0014](../../adr/0014-db-scheduler.md), [0015](../../adr/0015-routing-datasource.md). Önceki plan: [2026-10-05-faz-0a-zemin.md](2026-10-05-faz-0a-zemin.md).

## Global Constraints

- **Spike atılacak koddur** (§15.3): `spikes/s1-tenancy/` varsayılan reaktörde değildir (`./mvnw verify` ve CI onu derlemez), yalnızca `-Pspikes` ile derlenir ve Faz 0 kapanışında (plan 0E) silinir. Kalıcı çıktılar: `docs/spikes/s1-tenancy.md`, ADR güncellemeleri, `PostgresTestcontainer.newContainer()` ve kök `pom.xml`'deki `spikes` profili.
- Sürümler (Boot 4.1.1 BOM'undan, Maven Central'dan doğrulandı): Hibernate **7.4.5.Final**, jOOQ **3.21.7**, Flyway **12.4.0**, HikariCP **7.0.2**, PostgreSQL JDBC **42.7.13**, Spring Modulith **2.1.1**, db-scheduler **16.12.0** (Apache-2.0, sürümü spike pom'unda; ürün BOM'una girmez).
- Paket kökü: **`com.smart.erp.spike.s1`**. Kod, tablo ve kolon adları İngilizce; dokümanlar Türkçe (§14).
- **Varsayılan tenant yoktur.** Bağlamsız tenant DB erişimi `MissingTenantContextException` (unchecked) fırlatır (§4.4 madde 4). Routing DataSource'ta statik hedef haritası ve `defaultTargetDataSource` kullanılmaz.
- **Migration ayrı adımdır** (§4.6): Hibernate `ddl-auto` kapalı, Modulith şema kurulumu kapalı, Flyway sadece test fixture'ında (`flyway-core`, test kapsamı). `spring-boot-flyway` otomatik yapılandırması eklenmez.
- **K9:** Repoda kimlik bilgisi yok. Fixture rol şifrelerini her çalıştırmada `UUID.randomUUID()` ile üretir.
- **K10, K11, K12** spike'ta da build'i kırar: Error Prone (parent'tan miras) + spike'ın `ArchitectureRulesTests`'i. `@Scheduled` yok; zamanlanmış iş db-scheduler'dadır.
- Hibernate multi-tenancy SPI'ı ve ikinci seviye cache kullanılmaz (ADR-0015). Hibernate filtreleri kullanılmaz.
- Yeni altyapı bileşeni yok (Redis, Kafka vb.).
- Commit'ler Conventional Commits; kapsam `spike` (ör. `feat(spike): ...`). Her commit şu satırla biter: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `git add` her zaman açık yollarla yapılır (depodaki izlenmeyen `docs/guides/gelistirici-kilavuzu.pdf` commit'e girmez).

## Ortam notu

Komutlar depo kökünde, `mise` etkin bir kabukta çalıştırılır. Kabuk etkin değilse (bu makinede `~/.zshrc` düzenlenmedi) her komutun başına `mise x --` eklenir: `mise x -- ./mvnw ...`. Docker Desktop çalışıyor olmalıdır (Testcontainers).

Spike, `erp-parent`'ı parent olarak, `erp-platform-test`'i test bağımlılığı olarak kullanır. Bu ikisi yerel Maven deposunda olmalıdır:

```bash
./mvnw -q install -DskipTests
```

Sık kullanılan komutlar:

| İş | Komut |
|---|---|
| Spike'ın tek test sınıfı | `./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=<Sınıf>` |
| Spike'ın tamamı (test + Spotless + Error Prone) | `./mvnw -Pspikes -pl spikes/s1-tenancy verify` |
| Spike biçimlendirme | `./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply` |
| Ürün reaktörü (spike hariç) | `./mvnw verify` |

## Tasarım kararları (plan içinde verilenler)

1. **`TenantContext` = ThreadLocal + sadece kapsam API'si** (`run`/`call`, `set`/`clear` yok). Kapsam bitince önceki değer geri konur; havuzdaki bir thread'de bayat tenant kalamaz. Java 25'in `ScopedValue`'su aynı garantiyi verir ama Boot'un `TaskDecorator` zinciri, MDC, Spring Security ve Micrometer bağlam yayılımı ThreadLocal temellidir; bu yüzden şimdilik ThreadLocal. (Bulgu olarak `docs/spikes/s1-tenancy.md`'ye girer.)
2. **Transaction koruması kapsam girişinde:** `TenantContext.call` aktif bir Spring transaction'ı varken farklı bir tenant (ya da transaction'dan sonra ilk kez bir tenant) bağlanmasını reddeder. JPA transaction'ı başlarken bağlantıyı aldığı için, tenant'sız başlayan transaction routing DataSource'ta düşer.
3. **Routing DataSource = `AbstractRoutingDataSource` alt sınıfı,** `determineTargetDataSource()` override'ı ile: tenant başına HikariCP havuzu tembel (`new HikariDataSource()` + setter, ilk `getConnection`'a kadar bağlantı yok), sadece `ACTIVE` tenant'lara. `unwrap`/`isWrapperFor` yönlendirmez (Spring'in varsayılanı yönlendirir ve Boot'un metrik/sağlık kodu hatayı yutar). `AbstractRoutingDataSource` olduğu için Boot'un `management.health.db.ignore-routing-data-sources` ayarı onu sağlıktan çıkarabilir.
4. **Platform DataSource `@Bean(defaultCandidate = false)` + `@PlatformDb` qualifier'ı:** JPA, jOOQ, `JdbcClient` ve Modulith tek aday olarak routing DataSource'u alır (prototipte doğrulandı, `@Primary` gerekmez); platform DB'si yine `db` sağlık göstergesini alır.
5. **Modulith 2.1.1 uyum parçası** (`ModulithTenancySupport`, bir `InstantiationAwareBeanPostProcessor`): `JdbcEventPublicationAutoConfiguration#databaseType` açılışta bağlantı açar (koşulsuz bean); `DefaultEventPublicationRegistry#destroy()` kapanışta tamamlanmamış yayınları sorgular. İkisi de bean adı + sınıf adıyla eşlenip bağlantısız karşılıkla değiştirilir. Modulith adları değiştirirse açılış/kapanış testi kırılır.
6. **jOOQ öncesi flush otomatik:** `FlushBeforeJooqQueryListener` (jOOQ `ExecuteListener`), aktif ve salt-okunur olmayan bir JPA transaction'ında her jOOQ sorgusundan önce `EntityManager.flush()` çağırır. Tuzak, dinleyicisiz bir `DSLContext` ile ayrıca gösterilir.
7. **UUIDv7 uygulama tarafında** (`kernel.UuidV7`, RFC 9562 method 1); Hibernate 7.4.5'in `VERSION_7` stratejisi karşılaştırma için öğrenme testiyle incelenir. `@Version Long` (sarmalayıcı) sayesinde Spring Data atanmış kimlikli yeni kaydı `merge` değil `persist` eder.
8. **db-scheduler:** platform DB'sinde, Jackson 3 ile JSON iş verisi, `ExecutionInterceptor` iş verisindeki tenant'ı bağlar, iş örneği kimliği olayın `eventId`'si, `scheduleIfNotExists` ile tekilleştirme. Tenant dolaşan yeniden yayın bir tekrarlayan platform işidir; ilk çalışması bir aralık sonra (açılışta her tenant için havuz açmamak için).
9. **Bağımlılıklar görev görev eklenir.** Her yeni bileşenin (JPA, jOOQ, Modulith, db-scheduler) açılışta tenant'a dokunup dokunmadığını, büyüyen `StartupIsolationTests` o görevde yakalar.

## Review Focus

1. **Bir çerçeve bileşeni açılışta, sağlık/metrik bağlamada ya da kapanışta routing DataSource'tan tenant'sız bağlantı ister ve hatayı yutar** (Hibernate `Exception` yakalar, Boot'un `DataSourceUnwrapper`'ı `Exception` yakalar): uygulama sağlıklı görünür ama bir şey sessizce yanlış yere gider ya da düşer. Beklenen: tam açılış, `/actuator/health` ve kapanış boyunca `rejectedWithoutTenant() == 0`. → Task 4 `StartupIsolationTests` + `TenantRoutingDataSourceTests` (`DataSourceUnwrapper`), Task 5/6/7/8'de aynı test büyür; Task 4 `HealthTests`.
2. **Dışarıdan gelen tenant anahtarı** (büyük harf, Türkçe karakter, `acme;drop`, boş, 31 karakter) herhangi bir DB araması ya da havuz açılmadan reddedilmeli; bilinmeyen ve askıdaki tenant'a havuz açılmamalı. → Task 1 `TenantKeyTests`, Task 4 `TenantRoutingDataSourceTests`.
3. **Açık bir transaction içinde tenant değiştirmek** (bir servisin işlem ortasında başka tenant'ı çağırması) sessizce ilk tenant'ın bağlantısına yazmamalı; hata vermeli ve geri almalı. → Task 1 `TenantContextTests`, Task 5 `TransactionGuardTests`.
4. **Bir tenant'ın DB'si erişilemez ya da tenant askıda:** o tenant'ın işi/yeniden yayını görünür biçimde başarısız olmalı ve sonra tekrar denenmeli; diğer tenant'lar devam etmeli; veri başka yere yazılmamalı. → Task 8 `TenantJobTests` (askıdaki tenant), Task 9 `TenantEventRepublisherTests` (DB'si olmayan tenant).
5. **Olayın iki kez teslimi** (dinleyici işini yaptı ama tamamlanma kaydı düşmedi; yeniden yayın) işi iki kez planlamamalı; **havuzdaki bir thread** önceki görevin tenant'ını taşımamalı. → Task 8 `TenantJobTests` (aynı `eventId` ikinci kez `false`), Task 4 `TenantTaskDecoratorTests` (tek thread'li havuzda sızıntı yok).

---

## Dosya haritası (bu planın sonunda)

```
erp-v4/
 ├─ pom.xml                                   + `spikes` profili (spikes/s1-tenancy)
 ├─ platform/platform-test/src/main/java/com/smart/erp/platformtest/postgres/PostgresTestcontainer.java
 │                                            + newContainer() (çoklu DB fixture'ları için)
 ├─ platform/platform-test/src/test/java/com/smart/erp/platformtest/postgres/PostgresTestcontainerTests.java
 ├─ spikes/
 │   ├─ README.md                             Spike'lar nedir, nasıl çalışır, ne zaman silinir
 │   └─ s1-tenancy/
 │       ├─ pom.xml                           erp-spike-s1-tenancy (parent: erp-parent)
 │       └─ src/
 │           ├─ main/java/com/smart/erp/spike/s1/
 │           │   ├─ SpikeApplication.java
 │           │   ├─ kernel/    TenantKey, TenantContext, MissingTenantContextException,
 │           │   │             TenantSwitchInTransactionException, UuidV7
 │           │   ├─ tenancy/   PlatformDb, PlatformDataSourceProperties, TenantDataSourceProperties,
 │           │   │             TenantStatus, TenantDescriptor, TenantDirectory, JdbcTenantDirectory,
 │           │   │             TenantNotAvailableException, TenantDataSourceFactory, TenantRoutingDataSource,
 │           │   │             TenantTaskDecorator, TenancyConfiguration, FlushBeforeJooqQueryListener,
 │           │   │             JooqConfiguration, ModulithTenancySupport, TenantSafeEventPublicationRegistry
 │           │   ├─ jobs/      TenantScopedTaskData, JsonTaskDataSerializer, TenantExecutionInterceptor,
 │           │   │             JobsProperties, EventRepublishProperties, DelayedFixedDelay,
 │           │   │             TenantEventRepublisher, RepublishReport, JobsConfiguration
 │           │   └─ sample/    SampleRecord, SampleRecordRepository, SampleQueries, SampleRecorded,
 │           │                 SampleService, ProcessSampleData, SampleProcessor, SampleJobs,
 │           │                 SampleRecordedListener
 │           ├─ main/resources/
 │           │   ├─ application.yaml
 │           │   └─ db/platform/V202610051200__tenant_registry.sql, V202610051201__scheduled_tasks.sql
 │           │      db/tenant/V202610051200__platform_events.sql, V202610051201__sample.sql
 │           └─ test/java/com/smart/erp/spike/s1/
 │               ├─ support/  SpikeDatabases, SpikeTestProperties, SpikeTestConfiguration, SpikeTest, SpikeContexts
 │               ├─ ModularityTests, ArchitectureRulesTests, SpikeDatabasesTests, StartupIsolationTests, HealthTests
 │               ├─ kernel/   TenantKeyTests, TenantContextTests, UuidV7Tests, HibernateUuidV7Tests,
 │               │            UuidOrder, MutableClock
 │               ├─ tenancy/  TenantDataSourceFactoryTests, TenantRoutingDataSourceTests, TenantTaskDecoratorTests,
 │               │            JdbcTenantDirectoryTests, TenantRoutingTests
 │               ├─ sample/   HibernateBootstrapTests, SamplePersistenceTests, TransactionGuardTests,
 │               │            JooqReadTests, EventPublicationTests, TenantJobTests
 │               └─ jobs/     TenantEventRepublisherTests
 └─ docs/
     ├─ spikes/s1-tenancy.md                  Bulgular, kanıt tablosu, Faz 1/2 girdileri
     └─ adr/                                  0003, 0011, 0012, 0013, 0014, 0015 + README dizini güncellenir
```

---

### Task 1: Spike modülü iskeleti ve `TenantContext`

**Files:**
- Modify: `pom.xml` (`spikes` profili)
- Create: `spikes/README.md`
- Create: `spikes/s1-tenancy/pom.xml`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/SpikeApplication.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantKey.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantContext.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/MissingTenantContextException.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantSwitchInTransactionException.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/ModularityTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/ArchitectureRulesTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/TenantKeyTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/TenantContextTests.java`

**Interfaces:**
- Consumes: `erp-parent` (Error Prone, Spotless, enforcer), `com.smart.erp.platformtest.architecture.ErpArchitectureRules` (`NO_LOCALE_LESS_CASE_CONVERSION`, `NO_SPRING_SCHEDULING`).
- Produces:
  - `record TenantKey(String value)`: biçim `[a-z][a-z0-9_]{1,29}`, aksi halde `IllegalArgumentException`; `toString()` değeri döner.
  - `final class TenantContext`: `static Optional<TenantKey> current()`, `static TenantKey require()` (yoksa `MissingTenantContextException`), `static void run(TenantKey, Runnable)`, `static <T> T call(TenantKey, Supplier<T>)` (transaction aktifken farklı tenant → `TenantSwitchInTransactionException`).
  - `class MissingTenantContextException extends IllegalStateException` (argümansız ctor).
  - `class TenantSwitchInTransactionException extends IllegalStateException` (`(TenantKey current /*nullable*/, TenantKey requested)`).
  - Maven: `./mvnw -Pspikes -pl spikes/s1-tenancy ...` çalışır.

- [ ] **Step 1: Ürün reaktörünü yerel depoya kur**

```bash
./mvnw -q install -DskipTests
```

Expected: hata yok; `~/.m2/repository/com/smart/erp/erp-platform-test/0.1.0-SNAPSHOT/` oluşur.

- [ ] **Step 2: Kök `pom.xml`'e `spikes` profilini ekle**

`pom.xml`'de `</build>` satırından sonra, `</project>`'ten önce:

```xml
    <profiles>
        <profile>
            <!-- Phase 0 spikes (doc §15.3): throwaway code outside the default reactor, deleted when Phase 0 closes.
                 Run with: ./mvnw -Pspikes -pl spikes/<name> verify (see spikes/README.md). -->
            <id>spikes</id>
            <modules>
                <module>spikes/s1-tenancy</module>
            </modules>
        </profile>
    </profiles>
```

- [ ] **Step 3: `spikes/README.md` yaz**

```markdown
# Faz 0 spike'ları

[v4-platform.md](../docs/architecture/v4-platform.md) §15.3'teki riskli kararları gerçek kodla sınayan **atılacak** projeler.

- Varsayılan Maven reaktöründe değildir: `./mvnw verify` ve CI bunları derlemez. Sadece `spikes` profiliyle derlenir.
- Ön koşul (bir kez ve `platform/` değiştikçe): `./mvnw -q install -DskipTests` (parent ve `erp-platform-test` yerel depoya kurulur).
- Çalıştırma: `./mvnw -Pspikes -pl spikes/<ad> verify`. Docker çalışıyor olmalıdır (Testcontainers).
- Sonuçlar `docs/spikes/<ad>.md` ve ilgili ADR'lerin "Doğrulama" bölümlerindedir. Faz 0 kapanışında (plan 0E) bu klasör silinir; kalıcı olan sadece dokümanlardır. Spike kodu ürün koduna kopyalanmaz; Faz 1/2 kodu bulgulara göre yeniden yazılır.

| Spike | Klasör | Sonuç dokümanı |
|---|---|---|
| S1 — Tenancy yığını | [`s1-tenancy/`](s1-tenancy/) | [docs/spikes/s1-tenancy.md](../docs/spikes/s1-tenancy.md) |
```

- [ ] **Step 4: `spikes/s1-tenancy/pom.xml` yaz**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.smart.erp</groupId>
        <artifactId>erp-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>erp-spike-s1-tenancy</artifactId>
    <name>ERP :: Spike :: S1 tenancy</name>
    <description>Phase 0 S1 spike (doc §15.3). Throwaway: deleted when Phase 0 closes; findings live in docs/spikes/s1-tenancy.md.</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <!-- TenantContext's transaction guard (doc §4.4 rule 7) -->
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.smart.erp</groupId>
            <artifactId>erp-platform-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: Uygulama sınıfını yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/SpikeApplication.java`:

```java
package com.smart.erp.spike.s1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * S1 tenancy spike (doc §15.3): routing DataSource, Hibernate, jOOQ, Modulith events and db-scheduler on several tenant
 * databases. Throwaway code; findings live in docs/spikes/s1-tenancy.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeApplication.class, args);
    }
}
```

- [ ] **Step 6: Mimari testlerini yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/ModularityTests.java`:

```java
package com.smart.erp.spike.s1;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void moduleStructureIsValid() {
        ApplicationModules.of(SpikeApplication.class).verify();
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/ArchitectureRulesTests.java`:

```java
package com.smart.erp.spike.s1;

import com.smart.erp.platformtest.architecture.ErpArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTests {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.smart.erp.spike.s1");

    @Test
    void k10NoLocaleLessCaseConversion() {
        ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION.check(PRODUCTION_CLASSES);
    }

    @Test
    void k12NoSpringScheduling() {
        ErpArchitectureRules.NO_SPRING_SCHEDULING.check(PRODUCTION_CLASSES);
    }
}
```

- [ ] **Step 7: `TenantKey` ve `TenantContext` için başarısız testleri yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/TenantKeyTests.java`:

```java
package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantKeyTests {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "globex_2", "a1", "abcdefghijklmnopqrstuvwxyz0123"})
    void acceptsLowerCaseAsciiKeys(String value) {
        assertThat(new TenantKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "a",
                "Acme",
                "ACME",
                "1acme",
                "_acme",
                "acme-co",
                "acme co",
                "acme;drop",
                "ışık",
                "abcdefghijklmnopqrstuvwxyz01234"
            })
    void rejectsEverythingElseBeforeAnyLookup(String value) {
        assertThatThrownBy(() -> new TenantKey(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant key");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new TenantKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void printsAsItsValue() {
        assertThat(new TenantKey("acme")).hasToString("acme");
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/TenantContextTests.java`:

```java
package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantContextTests {

    private static final TenantKey ACME = new TenantKey("acme");
    private static final TenantKey GLOBEX = new TenantKey("globex");

    @Test
    void nothingIsBoundOutsideAScope() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void aScopeBindsTheTenantOnlyForItsDuration() {
        assertThat(TenantContext.call(ACME, TenantContext::require)).isEqualTo(ACME);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void nestedScopesRestoreTheOuterTenant() {
        TenantContext.run(ACME, () -> {
            assertThat(TenantContext.call(GLOBEX, TenantContext::require)).isEqualTo(GLOBEX);
            assertThat(TenantContext.require()).isEqualTo(ACME);
        });
    }

    @Test
    void aFailingActionStillClearsTheScope() {
        assertThatThrownBy(() -> TenantContext.run(ACME, () -> {
                    throw new IllegalStateException("boom");
                }))
                .hasMessage("boom");
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void theBindingIsNotVisibleToOtherThreads() {
        AtomicReference<Optional<TenantKey>> seen = new AtomicReference<>();
        TenantContext.run(ACME, () -> join(Thread.ofVirtual().start(() -> seen.set(TenantContext.current()))));
        assertThat(seen.get()).isEmpty();
    }

    @Test
    void switchingTenantInsideATransactionIsRejected() {
        TenantContext.run(ACME, () -> inTransaction(() -> assertThatThrownBy(() -> TenantContext.run(GLOBEX, () -> {}))
                .isInstanceOf(TenantSwitchInTransactionException.class)
                .hasMessageContaining("from acme to globex")));
    }

    @Test
    void reenteringTheBoundTenantInsideATransactionIsAllowed() {
        TenantContext.run(
                ACME,
                () -> inTransaction(() ->
                        assertThat(TenantContext.call(ACME, TenantContext::require)).isEqualTo(ACME)));
    }

    @Test
    void bindingATenantAfterTheTransactionStartedIsRejected() {
        inTransaction(() -> assertThatThrownBy(() -> TenantContext.run(ACME, () -> {}))
                .isInstanceOf(TenantSwitchInTransactionException.class)
                .hasMessageContaining("before the transaction starts"));
    }

    /** Marks a transaction active the way Spring's transaction managers do, without a database. */
    private static void inTransaction(Runnable action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 8: Testleri çalıştır, derleme hatası gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='TenantKeyTests,TenantContextTests'
```

Expected: FAIL, `COMPILATION ERROR` — `cannot find symbol: class TenantKey`.

- [ ] **Step 9: `kernel` sınıflarını yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantKey.java`:

```java
package com.smart.erp.spike.s1.kernel;

import java.util.regex.Pattern;

/**
 * Identifies a tenant (doc §4.2). The value ends up in database names ({@code erp_t_<key>}) and log lines, so it is
 * restricted to lower-case ASCII letters, digits and underscore, 2–30 characters, starting with a letter.
 */
public record TenantKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{1,29}");

    public TenantKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tenant key: '" + value + "' (expected " + FORMAT.pattern() + ")");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/MissingTenantContextException.java`:

```java
package com.smart.erp.spike.s1.kernel;

/**
 * Tenant database access without a bound tenant (doc §4.4: there is no default tenant). Unchecked on purpose: code that
 * only catches {@link java.sql.SQLException} cannot swallow it.
 */
public class MissingTenantContextException extends IllegalStateException {

    public MissingTenantContextException() {
        super("No tenant is bound to this thread; tenant database access must run inside TenantContext.run/call"
                + " (doc §4.4)");
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantSwitchInTransactionException.java`:

```java
package com.smart.erp.spike.s1.kernel;

/**
 * A tenant scope opened while a transaction is active (doc §4.4 rule 7): the transaction's connection already belongs
 * to the previous tenant, or the transaction started without one.
 */
public class TenantSwitchInTransactionException extends IllegalStateException {

    public TenantSwitchInTransactionException(TenantKey current, TenantKey requested) {
        super(
                current == null
                        ? "Tenant " + requested + " must be bound before the transaction starts (doc §4.4 rule 7)"
                        : "Cannot switch tenant from " + current + " to " + requested
                                + " inside a transaction (doc §4.4 rule 7)");
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/TenantContext.java`:

```java
package com.smart.erp.spike.s1.kernel;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The tenant bound to the current thread (doc §4.4, §6.1). Binding is scope-only: {@link #run} and {@link #call} bind
 * for the duration of the action and restore the previous binding afterwards, so a pooled thread never keeps a stale
 * tenant. There is no setter and no default tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantKey> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<TenantKey> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantKey require() {
        TenantKey tenant = CURRENT.get();
        if (tenant == null) {
            throw new MissingTenantContextException();
        }
        return tenant;
    }

    public static void run(TenantKey tenant, Runnable action) {
        call(tenant, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs {@code action} with {@code tenant} bound. While a transaction is active only the tenant that is already bound
     * may be re-entered: the transaction's connection was taken for that tenant (doc §4.4 rule 7).
     */
    public static <T> T call(TenantKey tenant, Supplier<T> action) {
        Objects.requireNonNull(tenant, "tenant");
        TenantKey previous = CURRENT.get();
        if (TransactionSynchronizationManager.isActualTransactionActive() && !tenant.equals(previous)) {
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
    }
}
```

- [ ] **Step 10: Testleri çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='TenantKeyTests,TenantContextTests,ModularityTests,ArchitectureRulesTests'
```

Expected: `Tests run: 28, Failures: 0, Errors: 0` (`TenantKeyTests` 17 = 4 kabul + 11 ret parametresi + 2; `TenantContextTests` 8; `ModularityTests` 1; `ArchitectureRulesTests` 2), `BUILD SUCCESS`.

- [ ] **Step 11: Biçimlendir, tüm spike'ı doğrula, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add pom.xml spikes/README.md spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "feat(spike): add S1 tenancy spike module with scope-only TenantContext

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: `verify` → `BUILD SUCCESS`.

---

### Task 2: Uygulama tarafı UUIDv7 üreteci

**Files:**
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/UuidV7.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/UuidV7Tests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/UuidOrder.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/MutableClock.java`

**Interfaces:**
- Consumes: —
- Produces:
  - `final class UuidV7`: `public static UUID next()`; paket içi `UuidV7(Clock, RandomGenerator)` ve `UUID generate()` (testler için).
  - Test yardımcıları: `UuidOrder.UNSIGNED` (`Comparator<UUID>`, PostgreSQL `uuid` sırası), `MutableClock(Instant)` + `set(Instant)`.

- [ ] **Step 1: Test yardımcılarını yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/UuidOrder.java`:

```java
package com.smart.erp.spike.s1.kernel;

import java.util.Comparator;
import java.util.UUID;

/** PostgreSQL orders {@code uuid} values as unsigned bytes; {@link UUID#compareTo} compares signed longs. */
final class UuidOrder {

    static final Comparator<UUID> UNSIGNED = Comparator.comparing(UUID::getMostSignificantBits, Long::compareUnsigned)
            .thenComparing(UUID::getLeastSignificantBits, Long::compareUnsigned);

    private UuidOrder() {}
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/MutableClock.java`:

```java
package com.smart.erp.spike.s1.kernel;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock a test can move, including backwards. */
final class MutableClock extends Clock {

    private volatile Instant instant;

    MutableClock(Instant instant) {
        this.instant = instant;
    }

    void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
```

- [ ] **Step 2: Başarısız testi yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/UuidV7Tests.java`:

```java
package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class UuidV7Tests {

    private static final Instant NOON = Instant.parse("2026-10-05T12:00:00.123Z");

    @Test
    void producesVersion7WithTheRfcVariant() {
        UUID id = UuidV7.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheClocksMillisecond() {
        UuidV7 generator = new UuidV7(Clock.fixed(NOON, ZoneOffset.UTC), new SplittableRandom(1));
        assertThat(generator.generate().getMostSignificantBits() >>> 16).isEqualTo(NOON.toEpochMilli());
    }

    @Test
    void isStrictlyIncreasingWithinOneMillisecondAndAcrossCounterOverflow() {
        // More than 4096 ids in one frozen millisecond: the 12-bit counter overflows several times.
        UuidV7 generator = new UuidV7(Clock.fixed(NOON, ZoneOffset.UTC), new SplittableRandom(2));
        assertStrictlyIncreasing(generator::generate, 10_000);
    }

    @Test
    void staysIncreasingWhenTheClockStepsBack() {
        MutableClock clock = new MutableClock(NOON);
        UuidV7 generator = new UuidV7(clock, new SplittableRandom(3));
        UUID before = generator.generate();
        clock.set(NOON.minusSeconds(1));
        assertThat(UuidOrder.UNSIGNED.compare(generator.generate(), before)).isPositive();
    }

    @Test
    void theSharedGeneratorIsStrictlyIncreasing() {
        assertStrictlyIncreasing(UuidV7::next, 100_000);
    }

    @Test
    void isUniqueAcrossThreads() {
        var ids = ConcurrentHashMap.<UUID>newKeySet();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int thread = 0; thread < 8; thread++) {
                executor.execute(() -> {
                    for (int i = 0; i < 25_000; i++) {
                        ids.add(UuidV7.next());
                    }
                });
            }
        }
        assertThat(ids).hasSize(200_000);
    }

    private static void assertStrictlyIncreasing(Supplier<UUID> ids, int count) {
        UUID previous = ids.get();
        for (int i = 1; i < count; i++) {
            UUID current = ids.get();
            assertThat(UuidOrder.UNSIGNED.compare(current, previous))
                    .as("id #%d %s after %s", i, current, previous)
                    .isPositive();
            previous = current;
        }
    }
}
```

- [ ] **Step 3: Testi çalıştır, derleme hatası gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=UuidV7Tests
```

Expected: FAIL, `cannot find symbol: class UuidV7`.

- [ ] **Step 4: Üreteci yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/kernel/UuidV7.java`:

```java
package com.smart.erp.spike.s1.kernel;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Application-side UUIDv7 (RFC 9562 §5.7, ADR-0013): a 48-bit Unix-millisecond timestamp, a 12-bit counter in
 * {@code rand_a} (§6.2 method 1, seeded randomly in its lower half every millisecond) and 62 random bits. Ids from one
 * generator are strictly increasing in PostgreSQL {@code uuid} order, also when the clock stalls or steps back: the
 * counter keeps counting and, when it overflows, the timestamp moves one millisecond ahead.
 */
public final class UuidV7 {

    private static final UuidV7 SHARED = new UuidV7(Clock.systemUTC(), new SecureRandom());
    private static final int COUNTER_MAX = 0xFFF;
    private static final int COUNTER_SEED_BOUND = 1 << 11;

    private final Clock clock;
    private final RandomGenerator random;
    private long lastMillis = -1;
    private int counter;

    UuidV7(Clock clock, RandomGenerator random) {
        this.clock = clock;
        this.random = random;
    }

    public static UUID next() {
        return SHARED.generate();
    }

    synchronized UUID generate() {
        long now = clock.millis();
        if (now > lastMillis) {
            lastMillis = now;
            counter = random.nextInt(COUNTER_SEED_BOUND);
        } else if (++counter > COUNTER_MAX) {
            lastMillis++;
            counter = random.nextInt(COUNTER_SEED_BOUND);
        }
        long mostSignificant = (lastMillis << 16) | 0x7000L | counter;
        long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
```

- [ ] **Step 5: Testi çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=UuidV7Tests
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 6: Biçimlendir, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/src
git commit -m "feat(spike): add monotonic application-side UUIDv7 generator

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Çok veritabanlı test fixture'ı ve migration'lar

**Files:**
- Modify: `platform/platform-test/src/main/java/com/smart/erp/platformtest/postgres/PostgresTestcontainer.java`
- Test: `platform/platform-test/src/test/java/com/smart/erp/platformtest/postgres/PostgresTestcontainerTests.java`
- Modify: `spikes/s1-tenancy/pom.xml` (JDBC, PostgreSQL sürücüsü, Flyway test kapsamında)
- Create: `spikes/s1-tenancy/src/main/resources/db/platform/V202610051200__tenant_registry.sql`
- Create: `spikes/s1-tenancy/src/main/resources/db/platform/V202610051201__scheduled_tasks.sql`
- Create: `spikes/s1-tenancy/src/main/resources/db/tenant/V202610051200__platform_events.sql`
- Create: `spikes/s1-tenancy/src/main/resources/db/tenant/V202610051201__sample.sql`
- Create: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeDatabases.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/SpikeDatabasesTests.java`

**Interfaces:**
- Consumes: `TenantKey` (Task 1), `PostgresTestcontainer.IMAGE`.
- Produces:
  - `PostgresTestcontainer.newContainer()` → başlatılmamış, üretimle aynı ayarlı `PostgreSQLContainer`.
  - `SpikeDatabases`: sabitler `ACME`, `GLOBEX` (aktif), `INITECH` (askıda, DB'si var), `GHOST` (aktif kayıtlı ama DB'si yok), `PLATFORM_DATABASE = "erp_platform"`; `databaseName(TenantKey)` → `erp_t_<key>`; `jdbcUrl(String database)`; `applicationProperties()` → `Map<String, String>` (`erp.platform.datasource.{url,username,password}`, `erp.tenancy.datasource.{url-template,username,password}`; şablon `{database}` yer tutucusunu içerir); `tenantDatabase(TenantKey)`, `platformDatabase()`, `server()` → süper kullanıcı `JdbcClient` (routing'i atlayan bağımsız tanık); `connectAsTenantRole(String database)` → `Connection`.
  - Şema: platform DB'de `tenant(tenant_key, database_name, status)` ve db-scheduler `scheduled_tasks`; her tenant DB'sinde `platform_events.event_publication` (Modulith v2 düzeni) ve `sample.sample_record(id uuid pk, version bigint, name text, processed_at timestamptz)`.

- [ ] **Step 1: `PostgresTestcontainer` için başarısız testi yaz**

`platform/platform-test/src/test/java/com/smart/erp/platformtest/postgres/PostgresTestcontainerTests.java`:

```java
package com.smart.erp.platformtest.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PostgresTestcontainerTests {

    @Test
    void newContainerUsesTheProductionImageAndInitdbArguments() {
        PostgreSQLContainer container = PostgresTestcontainer.newContainer();
        assertThat(container.getDockerImageName()).isEqualTo(PostgresTestcontainer.IMAGE);
        assertThat(container.getEnvMap())
                .containsEntry(
                        "POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale-provider=builtin --builtin-locale=C.UTF-8");
    }
}
```

- [ ] **Step 2: Testi çalıştır, derleme hatası gör**

```bash
./mvnw -pl platform/platform-test test -Dtest=PostgresTestcontainerTests
```

Expected: FAIL, `cannot find symbol: method newContainer()`.

- [ ] **Step 3: `newContainer()`'ı ekle**

`PostgresTestcontainer.java`'nın tamamı (`IMAGE` satırı Renovate'in regex'i için aynen kalır):

```java
package com.smart.erp.platformtest.postgres;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The one place that decides which PostgreSQL image tests run against (doc §12.1). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final String IMAGE = "postgres:18.6";

    /** Same initdb arguments as deploy/compose/compose.yaml (doc §7.8). */
    private static final String INITDB_ARGS = "--encoding=UTF8 --locale-provider=builtin --builtin-locale=C.UTF-8";

    /** A container configured like production, not yet started; for fixtures that need more than one database. */
    public static PostgreSQLContainer newContainer() {
        return new PostgreSQLContainer(IMAGE).withEnv("POSTGRES_INITDB_ARGS", INITDB_ARGS);
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return newContainer();
    }
}
```

- [ ] **Step 4: platform-test testini ve ürün reaktörünü çalıştır, yerel depoya kur**

```bash
./mvnw -pl platform/platform-test test -Dtest=PostgresTestcontainerTests
./mvnw verify
./mvnw -q install -DskipTests
```

Expected: ilki `Tests run: 1, Failures: 0`; `./mvnw verify` → `BUILD SUCCESS` (app'in `TestDatabaseLocaleTests`'i bean yolunu hâlâ doğrular); kurulum hatasız.

- [ ] **Step 5: Spike pom'una JDBC, sürücü ve Flyway'i ekle**

`spikes/s1-tenancy/pom.xml`'de `spring-tx` bağımlılığının altına:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```

`erp-platform-test` test bağımlılığının altına:

```xml
        <dependency>
            <!-- Migrations run in the test fixture, the way `erpctl migrate` would (doc §4.6); never at application start-up.
                 Only flyway-core: Boot 4 auto-configures Flyway only when spring-boot-flyway is on the classpath. -->
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 6: Migration'ları yaz**

`spikes/s1-tenancy/src/main/resources/db/platform/V202610051200__tenant_registry.sql`:

```sql
-- Tenant registry in the platform DB (doc §4.3). Spike subset: no cluster, time zone or schema version columns.
create table tenant (
    tenant_key    text primary key check (tenant_key ~ '^[a-z][a-z0-9_]{1,29}$'),
    database_name text not null unique,
    status        text not null check (status in ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'MAINTENANCE', 'ARCHIVED'))
);
```

`spikes/s1-tenancy/src/main/resources/db/platform/V202610051201__scheduled_tasks.sql`:

```sql
-- db-scheduler 16.12.0 table (db-scheduler/src/test/resources/postgresql_tables.sql at tag v16.12.0), platform DB (ADR-0014).
create table scheduled_tasks (
    task_name            text                     not null,
    task_instance        text                     not null,
    task_data            bytea,
    execution_time       timestamp with time zone not null,
    picked               boolean                  not null,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures int,
    last_heartbeat       timestamp with time zone,
    version              bigint                   not null,
    priority             smallint,
    primary key (task_name, task_instance)
);

create index execution_time_idx on scheduled_tasks (execution_time);
create index last_heartbeat_idx on scheduled_tasks (last_heartbeat);
create index priority_execution_time_idx on scheduled_tasks (priority desc, execution_time asc);
```

`spikes/s1-tenancy/src/main/resources/db/tenant/V202610051200__platform_events.sql`:

```sql
-- Spring Modulith 2.1.1 event publication registry, v2 layout
-- (org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql), in its own schema (doc §7.1, ADR-0012).
-- Created by migration because Modulith's own schema initialization is switched off (it would run at start-up).
create schema platform_events;

create table platform_events.event_publication (
    id                     uuid                     not null,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index event_publication_serialized_event_hash_idx on platform_events.event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on platform_events.event_publication (completion_date);
```

`spikes/s1-tenancy/src/main/resources/db/tenant/V202610051201__sample.sql`:

```sql
-- The spike's only business table (doc §7.2 conventions: uuid id, bigint version, snake_case).
create schema sample;

create table sample.sample_record (
    id           uuid primary key,
    version      bigint      not null,
    name         text        not null,
    processed_at timestamptz
);
```

- [ ] **Step 7: Fixture için başarısız testi yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/SpikeDatabasesTests.java`:

```java
package com.smart.erp.spike.s1;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeDatabases;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpikeDatabasesTests {

    @Test
    void createsThePlatformAndTenantDatabasesButNotTheGhost() {
        List<String> databases = SpikeDatabases.server()
                .sql("select datname from pg_database")
                .query(String.class)
                .list();
        assertThat(databases)
                .contains("erp_platform", "erp_t_acme", "erp_t_globex", "erp_t_initech")
                .doesNotContain("erp_t_ghost");
    }

    @Test
    void registersTheFixtureTenants() {
        List<String> tenants = SpikeDatabases.platformDatabase()
                .sql("select tenant_key || '=' || status from tenant order by tenant_key")
                .query(String.class)
                .list();
        assertThat(tenants).containsExactly("acme=ACTIVE", "ghost=ACTIVE", "globex=ACTIVE", "initech=SUSPENDED");
    }

    @Test
    void migratesEveryTenantDatabase() {
        for (TenantKey tenant : List.of(ACME, GLOBEX, INITECH)) {
            assertThat(SpikeDatabases.tenantDatabase(tenant)
                            .sql("select to_regclass('sample.sample_record')::text")
                            .query(String.class)
                            .single())
                    .isEqualTo("sample.sample_record");
            assertThat(SpikeDatabases.tenantDatabase(tenant)
                            .sql("select to_regclass('platform_events.event_publication')::text")
                            .query(String.class)
                            .single())
                    .isEqualTo("platform_events.event_publication");
        }
    }

    @Test
    void theTenantRoleCannotConnectToThePlatformDatabase() {
        assertThatThrownBy(() -> {
                    try (Connection connection =
                            SpikeDatabases.connectAsTenantRole(SpikeDatabases.PLATFORM_DATABASE)) {
                        connection.isValid(1);
                    }
                })
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied for database");
    }
}
```

- [ ] **Step 8: Testi çalıştır, derleme hatası gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=SpikeDatabasesTests
```

Expected: FAIL, `cannot find symbol: class SpikeDatabases`.

- [ ] **Step 9: Fixture'ı yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeDatabases.java`:

```java
package com.smart.erp.spike.s1.support;

import com.smart.erp.platformtest.postgres.PostgresTestcontainer;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 18 server per test run holding the databases of doc §7.1: the platform DB and one DB per tenant, each
 * owned by its own role and closed to PUBLIC. Plays the part of {@code erpctl migrate}: migrations run here, never at
 * application start-up (doc §4.6).
 */
public final class SpikeDatabases {

    public static final TenantKey ACME = new TenantKey("acme");
    public static final TenantKey GLOBEX = new TenantKey("globex");
    /** Registered as SUSPENDED; its database exists and is migrated. */
    public static final TenantKey INITECH = new TenantKey("initech");
    /** Registered as ACTIVE, but its database was never created: an unreachable tenant. */
    public static final TenantKey GHOST = new TenantKey("ghost");

    public static final String PLATFORM_DATABASE = "erp_platform";

    private static final String PLATFORM_ROLE = "erp_platform";
    private static final String TENANT_ROLE = "erp_tenant";
    // Generated per run so the repository holds no credential literals (K9).
    private static final String PLATFORM_PASSWORD = UUID.randomUUID().toString();
    private static final String TENANT_PASSWORD = UUID.randomUUID().toString();
    private static final List<TenantKey> TENANTS_WITH_A_DATABASE = List.of(ACME, GLOBEX, INITECH);

    private static final PostgreSQLContainer POSTGRES = PostgresTestcontainer.newContainer();

    static {
        POSTGRES.start();
        createRolesAndDatabases();
        migrate(PLATFORM_DATABASE, PLATFORM_ROLE, PLATFORM_PASSWORD, "classpath:db/platform");
        for (TenantKey tenant : TENANTS_WITH_A_DATABASE) {
            migrate(databaseName(tenant), TENANT_ROLE, TENANT_PASSWORD, "classpath:db/tenant");
        }
        registerTenants();
    }

    private SpikeDatabases() {}

    public static String databaseName(TenantKey tenant) {
        return "erp_t_" + tenant.value();
    }

    public static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    /** What an installer would write into the application's configuration. */
    public static Map<String, String> applicationProperties() {
        return Map.of(
                "erp.platform.datasource.url", jdbcUrl(PLATFORM_DATABASE),
                "erp.platform.datasource.username", PLATFORM_ROLE,
                "erp.platform.datasource.password", PLATFORM_PASSWORD,
                "erp.tenancy.datasource.url-template", jdbcUrl("{database}"),
                "erp.tenancy.datasource.username", TENANT_ROLE,
                "erp.tenancy.datasource.password", TENANT_PASSWORD);
    }

    /** Superuser view of a tenant DB that bypasses the application's routing: the independent witness for isolation. */
    public static JdbcClient tenantDatabase(TenantKey tenant) {
        return superuser(databaseName(tenant));
    }

    public static JdbcClient platformDatabase() {
        return superuser(PLATFORM_DATABASE);
    }

    public static JdbcClient server() {
        return superuser(POSTGRES.getDatabaseName());
    }

    public static Connection connectAsTenantRole(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), TENANT_ROLE, TENANT_PASSWORD);
    }

    private static JdbcClient superuser(String database) {
        return JdbcClient.create(
                new DriverManagerDataSource(jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static void createRolesAndDatabases() {
        List<String> statements = new ArrayList<>(List.of(
                "create role " + PLATFORM_ROLE + " login password '" + PLATFORM_PASSWORD + "'",
                "create database " + PLATFORM_DATABASE + " owner " + PLATFORM_ROLE,
                "revoke connect on database " + PLATFORM_DATABASE + " from public",
                "create role " + TENANT_ROLE + " login password '" + TENANT_PASSWORD + "'"));
        for (TenantKey tenant : TENANTS_WITH_A_DATABASE) {
            statements.add("create database " + databaseName(tenant) + " owner " + TENANT_ROLE);
            statements.add("revoke connect on database " + databaseName(tenant) + " from public");
        }
        try (Connection connection =
                        DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the spike databases", e);
        }
    }

    private static void migrate(String database, String role, String password, String location) {
        Flyway.configure()
                .dataSource(jdbcUrl(database), role, password)
                .locations(location)
                .load()
                .migrate();
    }

    private static void registerTenants() {
        JdbcClient platform = JdbcClient.create(
                new DriverManagerDataSource(jdbcUrl(PLATFORM_DATABASE), PLATFORM_ROLE, PLATFORM_PASSWORD));
        for (TenantKey tenant : List.of(ACME, GLOBEX, INITECH, GHOST)) {
            platform.sql("insert into tenant (tenant_key, database_name, status) values (?, ?, ?)")
                    .param(tenant.value())
                    .param(databaseName(tenant))
                    .param(INITECH.equals(tenant) ? "SUSPENDED" : "ACTIVE")
                    .update();
        }
    }
}
```

- [ ] **Step 10: Testi çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=SpikeDatabasesTests
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`. Logda Flyway'in `Successfully applied 2 migrations` satırı dört kez görünür (platform + 3 tenant).

- [ ] **Step 11: Biçimlendir, commit**

```bash
./mvnw -pl platform/platform-test spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add platform/platform-test/src spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "test(spike): add multi-database fixture with platform and tenant migrations

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Tenant'a yönlendiren DataSource, platform DataSource ve bağlam yayılımı

**Files:**
- Modify: `spikes/s1-tenancy/pom.xml` (actuator, webmvc; test: webmvc-test)
- Create: `spikes/s1-tenancy/src/main/resources/application.yaml`
- Create (tümü `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/`): `PlatformDb.java`, `PlatformDataSourceProperties.java`, `TenantDataSourceProperties.java`, `TenantStatus.java`, `TenantDescriptor.java`, `TenantDirectory.java`, `JdbcTenantDirectory.java`, `TenantNotAvailableException.java`, `TenantDataSourceFactory.java`, `TenantRoutingDataSource.java`, `TenantTaskDecorator.java`, `TenancyConfiguration.java`
- Create (test destek, `.../spike/s1/support/`): `SpikeTestProperties.java`, `SpikeTestConfiguration.java`, `SpikeTest.java`, `SpikeContexts.java`, `SpikeAssertions.java`
- Test (`.../spike/s1/tenancy/`): `TenantDataSourceFactoryTests.java`, `TenantRoutingDataSourceTests.java`, `TenantTaskDecoratorTests.java`, `JdbcTenantDirectoryTests.java`, `TenantRoutingTests.java`
- Test (`.../spike/s1/`): `StartupIsolationTests.java`, `HealthTests.java`

**Interfaces:**
- Consumes: `TenantKey`, `TenantContext`, `MissingTenantContextException`, `UuidV7` (Task 1–2); `SpikeDatabases` (Task 3).
- Produces:
  - `@PlatformDb` (Spring `@Qualifier`); platform DataSource bean'i `platformDataSource` (`HikariDataSource`, `defaultCandidate = false`).
  - `record PlatformDataSourceProperties(String url, String username, String password)` — `erp.platform.datasource`.
  - `record TenantDataSourceProperties(String urlTemplate, String username, String password, int maximumPoolSize /*4*/, Duration idleTimeout /*5m*/, Duration connectionTimeout /*10s*/)` — `erp.tenancy.datasource`; şablonda `{database}` yoksa `IllegalArgumentException`.
  - `enum TenantStatus { PROVISIONING, ACTIVE, SUSPENDED, MAINTENANCE, ARCHIVED }`, `record TenantDescriptor(TenantKey key, String databaseName, TenantStatus status)`.
  - `interface TenantDirectory { Optional<TenantDescriptor> find(TenantKey); List<TenantKey> activeTenants(); }` (sıralı); bean: `JdbcTenantDirectory` (platform DB'si).
  - `class TenantNotAvailableException extends IllegalStateException` (`(TenantKey tenant, String reason)`; `tenant()`).
  - `final class TenantDataSourceFactory`: `TenantDataSourceFactory(TenantDataSourceProperties)`, `HikariDataSource create(TenantDescriptor)` (havuz adı `tenant-<key>`, bağlantı açmaz).
  - `class TenantRoutingDataSource extends AbstractRoutingDataSource`: `TenantRoutingDataSource(TenantDirectory, TenantDataSourceFactory)`, `long rejectedWithoutTenant()`, `Set<TenantKey> openPools()`, `void close()`; korumalı `DataSource determineTargetDataSource()`.
  - `final class TenantTaskDecorator implements TaskDecorator` (bean).
  - Test: `@SpikeTest` (= `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(SpikeTestConfiguration.class)`), `SpikeTestProperties.all()` → `Map<String, String>`, `SpikeContexts.start(String... args)` → `ConfigurableApplicationContext` (web yok, test bağlam önbelleği dışında), `SpikeAssertions.assertRootCause(ThrowingCallable)` → en içteki nedenin (sarılmamışsa istisnanın kendisinin) AssertJ iddiası.

- [ ] **Step 1: Pom'a actuator ve web'i ekle**

`spikes/s1-tenancy/pom.xml`'de `spring-boot-starter-jdbc`'nin altına:

```xml
        <dependency>
            <!-- Health/readiness and metrics bind to every DataSource bean: exactly where a routing DataSource gets probed. -->
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
```

`spring-boot-starter-test`'in altına:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Test destek sınıflarını yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeTestProperties.java`:

```java
package com.smart.erp.spike.s1.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Properties every spike test context gets: the fixture's connection settings plus test timings. */
public final class SpikeTestProperties {

    private SpikeTestProperties() {}

    public static Map<String, String> all() {
        Map<String, String> properties = new LinkedHashMap<>(SpikeDatabases.applicationProperties());
        properties.put("erp.tenancy.datasource.connection-timeout", "2s");
        return properties;
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeTestConfiguration.java`:

```java
package com.smart.erp.spike.s1.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class SpikeTestConfiguration {

    @Bean
    static DynamicPropertyRegistrar spikeTestProperties() {
        return registry -> SpikeTestProperties.all().forEach((name, value) -> registry.add(name, () -> value));
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeTest.java`:

```java
package com.smart.erp.spike.s1.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** Full spike context against the shared fixture databases; one cached context for all classes using it. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(SpikeTestConfiguration.class)
public @interface SpikeTest {}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeContexts.java`:

```java
package com.smart.erp.spike.s1.support;

import com.smart.erp.spike.s1.SpikeApplication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the spike outside Spring's test-context cache, so a test observes start-up and shutdown from the first bean to
 * the last. Everything is passed as command-line arguments: they override application.yaml.
 */
public final class SpikeContexts {

    private SpikeContexts() {}

    public static ConfigurableApplicationContext start(String... extraArguments) {
        List<String> arguments = new ArrayList<>();
        SpikeTestProperties.all().forEach((name, value) -> arguments.add("--" + name + "=" + value));
        arguments.addAll(List.of(extraArguments));
        return new SpringApplicationBuilder(SpikeApplication.class)
                .web(WebApplicationType.NONE)
                .run(arguments.toArray(String[]::new));
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeAssertions.java`:

```java
package com.smart.erp.spike.s1.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.core.NestedExceptionUtils;

public final class SpikeAssertions {

    private SpikeAssertions() {}

    /**
     * Asserts on the innermost cause of the failure, or on the failure itself when nothing wraps it. Spring wraps a
     * refused connection in {@code DataSourceUtils.getConnection} but not in {@code doGetConnection}, which jOOQ's
     * transaction-aware proxy uses; the test must not depend on which path a framework takes.
     */
    public static AbstractThrowableAssert<?, ? extends Throwable> assertRootCause(ThrowingCallable call) {
        Throwable failure = catchThrowable(call);
        assertThat(failure).as("expected the call to fail").isNotNull();
        return assertThat(NestedExceptionUtils.getMostSpecificCause(failure));
    }
}
```

- [ ] **Step 3: Birim testlerini yaz (DB yok)**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/tenancy/TenantDataSourceFactoryTests.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TenantDataSourceFactoryTests {

    private static final TenantDataSourceProperties PROPERTIES = new TenantDataSourceProperties(
            "jdbc:postgresql://127.0.0.1:1/{database}",
            "erp_tenant",
            "unused",
            4,
            Duration.ofMinutes(5),
            Duration.ofSeconds(10));

    @Test
    void poolsFollowTheDocumentedLimits() {
        TenantKey acme = new TenantKey("acme");
        try (HikariDataSource pool = new TenantDataSourceFactory(PROPERTIES)
                .create(new TenantDescriptor(acme, "erp_t_acme", TenantStatus.ACTIVE))) {
            assertThat(pool.getPoolName()).isEqualTo("tenant-acme");
            assertThat(pool.getJdbcUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:1/erp_t_acme");
            assertThat(pool.getUsername()).isEqualTo("erp_tenant");
            // doc §4.5: minIdle=0, max=4, idle connections close after 5 minutes
            assertThat(pool.getMinimumIdle()).isZero();
            assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
            assertThat(pool.getIdleTimeout()).isEqualTo(Duration.ofMinutes(5).toMillis());
            assertThat(pool.isRunning()).as("no connection is opened when the pool is created").isFalse();
        }
    }

    @Test
    void aTemplateWithoutTheDatabasePlaceholderIsRejected() {
        assertThatThrownBy(() -> new TenantDataSourceProperties(
                        "jdbc:postgresql://localhost/erp", "u", "p", 4, Duration.ofMinutes(5), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{database}");
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/tenancy/TenantRoutingDataSourceTests.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;

class TenantRoutingDataSourceTests {

    private static final TenantKey ACME = new TenantKey("acme");
    private static final TenantKey INITECH = new TenantKey("initech");
    private static final TenantKey UNKNOWN = new TenantKey("unknown");

    // Points at a closed port: these tests must never open a connection.
    private final TenantRoutingDataSource routing = new TenantRoutingDataSource(
            new InMemoryDirectory(Map.of(
                    ACME, new TenantDescriptor(ACME, "erp_t_acme", TenantStatus.ACTIVE),
                    INITECH, new TenantDescriptor(INITECH, "erp_t_initech", TenantStatus.SUSPENDED))),
            new TenantDataSourceFactory(new TenantDataSourceProperties(
                    "jdbc:postgresql://127.0.0.1:1/{database}",
                    "nobody",
                    "unused",
                    4,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(1))));

    @AfterEach
    void closePools() {
        routing.close();
    }

    @Test
    void aConnectionWithoutATenantIsRefusedAndCounted() {
        assertThatThrownBy(routing::getConnection).isInstanceOf(MissingTenantContextException.class);
        assertThat(routing.rejectedWithoutTenant()).isEqualTo(1);
    }

    @Test
    void codeThatOnlyCatchesSqlExceptionCannotSwallowTheRefusal() {
        // Boot's embedded-database probe (used to default spring.jpa.hibernate.ddl-auto) catches SQLException only.
        assertThatThrownBy(() -> EmbeddedDatabaseConnection.isEmbedded(routing))
                .isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void wrapperIntrospectionIsAnsweredWithoutRouting() throws SQLException {
        assertThat(routing.isWrapperFor(HikariDataSource.class)).isFalse();
        assertThat(routing.unwrap(TenantRoutingDataSource.class)).isSameAs(routing);
        assertThatThrownBy(() -> routing.unwrap(HikariDataSource.class)).isInstanceOf(SQLException.class);
        // Boot's metrics and health binding use this and swallow every exception (DataSourceUnwrapper#safeUnwrap).
        assertThat(DataSourceUnwrapper.unwrap(routing, HikariConfigMXBean.class, HikariDataSource.class))
                .isNull();
        assertThat(routing.rejectedWithoutTenant()).isZero();
    }

    @Test
    void anActiveTenantGetsItsOwnLazilyStartedPool() {
        DataSource target = TenantContext.call(ACME, routing::determineTargetDataSource);
        assertThat(target).isInstanceOfSatisfying(HikariDataSource.class, pool -> {
            assertThat(pool.getPoolName()).isEqualTo("tenant-acme");
            assertThat(pool.isRunning()).isFalse();
        });
        assertThat(TenantContext.call(ACME, routing::determineTargetDataSource)).isSameAs(target);
        assertThat(routing.openPools()).containsExactly(ACME);
    }

    @Test
    void anUnknownTenantIsRefusedWithoutAPool() {
        assertThatThrownBy(() -> TenantContext.run(UNKNOWN, routing::determineTargetDataSource))
                .isInstanceOf(TenantNotAvailableException.class)
                .hasMessageContaining("unknown tenant");
        assertThat(routing.openPools()).isEmpty();
    }

    @Test
    void aSuspendedTenantIsRefusedWithoutAPool() {
        assertThatThrownBy(() -> TenantContext.run(INITECH, routing::determineTargetDataSource))
                .isInstanceOf(TenantNotAvailableException.class)
                .hasMessageContaining("SUSPENDED");
        assertThat(routing.openPools()).isEmpty();
    }

    private record InMemoryDirectory(Map<TenantKey, TenantDescriptor> tenants) implements TenantDirectory {

        @Override
        public Optional<TenantDescriptor> find(TenantKey tenant) {
            return Optional.ofNullable(tenants.get(tenant));
        }

        @Override
        public List<TenantKey> activeTenants() {
            return tenants.values().stream()
                    .filter(tenant -> tenant.status() == TenantStatus.ACTIVE)
                    .map(TenantDescriptor::key)
                    .sorted(Comparator.comparing(TenantKey::value))
                    .toList();
        }
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/tenancy/TenantTaskDecoratorTests.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TenantTaskDecoratorTests {

    private static final TenantKey ACME = new TenantKey("acme");

    // One reused worker thread: the setting in which a leaked tenant would show up.
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @BeforeEach
    void startExecutor() {
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(new TenantTaskDecorator());
        executor.initialize();
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdown();
    }

    @Test
    void theSubmittersTenantIsBoundOnTheWorker() throws Exception {
        Future<Optional<TenantKey>> seen = TenantContext.call(ACME, () -> executor.submit(TenantContext::current));
        assertThat(seen.get(5, TimeUnit.SECONDS)).contains(ACME);
    }

    @Test
    void theWorkerDoesNotKeepTheTenantForTheNextTask() throws Exception {
        TenantContext.call(ACME, () -> executor.submit(TenantContext::current)).get(5, TimeUnit.SECONDS);
        assertThat(executor.submit(TenantContext::current).get(5, TimeUnit.SECONDS)).isEmpty();
    }
}
```

- [ ] **Step 4: Entegrasyon testlerini yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/tenancy/JdbcTenantDirectoryTests.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GHOST;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SpikeTest
class JdbcTenantDirectoryTests {

    @Autowired
    TenantDirectory directory;

    @Test
    void readsTheRegistryFromThePlatformDatabase() {
        assertThat(directory.find(ACME)).contains(new TenantDescriptor(ACME, "erp_t_acme", TenantStatus.ACTIVE));
        assertThat(directory.find(INITECH))
                .contains(new TenantDescriptor(INITECH, "erp_t_initech", TenantStatus.SUSPENDED));
        assertThat(directory.find(new TenantKey("nobody"))).isEmpty();
    }

    @Test
    void listsActiveTenantsInKeyOrder() {
        assertThat(directory.activeTenants()).containsExactly(ACME, GHOST, GLOBEX);
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/tenancy/TenantRoutingTests.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.kernel.UuidV7;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpikeTest
class TenantRoutingTests {

    /** Boot's JdbcClient: built on the single default DataSource candidate, the routing one. */
    @Autowired
    JdbcClient jdbc;

    @Test
    void aWriteLandsInTheBoundTenantsDatabaseOnly() {
        String name = "routing-" + UUID.randomUUID();
        TenantContext.run(ACME, () -> insert(name));

        assertThat(countByName(tenantDatabase(ACME), name)).isOne();
        assertThat(countByName(tenantDatabase(GLOBEX), name)).isZero();
    }

    @Test
    void eachTenantSeesOnlyItsOwnRows() {
        String name = "visible-" + UUID.randomUUID();
        TenantContext.run(GLOBEX, () -> insert(name));

        assertThat(TenantContext.call(GLOBEX, () -> countByName(jdbc, name))).isOne();
        assertThat(TenantContext.call(ACME, () -> countByName(jdbc, name))).isZero();
    }

    @Test
    void accessWithoutATenantFails() {
        assertRootCause(() -> countByName(jdbc, "anything")).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void aSuspendedTenantIsRefused() {
        assertRootCause(() -> TenantContext.call(INITECH, () -> countByName(jdbc, "anything")))
                .isInstanceOf(TenantNotAvailableException.class);
    }

    @Test
    void anUnknownTenantIsRefused() {
        assertRootCause(() -> TenantContext.call(new TenantKey("nobody"), () -> countByName(jdbc, "anything")))
                .isInstanceOf(TenantNotAvailableException.class);
    }

    private void insert(String name) {
        jdbc.sql("insert into sample.sample_record (id, version, name) values (?, 0, ?)")
                .param(UuidV7.next())
                .param(name)
                .update();
    }

    private static long countByName(JdbcClient client, String name) {
        return client.sql("select count(*) from sample.sample_record where name = ?")
                .param(name)
                .query(Long.class)
                .single();
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/StartupIsolationTests.java`:

```java
package com.smart.erp.spike.s1;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.support.SpikeContexts;
import com.smart.erp.spike.s1.tenancy.TenantRoutingDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The central S1 claim (doc §4.4–§4.5, ADR-0015): no framework component asks the routing DataSource for a tenant
 * connection while no tenant is bound — not at start-up, not while binding health and metrics, not at shutdown. The
 * counter also sees attempts whose exception the caller swallowed. Each later task adds a component and keeps this green.
 */
class StartupIsolationTests {

    @Test
    void startupAndShutdownNeverAskForATenantConnection() {
        TenantRoutingDataSource routing;
        try (ConfigurableApplicationContext context = SpikeContexts.start()) {
            routing = context.getBean(TenantRoutingDataSource.class);
            assertThat(routing.rejectedWithoutTenant())
                    .as("tenant-less connection requests during start-up")
                    .isZero();
            assertThat(routing.openPools()).as("tenant pools opened during start-up").isEmpty();
        }
        assertThat(routing.rejectedWithoutTenant())
                .as("tenant-less connection requests during shutdown")
                .isZero();
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/HealthTests.java`:

```java
package com.smart.erp.spike.s1;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.support.SpikeTest;
import com.smart.erp.spike.s1.tenancy.TenantRoutingDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpikeTest
class HealthTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TenantRoutingDataSource routing;

    @Test
    void readinessChecksThePlatformDatabaseAndNoTenant() {
        long before = routing.rejectedWithoutTenant();

        assertThat(mvc.get().uri("/actuator/health/readiness"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.components.db.status")
                .isEqualTo("UP");

        assertThat(routing.rejectedWithoutTenant()).isEqualTo(before);
    }
}
```

- [ ] **Step 5: Testleri çalıştır, derleme hatası gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='TenantDataSourceFactoryTests,TenantRoutingDataSourceTests,TenantTaskDecoratorTests'
```

Expected: FAIL, `cannot find symbol: class TenantDataSourceProperties` (ve diğer `tenancy` sınıfları).

- [ ] **Step 6: `tenancy` sınıflarını yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/PlatformDb.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/** Selects the platform DB's DataSource (doc §4.3). Everything unqualified gets the tenant-routing DataSource. */
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface PlatformDb {}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/PlatformDataSourceProperties.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code erp.platform.datasource.*}: the non-routed platform DB (doc §4.3, §4.5). */
@ConfigurationProperties(prefix = "erp.platform.datasource")
public record PlatformDataSourceProperties(String url, String username, String password) {

    public PlatformDataSourceProperties {
        Objects.requireNonNull(url, "erp.platform.datasource.url");
        Objects.requireNonNull(username, "erp.platform.datasource.username");
        Objects.requireNonNull(password, "erp.platform.datasource.password");
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantDataSourceProperties.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code erp.tenancy.datasource.*}: how tenant pools are built (doc §4.5). {@code url-template} contains
 * {@code {database}}, replaced by the tenant's database name from the registry.
 */
@ConfigurationProperties(prefix = "erp.tenancy.datasource")
public record TenantDataSourceProperties(
        String urlTemplate,
        String username,
        String password,
        @DefaultValue("4") int maximumPoolSize,
        @DefaultValue("5m") Duration idleTimeout,
        @DefaultValue("10s") Duration connectionTimeout) {

    static final String DATABASE_PLACEHOLDER = "{database}";

    public TenantDataSourceProperties {
        Objects.requireNonNull(urlTemplate, "erp.tenancy.datasource.url-template");
        Objects.requireNonNull(username, "erp.tenancy.datasource.username");
        Objects.requireNonNull(password, "erp.tenancy.datasource.password");
        if (!urlTemplate.contains(DATABASE_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "erp.tenancy.datasource.url-template must contain " + DATABASE_PLACEHOLDER + ": " + urlTemplate);
        }
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantStatus.java`:

```java
package com.smart.erp.spike.s1.tenancy;

/** Tenant lifecycle states (doc §4.3). Only ACTIVE tenants get a pool. */
public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    MAINTENANCE,
    ARCHIVED
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantDescriptor.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;

/** One row of the platform DB's tenant registry. */
public record TenantDescriptor(TenantKey key, String databaseName, TenantStatus status) {}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantDirectory.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.List;
import java.util.Optional;

/** The tenant registry (doc §4.3). */
public interface TenantDirectory {

    Optional<TenantDescriptor> find(TenantKey tenant);

    /** ACTIVE tenants, ordered by key. */
    List<TenantKey> activeTenants();
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/JdbcTenantDirectory.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads the registry from the platform DB (never through the routing DataSource). */
final class JdbcTenantDirectory implements TenantDirectory {

    private final JdbcClient platform;

    JdbcTenantDirectory(JdbcClient platform) {
        this.platform = platform;
    }

    @Override
    public Optional<TenantDescriptor> find(TenantKey tenant) {
        return platform.sql("select tenant_key, database_name, status from tenant where tenant_key = ?")
                .param(tenant.value())
                .query(JdbcTenantDirectory::descriptor)
                .optional();
    }

    @Override
    public List<TenantKey> activeTenants() {
        return platform.sql("select tenant_key from tenant where status = 'ACTIVE' order by tenant_key")
                .query((rs, row) -> new TenantKey(rs.getString("tenant_key")))
                .list();
    }

    private static TenantDescriptor descriptor(ResultSet rs, int row) throws SQLException {
        return new TenantDescriptor(
                new TenantKey(rs.getString("tenant_key")),
                rs.getString("database_name"),
                TenantStatus.valueOf(rs.getString("status")));
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantNotAvailableException.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;

/** The bound tenant is not in the registry or is not ACTIVE; no pool is opened for it. */
public class TenantNotAvailableException extends IllegalStateException {

    private final transient TenantKey tenant;

    public TenantNotAvailableException(TenantKey tenant, String reason) {
        super("Tenant " + tenant + " is not available: " + reason);
        this.tenant = tenant;
    }

    public TenantKey tenant() {
        return tenant;
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantDataSourceFactory.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.zaxxer.hikari.HikariDataSource;

/** Builds one HikariCP pool per tenant DB (doc §4.5). */
public final class TenantDataSourceFactory {

    private final TenantDataSourceProperties properties;

    public TenantDataSourceFactory(TenantDataSourceProperties properties) {
        this.properties = properties;
    }

    /**
     * The no-argument {@link HikariDataSource} constructor starts the pool on the first {@code getConnection()}, so
     * creating a pool never connects.
     */
    public HikariDataSource create(TenantDescriptor tenant) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("tenant-" + tenant.key().value());
        pool.setJdbcUrl(properties
                .urlTemplate()
                .replace(TenantDataSourceProperties.DATABASE_PLACEHOLDER, tenant.databaseName()));
        pool.setUsername(properties.username());
        pool.setPassword(properties.password());
        pool.setMinimumIdle(0);
        pool.setMaximumPoolSize(properties.maximumPoolSize());
        pool.setIdleTimeout(properties.idleTimeout().toMillis());
        pool.setConnectionTimeout(properties.connectionTimeout().toMillis());
        return pool;
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantRoutingDataSource.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * The one DataSource that JPA, jOOQ, JdbcClient and Modulith use (ADR-0015). It routes to the pool of the tenant bound
 * in {@link TenantContext}; there is no static target map and no default target. Pools are created lazily, only for
 * ACTIVE tenants. Extends {@link AbstractRoutingDataSource} so Boot recognises it as a routing DataSource
 * ({@code management.health.db.ignore-routing-data-sources}).
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDirectory directory;
    private final TenantDataSourceFactory factory;
    private final ConcurrentMap<TenantKey, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final AtomicLong rejectedWithoutTenant = new AtomicLong();

    public TenantRoutingDataSource(TenantDirectory directory, TenantDataSourceFactory factory) {
        this.directory = directory;
        this.factory = factory;
        setTargetDataSources(Map.of());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.current().orElse(null);
    }

    @Override
    protected DataSource determineTargetDataSource() {
        TenantKey tenant = TenantContext.current().orElse(null);
        if (tenant == null) {
            rejectedWithoutTenant.incrementAndGet();
            throw new MissingTenantContextException();
        }
        HikariDataSource pool = pools.get(tenant);
        if (pool != null) {
            return pool;
        }
        TenantDescriptor descriptor =
                directory.find(tenant).orElseThrow(() -> new TenantNotAvailableException(tenant, "unknown tenant"));
        if (descriptor.status() != TenantStatus.ACTIVE) {
            throw new TenantNotAvailableException(tenant, descriptor.status().name());
        }
        return pools.computeIfAbsent(tenant, key -> factory.create(descriptor));
    }

    /**
     * Spring's default routes {@code unwrap} to the current target. Boot's metrics and health binding call it with no
     * tenant bound and swallow the failure, so it must not route.
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("A tenant-routing DataSource does not expose a tenant's pool: " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    /** Requests made while no tenant was bound; counts also those whose exception the caller swallowed. */
    public long rejectedWithoutTenant() {
        return rejectedWithoutTenant.get();
    }

    public Set<TenantKey> openPools() {
        return Set.copyOf(pools.keySet());
    }

    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantTaskDecorator.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantContext;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the submitter's tenant to the worker thread (doc §4.4 rule 4). The worker gets the binding only for the
 * task's duration; with no tenant at submit time the task runs without one.
 */
public final class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return TenantContext.current()
                .<Runnable>map(tenant -> () -> TenantContext.run(tenant, runnable))
                .orElse(runnable);
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenancyConfiguration.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
class TenancyConfiguration {

    /**
     * Not a default candidate: JPA, jOOQ, JdbcClient and Modulith must get the routing DataSource (ADR-0015). Boot's
     * health still checks it, so readiness follows the platform DB.
     */
    @Bean(defaultCandidate = false)
    @PlatformDb
    HikariDataSource platformDataSource(PlatformDataSourceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("platform");
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        return dataSource;
    }

    @Bean
    TenantDirectory tenantDirectory(@PlatformDb DataSource platformDataSource) {
        return new JdbcTenantDirectory(JdbcClient.create(platformDataSource));
    }

    @Bean
    TenantRoutingDataSource tenantDataSource(TenantDirectory directory, TenantDataSourceProperties properties) {
        return new TenantRoutingDataSource(directory, new TenantDataSourceFactory(properties));
    }

    /** Boot composes every TaskDecorator bean into the application task executor (@Async, Modulith listeners). */
    @Bean
    TenantTaskDecorator tenantTaskDecorator() {
        return new TenantTaskDecorator();
    }
}
```

- [ ] **Step 7: `application.yaml`'ı yaz**

`spikes/s1-tenancy/src/main/resources/application.yaml`:

```yaml
# S1 tenancy spike (doc §15.3). Settings marked "start-up" exist because, without them, a framework component asks the
# routing DataSource for a connection while no tenant is bound (doc §4.4–§4.5); StartupIsolationTests guards them.
spring:
  application:
    name: erp-spike-s1
  threads:
    virtual:
      enabled: true
  sql:
    init:
      mode: never # migrations are a separate step (doc §4.6); the test fixture plays `erpctl migrate`

management:
  endpoint:
    health:
      probes:
        enabled: true
      show-components: always
      group:
        readiness:
          include: readinessState,db
  health:
    db:
      ignore-routing-data-sources: true # start-up + readiness: readiness is the platform DB, never a tenant DB

erp:
  tenancy:
    datasource:
      maximum-pool-size: 4
      idle-timeout: 5m
```

- [ ] **Step 8: Tüm Task 4 testlerini çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='TenantDataSourceFactoryTests,TenantRoutingDataSourceTests,TenantTaskDecoratorTests,JdbcTenantDirectoryTests,TenantRoutingTests,StartupIsolationTests,HealthTests,ModularityTests'
```

Expected: `Failures: 0, Errors: 0`. `ModularityTests` `tenancy → kernel` bağımlılığını kabul eder.

- [ ] **Step 9: Biçimlendir, tüm spike'ı doğrula, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "feat(spike): route to lazily created tenant pools with no default tenant

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Routing DataSource üzerinde Hibernate 7, transaction koruması, UUIDv7 karşılaştırması

**Files:**
- Modify: `spikes/s1-tenancy/pom.xml` (`spring-boot-starter-data-jpa`)
- Modify: `spikes/s1-tenancy/src/main/resources/application.yaml` (`spring.jpa`)
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecord.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecordRepository.java`
- Modify: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/StartupIsolationTests.java` (negatif kontrol)
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/HibernateBootstrapTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/SamplePersistenceTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/TransactionGuardTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/HibernateUuidV7Tests.java`

**Interfaces:**
- Consumes: Task 1–4.
- Produces:
  - `@Entity SampleRecord` (`sample.sample_record`): `public SampleRecord(String name)` (kimlik `UuidV7.next()` ile kurulumda atanır), `UUID getId()`, `Long getVersion()`, `String getName()`, `Instant getProcessedAt()`; eşitlik `id` üzerinden.
  - `interface SampleRecordRepository extends JpaRepository<SampleRecord, UUID>`.
  - Boot'un `TransactionTemplate` ve `EntityManager` bean'leri routing DataSource üzerinde.

- [ ] **Step 1: Yeni testleri yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/HibernateBootstrapTests.java`:

```java
package com.smart.erp.spike.s1.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.support.SpikeTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SpikeTest
class HibernateBootstrapTests {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theDialectComesFromConfigurationNotFromATenantConnection() {
        Dialect dialect = entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getJdbcServices()
                .getDialect();
        assertThat(dialect).isInstanceOf(PostgreSQLDialect.class);
        assertThat(dialect.getVersion().getDatabaseMajorVersion()).isEqualTo(18);
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/SamplePersistenceTests.java`:

```java
package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@SpikeTest
class SamplePersistenceTests {

    @Autowired
    SampleRecordRepository records;

    @Autowired
    TransactionTemplate tx;

    @Test
    void theIdIsAUuidV7KnownBeforePersist() {
        assertThat(new SampleRecord("uuid-" + UUID.randomUUID()).getId().version()).isEqualTo(7);
    }

    @Test
    void aNewRecordWithAnAssignedIdIsPersistedNotMerged() {
        SampleRecord record = new SampleRecord("persist-" + UUID.randomUUID());

        SampleRecord saved = TenantContext.call(ACME, () -> tx.execute(status -> records.save(record)));

        // merge() would have issued a SELECT and returned a copy; persist() keeps the instance (ADR-0013).
        assertThat(saved).isSameAs(record);
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void theRowLandsInTheBoundTenantsDatabaseOnly() {
        SampleRecord record = new SampleRecord("jpa-" + UUID.randomUUID());

        TenantContext.run(GLOBEX, () -> tx.executeWithoutResult(status -> records.save(record)));

        assertThat(countById(GLOBEX, record.getId())).isOne();
        assertThat(countById(ACME, record.getId())).isZero();
    }

    @Test
    void repositoryAccessWithoutATenantFails() {
        assertRootCause(() -> records.count()).isInstanceOf(MissingTenantContextException.class);
    }

    private static long countById(TenantKey tenant, UUID id) {
        return tenantDatabase(tenant)
                .sql("select count(*) from sample.sample_record where id = ?")
                .param(id)
                .query(Long.class)
                .single();
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/TransactionGuardTests.java`:

```java
package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.kernel.TenantSwitchInTransactionException;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** doc §4.4 rule 7: the tenant is fixed before the transaction starts and cannot change inside it. */
@SpikeTest
class TransactionGuardTests {

    @Autowired
    SampleRecordRepository records;

    @Autowired
    TransactionTemplate tx;

    @Test
    void switchingTenantInsideATransactionFailsAndRollsBack() {
        String name = "switch-" + UUID.randomUUID();

        TenantContext.run(ACME, () -> assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    records.save(new SampleRecord(name));
                    TenantContext.run(GLOBEX, () -> records.save(new SampleRecord(name)));
                }))
                .isInstanceOf(TenantSwitchInTransactionException.class));

        assertThat(countByName(ACME, name)).isZero();
        assertThat(countByName(GLOBEX, name)).isZero();
    }

    @Test
    void reenteringTheSameTenantInsideATransactionIsAllowed() {
        String name = "reenter-" + UUID.randomUUID();

        TenantContext.run(
                ACME,
                () -> tx.executeWithoutResult(
                        status -> TenantContext.run(ACME, () -> records.save(new SampleRecord(name)))));

        assertThat(countByName(ACME, name)).isOne();
    }

    @Test
    void aTransactionCannotStartWithoutATenant() {
        assertRootCause(() -> tx.executeWithoutResult(status -> records.save(new SampleRecord("orphan"))))
                .isInstanceOf(MissingTenantContextException.class);
    }

    private static long countByName(TenantKey tenant, String name) {
        return tenantDatabase(tenant)
                .sql("select count(*) from sample.sample_record where name = ?")
                .param(name)
                .query(Long.class)
                .single();
    }
}
```

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/kernel/HibernateUuidV7Tests.java`:

```java
package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.hibernate.Incubating;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.junit.jupiter.api.Test;

/**
 * Learning test for the Phase 0 check "UUIDv7 generation in Hibernate 7" (ADR-0013). Facts only; the decision and its
 * reasons are in docs/spikes/s1-tenancy.md. If an assertion fails, record what Hibernate actually does there instead
 * of changing the generator.
 */
class HibernateUuidV7Tests {

    @Test
    void hibernatesVersion7StrategyProducesIncreasingVersion7Ids() {
        UUID previous = UuidVersion7Strategy.INSTANCE.generateUuid(null);
        for (int i = 0; i < 10_000; i++) {
            UUID current = UuidVersion7Strategy.INSTANCE.generateUuid(null);
            assertThat(current.version()).isEqualTo(7);
            assertThat(UuidOrder.UNSIGNED.compare(current, previous)).isPositive();
            previous = current;
        }
    }

    @Test
    void theVersion7StyleIsStillIncubating() throws NoSuchFieldException {
        assertThat(UuidGenerator.Style.class.getField("VERSION_7").isAnnotationPresent(Incubating.class))
                .isTrue();
    }
}
```

`StartupIsolationTests`'e (sınıfın içine) negatif kontrolü ekle; importlara `import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;` ekle:

```java
    /**
     * Negative control: with JDBC metadata access allowed, Hibernate 7.4 asks for a connection at start-up, swallows
     * the refusal and then cannot pick a dialect. This is the trap doc §4.5 names.
     */
    @Test
    void hibernateMetadataAccessWouldNeedATenantAtStartup() {
        assertRootCause(() -> SpikeContexts.start(
                                "--spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true")
                        .close())
                .hasMessageContaining("Unable to determine Dialect without JDBC metadata");
    }
```

- [ ] **Step 2: Sadece JPA bağımlılığını ekle ve açılış testinin kırıldığını gör**

`spikes/s1-tenancy/pom.xml`'de `spring-boot-starter-jdbc`'nin altına:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
```

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=StartupIsolationTests#startupAndShutdownNeverAskForATenantConnection
```

Expected: FAIL, derleme hatası (`SampleRecord` yok) — `SampleRecord`/`SampleRecordRepository`'yi Step 3'te yazdıktan sonra tekrar çalıştırınca FAIL: açılış `MissingTenantContextException` ile durur (Boot, `ddl-auto` varsayılanını bulmak için `EmbeddedDatabaseConnection.isEmbedded(...)` ile bağlantı ister). Bu, `spring.jpa.hibernate.ddl-auto: none`'ın gerekçesidir.

- [ ] **Step 3: Entity ve repository'yi yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecord.java`:

```java
package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.kernel.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sample_record", schema = "sample")
public class SampleRecord {

    /** Assigned at construction (ADR-0013): known to events and idempotency keys before anything is written. */
    @Id
    private UUID id;

    /** A wrapper type on purpose: Spring Data treats a null version as new and persists instead of merging. */
    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    private Instant processedAt;

    protected SampleRecord() {}

    public SampleRecord(String name) {
        this.id = UuidV7.next();
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SampleRecord that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecordRepository.java`:

```java
package com.smart.erp.spike.s1.sample;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleRecordRepository extends JpaRepository<SampleRecord, UUID> {}
```

Step 2'deki komutu tekrar çalıştır. Expected: FAIL — kök neden `MissingTenantContextException`, yığında `EmbeddedDatabaseConnection.isEmbedded` (ya da Hibernate'in diyalekt hatası; ikisi de yapılandırma eksikliğini gösterir).

- [ ] **Step 4: JPA yapılandırmasını ekle**

`application.yaml`'da `spring:` altına, `sql:` bloğundan sonra:

```yaml
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none # start-up: otherwise Boot connects to ask whether the database is embedded
    properties:
      hibernate.boot.allow_jdbc_metadata_access: false # start-up: Hibernate must not pick a tenant (doc §4.5)
      jakarta.persistence.database-product-name: PostgreSQL
      jakarta.persistence.database-major-version: 18
      hibernate.cache.use_second_level_cache: false # ADR-0015
```

- [ ] **Step 5: Task 5 testlerini ve açılış testini çalıştır**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='StartupIsolationTests,HibernateBootstrapTests,SamplePersistenceTests,TransactionGuardTests,HibernateUuidV7Tests,HealthTests'
```

Expected: `Failures: 0, Errors: 0`. `HibernateUuidV7Tests` bir öğrenme testidir: kırılırsa testi Hibernate'in gerçek davranışına göre düzelt ve davranışı Task 10'da `docs/spikes/s1-tenancy.md` → B14'e yaz (kararı değiştirmez, gerekçeyi günceller).

- [ ] **Step 6: Biçimlendir, tüm spike'ı doğrula, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "feat(spike): run Hibernate 7 on the routing DataSource with no start-up tenant access

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: jOOQ okuması ve jOOQ öncesi otomatik flush

**Files:**
- Modify: `spikes/s1-tenancy/pom.xml` (`spring-boot-starter-jooq`)
- Modify: `spikes/s1-tenancy/src/main/resources/application.yaml` (`spring.jooq.sql-dialect`)
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleQueries.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/FlushBeforeJooqQueryListener.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/JooqConfiguration.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/JooqReadTests.java`

**Interfaces:**
- Consumes: `SampleRecord`, `SampleRecordRepository` (Task 5), routing DataSource (Task 4).
- Produces:
  - `@Component SampleQueries`: `int countByName(String name)`, `Optional<Instant> processedAt(UUID id)`; paket içi sabitler `SAMPLE_RECORD` (`Table<?>`), `ID` (`Field<UUID>`), `NAME` (`Field<String>`), `PROCESSED_AT` (`Field<Instant>`).
  - `final class FlushBeforeJooqQueryListener implements ExecuteListener` (`(EntityManagerFactory)`), `JooqConfiguration` içinde `DefaultExecuteListenerProvider` bean'i olarak kayıtlı.

- [ ] **Step 1: Başarısız testi yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/JooqReadTests.java`:

```java
package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.support.SpikeTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@SpikeTest
class JooqReadTests {

    @Autowired
    DSLContext dsl;

    @Autowired
    SampleQueries queries;

    @Autowired
    SampleRecordRepository records;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate tx;

    @Test
    void theDialectIsConfiguredNotDetected() {
        assertThat(dsl.dialect()).isEqualTo(SQLDialect.POSTGRES);
    }

    @Test
    void readsOnlyTheBoundTenantsRows() {
        String name = "jooq-" + UUID.randomUUID();
        TenantContext.run(ACME, () -> tx.executeWithoutResult(status -> records.save(new SampleRecord(name))));

        assertThat(TenantContext.call(ACME, () -> queries.countByName(name))).isOne();
        assertThat(TenantContext.call(GLOBEX, () -> queries.countByName(name))).isZero();
    }

    /** The trap of doc §4.5 / §7.7: jOOQ bypasses Hibernate, so an unflushed persist is invisible to it. */
    @Test
    void withoutAFlushJooqDoesNotSeeTheTransactionsOwnJpaWrites() {
        String name = "unflushed-" + UUID.randomUUID();
        DSLContext withoutListeners = DSL.using(dsl.configuration().derive(new ExecuteListenerProvider[0]));

        TenantContext.run(ACME, () -> tx.executeWithoutResult(status -> {
            records.save(new SampleRecord(name));
            assertThat(withoutListeners.fetchCount(SampleQueries.SAMPLE_RECORD, SampleQueries.NAME.eq(name)))
                    .isZero();
            entityManager.flush();
            assertThat(withoutListeners.fetchCount(SampleQueries.SAMPLE_RECORD, SampleQueries.NAME.eq(name)))
                    .isOne();
            status.setRollbackOnly();
        }));
    }

    @Test
    void theFlushListenerMakesUnflushedWritesVisibleAutomatically() {
        String name = "auto-flush-" + UUID.randomUUID();

        TenantContext.run(ACME, () -> tx.executeWithoutResult(status -> {
            records.save(new SampleRecord(name));
            assertThat(queries.countByName(name)).isOne();
            status.setRollbackOnly();
        }));
    }

    @Test
    void jooqWithoutATenantFails() {
        assertRootCause(() -> queries.countByName("anything")).isInstanceOf(MissingTenantContextException.class);
    }
}
```

- [ ] **Step 2: Sadece jOOQ bağımlılığını ekle, testleri çalıştır**

`spikes/s1-tenancy/pom.xml`'de `spring-boot-starter-data-jpa`'nın altına:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jooq</artifactId>
        </dependency>
```

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='JooqReadTests,StartupIsolationTests'
```

Expected: FAIL, derleme hatası (`SampleQueries` yok).

- [ ] **Step 3: `SampleQueries`'i yaz ve açılışın kırıldığını gör**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleQueries.java`:

```java
package com.smart.erp.spike.s1.sample;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

/** Read side with jOOQ (ADR-0011). No code generation in the spike; S3 covers codegen. */
@Component
public class SampleQueries {

    static final Table<?> SAMPLE_RECORD = DSL.table(DSL.name("sample", "sample_record"));
    static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
    static final Field<String> NAME = DSL.field(DSL.name("name"), SQLDataType.VARCHAR);
    static final Field<Instant> PROCESSED_AT = DSL.field(DSL.name("processed_at"), SQLDataType.INSTANT);

    private final DSLContext dsl;

    SampleQueries(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int countByName(String name) {
        return dsl.fetchCount(SAMPLE_RECORD, NAME.eq(name));
    }

    public Optional<Instant> processedAt(UUID id) {
        return Optional.ofNullable(dsl.select(PROCESSED_AT)
                .from(SAMPLE_RECORD)
                .where(ID.eq(id))
                .fetchOne(PROCESSED_AT));
    }
}
```

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=StartupIsolationTests#startupAndShutdownNeverAskForATenantConnection
```

Expected: FAIL — açılış `MissingTenantContextException` ile durur; yığında `SqlDialectLookup.getDialect` (Boot, diyalekti tespit etmek için bağlantı ister). Bu, `spring.jooq.sql-dialect`'in gerekçesidir.

- [ ] **Step 4: Diyalekti ve flush dinleyicisini ekle**

`application.yaml`'da `spring:` altına, `jpa:` bloğundan sonra:

```yaml
  jooq:
    sql-dialect: POSTGRES # start-up: otherwise Boot connects to detect the dialect
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/FlushBeforeJooqQueryListener.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * doc §7.7 rule 7, made automatic: before a jOOQ statement runs inside a read-write JPA transaction, the transaction's
 * pending JPA writes are flushed, so jOOQ sees them. Like Hibernate's own auto-flush before native queries. Uses only
 * an EntityManager that is already bound; never creates one.
 */
final class FlushBeforeJooqQueryListener implements ExecuteListener {

    private final transient EntityManagerFactory entityManagerFactory;

    FlushBeforeJooqQueryListener(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void start(ExecuteContext ctx) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return;
        }
        if (TransactionSynchronizationManager.getResource(entityManagerFactory) instanceof EntityManagerHolder holder) {
            holder.getEntityManager().flush();
        }
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/JooqConfiguration.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JooqConfiguration {

    /** Boot adds every ExecuteListenerProvider bean to jOOQ's configuration. */
    @Bean
    DefaultExecuteListenerProvider flushBeforeJooqQuery(EntityManagerFactory entityManagerFactory) {
        return new DefaultExecuteListenerProvider(new FlushBeforeJooqQueryListener(entityManagerFactory));
    }
}
```

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='JooqReadTests,StartupIsolationTests,TransactionGuardTests'
```

Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 6: Biçimlendir, tüm spike'ı doğrula, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "feat(spike): read with jOOQ on the routing DataSource and flush JPA writes first

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Tenant DB'sinde Modulith olay kaydı

**Files:**
- Modify: `spikes/s1-tenancy/pom.xml` (`spring-modulith-starter-jdbc`, `spring-modulith-events-core`; test: `awaitility`)
- Modify: `spikes/s1-tenancy/src/main/resources/application.yaml` (`spring.modulith`)
- Modify: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/SpikeApplication.java` (`Clock` bean'i)
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/ModulithTenancySupport.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantSafeEventPublicationRegistry.java`
- Modify: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenancyConfiguration.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecorded.java`
- Create: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleService.java`
- Modify: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/StartupIsolationTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/EventPublicationTests.java`

**Interfaces:**
- Consumes: Task 1–6; Boot'un `applicationTaskExecutor`'ı (`TenantTaskDecorator` ile).
- Produces:
  - `record SampleRecorded(UUID eventId, TenantKey tenantKey, UUID sampleId, Instant occurredAt)` (§5.4'ün olay alanlarının spike alt kümesi).
  - `@Service SampleService`: `@Transactional UUID record(String name)` — `SampleRecord`'u kaydeder, `SampleRecorded`'ı yayınlar, kaydın kimliğini döner; bağlı tenant gerekir.
  - `Clock` bean'i (`Clock.systemUTC()`).
  - `final class ModulithTenancySupport implements InstantiationAwareBeanPostProcessor, BeanFactoryAware` (statik bean); `class TenantSafeEventPublicationRegistry extends DefaultEventPublicationRegistry`.
  - Olay yayın tablosu: her tenant'ın `platform_events.event_publication`'ı; tamamlanan yayın silinir (`completion-mode: delete`).

- [ ] **Step 1: Bağımlılıkları ve Modulith yapılandırmasını ekle**

`spikes/s1-tenancy/pom.xml`'de `spring-boot-starter-jooq`'nun altına:

```xml
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <!-- Compile scope for ModulithTenancySupport (the starter brings it at runtime scope only). -->
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-events-core</artifactId>
        </dependency>
```

`spring-boot-starter-webmvc-test`'in altına:

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

`application.yaml`'da `spring:` altına, `jooq:` bloğundan sonra:

```yaml
  modulith:
    events:
      jdbc:
        schema-initialization:
          enabled: false # start-up: Modulith 2.1.1 runs its DDL when this property is absent (matchIfMissing=true)
        schema: platform_events
      republish-outstanding-events-on-restart: false # ADR-0012: the tenant-walking republisher does this (Task 9)
      completion-mode: delete
```

- [ ] **Step 2: Açılış testine Modulith beklentilerini ekle**

`StartupIsolationTests`'e ekle (importlar: `org.springframework.aop.support.AopUtils`, `org.springframework.modulith.events.core.EventPublicationRegistry`):

```java
    @Test
    void modulithGetsItsDatabaseTypeAndRegistryWithoutAConnection() {
        try (ConfigurableApplicationContext context = SpikeContexts.start()) {
            assertThat(context.getBean("databaseType")).hasToString("POSTGRES");
            assertThat(AopUtils.getTargetClass(context.getBean(EventPublicationRegistry.class)).getSimpleName())
                    .isEqualTo("TenantSafeEventPublicationRegistry");
        }
    }
```

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=StartupIsolationTests
```

Expected: FAIL — açılış durur; kök neden `MissingTenantContextException`, yığında `JdbcEventPublicationAutoConfiguration.databaseType` (`JdbcUtils.extractDatabaseMetaData`). Bu, Modulith 2.1.1'in koşulsuz `databaseType` bean'inin açılışta tenant'sız bağlantı istediğini kanıtlar.

- [ ] **Step 3: Modulith uyum parçasını yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/TenantSafeEventPublicationRegistry.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;

/**
 * Modulith's registry whose {@code destroy()} does not query: the stock one lists incomplete publications at shutdown,
 * a query with no tenant bound. Not final: Modulith's transactional methods need a CGLIB proxy.
 */
class TenantSafeEventPublicationRegistry extends DefaultEventPublicationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantSafeEventPublicationRegistry.class);

    TenantSafeEventPublicationRegistry(EventPublicationRepository events, Clock clock) {
        super(events, clock);
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down; incomplete event publications stay in each tenant database and are resubmitted"
                + " by the tenant-walking republisher (ADR-0012)");
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/tenancy/ModulithTenancySupport.java`:

```java
package com.smart.erp.spike.s1.tenancy;

import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;

/**
 * Spring Modulith 2.1.1 has no multi-tenancy (doc §4.4) and touches its DataSource where no tenant is bound:
 *
 * <ul>
 *   <li>{@code JdbcEventPublicationAutoConfiguration#databaseType} reads JDBC metadata at start-up to pick SQL. The
 *       bean is unconditional and its type package-private, so it is answered here, without a connection.
 *   <li>{@code DefaultEventPublicationRegistry#destroy()} lists incomplete publications at shutdown; replaced by
 *       {@link TenantSafeEventPublicationRegistry}.
 * </ul>
 *
 * Both are matched by bean name and class, so a Modulith upgrade that renames them makes StartupIsolationTests fail
 * instead of silently bringing the access back.
 */
final class ModulithTenancySupport implements InstantiationAwareBeanPostProcessor, BeanFactoryAware {

    static final String DATABASE_TYPE_BEAN = "databaseType";
    static final String DATABASE_TYPE_CLASS = "org.springframework.modulith.events.jdbc.DatabaseType";
    static final String REGISTRY_BEAN = "eventPublicationRegistry";

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        if (DATABASE_TYPE_BEAN.equals(beanName) && DATABASE_TYPE_CLASS.equals(beanClass.getName())) {
            return postgres(beanClass);
        }
        if (REGISTRY_BEAN.equals(beanName) && DefaultEventPublicationRegistry.class.equals(beanClass)) {
            BeanFactory factory = Objects.requireNonNull(beanFactory, "beanFactory");
            return new TenantSafeEventPublicationRegistry(
                    factory.getBean(EventPublicationRepository.class),
                    factory.getBeanProvider(Clock.class).getIfAvailable(Clock::systemUTC));
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object postgres(Class<?> databaseType) {
        return Enum.valueOf((Class) databaseType, "POSTGRES");
    }
}
```

`TenancyConfiguration`'a ekle:

```java
    /** Static: a BeanPostProcessor must exist before the beans it intercepts are created. */
    @Bean
    static ModulithTenancySupport modulithTenancySupport() {
        return new ModulithTenancySupport();
    }
```

`SpikeApplication`'a `Clock` bean'ini ekle (importlar: `java.time.Clock`, `org.springframework.context.annotation.Bean`):

```java
    /** Server time is the source of truth (doc §3.1.4, §6.1); tests may replace it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
```

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=StartupIsolationTests
```

Expected: `Failures: 0, Errors: 0`. (Not: `REGISTRY_BEAN` dalı olmadan `startupAndShutdownNeverAskForATenantConnection`'ın kapanış iddiası 1 ile kırılır; bu bulgu B2'dir.)

- [ ] **Step 4: Olay testini yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/EventPublicationTests.java`:

```java
package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

@SpikeTest
@Import(EventPublicationTests.RecordingListenerConfiguration.class)
class EventPublicationTests {

    @Autowired
    SampleService samples;

    @Autowired
    RecordingListener listener;

    @Autowired
    TransactionTemplate tx;

    @Test
    void thePublicationIsKeptInThePublishersTenantDatabaseUntilTheListenerCompletes() {
        CountDownLatch gate = listener.holdNextInvocation();
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("event-" + UUID.randomUUID()));
        try {
            await().untilAsserted(() -> assertThat(recordingPublications(ACME, sampleId))
                    .containsExactly("PROCESSING"));
            assertThat(recordingPublications(GLOBEX, sampleId)).isEmpty();
        } finally {
            gate.countDown();
        }
        // completion-mode: delete
        await().untilAsserted(() -> assertThat(recordingPublications(ACME, sampleId)).isEmpty());
    }

    @Test
    void theListenerRunsAsynchronouslyInThePublishersTenant() {
        long testThread = Thread.currentThread().threadId();
        UUID sampleId = TenantContext.call(GLOBEX, () -> samples.record("event-" + UUID.randomUUID()));

        await().untilAsserted(() -> assertThat(listener.invocationFor(sampleId))
                .hasValueSatisfying(invocation -> {
                    assertThat(invocation.tenant()).isEqualTo(GLOBEX);
                    assertThat(invocation.threadId()).isNotEqualTo(testThread);
                }));
    }

    @Test
    void aRolledBackTransactionPublishesNothing() {
        UUID sampleId = TenantContext.call(ACME, () -> tx.execute(status -> {
            UUID id = samples.record("rolled-back-" + UUID.randomUUID());
            status.setRollbackOnly();
            return id;
        }));

        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(listener.invocationFor(sampleId)).isEmpty());
        assertThat(recordingPublications(ACME, sampleId)).isEmpty();
    }

    private static List<String> recordingPublications(TenantKey tenant, UUID sampleId) {
        return tenantDatabase(tenant)
                .sql("""
                        select status from platform_events.event_publication
                         where listener_id like '%RecordingListener%' and serialized_event like ?
                        """)
                .param("%" + sampleId + "%")
                .query(String.class)
                .list();
    }

    record Invocation(TenantKey tenant, long threadId) {}

    /** Test-only listener; accessed through methods because the bean is a CGLIB proxy. */
    static class RecordingListener {

        private final Map<UUID, Invocation> invocations = new ConcurrentHashMap<>();
        private final AtomicReference<CountDownLatch> gate = new AtomicReference<>();

        public CountDownLatch holdNextInvocation() {
            CountDownLatch latch = new CountDownLatch(1);
            gate.set(latch);
            return latch;
        }

        public Optional<Invocation> invocationFor(UUID sampleId) {
            return Optional.ofNullable(invocations.get(sampleId));
        }

        @ApplicationModuleListener
        void on(SampleRecorded event) throws InterruptedException {
            CountDownLatch latch = gate.getAndSet(null);
            if (latch != null && !latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The test never opened the gate");
            }
            invocations.put(
                    event.sampleId(),
                    new Invocation(TenantContext.current().orElse(null), Thread.currentThread().threadId()));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingListenerConfiguration {

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }
}
```

- [ ] **Step 5: Testi çalıştır, derleme hatası gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=EventPublicationTests
```

Expected: FAIL, `cannot find symbol: class SampleService` / `SampleRecorded`.

- [ ] **Step 6: Olayı ve servisi yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecorded.java`:

```java
package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event (doc §5.4): past tense, ids and minimal data, carries its tenant. Spike subset: no companyId and no
 * schemaVersion.
 */
public record SampleRecorded(UUID eventId, TenantKey tenantKey, UUID sampleId, Instant occurredAt) {}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleService.java`:

```java
package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.UuidV7;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application layer (K3): one command, one transaction; the event is stored in the same tenant transaction. */
@Service
public class SampleService {

    private final SampleRecordRepository records;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SampleService(SampleRecordRepository records, ApplicationEventPublisher events, Clock clock) {
        this.records = records;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID record(String name) {
        SampleRecord record = records.save(new SampleRecord(name));
        events.publishEvent(new SampleRecorded(UuidV7.next(), TenantContext.require(), record.getId(), clock.instant()));
        return record.getId();
    }
}
```

- [ ] **Step 7: Testleri çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='EventPublicationTests,StartupIsolationTests,ModularityTests'
```

Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 8: Biçimlendir, tüm spike'ı doğrula, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "feat(spike): keep Modulith event publications in the tenant database

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: db-scheduler ile tenant işleri

**Files:**
- Modify: `spikes/s1-tenancy/pom.xml` (`db-scheduler`)
- Modify: `spikes/s1-tenancy/src/main/resources/application.yaml` (`spike.jobs`)
- Create (`.../spike/s1/jobs/`): `TenantScopedTaskData.java`, `JsonTaskDataSerializer.java`, `TenantExecutionInterceptor.java`, `JobsProperties.java`, `JobsConfiguration.java`
- Create (`.../spike/s1/sample/`): `ProcessSampleData.java`, `SampleProcessor.java`, `SampleJobs.java`, `SampleRecordedListener.java`
- Modify: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecord.java` (`markProcessed`)
- Modify: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeTestProperties.java`
- Modify: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/StartupIsolationTests.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/TenantJobTests.java`

**Interfaces:**
- Consumes: Task 1–7 (`@PlatformDb DataSource`, `SampleRecorded`, `SampleService`, `SampleQueries`).
- Produces:
  - `interface TenantScopedTaskData { TenantKey tenant(); }` — bu arayüzü uygulayan iş verisi, işin tenant'ını belirler.
  - `Scheduler` bean'i (aynı zamanda `SchedulerClient`), platform DB'sinde; `SmartLifecycle` bean'i `schedulerLifecycle`.
  - `record JobsProperties(Duration pollingInterval /*1s*/, int threads /*4*/, Duration shutdownMaxWait /*30s*/)` — `spike.jobs`.
  - `record ProcessSampleData(String tenantKey, UUID sampleId) implements TenantScopedTaskData`.
  - `SampleJobs.PROCESS_SAMPLE = "sample.process-sample"`; `OneTimeTask<ProcessSampleData>` bean'i.
  - `SampleProcessor.process(UUID sampleId)` (`@Transactional`, idempotent); `SampleRecord.markProcessed(Instant)` (paket içi).
  - `SampleRecordedListener`: `@ApplicationModuleListener`, iş örneği kimliği = `eventId`, `scheduleIfNotExists`.

- [ ] **Step 1: Başarısız testi yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/sample/TenantJobTests.java`:

```java
package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static com.smart.erp.spike.s1.support.SpikeDatabases.platformDatabase;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.UuidV7;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SpikeTest
class TenantJobTests {

    @Autowired
    SampleService samples;

    @Autowired
    SampleQueries queries;

    @Autowired
    SchedulerClient scheduler;

    @Autowired
    OneTimeTask<ProcessSampleData> processSample;

    @Test
    void recordingASampleRunsItsJobInTheSameTenant() {
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("job-" + UUID.randomUUID()));

        await().untilAsserted(() ->
                assertThat(TenantContext.call(ACME, () -> queries.processedAt(sampleId))).isPresent());
        assertThat(tenantDatabase(GLOBEX)
                        .sql("select count(*) from sample.sample_record where id = ?")
                        .param(sampleId)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void theEventIdIsTheJobInstanceIdSoARedeliveryIsDropped() {
        String eventId = UuidV7.next().toString();
        ProcessSampleData data = new ProcessSampleData(GLOBEX.value(), UuidV7.next());
        Instant later = Instant.now().plus(Duration.ofHours(1));
        try {
            assertThat(scheduler.scheduleIfNotExists(processSample.instance(eventId, data), later))
                    .isTrue();
            assertThat(scheduler.scheduleIfNotExists(processSample.instance(eventId, data), later))
                    .isFalse();
            assertThat(scheduledRows(eventId)).isOne();
        } finally {
            scheduler.cancel(TaskInstanceId.of(SampleJobs.PROCESS_SAMPLE, eventId));
        }
    }

    @Test
    void taskDataIsJsonThatNamesTheTenant() {
        String eventId = UuidV7.next().toString();
        scheduler.scheduleIfNotExists(
                processSample.instance(eventId, new ProcessSampleData(GLOBEX.value(), UuidV7.next())),
                Instant.now().plus(Duration.ofHours(1)));
        try {
            byte[] taskData = platformDatabase()
                    .sql("select task_data from scheduled_tasks where task_name = ? and task_instance = ?")
                    .param(SampleJobs.PROCESS_SAMPLE)
                    .param(eventId)
                    .query(byte[].class)
                    .single();
            assertThat(new String(taskData, StandardCharsets.UTF_8)).contains("\"tenantKey\":\"globex\"");
        } finally {
            scheduler.cancel(TaskInstanceId.of(SampleJobs.PROCESS_SAMPLE, eventId));
        }
    }

    @Test
    void aJobForASuspendedTenantFailsVisiblyAndWaitsForARetry() {
        String eventId = UuidV7.next().toString();
        scheduler.scheduleIfNotExists(
                processSample.instance(eventId, new ProcessSampleData(INITECH.value(), UuidV7.next())),
                Instant.now());
        try {
            await().untilAsserted(() -> assertThat(platformDatabase()
                            .sql("select consecutive_failures from scheduled_tasks"
                                    + " where task_name = ? and task_instance = ?")
                            .param(SampleJobs.PROCESS_SAMPLE)
                            .param(eventId)
                            .query(Integer.class)
                            .optional())
                    .hasValue(1));
        } finally {
            scheduler.cancel(TaskInstanceId.of(SampleJobs.PROCESS_SAMPLE, eventId));
        }
    }

    private static long scheduledRows(String eventId) {
        return platformDatabase()
                .sql("select count(*) from scheduled_tasks where task_name = ? and task_instance = ?")
                .param(SampleJobs.PROCESS_SAMPLE)
                .param(eventId)
                .query(Long.class)
                .single();
    }
}
```

`StartupIsolationTests`'teki `startupAndShutdownNeverAskForATenantConnection`'ın `try` bloğuna, `openPools` iddiasından sonra ekle (import: `com.github.kagkarlsson.scheduler.Scheduler`):

```java
            assertThat(context.getBean(Scheduler.class).getSchedulerState().isStarted())
                    .as("the job scheduler is part of the observed start-up")
                    .isTrue();
```

- [ ] **Step 2: Bağımlılığı ekle, testi çalıştır, derleme hatası gör**

`spikes/s1-tenancy/pom.xml`'de `<description>`'dan sonra:

```xml
    <properties>
        <db-scheduler.version>16.12.0</db-scheduler.version>
    </properties>
```

`spring-modulith-events-core`'un altına:

```xml
        <dependency>
            <!-- Apache-2.0; cluster-safe scheduler on the platform DB (ADR-0014). -->
            <groupId>com.github.kagkarlsson</groupId>
            <artifactId>db-scheduler</artifactId>
            <version>${db-scheduler.version}</version>
        </dependency>
```

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=TenantJobTests
```

Expected: FAIL, `cannot find symbol: class ProcessSampleData`.

- [ ] **Step 3: `jobs` modülünü yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/TenantScopedTaskData.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.smart.erp.spike.s1.kernel.TenantKey;

/** Task data of a tenant job (doc §6.12): the job runs with this tenant bound, nothing else. */
public interface TenantScopedTaskData {

    TenantKey tenant();
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/JsonTaskDataSerializer.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.serializer.Serializer;
import tools.jackson.databind.json.JsonMapper;

/** Task data as JSON (Jackson 3, Boot's mapper): readable in the platform DB, no Java serialization. */
final class JsonTaskDataSerializer implements Serializer {

    private final JsonMapper json;

    JsonTaskDataSerializer(JsonMapper json) {
        this.json = json;
    }

    @Override
    public byte[] serialize(Object data) {
        return data == null ? null : json.writeValueAsBytes(data);
    }

    @Override
    public <T> T deserialize(Class<T> type, byte[] serializedData) {
        return serializedData == null ? null : json.readValue(serializedData, type);
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/TenantExecutionInterceptor.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.event.ExecutionChain;
import com.github.kagkarlsson.scheduler.event.ExecutionInterceptor;
import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.ExecutionContext;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.smart.erp.spike.s1.kernel.TenantContext;

/**
 * Binds the job's tenant around its execution and clears it afterwards (doc §4.4 rule 6, ADR-0014). Platform jobs (no
 * tenant in their data) run with none.
 */
final class TenantExecutionInterceptor implements ExecutionInterceptor {

    @Override
    public CompletionHandler<?> execute(
            TaskInstance<?> taskInstance, ExecutionContext executionContext, ExecutionChain chain) {
        if (taskInstance.getData() instanceof TenantScopedTaskData data) {
            return TenantContext.call(data.tenant(), () -> chain.proceed(taskInstance, executionContext));
        }
        return chain.proceed(taskInstance, executionContext);
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/JobsProperties.java`:

```java
package com.smart.erp.spike.s1.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "spike.jobs")
public record JobsProperties(
        @DefaultValue("1s") Duration pollingInterval,
        @DefaultValue("4") int threads,
        @DefaultValue("30s") Duration shutdownMaxWait) {}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/JobsConfiguration.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.smart.erp.spike.s1.tenancy.PlatformDb;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class JobsConfiguration {

    /** db-scheduler on the platform DB (ADR-0014); the task queue never lives in a tenant DB. */
    @Bean
    Scheduler scheduler(
            @PlatformDb DataSource platformDataSource,
            ObjectProvider<OneTimeTask<?>> oneTimeTasks,
            ObjectProvider<RecurringTask<?>> recurringTasks,
            JsonMapper jsonMapper,
            JobsProperties properties) {
        List<Task<?>> knownTasks = new ArrayList<>(oneTimeTasks.orderedStream().toList());
        return Scheduler.create(platformDataSource, knownTasks)
                .startTasks(recurringTasks.orderedStream().toList())
                .serializer(new JsonTaskDataSerializer(jsonMapper))
                .addExecutionInterceptor(new TenantExecutionInterceptor())
                .pollingInterval(properties.pollingInterval())
                .threads(properties.threads())
                .shutdownMaxWait(properties.shutdownMaxWait())
                .enableImmediateExecution()
                .build();
    }

    /** Polls only after the context is up and stops before the DataSources close (last to start, first to stop). */
    @Bean
    SmartLifecycle schedulerLifecycle(Scheduler scheduler) {
        return new SmartLifecycle() {

            private volatile boolean running;

            @Override
            public void start() {
                scheduler.start();
                running = true;
            }

            @Override
            public void stop() {
                scheduler.stop();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }
}
```

- [ ] **Step 4: `sample` tarafını yaz**

`SampleRecord.java`'ya, `getProcessedAt()`'tan sonra:

```java
    /** Idempotent: a redelivered job does not move the timestamp. */
    void markProcessed(Instant at) {
        if (processedAt == null) {
            processedAt = at;
        }
    }
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/ProcessSampleData.java`:

```java
package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.jobs.TenantScopedTaskData;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.UUID;

/** Serialized as {@code {"tenantKey":"…","sampleId":"…"}}; {@link #tenant()} is not a bean property. */
public record ProcessSampleData(String tenantKey, UUID sampleId) implements TenantScopedTaskData {

    @Override
    public TenantKey tenant() {
        return new TenantKey(tenantKey);
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleProcessor.java`:

```java
package com.smart.erp.spike.s1.sample;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleProcessor {

    private final SampleRecordRepository records;
    private final Clock clock;

    SampleProcessor(SampleRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional
    public void process(UUID sampleId) {
        records.findById(sampleId).ifPresent(record -> record.markProcessed(clock.instant()));
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleJobs.java`:

```java
package com.smart.erp.spike.s1.sample;

import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SampleJobs {

    static final String PROCESS_SAMPLE = "sample.process-sample";

    /** Runs with the tenant of its data bound (TenantExecutionInterceptor). Failures retry after 5 minutes. */
    @Bean
    OneTimeTask<ProcessSampleData> processSampleTask(SampleProcessor processor) {
        return Tasks.oneTime(PROCESS_SAMPLE, ProcessSampleData.class)
                .execute((instance, context) -> processor.process(instance.getData().sampleId()));
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/sample/SampleRecordedListener.java`:

```java
package com.smart.erp.spike.s1.sample;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.smart.erp.spike.s1.kernel.TenantContext;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enqueues the follow-up job after the tenant transaction committed (doc §6.12 "transaction ile kuyruğa alma"). The
 * job instance id is the event id, so a redelivered event schedules nothing new.
 */
@Component
class SampleRecordedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleRecordedListener.class);

    private final SchedulerClient scheduler;
    private final OneTimeTask<ProcessSampleData> processSample;
    private final Clock clock;

    SampleRecordedListener(SchedulerClient scheduler, OneTimeTask<ProcessSampleData> processSample, Clock clock) {
        this.scheduler = scheduler;
        this.processSample = processSample;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(SampleRecorded event) {
        if (!event.tenantKey().equals(TenantContext.require())) {
            throw new IllegalStateException(
                    "Event of tenant " + event.tenantKey() + " delivered under " + TenantContext.require());
        }
        boolean scheduled = scheduler.scheduleIfNotExists(
                processSample.instance(
                        event.eventId().toString(),
                        new ProcessSampleData(event.tenantKey().value(), event.sampleId())),
                clock.instant());
        if (!scheduled) {
            LOGGER.debug("Job for event {} already scheduled (redelivery)", event.eventId());
        }
    }
}
```

- [ ] **Step 5: Yapılandırmayı ekle**

`application.yaml`'ın sonuna:

```yaml
spike:
  jobs:
    polling-interval: 1s
    threads: 4
    shutdown-max-wait: 30s
```

`SpikeTestProperties.all()`'a, `connection-timeout` satırından sonra:

```java
        properties.put("spike.jobs.polling-interval", "100ms");
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='TenantJobTests,StartupIsolationTests,EventPublicationTests,ModularityTests'
```

Expected: `Failures: 0, Errors: 0`. `aJobForASuspendedTenantFailsVisiblyAndWaitsForARetry` sırasında logda db-scheduler'ın `TenantNotAvailableException: Tenant initech is not available: SUSPENDED` hatası görünür; beklenen budur.

- [ ] **Step 7: Biçimlendir, tüm spike'ı doğrula, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/pom.xml spikes/s1-tenancy/src
git commit -m "feat(spike): run tenant jobs on db-scheduler keyed by event id

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Tenant dolaşan yeniden yayın

**Files:**
- Modify: `spikes/s1-tenancy/src/main/resources/application.yaml` (`spike.events.republish`)
- Create (`.../spike/s1/jobs/`): `EventRepublishProperties.java`, `DelayedFixedDelay.java`, `RepublishReport.java`, `TenantEventRepublisher.java`
- Modify: `spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/JobsConfiguration.java`
- Modify: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/support/SpikeTestProperties.java`
- Test: `spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/jobs/TenantEventRepublisherTests.java`

**Interfaces:**
- Consumes: `TenantDirectory.activeTenants()` (Task 4), Modulith `IncompleteEventPublications` (Task 7), `SampleService`, `SampleRecorded` (Task 7), `JobsConfiguration` (Task 8).
- Produces:
  - `record EventRepublishProperties(Duration interval /*1m*/, Duration minAge /*5m*/)` — `spike.events.republish`.
  - `record RepublishReport(List<TenantKey> republished, Map<TenantKey, String> failed)`.
  - `@Component TenantEventRepublisher`: `RepublishReport republishAll(Duration minAge)`.
  - Tekrarlayan platform işi `platform.event-republisher` (`RecurringTask<Void>`), ilk çalışması bir aralık sonra (`DelayedFixedDelay`).

- [ ] **Step 1: Başarısız testi yaz**

`spikes/s1-tenancy/src/test/java/com/smart/erp/spike/s1/jobs/TenantEventRepublisherTests.java`:

```java
package com.smart.erp.spike.s1.jobs;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GHOST;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static com.smart.erp.spike.s1.support.SpikeDatabases.platformDatabase;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.sample.SampleRecorded;
import com.smart.erp.spike.s1.sample.SampleService;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;

@SpikeTest
@Import(TenantEventRepublisherTests.FlakyListenerConfiguration.class)
class TenantEventRepublisherTests {

    @Autowired
    SampleService samples;

    @Autowired
    FlakyListener flaky;

    @Autowired
    TenantEventRepublisher republisher;

    @Test
    void aFailedPublicationIsResubmittedInItsOwnTenant() {
        flaky.failNextInvocation();
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("flaky-" + UUID.randomUUID()));
        await().untilAsserted(() -> assertThat(flakyPublications(ACME, sampleId)).containsExactly("FAILED"));

        RepublishReport report = republisher.republishAll(Duration.ZERO);

        assertThat(report.republished()).contains(ACME);
        await().untilAsserted(() -> assertThat(flaky.successfulTenantFor(sampleId)).contains(ACME));
        await().untilAsserted(() -> assertThat(flakyPublications(ACME, sampleId)).isEmpty());
        assertThat(flakyPublications(GLOBEX, sampleId)).isEmpty();
    }

    @Test
    void anUnreachableTenantIsReportedAndDoesNotStopTheOthers() {
        RepublishReport report = republisher.republishAll(Duration.ZERO);

        assertThat(report.republished()).containsExactly(ACME, GLOBEX).doesNotContain(INITECH);
        assertThat(report.failed()).containsOnlyKeys(GHOST);
        assertThat(report.failed().get(GHOST)).contains("erp_t_ghost");
    }

    @Test
    void theRepublisherIsARecurringPlatformJobWhoseFirstRunIsDelayed() {
        OffsetDateTime nextRun = platformDatabase()
                .sql("select execution_time from scheduled_tasks"
                        + " where task_name = 'platform.event-republisher' and task_instance = 'recurring'")
                .query(OffsetDateTime.class)
                .single();
        assertThat(nextRun).isAfter(OffsetDateTime.now().plusMinutes(30));
    }

    private static List<String> flakyPublications(TenantKey tenant, UUID sampleId) {
        return tenantDatabase(tenant)
                .sql("""
                        select status from platform_events.event_publication
                         where listener_id like '%FlakyListener%' and serialized_event like ?
                        """)
                .param("%" + sampleId + "%")
                .query(String.class)
                .list();
    }

    /** Fails once on demand; accessed through methods because the bean is a CGLIB proxy. */
    static class FlakyListener {

        private final AtomicBoolean failNext = new AtomicBoolean();
        private final Map<UUID, TenantKey> successes = new ConcurrentHashMap<>();

        public void failNextInvocation() {
            failNext.set(true);
        }

        public Optional<TenantKey> successfulTenantFor(UUID sampleId) {
            return Optional.ofNullable(successes.get(sampleId));
        }

        @ApplicationModuleListener
        void on(SampleRecorded event) {
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("Simulated listener failure");
            }
            successes.put(event.sampleId(), TenantContext.require());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FlakyListenerConfiguration {

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }
}
```

- [ ] **Step 2: Testi çalıştır, derleme hatası gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest=TenantEventRepublisherTests
```

Expected: FAIL, `cannot find symbol: class TenantEventRepublisher`.

- [ ] **Step 3: Yeniden yayın sınıflarını yaz**

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/EventRepublishProperties.java`:

```java
package com.smart.erp.spike.s1.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code min-age} keeps in-flight publications out of a resubmission. */
@ConfigurationProperties(prefix = "spike.events.republish")
public record EventRepublishProperties(@DefaultValue("1m") Duration interval, @DefaultValue("5m") Duration minAge) {}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/DelayedFixedDelay.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed delay whose first run is one interval after start-up. db-scheduler's FixedDelay runs at once, and walking the
 * tenants at start-up would open a pool per tenant (doc §6.12).
 */
record DelayedFixedDelay(Duration interval) implements Schedule {

    @Override
    public Instant getNextExecutionTime(ExecutionComplete executionComplete) {
        return executionComplete.getTimeDone().plus(interval);
    }

    @Override
    public boolean isDeterministic() {
        return false;
    }
}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/RepublishReport.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.List;
import java.util.Map;

/** Outcome of one tenant-walking run: tenants whose publications were resubmitted, and failures with their cause. */
public record RepublishReport(List<TenantKey> republished, Map<TenantKey, String> failed) {}
```

`spikes/s1-tenancy/src/main/java/com/smart/erp/spike/s1/jobs/TenantEventRepublisher.java`:

```java
package com.smart.erp.spike.s1.jobs;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.tenancy.TenantDirectory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Component;

/**
 * Resubmits incomplete event publications tenant by tenant (ADR-0012): Modulith's own start-up republish has no
 * tenant. A failing tenant is reported and skipped; the others continue. Spike simplification: it walks every ACTIVE
 * tenant, which opens a pool per tenant per run; doc §6.12 asks for an activity index instead (Phase 2).
 */
@Component
public class TenantEventRepublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantEventRepublisher.class);

    private final TenantDirectory directory;
    private final IncompleteEventPublications publications;

    TenantEventRepublisher(TenantDirectory directory, IncompleteEventPublications publications) {
        this.directory = directory;
        this.publications = publications;
    }

    public RepublishReport republishAll(Duration minAge) {
        List<TenantKey> republished = new ArrayList<>();
        Map<TenantKey, String> failed = new LinkedHashMap<>();
        for (TenantKey tenant : directory.activeTenants()) {
            try {
                TenantContext.run(tenant, () -> publications.resubmitIncompletePublicationsOlderThan(minAge));
                republished.add(tenant);
            } catch (RuntimeException e) {
                String cause = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
                LOGGER.warn("Republishing incomplete event publications failed for tenant {}: {}", tenant, cause, e);
                failed.put(tenant, cause);
            }
        }
        return new RepublishReport(List.copyOf(republished), Map.copyOf(failed));
    }
}
```

`JobsConfiguration`'a ekle (importlar: `com.github.kagkarlsson.scheduler.task.helper.Tasks`):

```java
    static final String EVENT_REPUBLISHER = "platform.event-republisher";

    /** A platform job: no tenant in its data, it binds each tenant itself (ADR-0012, ADR-0014). */
    @Bean
    RecurringTask<Void> eventRepublisherTask(TenantEventRepublisher republisher, EventRepublishProperties properties) {
        return Tasks.recurring(EVENT_REPUBLISHER, new DelayedFixedDelay(properties.interval()))
                .execute((instance, context) -> republisher.republishAll(properties.minAge()));
    }
```

- [ ] **Step 4: Yapılandırmayı ekle**

`application.yaml`'da `spike:` altına:

```yaml
  events:
    republish:
      interval: 1m
      min-age: 5m
```

`SpikeTestProperties.all()`'a:

```java
        // The recurring republisher must not fire during a test run; tests call republishAll directly.
        properties.put("spike.events.republish.interval", "1h");
```

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy test -Dtest='TenantEventRepublisherTests,StartupIsolationTests,ModularityTests'
```

Expected: `Failures: 0, Errors: 0`. Logda `ghost` için `database "erp_t_ghost" does not exist` uyarısı görünür; beklenen budur.

- [ ] **Step 6: Tüm spike'ı doğrula, biçimlendir, commit**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy spotless:apply
./mvnw -Pspikes -pl spikes/s1-tenancy verify
git add spikes/s1-tenancy/src
git commit -m "feat(spike): resubmit incomplete event publications tenant by tenant

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Bulgu raporu, ADR'ler ve çıkış kontrolü

**Files:**
- Create: `docs/spikes/s1-tenancy.md`
- Modify: `docs/adr/0003-tenant-basina-veritabani.md`, `docs/adr/0011-jpa-yazma-jooq-okuma.md`, `docs/adr/0012-olay-kaydi-tenant-db.md`, `docs/adr/0013-uuidv7.md`, `docs/adr/0014-db-scheduler.md`, `docs/adr/0015-routing-datasource.md`
- Modify: `docs/adr/README.md` (dizindeki durumlar)

**Interfaces:**
- Consumes: Task 1–9'un yeşil test çalıştırması.
- Produces: S1 sonucu ADR'lerde; Faz 1/2'ye girdiler `docs/spikes/s1-tenancy.md`'de.

- [ ] **Step 1: Kanıtı topla**

```bash
./mvnw -Pspikes -pl spikes/s1-tenancy verify 2>&1 | grep -E "Tests run:|BUILD"
git rev-parse --short HEAD
```

Expected: son `Tests run:` satırında `Failures: 0, Errors: 0`, `BUILD SUCCESS`. Toplam test sayısını ve commit kısa hash'ini Step 2'deki `<TEST_SAYISI>` ve `<COMMIT>` yerlerine yaz. Herhangi bir test bu planda yazılandan farklı davrandıysa (ör. `HibernateUuidV7Tests`), gerçek davranışı ilgili bulguya yaz ve ADR durumunu Step 3'teki kurala göre seç.

- [ ] **Step 2: `docs/spikes/s1-tenancy.md`'yi yaz**

```markdown
# S1 — Tenancy yığını spike'ı: sonuçlar

| | |
|---|---|
| **Kapsam** | v4-platform.md §15.3 S1: 2+ tenant DB ile routing DataSource + Hibernate 7 + jOOQ + Modulith olay kaydı + db-scheduler; bağlam yayılımı, tenant dolaşan yeniden yayın, §4.5'teki Hibernate tuzakları |
| **Kod** | `spikes/s1-tenancy/` (commit `<COMMIT>`), Faz 0 kapanışında silinir |
| **Çalıştırma** | `./mvnw -q install -DskipTests && ./mvnw -Pspikes -pl spikes/s1-tenancy verify` (Docker gerekir) |
| **Sonuç** | `<TEST_SAYISI>` test, hepsi yeşil. Doküman kararları ayakta; uygulama kuralları aşağıda. |
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

## Bulgular

**B1 — Modulith 2.1.1, açılışta olay deposunun DataSource'unu sorgular.** `JdbcEventPublicationAutoConfiguration#databaseType` koşulsuz bir bean'dir ve `JdbcUtils.extractDatabaseMetaData` ile bağlantı açar; tipi (`DatabaseType`) paket içidir, `@ConditionalOnMissingBean` yoktur. Çözüm: `ModulithTenancySupport` (`InstantiationAwareBeanPostProcessor`) bean'i bağlantısız `POSTGRES` ile karşılar. Kanıt: Task 7 Step 2'de açılış bu bean'de durdu.

**B2 — Modulith 2.1.1, kapanışta da sorgular.** `DefaultEventPublicationRegistry#destroy()` tamamlanmamış yayınları listeler. Çözüm: `TenantSafeEventPublicationRegistry` (sorgusuz `destroy()`); tamamlanmamış yayınların raporu tenant dolaşan işin işidir.

**B3 — Modulith şema kurulumu özellik yokken açıktır.** `spring.modulith.events.jdbc.schema-initialization.enabled` yoksa koşul `matchIfMissing = true` ile eşleşir ve açılışta DDL çalışır (kaynak kod okuması; özelliğin belgelenmiş varsayılanı `false` olsa da). Özellik açıkça `false` yapılır; tablo migration'la (`platform_events.event_publication`, v2 düzeni) kurulur. `spring.modulith.events.jdbc.schema` tablo adına önek olarak eklenir, `search_path`'e dokunmaz.

**B4 — Boot, `ddl-auto` verilmezse açılışta bağlantı ister** (`HibernateDefaultDdlAutoProvider` → `EmbeddedDatabaseConnection.isEmbedded`). `spring.jpa.hibernate.ddl-auto: none` zorunlu.

**B5 — Boot, jOOQ diyalekti verilmezse açılışta bağlantı ister** (`SqlDialectLookup`). `spring.jooq.sql-dialect: POSTGRES` zorunlu.

**B6 — Hibernate 7.4.5:** metadata erişimi açıkken açılışta bağlantı ister, reddi yutar (`catch (Exception)`) ve sonra açıkça verilmiş `jakarta.persistence.database-product-name`'e rağmen "Unable to determine Dialect without JDBC metadata" ile durur. Doğru ayar: `hibernate.boot.allow_jdbc_metadata_access=false` + `jakarta.persistence.database-product-name=PostgreSQL` + `jakarta.persistence.database-major-version=18` → `PostgreSQLDialect`, sürüm 18.

**B7 — `AbstractRoutingDataSource.unwrap/isWrapperFor` o anki hedefe yönlendirir.** Boot'un metrik ve sağlık kodu (`DataSourceUnwrapper#safeUnwrap`) bunları tenant'sız çağırır ve her hatayı yutar. Routing DataSource bu iki metodu yönlendirmeden yanıtlar.

**B8 — İki DataSource'un Boot'la kablolanması:** platform DataSource `@Bean(defaultCandidate = false)` + `@PlatformDb` ile işaretlenince JPA, jOOQ, `JdbcClient`, `TransactionTemplate` ve Modulith routing DataSource'u tek aday olarak alır (`@Primary` gerekmez); Boot'un sağlık kodu platform DB'sini yine `db` bileşeni olarak denetler. `management.health.db.ignore-routing-data-sources=true` ile readiness = platform DB; tenant DB'leri readiness konusu değildir.

**B9 — Ret istisnası unchecked olmalı.** `MissingTenantContextException extends IllegalStateException`: sadece `SQLException` yakalayan kod (Boot'un gömülü DB yoklaması, jOOQ diyalekt tespiti) onu yutamaz, açılış gürültüyle durur. `Exception` yakalayan kod (Hibernate, Boot'un unwrapper'ı) için tek güvence `rejectedWithoutTenant` sayacı ve onu sıfırda tutan açılış testidir. Faz 1'de bu test (`StartupIsolationTests` eşdeğeri) kalıcı kalite kapısıdır.

**B10 — Modulith'in async dinleyicisi tenant ister.** Kayıt defterinin `markProcessing/markCompleted` çağrıları kendi transaction'larını (`REQUIRES_NEW`) dinleyici thread'inde açar. Boot 4.1, her `TaskDecorator` bean'ini `applicationTaskExecutor`'a (sanal thread'li) bileştirir; `TenantTaskDecorator` yeterli. Tamamlanma kaydı dinleyicinin transaction'ından sonra ayrı yazılır: dinleyici işini commit edip tamamlanma kaydı düşmeden çökerse olay yeniden teslim edilir. Dinleyiciler idempotent olmalı (B15).

**B11 — `TenantContext` için ThreadLocal + sadece kapsam API'si.** `run/call` dışında bağlama yolu yok; kapsam bitince önceki değer geri konur. `ScopedValue` (Java 25) aynı garantiyi verir, ama `TaskDecorator` zinciri, MDC, Spring Security ve Micrometer bağlam yayılımı ThreadLocal temellidir. Micrometer context-propagation `ScopedValue` desteği verince yeniden değerlendirilir.

**B12 — Transaction koruması kapsam girişinde yeterli.** JPA transaction'ı başlarken bağlantıyı aldığı için tenant'sız transaction başlayamaz; transaction içinde başka tenant'a geçiş `TenantContext.call`'da reddedilir. Bu koruma olmadan jOOQ ve Modulith, transaction'a bağlı bağlantıyı kullanmaya devam eder ve **sessizce** ilk tenant'a yazar: korumanın varlık nedeni budur.

**B13 — jOOQ öncesi flush'ı altyapı yapmalı.** Tuzak gerçek (atanmış UUID ile Hibernate INSERT'ü flush'a erteler). `FlushBeforeJooqQueryListener` (jOOQ `ExecuteListener`) aktif ve salt-okunur olmayan JPA transaction'ında her jOOQ sorgusundan önce flush eder; Hibernate'in native sorgu öncesi auto-flush'ının karşılığı. Öneri: Faz 1 kernel'inde varsayılan olsun (doğruluk > performans, §1.3); "CRUD motoru ve belge çatısı flush etmeyi hatırlar" kuralı yerine.

**B14 — UUIDv7: uygulama tarafı üreteç.** `kernel.UuidV7` (RFC 9562 method 1: 12 bit sayaç + 62 bit rastgele; saat donsa da geri gitse de kesin artan). Hibernate 7.4.5'in `@UuidGenerator(style = VERSION_7)`'si geçerli ve monoton v7 üretiyor, ama `@Incubating`, saati `Instant.now()`'dan alıyor (`Clock` enjekte edilemez) ve kimliği ancak `persist` anında veriyor. Uygulama üreteciyle kimlik kurulumda bilinir (olay, idempotency anahtarı, aynı transaction'da bağlama). `@Version Long` (sarmalayıcı) sayesinde Spring Data atanmış kimlikli yeni kaydı `persist` eder; `long` olsaydı `merge` eder ve önce SELECT atardı.

**B15 — db-scheduler:** platform DB'sinde, `ExecutionInterceptor` iş verisindeki tenant'ı (`TenantScopedTaskData`) bağlar ve iş bitince kaldırır. İş verisi Jackson 3 ile JSON (`{"tenantKey":"globex","sampleId":"…"}`), platform DB'sinde okunur. İş örneği kimliği = `eventId`, `scheduleIfNotExists` ikinci denemede `false` döner. Askıdaki tenant'ın işi `TenantNotAvailableException` ile düşer, `consecutive_failures=1`, 5 dakika sonra tekrar denenir. Faz 2: geri yükleme el kitabının 5. adımı (§4.7.1, "o tenant'a ait işler temizlenir") için `scheduled_tasks`'ta tenant'a göre sorgu gerekir. Seçenekler: iş örneği kimliğine tenant öneki (`<tenant>:<eventId>`) ya da JSON'dan üretilmiş (generated) bir kolon + index.

**B16 — Tenant dolaşan yeniden yayının maliyeti.** Her çalışmada her ACTIVE tenant için havuz açar; §6.12'nin "boşta duran tenant'lar için havuz açılmaz" ilkesine aykırı. Spike'ta ilk çalışma bir aralık ertelendi (`DelayedFixedDelay`), böylece açılış havuz açmaz. Faz 2: platform DB'sinde bir etkinlik göstergesi (son aktivite / bekleyen yayın işareti) ile sadece işi olan tenant'lar dolaşılır. Erişilemeyen tenant (DB yok) diğerlerini durdurmaz, raporlanır; her çalışmada yeniden denenir (Faz 2: geri çekilme).

**B17 — Boot 4 modüler otomatik yapılandırma:** Flyway otomatik yapılandırması sadece `spring-boot-flyway` sınıf yolundaysa çalışır. Spike `flyway-core`'u yalnızca fixture'da kullandı; açılışta migration yok. Faz 2 orkestratörü Flyway API'sini doğrudan kullanır, `spring-boot-starter-flyway` eklenmez.

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
```

- [ ] **Step 3: ADR'leri güncelle**

Kural (ADR README "Durum sözlüğü"): Doğrulama kararı olduğu gibi desteklediyse **Kabul edildi**; karar metninin değişmesi gerektiyse **Değişti** (ne ve neden Doğrulama'da). Kabul edilen bir ADR'nin "Karar" metnine dokunulmaz; sadece "Durum" satırı ve "Doğrulama" bölümü değişir. Task 1–9 yeşilse aşağıdaki durumlar geçerlidir.

Her dosyada `| **Durum** | Önerildi |` satırını belirtilen durumla değiştir ve "Doğrulama" bölümünün sonuna belirtilen paragrafı ekle (`<TARİH>` = commit günü, `YYYY-MM-DD`; `<COMMIT>` = Step 1'deki hash).

`docs/adr/0003-tenant-basina-veritabani.md` → `| **Durum** | Kabul edildi |`

```markdown
**S1 sonucu (<TARİH>, `<COMMIT>`):** Platform DB + iki aktif, bir askıdaki ve DB'si olmayan bir tenant ile JDBC, JPA ve jOOQ yazma/okumaları sadece bağlı tenant'ın DB'sine gitti (routing'i atlayan süper kullanıcı sorgusuyla doğrulandı); tenant rolü platform DB'sine bağlanamıyor. Ayrıntı ve bulgular: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md). Faz 2 çıkış kriteri (3 tenant'lı izolasyon paketi) geçerliliğini korur.
```

`docs/adr/0011-jpa-yazma-jooq-okuma.md` → durum **Önerildi** kalır (lisans kararı plan 0E'de, §20 soru 13).

```markdown
**S1 sonucu (<TARİH>, `<COMMIT>`), yazma/okuma ayrımı kısmı doğrulandı:** Hibernate 7.4.5 ve jOOQ 3.21.7 aynı routing DataSource üzerinde, aynı transaction'da çalışıyor. jOOQ diyalekti `spring.jooq.sql-dialect=POSTGRES` ile verilmeli (yoksa Boot açılışta bağlantı ister). Flush kuralının tuzağı gerçek; spike, jOOQ `ExecuteListener`'ı ile otomatik flush'ı doğruladı ve Faz 1 kernel'i için öneriyor ([bulgu B13](../spikes/s1-tenancy.md)). Kalan: ticari jOOQ lisansı kararı (plan 0E).
```

`docs/adr/0012-olay-kaydi-tenant-db.md` → `| **Durum** | Kabul edildi |`

```markdown
**S1 sonucu (<TARİH>, `<COMMIT>`):** Yayın kaydı tenant DB'sinde (`platform_events.event_publication`, Modulith 2.1.1 v2 düzeni, migration ile); async dinleyici yayıncının tenant'ında çalıştı; `completion-mode=delete` ile tamamlanan yayın silindi; başarısız yayın tenant dolaşan bir db-scheduler işiyle kendi tenant'ında yeniden gönderildi, erişilemeyen tenant diğerlerini durdurmadı. Uygulama kuralları: Modulith 2.1.1 açılışta `databaseType` için ve kapanışta `destroy()` için tenant'sız sorgu yapıyor; `ModulithTenancySupport` ile bağlantısız karşılandı (upstream'e bildirilecek). `schema-initialization.enabled=false` açıkça verilmeli (özellik yokken açık). Dolaşan iş her tenant için havuz açtığından Faz 2'de etkinlik göstergesine bağlanmalı. Ayrıntı: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md) B1–B3, B10, B16.
```

`docs/adr/0013-uuidv7.md` → `| **Durum** | Kabul edildi |`

```markdown
**S1 sonucu (<TARİH>, `<COMMIT>`), "Hibernate 7'de UUIDv7 üretimi" teyidi:** Hibernate 7.4.5'in `@UuidGenerator(style = VERSION_7)`'si geçerli ve monoton v7 üretiyor, ancak `@Incubating`, saati `Instant.now()`'dan alıyor ve kimliği ancak `persist` anında veriyor. Karar uygulama tarafı üreteçtir (kernel `UuidV7`, RFC 9562 method 1, saat donsa ya da geri gitse de kesin artan); kimlik entity kurulumunda atanır. `@Version` sarmalayıcı tip (`Long`) olmalı: Spring Data atanmış kimlikli yeni kaydı ancak böyle `persist` eder (yoksa `merge` + SELECT). Ayrıntı: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md) B14.
```

`docs/adr/0014-db-scheduler.md` → `| **Durum** | Kabul edildi |`

```markdown
**S1 sonucu (<TARİH>, `<COMMIT>`):** db-scheduler 16.12.0 platform DB'sinde; `ExecutionInterceptor` iş verisindeki tenant'ı bağlayıp iş bitince kaldırıyor; iş verisi JSON (Jackson 3); iş örneği kimliği = `eventId`, `scheduleIfNotExists` tekrar teslimde yeni iş üretmedi; askıdaki tenant'ın işi görünür biçimde düştü ve tekrar denenmek üzere bekledi. Tenant dolaşan yeniden yayın tekrarlayan bir platform işi olarak çalıştı (ilk çalışma açılıştan bir aralık sonra). Faz 2'ye: tenant başına iş örnekleri ve kotalar, etkinlik göstergesi, `scheduled_tasks`'ta tenant'a göre temizlik (§4.7.1). Ayrıntı: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md) B15–B16.
```

`docs/adr/0015-routing-datasource.md` → `| **Durum** | Kabul edildi |`

```markdown
**S1 sonucu (<TARİH>, `<COMMIT>`):** Tek routing DataSource (statik hedef haritası ve varsayılan hedef yok) JPA, jOOQ, `JdbcClient` ve Modulith'e hizmet etti; tam açılış, sağlık/metrik bağlama ve kapanış boyunca tenant'sız bağlantı isteği 0 (sayaçlı test). Gerekli ayarlar: `allow_jdbc_metadata_access=false` + ürün adı ve ana sürüm (diyalekt PostgreSQL 18), `ddl-auto=none`, `sql-dialect=POSTGRES`, `ignore-routing-data-sources=true`; `unwrap/isWrapperFor` yönlendirilmez. Platform DataSource `defaultCandidate=false` ile ayrıldı. Tembel havuzlar (`minIdle=0`, `max=4`, 5 dk boşta) oluşturulurken bağlantı açmıyor; bilinmeyen/askıdaki tenant'a havuz açılmıyor; transaction içinde tenant değiştirme reddediliyor. Faz 2'ye: LRU sınırı (aktif bağlantılı havuz kapatılmaz), durum değişikliğinde havuz kapatma, tenant başına metrik. Ayrıntı: [docs/spikes/s1-tenancy.md](../spikes/s1-tenancy.md) B4–B9, B12, B18.
```

`docs/adr/README.md` dizin tablosunda ADR-0003, 0012, 0013, 0014, 0015 satırlarının "Durum" hücresini `Kabul edildi` yap; ADR-0011 `Önerildi` kalır.

- [ ] **Step 4: Ürün reaktörünü ve sır taramasını doğrula**

```bash
./mvnw verify
gitleaks git --config .gitleaks.toml --redact .
gitleaks dir --config .gitleaks.toml --redact spikes docs
```

Expected: `BUILD SUCCESS` (spike ürün reaktöründe değil); iki gitleaks komutu da `no leaks found`.

- [ ] **Step 5: Commit**

```bash
git add docs/spikes/s1-tenancy.md docs/adr/0003-tenant-basina-veritabani.md docs/adr/0011-jpa-yazma-jooq-okuma.md docs/adr/0012-olay-kaydi-tenant-db.md docs/adr/0013-uuidv7.md docs/adr/0014-db-scheduler.md docs/adr/0015-routing-datasource.md docs/adr/README.md
git commit -m "docs(adr): record S1 tenancy spike results and accept ADR-0003, 0012-0015

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Plan 0B çıkış kontrolü

- [ ] `./mvnw verify` yeşil (ürün reaktörü; spike dahil değil).
- [ ] `./mvnw -Pspikes -pl spikes/s1-tenancy verify` yeşil (testler, Error Prone, Spotless, Modulith, ArchUnit).
- [ ] `StartupIsolationTests` yeşil: açılış ve kapanışta `rejectedWithoutTenant() == 0`.
- [ ] `gitleaks git --config .gitleaks.toml --redact .` → `no leaks found`.
- [ ] `docs/spikes/s1-tenancy.md` commit'li; ADR-0003, 0012, 0013, 0014, 0015 `Kabul edildi` (ya da gerekçeli `Değişti`), ADR-0011 S1 sonucuyla `Önerildi`; `docs/adr/README.md` güncel.
- [ ] Spike kodu `spikes/s1-tenancy/` altında; silinmesi plan 0E'nin işi.

## Sonraki planlar

| Plan | Durum |
|---|---|
| 0C — S2 Kimlik | Bu plandan bağımsız; S2, `TenantKey` biçim kuralını (B-tablosu #3) ve "her istekte host ↔ oturum tenant'ı" kontrolünü `TenantContext.call` ile bağlayacak. |
| 0D — S3 Metadata ve belge dilimi | S1'in routing DataSource + jOOQ + flush dinleyicisi düzenini varsayar. |
| 0E — Teyit listesi ve kapanış | `spikes/` klasörünün silinmesi, Flyway/Liquibase lisansı, jOOQ ticari lisans kararı (ADR-0011), ADR durumlarının son denetimi. |
