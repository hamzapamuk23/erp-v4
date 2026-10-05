# ADR-0032: Yazdırma şablonu güvenliği

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.13 |

## Bağlam

Jasper ifadeleri derlenmiş Java'dır, yani bir şablon sunucuda rastgele kod çalıştırabilir. Danışmanlar şirkete özel şablon yükleyebilir (genişletme merdiveninin 4. basamağı, §5.5). Tenant'ın yüklediği içerikle kod çalıştırma riskinin etkisi kritiktir (§19 R14).

## Karar

- SaaS'ta tenant'ın ya da danışmanın yüklediği şablon doğrudan kullanılmaz; ürün ekibinin incelemesinden geçip **imzalanır**.
- Jasper sınıf beyaz listesi (class allowlist) açıktır.
- Ürün dışı şablonlar **DB kimlik bilgisi ve ağ erişimi olmayan ayrı bir render işçisinde** çalışır ve veriyi JSON DTO olarak alır. Render işçisi aynı imajın ayrı bir giriş noktasıdır.
- Şablon doğrudan SQL çalıştırmaz; veri jOOQ ile hazırlanan yazdırma DTO'larından gelir.

## Sonuçlar

- Olumlu: Şablonla sunucuda kod çalıştırma tehdidi imza, sınıf beyaz listesi ve izole işçiyle önlenir (§10.1).
- Olumsuz: SaaS'ta şablon override'ı ürün ekibinin incelemesine bağlıdır. Ayrı bir render işçisi işletilir.
- Takip: İmzalama ve izole render işçisi Faz 6 kapsamındadır (§15.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

Teyit listesi: JasperReports 7 + Java 25 uyumu ve sınıf beyaz listesi (§15.3). Faz 6 çıkış kriteri: tenant şablonu izole işçide çalışıyor; şablondan DB'ye ya da ağa erişim denemesi engelleniyor (güvenlik testi). Çekirdek tamam kriteri §16.1 madde 14 (şablon sandbox testi).
