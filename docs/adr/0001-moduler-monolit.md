# ADR-0001: Modüler monolit (Spring Modulith), mikroservis yok

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §5 |

## Bağlam

v1'de 25'ten fazla servis aynı PostgreSQL'i paylaşıyor ve birbirinin tablolarını doğrudan okuyordu: mikroservisin maliyeti vardı, faydası yoktu (§2.2 H5). Dyeing ve knitting servislerindeki kopyala-yapıştır sınıflar ortak davranışın yanlış yerde durduğunu gösterdi (H7). Ekip küçüktür ve ilke, gerekmedikçe yeni altyapı bileşeni eklememektir (§3.1 madde 7). Platform, iş modülleri, sektör paketleri ve müşteri uzantıları arasında net sınırlar ve tek bir bağımlılık yönü gerekiyor (§5.1).

## Karar

- Uygulama Spring Modulith ile kurulan bir **modüler monolittir**; tek imajda, N instance olarak çalışır (§5.2). Mikroservis kullanılmaz.
- Katmanlar: müşteri uzantıları → sektör paketleri → iş modülleri → platform. Bağımlılık her zaman bağımlıdan sağlayıcıya doğrudur (sektör paketi → iş modülü → platform); döngü yasaktır (§5.1, §5.4).
- Her modül hem bir Maven modülü hem bir Spring Modulith uygulama modülüdür. Açık paketler `api` ve `spi`, iç paketler `application`, `domain`, `infra`, `web`'dir (§5.3).
- Modüller birbirleriyle sadece senkron API, domain olayı, uzatma noktası (SPI) ve yayınlanmış okuma view'ı üzerinden konuşur (§5.4, K1).
- `@Stable`/`@Experimental`/`@Internal` anotasyonları ve japicmp karşılaştırmasıyla kırıcı değişiklik politikası uygulanır (§5.4).

## Sonuçlar

- Olumlu: Modül sınırları mikroservisin operasyon maliyeti olmadan elde edilir (§12.4). Senkron API çağrısı aynı transaction'a katılabilir (§5.4). Sınırlar build sırasında denetlenir.
- Olumsuz: Tüm modüller birlikte sürümlenir ve dağıtılır; tek bir modül bağımsız ölçeklenemez.
- Takip: Bir modülün bağımsız ölçeklenme ya da yayın ihtiyacı kanıtlanırsa o modül ayrılır (§12.4).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Mikroservisler | Küçük ekip için operasyon maliyeti büyük; modüler monolit aynı sınırları daha ucuza verir (§12.4). v1'de veritabanını paylaşan servisler bu maliyeti faydasız ödedi (H5). |

## Doğrulama

Çekirdek tamam kriteri §16.1 madde 5: modüller arası erişim sadece API, olay ve view üzerinden ve build'de denetleniyor (Modulith verify + ArchUnit, negatif testlerle). Faz 0A'da Spring Modulith yapı doğrulaması `./mvnw verify` içinde çalışıyor (`app` → `ModularityTests`, K1); ArchUnit ek kuralları Faz 1'de eklenir (`docs/guides/coding-rules.md`).
