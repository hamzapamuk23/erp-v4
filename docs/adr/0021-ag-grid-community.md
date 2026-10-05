# ADR-0021: AG Grid Community; Enterprise kararı ertelendi

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §9.1 |

## Bağlam

Belge arketipinin satır ızgarası klavye öncelikli veri girişinin merkezidir (§9.3, §9.4); 1000 satırlık belgede akıcı kaydırma ve ≤ 50 ms düzenleme gecikmesi hedeflenir (§9.9). AG Grid Community (MIT, v36) sanallaştırma, klavye gezintisi, hücre düzenleme, sütun sabitleme ve taşımayı ücretsiz verir. Excel'den hücre aralığı yapıştırma, aralık seçimi, satır gruplama ve Excel export ise Enterprise sürümdedir.

## Karar

- Veri ızgarası **AG Grid Community**'dir, `@erp/ui` içinde sarmalanır.
- Excel export sunucuda yapılır (§6.14).
- "Excel'den satır yapıştırma" Community sürümün klavye ve pano olaylarıyla `@erp/ui` içinde yazılır.
- Enterprise kararı ertelenir; kullanıcı ihtiyacı bunu aşarsa Enterprise lisansı ayrıca değerlendirilir.

## Sonuçlar

- Olumlu: Lisans maliyeti yok. Izgara `@erp/ui` içinde sarmalandığı için Enterprise'a geçiş yerel bir değişikliktir.
- Olumsuz: Yapıştırma davranışı bizim yazdığımız koddur; aralık seçimi ve satır gruplama yoktur.
- Takip: "AG Grid Enterprise gerekli mi?" sorusu kullanıcı testlerine göre Faz 5 sonunda cevaplanır (§20 soru 9).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| AG Grid Enterprise | Ertelendi: Excel export sunucuda, satır yapıştırma Community üzerinde yazılıyor; ihtiyaç bunu aşarsa değerlendirilir |

## Doğrulama

S3 spike'ı: AG Grid satır ızgarası. Teyit listesi: AG Grid Community'de Excel'den yapıştırma prototipi (§15.3). Enterprise sorusu Faz 5 sonunda (§20 soru 9).
