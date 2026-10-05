# Karar kayıtları (ADR)

Kararların gerekçesi; süreç: [CONTRIBUTING.md](../../CONTRIBUTING.md). Kararların kaynağı [v4-platform.md](../architecture/v4-platform.md) §18'dir; dokümandaki `ADR-0NN` atıfları buradaki `ADR-00NN` dosyalarına karşılık gelir. Yeni bir ADR `0000-template.md` kopyalanarak açılır.

## Durum sözlüğü

| Durum | Anlamı |
|---|---|
| Önerildi | Karar açıldı; spike, test ya da inceleme ile henüz doğrulanmadı. `Doğrulama` bölümü nasıl doğrulanacağını yazar. |
| Kabul edildi | Doğrulama kararı olduğu gibi destekledi. Kabul edilmiş kararın metni değiştirilmez. |
| Değişti | Doğrulama sonucunda karar değiştirildi; ne değiştiği ve nedeni `Doğrulama` bölümündedir. |
| Reddedildi | Öneri kabul edilmedi. |
| Yerini aldı (ADR-NNNN) | Kabul edilmiş karar yeni bir ADR ile değiştirildi. |

## Geri dönüş maliyeti (§18)

- *Tek yön:* sonradan değiştirmek neredeyse yeniden yazım demek.
- *Pahalı:* her tenant'a ya da tüm modüllere dokunan, aylarca sürecek bir göç.
- *Kolay:* yerel bir değişiklik.

## Dizin

| ADR | Başlık | Durum | Geri dönüş |
|---|---|---|---|
| [ADR-0001](0001-moduler-monolit.md) | Modüler monolit (Spring Modulith), mikroservis yok | Önerildi | Pahalı |
| [ADR-0002](0002-tek-imaj-saas-onprem.md) | Tek imaj ve tek kod tabanı: SaaS + on-prem | Önerildi | Tek yön |
| [ADR-0003](0003-tenant-basina-veritabani.md) | Tenant başına veritabanı | Kabul edildi | Tek yön |
| [ADR-0004](0004-platform-veritabani.md) | Platform DB ayrımı | Önerildi | Pahalı |
| [ADR-0005](0005-keycloak-organizations.md) | Keycloak 26.8, tek iş realm'i + Organizations | Önerildi | Pahalı |
| [ADR-0006](0006-bff-oturum-cerezi.md) | BFF + `__Host-` oturum çerezi; tarayıcıda token yok | Önerildi | Pahalı |
| [ADR-0007](0007-metadata-yaml-overlay.md) | Metadata: deklaratif YAML + Java davranış, overlay'ler DB'de | Önerildi | Tek yön |
| [ADR-0008](0008-ozel-alanlar-ext-jsonb.md) | Özel alanlar: `ext jsonb` + isteğe bağlı ifade index'i | Önerildi | Pahalı |
| [ADR-0009](0009-moduller-arasi-fk-ve-viewlar.md) | Modüller arası FK ve yayınlanmış view kuralları | Önerildi | Pahalı |
| [ADR-0010](0010-sema-kapsami-ve-modul-durumlari.md) | Şema kapsamı ve modül durumları | Önerildi | Pahalı |
| [ADR-0011](0011-jpa-yazma-jooq-okuma.md) | Yazma JPA/Hibernate, okuma jOOQ; ticari jOOQ lisansı | Önerildi | Pahalı |
| [ADR-0012](0012-olay-kaydi-tenant-db.md) | Olay kaydı tenant DB'sinde; tenant dolaşan yeniden yayın | Kabul edildi | Pahalı |
| [ADR-0013](0013-uuidv7.md) | Teknik kimlik UUIDv7, uygulama tarafında üretilir | Kabul edildi | Pahalı |
| [ADR-0014](0014-db-scheduler.md) | db-scheduler platform DB'sinde; tenant başına iş örnekleri | Kabul edildi | Pahalı |
| [ADR-0015](0015-routing-datasource.md) | Routing DataSource; Hibernate multi-tenancy SPI'ı ve L2 cache yok | Kabul edildi | Pahalı |
| [ADR-0016](0016-redis-yok-listen-notify.md) | Redis yok; LISTEN/NOTIFY + TTL ve sürüm kontrolü | Önerildi | Kolay |
| [ADR-0017](0017-arama-pg-trgm.md) | Arama: `search_text` + `pg_trgm` | Önerildi | Kolay |
| [ADR-0018](0018-belge-durum-eksenleri.md) | Belge durum eksenleri ve üç düzeltme deseni | Önerildi | Pahalı |
| [ADR-0019](0019-bpmn-yok.md) | BPMN yok, onay politikaları | Önerildi | Kolay |
| [ADR-0020](0020-vuetify-erp-ui.md) | Vuetify 4 + `@erp/ui` sarmalayıcısı; PrimeVue reddedildi | Önerildi | Pahalı |
| [ADR-0021](0021-ag-grid-community.md) | AG Grid Community; Enterprise kararı ertelendi | Önerildi | Kolay |
| [ADR-0022](0022-yerel-donanim-ajani.md) | Yerel donanım ajanı: protokol ve kimlik çekirdekte | Önerildi | Kolay |
| [ADR-0023](0023-saha-pwa-kiosk.md) | Saha: PWA ve kiosk arketipi; gerekirse Capacitor | Önerildi | Kolay |
| [ADR-0024](0024-onprem-linux-compose-erpctl.md) | On-prem: Linux + compose + `erpctl`, zorunlu TLS; Windows için appliance VM | Önerildi | Pahalı |
| [ADR-0025](0025-tek-surum-hatti-lts.md) | Tek sürüm hattı + LTS | Önerildi | Pahalı |
| [ADR-0026](0026-yazdirma-jasper-xslt-zpl.md) | Yazdırma: Jasper + XSLT + ZPL; jsreport yok | Önerildi | Kolay |
| [ADR-0027](0027-kod-ingilizce-turkce-metin.md) | Kod İngilizce, UI i18n; Türkçe metin kuralları | Önerildi | Kolay |
| [ADR-0028](0028-monorepo-gitlab-ci.md) | Monorepo, GitLab CI | Önerildi | Kolay |
| [ADR-0029](0029-baslangic-surumleri.md) | Başlangıç sürümleri | Önerildi | Pahalı |
| [ADR-0030](0030-numeric-para-miktar.md) | Para ve miktar için `numeric` + değer tipleri | Önerildi | Tek yön |
| [ADR-0031](0031-para-birimi-kur-modeli.md) | Para birimi ve kur modeli | Önerildi | Tek yön |
| [ADR-0032](0032-yazdirma-sablon-guvenligi.md) | Yazdırma şablonu güvenliği | Önerildi | Kolay |
| [ADR-0033](0033-numaralandirma-modlari.md) | Numaralandırma modları, tarih monotonluğu, seri seçim SPI'ı | Önerildi | Pahalı |
| [ADR-0034](0034-belge-satiri-modeli.md) | Belge satırı modeli ve satır bazında bağlantılar | Önerildi | Tek yön |
| [ADR-0035](0035-hesaplama-hatti.md) | Hesaplama hattı SPI'ı, yuvarlama politikası, simülasyon | Önerildi | Pahalı |
| [ADR-0036](0036-analitik-boyutlar.md) | Analitik boyut seti | Önerildi | Pahalı |
| [ADR-0037](0037-ledger-taahhut-defteri.md) | Ledger sözleşmesi + taahhüt defteri | Önerildi | Pahalı |
| [ADR-0038](0038-migration-orkestratoru.md) | Migration orkestratörü, şema sürüm aralığı, `outOfOrder` kapalı | Önerildi | Pahalı |
| [ADR-0039](0039-spring-session-jdbc.md) | Oturum deposu: Spring Session JDBC (platform DB) | Önerildi | Kolay |
| [ADR-0040](0040-uc-kimlik-yolu.md) | Üç kimlik yolu | Önerildi | Pahalı |
| [ADR-0041](0041-kuresel-referans-veriler.md) | Küresel referans veriler platform DB'sinde | Önerildi | Pahalı |
| [ADR-0042](0042-personel-realm-impersonation.md) | Personel realm'i, step-up ve audit'li impersonation | Önerildi | Kolay |
| [ADR-0043](0043-geri-yukleme-el-kitabi-wal.md) | Tenant geri yükleme el kitabı ve WAL arşivleme | Önerildi | Kolay |
| [ADR-0044](0044-referans-modul.md) | Platformu nötr bir referans modülle kanıtlama | Önerildi | Kolay |
