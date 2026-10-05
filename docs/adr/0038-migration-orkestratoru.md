# ADR-0038: Migration orkestratörü, şema sürüm aralığı, `outOfOrder` kapalı

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §4.6 |

## Bağlam

Her tenant'ın ayrı bir veritabanı vardır (ADR-0003). Spring Modulith 2'nin modül bazında Flyway desteği tek bir DataSource üzerinde çalışır ve tenant'ları dolaşmaz. SaaS'ta rolling deploy sırasında eski ve yeni instance'lar aynı anda çalışır (§11.3). Paralel geliştirmede migration sürümleri çakışabilir (§7.9).

## Karar

- Flyway migration'ları modül klasörlerindedir. **Tenant orkestratörünü biz yazarız** (`tenancy` modülü).
- Migration açılışın yan etkisi değil, ayrı bir adımdır: on-prem'de `erpctl migrate`, SaaS'ta lider seçimli bir iş. Paralellik sınırlıdır (ör. aynı anda 4 tenant). Her instance açılışta tüm tenant'ları migrate etmeye çalışmaz.
- **Şema sürüm aralığı:** her uygulama sürümü modül başına kabul ettiği aralığı (`min..max`) bildirir. Aralık dışındaki bir tenant'a istek kabul edilmez ve tenant `MAINTENANCE` durumuna alınır.
- Durum `migration_run` tablosuna yazılır; başarısız tenant bakım moduna geçer, diğerleri devam eder, uyarı üretilir.
- Genişlet/daralt: bir sürümdeki migration önceki sürümün kodunu kırmaz; kolon silme en erken bir sonraki sürümde yapılır.
- Sürümler zaman damgalıdır (`V<yyyyMMddHHmm>__<açıklama>.sql`, §7.9). Flyway'in `outOfOrder` seçeneği kapalıdır; CI yeni migration'ın son yayınlanmış sürümden yeni olduğunu denetler.
- Sadece ileri giden migration yazılır; geri dönüş yükseltme öncesi otomatik alınan yedekle yapılır. On-prem güncellemede önce platform DB'si, sonra tenant DB'leri migrate edilir (§11.2).

## Sonuçlar

- Olumlu: Bir tenant'taki migration hatası diğerlerini durdurmaz. Eski instance'lar genişletilmiş şemayı kendi aralığında gördüğü için rolling deploy sırasında çalışmaya devam eder.
- Olumsuz: Orkestratör bizim yazıp bakımını yaptığımız koddur. Geri alma migration'ı yoktur; geri dönüş yedekten yapılır.
- Takip: Flyway ve Liquibase lisans durumunun teyidi (§12.1, §15.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Sadece Spring Modulith 2'nin modül bazında Flyway desteği | Tek bir DataSource üzerinde çalışır, tenant'ları dolaşmaz |
| Liquibase | Lisans değişikliği nedeniyle tercih edilmedi (teyit Faz 0'da, §12.1) |

## Doğrulama

Faz 2 çıkış kriteri: bir tenant'taki migration hatası diğerlerini etkilemiyor; eski sürüm instance'lar genişletilmiş şemayla çalışmaya devam ediyor. Faz 7 çıkış kriteri: kasıtlı olarak bozulan bir migration'da otomatik geri dönüş çalışıyor. Teyit listesi: Flyway ve Liquibase lisans durumu.
