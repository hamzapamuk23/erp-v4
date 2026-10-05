# ADR-0042: Personel realm'i, step-up ve audit'li impersonation

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.3 |

## Bağlam

Destek personelinin müşteri tenant'larında "kullanıcı olarak" işlem yapabilmesi gerekir; bu, güçlü ve kötüye kullanılabilir bir yetkidir. İş realm'inde kullanıcılar ve politikalar realm geneldir (ADR-0005).

## Karar

- Destek personeli ayrı bir **personel realm'inde** (`erp-staff`) tutulur. MFA zorunludur. Personel müşteri realm'ine kullanıcı olarak eklenmez (§6.3.1).
- **Impersonation** (§6.3.2): tenant admini süreli izin verir. Destek personeli personel realm'inden, adım-yukarı (step-up) doğrulamayla "X kullanıcısı olarak" işlem yapar. UI'da sürekli bir uyarı bandı görünür. Her işlem çift kimlikle (gerçek kullanıcı + bürünülen kullanıcı) audit'e yazılır.
- Impersonation izni platform DB'sindeki `platform_audit`'e (§4.3), işlemler iş ve güvenlik günlüğüne yazılır; Envers revizyon kaydı bürünülen kullanıcıyı da tutar (§6.9).

## Sonuçlar

- Olumlu: Destek erişimi müşteri onaylı, süreli ve izlenebilirdir.
- Olumsuz: Keycloak'ta ikinci bir realm (`erp-staff`) yönetilir.
- Takip: Personel realm'i ve impersonation Faz 3 kapsamındadır (§15.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Personeli müşteri realm'ine kullanıcı olarak eklemek | Doküman bunu açıkça dışarıda bırakıyor (§6.3.1); ayrı bir gerekçe vermiyor |

## Doğrulama

Faz 3 çıkış kriteri: impersonation işlemleri audit'e çift kimlikle yazılıyor. Çekirdek tamam kriteri §16.1 madde 14 (admin işlemleri ve impersonation audit'leniyor).
