# ADR-0004: Platform DB ayrımı

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §4.3 |

## Bağlam

Tenant başına veritabanı modelinde (ADR-0003) tenant kaydı, alan adı eşlemesi, oturumlar, iş kuyruğu, lisans ve herkese açık küresel referans veriler hiçbir tenant'a ait değildir. v1'de kontrol düzlemi kayıtları (müşteri, fabrika, uygulama, port, ortam değişkeni) ayrı bir `smart-update` ve CRM yığınında tutuluyordu (§2.3).

## Karar

- Tenant'tan bağımsız veriler ayrı bir veritabanında, `erp_platform`'da tutulur. Tablo grupları: `tenant` (durum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `MAINTENANCE`, `ARCHIVED`; DB küme ve adı, saat dilimi, varsayılan dil, şema sürümü), `tenant_module`, `tenant_domain`, `entitlement`/`license`, `spring_session*`, `scheduled_tasks`, `migration_run`, `platform_audit`, `job_run`, `global_refdata`.
- Tenant'a ait iş verisi asla platform DB'ye yazılmaz. Belgeler küresel referans verilere FK vermez; kodu ve gerekiyorsa değeri (kur gibi) kopyalayarak saklar.
- Platform DB'si ayrı, yönlendirilmeyen bir `DataSource` kullanır; `LISTEN/NOTIFY` için instance başına havuz dışında tek bir bağlantı açılır (§4.5).
- On-prem'de de platform DB'si vardır (§6.17).

## Sonuçlar

- Olumlu: Tenant çözümleme, oturum, iş ve lisans tek yerdedir. SaaS ve on-prem aynı modeli kullanır (§11.5).
- Olumsuz: İki DB arasında atomik yazma yoktur; tenant transaction'ı iş kuyruğuna Modulith outbox üzerinden bağlanır (§6.12, ADR-0014).
- Takip: İlgili kararlar: oturumlar ADR-0039, işler ADR-0014, küresel referans veriler ADR-0041, migration durumu ADR-0038.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

İlgili fazın çıkış kriteri: Faz 2 (kapsam: platform DB'si, tenant durum makinesi, alan adı eşleme; §15.3).

**S2 sonucu (2026-10-10, `bde87e6`):** Not: Organization bağı `tenant` tablosunda durdu (`oidc_issuer`, `organization_alias`; `(oidc_issuer, organization_alias)` tekil), `tenant_domain` sadece host → tenant eşlemesi: bir tenant'ın birden çok host'u olabilir ve bearer isteğinin anlamlı bir host'u yoktur. §4.3 bağı `tenant_domain`'a koyuyor; doküman önerisi bulgularda. BFF oturumlarının platform DB'sinde, ayrı ve yönlendirilmeyen platform DataSource'u ve kendi transaction'ıyla tutulduğu doğrulandı (ADR-0039). Tenant araması (host, `(issuer, alias)`) platform DB'sinden önbelleksiz yapıldı; önbellek Faz 2'de. Faz 2 çıkış kriteri geçerliliğini korur. Ayrıntı: [docs/spikes/s2-identity.md](../spikes/s2-identity.md) B11, B19.
