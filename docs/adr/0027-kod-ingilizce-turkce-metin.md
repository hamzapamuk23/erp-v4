# ADR-0027: Kod İngilizce, UI i18n; Türkçe metin kuralları

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §7.8 |

## Bağlam

v1'de `uretimhareket` ve `getUHDURMASQuestions` gibi karışık adlandırma vardı (§14, §2.2 H8). Ürün varsayılan olarak Türkçe kullanılır; büyük/küçük harf dönüşümü, arama ve sıralamada Türkçe karakterler için ortak kurallar gerekiyor (K10). Kullanıcı "ısık", "Işık" ve "isik" yazdığında aynı sonucu bulmalı.

## Karar

- Kod, tablo ve kolon adları İngilizcedir; kullanıcıya görünen metinler i18n dosyalarındadır (§14). Domain sözlüğü Ek A'dadır. Varsayılan dil Türkçe, ikinci dil İngilizce; anahtar düzeni `<modül>.<kaynak>.<alan>`, backend ve frontend aynı anahtar ailesini kullanır (§9.8).
- Kodda `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)` kullanılır; parametresiz çağrı yasaktır (K10). `equalsIgnoreCase` Türkçe metinde kullanılmaz.
- Arama: `search_text = TurkishText.fold(...)` uygulamada üretilir (ı→i, İ→i, ş→s, ğ→g, ü→u, ö→o, ç→c ve küçük harf; §6.1); sorgu metni de aynı şekilde normalize edilir (ADR-0017).
- Sıralama: isim sıralamasında ICU Türkçe collation kullanılır (`COLLATE "tr-TR-x-icu"`, kolon ya da sorgu bazında). Veritabanı varsayılan collation'ı `und-x-icu` ya da `C.UTF-8`'dir.
- Sayı ve tarih biçimi UI'da kullanıcının diline göre yapılır; API her zaman ISO-8601 ve noktalı ondalık kullanır.

## Sonuçlar

- Olumlu: Kod tek dilde; Türkçe arama ve sıralama tek kurala bağlı.
- Olumsuz: Türkçe sıralama gereken her kolon ya da sorguda collation açıkça belirtilir.
- Takip: RTL şimdilik kapsam dışıdır (§9.8).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Tüm veritabanını Türkçe collation'a almak | `lower()` ve index davranışında sürprizler yaratır |

## Doğrulama

K10 Faz 0A'dan itibaren build'i kırıyor (Error Prone `StringCaseLocaleUsage` + ArchUnit; `docs/guides/coding-rules.md`). `TurkishText` Faz 1'de kernel ile gelir (§15.3); arama Faz 4 çıkış kriteriyle doğrulanır (ADR-0017).
