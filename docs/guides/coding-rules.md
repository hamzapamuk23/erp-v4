# Kodlama kuralları

Kaynak: [v4-platform.md](../architecture/v4-platform.md) §3.2, §5.3, §7, §9, §14. Bu dosya kuralların **nasıl** uygulandığını ve hangisinin şu an otomatik denetlendiğini gösterir.

## Kırmızı çizgiler ve denetim durumu

| # | Kural (özet) | Denetim | Durum |
|---|---|---|---|
| K1 | Modüller arası erişim sadece `api`/`spi`/olay/view | Spring Modulith `verify` (`app` → `ModularityTests`) | Aktif (Faz 0); ArchUnit ek kuralları Faz 1 |
| K2 | Jenerik modülde sektör kavramı yok | Kod incelemesi | Süreç |
| K3 | Bir iş işlemi = tek komut + tek transaction | ArchUnit (`@Transactional` application katmanında) | Faz 1 |
| K4 | Kesinleşmiş belge/ledger içerik kolonları değişmez | DB trigger + test | Faz 5 |
| K5 | Para/miktar/oran/kur için `float`/`double` yok | ArchUnit | Faz 1 |
| K6 | Çekirdek iş verisi jsonb'de değil | Metadata doğrulayıcı | Faz 4 |
| K7 | Her tabloda `@Version`, güncellemede If-Match | Kernel temel sınıfı + ArchUnit | Faz 1 |
| K8 | Müşteriye özel build/dal/fork yok | Süreç | Süreç |
| K9 | Repoda sır yok | gitleaks (CI `secrets:gitleaks`), `.gitignore` | **Aktif** |
| K10 | `toUpperCase()`/`toLowerCase()` parametresiz yasak (çağrı ve `String::toUpperCase` metot referansı) | Error Prone `StringCaseLocaleUsage` (ERROR) + ArchUnit `NO_LOCALE_LESS_CASE_CONVERSION` | **Aktif** |
| K11 | Nesneler `==` ile karşılaştırılmaz | Error Prone `ReferenceEquality`, `BoxedPrimitiveEquality` (ERROR) | **Aktif** |
| K12 | `@Scheduled`/`@Schedules`/`@EnableScheduling` yasak (bunlarla meta-anotasyonlanmış birleşik anotasyonlar dahil) | ArchUnit `NO_SPRING_SCHEDULING` | **Aktif** |
| K13 | Liste uç noktaları her zaman sayfalı | ArchUnit + API testleri | Faz 1 |
| K14 | Entity'ler API'de serileştirilmez | ArchUnit | Faz 1 |
| K15 | Kişisel veri alanları işaretli, logda yok | Metadata doğrulayıcı, log maskeleme | Faz 4 |
| — | Üretim bağımlılıklarında lisans izinli listesi | `license-maven-plugin` (`app`), `web/scripts/check-licenses.mjs` | **Aktif** |
| — | `v-html` yasak (§10.1) | ESLint `vue/no-v-html` | **Aktif** |

Lisans denetimleri, çift lisanslı bir bağımlılığı alternatiflerinden herhangi biri izinli listedeyse geçirir (ör. logback, EPL-2.0 üzerinden).

Yeni bir mimari kural `platform/platform-test` içindeki `ErpArchitectureRules`'a, **negatif bir fixture testiyle birlikte** eklenir ve her modülün `ArchitectureRulesTests` sınıfında uygulanır.

## Java

- Paket kökü `com.smart.erp.<modül>`; modül içi düzen §5.3: `api`, `spi`, `application`, `domain`, `infra`, `web`. Modülün kök paketi ve `api` açık, diğerleri iç pakettir.
- DTO, komut, olay ve değer nesneleri `record`'dur. Lombok sadece entity'lerde `@Getter`/`@Setter` ile kullanılabilir; `@Data` yasaktır.
- Bağımlılıklar constructor ile enjekte edilir; alan enjeksiyonu kullanılmaz.
- Yapılandırma `@ConfigurationProperties` record'larıyla, `@Validated` olarak okunur; `@Value` dağınık kullanılmaz.
- Para ve miktar `BigDecimal`, iş tarihi `LocalDate`, an `Instant`. Zaman `Clock` üzerinden alınır.
- Büyük/küçük harf dönüşümü `Locale.ROOT` ile; Türkçe arama normalizasyonu kernel'deki `TurkishText.fold()` ile (Faz 1). `equalsIgnoreCase` Türkçe metinde kullanılmaz.
- Biçim palantir-java-format'tır: `./mvnw spotless:apply`. Biçim tartışması kod incelemesinde yapılmaz.
- Test sınıfları `*Tests` adını taşır. Gerçek veritabanı gereken testler `PostgresTestcontainer` kullanır; H2 gibi gömülü veritabanları kullanılmaz.

## Veritabanı

- İsimlendirme `snake_case`, tablo adları tekil, FK kolonları `<hedef>_id` (§7.2).
- Standart kolonlar ve tipler §7.2–§7.3'tedir.
- Migration dosyası: `db/migration/<modül>/V<yyyyMMddHHmm>__<açıklama>.sql`; genişlet/daralt kuralı (§4.6, §7.9).

## Frontend

- Bileşenler `<script setup lang="ts">` ile yazılır (ESLint zorlar).
- Biçimi Prettier belirler: `pnpm format`. `web/eslint.config.js` sonda `@vue/eslint-config-prettier/skip-formatting` kullandığı için ESLint'in Vue biçim kuralları kapalıdır; lint yalnızca doğruluk kurallarını raporlar.
- Paket bağımlılık yönü: `apps → modules → shell → (meta-renderer) → core → ui`. Bir paket sadece `package.json`'unda bildirdiği paketi import edebilir; pnpm bunu çözümleme seviyesinde zorlar.
- Vuetify yalnızca `@erp/ui` üzerinden yapılandırılır (`createErpVuetify`). Modül paketleri `vuetify`'ı bağımlılık olarak bildirmez, `@erp/ui` bileşenlerini kullanır. Shell, Vuetify yerleşim bileşenlerini (`v-app`, `v-app-bar`, `v-main`) doğrudan kullanabilir.
- Kullanıcıya görünen her metin i18n dosyasındadır (`tr` ve `en`). Anahtar düzeni `<modül>.<kaynak>.<alan>` (§9.8).
- Sunucu durumu TanStack Query ile yönetilir; API hataları `@erp/core`'daki `ApiError` olarak gelir.
- Sürümler `web/pnpm-workspace.yaml` → `catalog:` bölümündedir; paketler `"catalog:"` yazar.

## Test isimlendirme ve E2E sözleşmesi

- E2E testlerinin kullandığı DOM kancaları `data-testid` ile verilir ve bileşenin dokümante edilmiş sözleşmesidir (ör. `shell-title`, `system-version`, `backend-unavailable`). Görsel metne ya da CSS sınıfına bağlı seçici yazılmaz; istisna, metnin kendisinin test edildiği durumlardır (ör. Türkçe karakter kontrolü).
