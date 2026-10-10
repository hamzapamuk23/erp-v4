# ADR-0039: Oturum deposu: Spring Session JDBC (platform DB)

| | |
|---|---|
| **Durum** | Kabul edildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.3.1 |

## Bağlam

BFF deseninde tarayıcı oturumu sunucuda tutulur (ADR-0006). Uygulama durumsuz kalmalı ve instance'lar yatayda çoğalabilmeli (§5.2). Redis kullanılmıyor (ADR-0016).

## Karar

- BFF oturumları **Spring Session JDBC** ile platform DB'sinde tutulur (`spring_session*` tabloları, §4.3).
- Oturum `(kullanıcı, tenant)` çiftine bağlanır; birden çok tenant'a üye kullanıcı için her tenant ayrı bir oturumdur (§4.4).

## Sonuçlar

- Olumlu: Oturumlar DB'de olduğu için instance'lar durumsuzdur ve yatayda çoğalır (§5.2). Ek bileşen gerekmez.
- Olumsuz: Doküman ayrı bir bedel adlandırmıyor.
- Takip: SaaS'ta ölçülmüş bir darboğaz olursa Redis yeniden değerlendirilir (§12.4).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Redis | LISTEN/NOTIFY + Caffeine + DB oturumları yeterli (§12.4) |

## Doğrulama

S2 spike'ı (Faz 0 kimlik spike'ı, §15.3): BFF oturumunun Spring Session JDBC ile platform DB'de tutulması.

**S2 sonucu (2026-10-10, `bde87e6`):** Oturumlar platform DB'sinde tutuldu (`spring_session*` migration'la kurulur, `initialize-schema: never`); tam giriş, oturumlu ve bearer API çağrıları ve kapanış boyunca tenant DataSource'una 0 istek. Zorunlu uygulama kuralı: platform DataSource `@SpringSessionDataSource` ile işaretlenir ve Spring Session'a kendi transaction'ı verilir (`@Bean(name = "springSessionTransactionOperations", defaultCandidate = false)`, platform DS üzerinde `DataSourceTransactionManager`, `REQUIRES_NEW`). Kanca yokken Spring Session bağlamdaki tek `PlatformTransactionManager`'ı (Boot'un, varsayılan DataSource üzerindeki) aldı ve ilk oturum kaydı tenant DataSource'una gitti (giriş 500); Faz 1'in routing DataSource'unda bu hata sessiz olurdu (oturum transaction'ı tenant DB'sinde, JDBC işi platform DB'sinde). `defaultCandidate = false` da zorunlu (kaynak okuması): varsayılan aday bir `TransactionOperations` Boot'un `transactionTemplate`'ini bastırır. Principal adı `<tenant>:<sub>`; principal index'i `(kullanıcı, tenant)` oturumlarını buluyor, bir kullanıcının bütün tenant'lardaki oturumlarını tek seferde bulamıyor (Faz 3). Spring Session'ın temizlik zamanlayıcısı kapalı (`cleanup-cron: "-"`, K12); süresi dolan oturumları Faz 1'de db-scheduler işi siler. Bearer zinciri `STATELESS` olduğu hâlde Spring Session arkasında oturum çerezi taşıyan isteğin oturumunu yükledi ve kimliğini döndürdü; zincir oturumu istekten gizlemek zorunda (ADR-0040). Sonuçlar'daki "Olumsuz" satırı için ölçülen bedel: her tarayıcı isteği platform DB'sinde host araması (önbelleksiz), oturumlu istek ayrıca oturum okuması ve `UPDATE spring_session` (`last_access_time`) yapar; her giriş başlangıcı ve oturumsuz sayfa isteği (istek önbelleği) kimliği doğrulanmamış bir oturum satırı yazar ve bu satırlar paylaşılan platform DB'sinde tenant'lar arası bir DoS büyütme yüzeyidir (Faz 1 temizlik, Faz 2 host önbelleği, Faz 7 oran sınırı). Takip maddesi (ölçülmüş darboğazda Redis) geçerli. Ayrıntı: [docs/spikes/s2-identity.md](../spikes/s2-identity.md) B7, B10–B13, B22.
