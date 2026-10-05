# ADR-0007: Metadata: deklaratif YAML + Java davranış, overlay'ler DB'de

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Tek yön |
| **Kaynak** | v4-platform.md §6.4 |

## Bağlam

v1'de metadata entity üzerindeydi ve UI'a bağlıydı (`@Metadata/@MetaColumn` → `MetaDataService`); v2 ve v3 bunu değiştirmedi (§2.1, §2.4). Metadata motoru çekirdeğin en kritik bileşenidir: CRUD motoru ve frontend renderer aynı tanımdan çalışır (§6.5, §9.5). Müşteri farkı önce konfigürasyonla karşılanmalı (§3.1 madde 2) ve özelleştirmeler güncellemeyi kırmamalı (§1.3 hedef 4).

## Karar

- İlkeler (§6.4.1): UI'dan bağımsız (Vuetify sınıfı, piksel değeri, bileşen adı yok; istisna kayıtlı özel widget anahtarı); sunucuda da geçerli (zorunluluk, salt okunurluk, gizlilik ve alan izni API'de uygulanır); kodda tanımlı, veritabanında özelleştirilir; temel tanım ve overlay tek JSON şemasına uyar.
- Kaynaklar modülün `metadata/` klasöründe deklaratif **YAML** dosyalarıyla tanımlanır. Davranış (hesaplama, doğrulama, eylem) Java'da yazılır ve tanımda adıyla bağlanır (§6.4.2). Nihai şema Faz 4'te kesinleşir.
- Overlay katman sırası: kod (modül) → sektör paketi overlay'i (pakette dosya) → tenant overlay'i (DB). Overlay kodun koyduğu kısıtı gevşetemez, davranışı değiştiremez, script çalıştıramaz. Kodda bir alan kaldırılır ya da yeniden adlandırılırsa kodla birlikte overlay migration yazılır (§6.4.4).
- Tip güvenliğindeki kayıp build sırasında çalışan **metadata doğrulayıcı** ile telafi edilir: JSON şeması, JPA metamodeli, gerçek veritabanı şeması (Testcontainers), izin kataloğu ve hook kayıtları denetlenir; uyumsuzluk build'i kırar, açılışta da tekrarlanır.
- Birleştirilmiş tanım `(tenant, kaynak, sürüm, overlay sürümü)` anahtarıyla Caffeine'de önbelleğe alınır, `LISTEN/NOTIFY` ile geçersizleştirilir (ADR-0016) ve istemciye ETag ile sunulur.

## Sonuçlar

- Olumlu: Temel tanım ve overlay aynı formatta olduğu için birleştirme basit; inceleme ve diff kolay; scaffold YAML üretir, ileride görsel overlay editörü aynı modeli düzenleyebilir.
- Olumsuz: YAML, Java DSL'in sağlayacağı tip güvenliğini vermez; doğrulayıcı zorunludur. Motorun katılaşma riski vardır (§19 R2); kaçış yolları zorunlu ilkedir (§6.5, §9.5).
- Takip: Özel alanlar ADR-0008'de.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Java DSL | Temel tanım ile overlay aynı formatta olmaz; inceleme ve diff zorlaşır. Frappe (DocType JSON) ve Axelor (XML) gibi başarılı metadata güdümlü sistemler de deklaratif dosya tercih ediyor. |
| Entity üzerinde, UI'a bağlı metadata (v1–v3) | UI'a bağlı (§2.4); v4'te UI'dan bağımsız, overlay'li ve sunucuda da uygulanan bir motor isteniyor (§2.1) |

## Doğrulama

S3 spike'ı: bir YAML kaynak ve satırlı bir belge → jOOQ liste sorgusu → REST → Vuetify 4 formu + AG Grid satır ızgarası (§15.3). Faz 4 çıkış kriteri: referans modülün ana verileri hiç controller ve Vue kodu yazılmadan çalışıyor; overlay editörüyle etiket değiştirme, alan gizleme, zorunlu yapma ve özel alan ekleme deploy etmeden yapılabiliyor (E2E).
