# ADR-0040: Üç kimlik yolu

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.3.1, §6.18 |

## Bağlam

Tarayıcı kullanıcıları, mobil uygulama ve entegrasyon istemcileri ile saha cihazları (kiosk tabletler, native kabuk, yerel ajan) farklı kimlik biçimleri ister (§2.3). Client credentials token'larında organization iddiası doğal olarak bulunmaz (§4.4). v1'de cihaz soket namespace'i kimliksizdi ve operatör şifreleri tablete gönderiliyordu (§2.2 H14). Tarayıcıda token tutulmaz (ADR-0006).

## Karar

- Üç kimlik yolu vardır; her biri ayrı bir `SecurityFilterChain`'dir:
  1. **Tarayıcı oturumu** (BFF, ADR-0006).
  2. **OIDC bearer:** mobil uygulama ve entegrasyon API istemcileri. Tenant token'daki `issuer` ve `organization` iddiasından çözülür. Her API istemcisine Keycloak'ta sabit tenant iddiası eşleyicisi (hardcoded claim mapper) tanımlanır; iddiası olmayan token reddedilir (§4.4).
  3. **Cihaz kimliği:** tarayıcıda çalışan kiosk için kayıt sırasında BFF'nin oluşturduğu, cihaza bağlı, uzun ömürlü HttpOnly cihaz oturum çerezi; native kabuk ve yerel ajan için uygulamanın ürettiği, döndürülebilir, hash'li saklanan cihaz token'ı.
- Cihaz kaydı tenant bazındadır; tek kullanımlık kayıt koduyla eşleştirilir ve iptal edilebilir.
- Ortak cihazda operatör kart ya da PIN ile tanınır; doğrulama sunucuda yapılır (hash'li PIN, deneme sınırı). İşlemler "cihaz + operatör" olarak kaydedilir. Operatörler Keycloak hesabı almaz.

## Sonuçlar

- Olumlu: Tarayıcıda token tutmama kuralı kiosklarda da korunur (§9.7). Her yolda tenant'ın nasıl çözüldüğü açıktır.
- Olumsuz: Üç ayrı güvenlik zinciri ayrı ayrı test edilir ve bakılır.
- Takip: API istemcileri, cihaz kaydı ve operatör oturumunun temeli Faz 3 kapsamındadır (§15.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Kimliksiz cihaz bağlantısı ve istemcide şifre doğrulama (v1) | Saha güvenliği yok (§2.2 H14) |

## Doğrulama

S2 spike'ı: bearer zinciri (sabit tenant iddiası) (§15.3). Faz 3: API istemcileri ve cihaz kaydı (kiosk cihaz çerezi + native token) kapsamda; çıkış kriteri izinsiz erişimde 403, kapsam dışı kayıtta 404.
