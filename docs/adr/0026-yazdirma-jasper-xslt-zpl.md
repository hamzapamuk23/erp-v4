# ADR-0026: Yazdırma: Jasper + XSLT + ZPL; jsreport yok

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.13 |

## Bağlam

v1'de Jasper ve jsreport aynı anda kullanılıyordu ve rapor kodu fabrika bazında parametreyle şablona enjekte ediliyordu (§2.2 H17). e-Fatura, e-Arşiv ve e-İrsaliye görselleri UBL içine gömülü XSLT ile oluşur. Paket QR'ları, iş emri barkodları ve fabrika bazında kontrollü doküman kodları gibi ihtiyaçlar var (§2.3). Jasper'da Türkçe karakter kaybı klasik bir sorundur.

## Karar

- Şablon tipleri: **Jasper** (`jrxml`) belge ve rapor çıktıları için; **XSLT** e-belge görselleri için (Jasper ile değil); **ZPL** etiketler için. jsreport kaldırılır.
- Şablon deposu: modül varsayılan şablonları getirir; tenant ve şirket kendi şablonunu kullanabilir; şablonlar sürümlü, önizlenebilir ve geri alınabilir. Şablon metadata'sı kontrollü doküman kodu, kağıt boyutu ve kopya sayısı taşır.
- Veri jOOQ sorgularıyla hazırlanan yazdırma DTO'larından gelir; şablon doğrudan SQL çalıştırmaz.
- Noto veya DejaVu fontları paketle gelir ve PDF'e gömülür.
- Toplu yazdırma ve iş istasyonu bazında yazıcı profilleri; ZPL, barkod ve QR üretimi, gıda için GS1-128. QR içeriği ham UUID değil imzalı kısa koddur.
- Şablon güvenliği ADR-0032'dedir.

## Sonuçlar

- Olumlu: Tek rapor motoru; iki motorun bakım yükü biter.
- Olumsuz: Jasper ifadeleri derlenmiş Java'dır; şablonla kod çalıştırma riski ayrı önlemler ister (ADR-0032, §19 R14).
- Takip: Toplu yazdırma ve yazıcı profilleri kesme listesindedir (§15.4 madde 7). Sessiz yazdırma yerel donanım ajanıyla yapılır (ADR-0022).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| jsreport (Jasper ile birlikte, v1) | İki motorun bakım yükü (§2.2 H17) |

## Doğrulama

Teyit listesi: JasperReports 7 + Java 25 uyumu (§15.3; §12.1 "Java 25 uyumu Faz 0'da doğrulanacak"). Faz 6 çıkış kriteri: referans belge PDF olarak basılıyor.
