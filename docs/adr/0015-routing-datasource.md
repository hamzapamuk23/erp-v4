# ADR-0015: Routing DataSource; Hibernate multi-tenancy SPI'ı ve L2 cache yok

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §4.5 |

## Bağlam

Tenant başına veritabanı modelinde (ADR-0003) JPA, jOOQ ve Modulith'in hepsi doğru tenant'ın bağlantısını almalı. Hibernate'in açılıştaki JDBC metadata erişimi hangi tenant'a bağlanacağını bilemez; havuzlu sequence optimizer bir tenant'ın değerlerini başka bir tenant'a verebilir; Hibernate filtreleri `find(id)` çağrısına uygulanmaz. Tenant sayısı arttıkça havuz ve bağlantı sayısı büyür; PgBouncer bunu çözmez, çünkü o da veritabanı başına havuz tutar.

## Karar

- Tenant'a yönlendiren tek bir `DataSource` kullanılır; JPA, jOOQ ve Modulith aynı kaynaktan bağlantı alır. Hibernate multi-tenancy SPI'ı kullanılmaz, ikinci seviye cache kapalıdır.
- Hibernate tuzakları: `hibernate.boot.allow_jdbc_metadata_access=false` ve açıkça verilen diyalekt; havuzlu sequence optimizer yok; yazma tarafında veri kapsamı açıkça kontrol edilir (§7.7); aynı transaction'da jOOQ ile okumadan önce JPA `flush` edilir.
- Bağlamı olmayan thread'de tenant DB'sine erişim hata fırlatır, varsayılan tenant yoktur. Tenant transaction başlamadan önce belirlenir; transaction içinde değiştirmek yasaktır (§4.4).
- Havuzlar: tenant başına HikariCP havuzu tembel açılır (`minIdle=0`, `max=4` varsayılan, 5 dakika boşta kalan bağlantı kapanır). Instance başına açık havuz sayısı sınırlıdır (ör. 150); sınırda en uzun süredir kullanılmayan havuz kapatılır (LRU).
- Bağlantı tavanı şimdiden hesaplanır: `instance sayısı × aynı anda aktif tenant × havuz üst sınırı` (ör. 2 × 100 × 4 = 800). PostgreSQL'in rahat taşıdığı eşik (ör. 500) aşılacaksa yeni küme açılır (§4.2).
- Platform DB'si ayrı, yönlendirilmeyen bir `DataSource` kullanır.

## Sonuçlar

- Olumlu: Tek yönlendirme noktası. Boşta duran tenant'lar bağlantı tutmaz. Tenant'lar arası sequence sızıntısı riski yoktur.
- Olumsuz: Hibernate'in kendi multi-tenancy desteği ve ikinci seviye cache'i kullanılmaz; tuzaklar koruma kodu ve testle sürekli denetlenmelidir. Tenant başına DB'nin operasyon yükü (§19 R6).
- Takip: Küme başına bağlantı sayısı kapasite eşiği olarak izlenir (§11.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Hibernate multi-tenancy SPI'ı | Doküman ayrı bir gerekçe vermiyor; karar JPA, jOOQ ve Modulith'in tek bir yönlendirilen kaynaktan bağlantı almasıdır |
| PgBouncer | Bağlantı tavanı sorununu çözmez, o da veritabanı başına havuz tutar |

## Doğrulama

S1 spike'ı (§4.5: "Faz 0'daki S1 spike'ında doğrulanır"): 2 tenant DB + routing DataSource + Hibernate 7 (`allow_jdbc_metadata_access=false`, havuzlu optimizer yok), bağlam yayılımı, bağlamsız erişimde hata, transaction içinde tenant değiştirme koruması, jOOQ öncesi `flush`. Faz 2 çıkış kriteri: bağlamsız erişim hata fırlatıyor; kapsam: çoklu tenant routing ve tembel havuzlar (LRU).
