# ADR-0043: Tenant geri yükleme el kitabı ve WAL arşivleme

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §4.7.1, §11.4 |

## Bağlam

Bir tenant'ı geçmiş bir ana döndürmek dış dünyayla tutarsızlık yaratır: GİB'e gönderilmiş fatura numaraları, tetiklenmiş webhook'lar, kuyruktaki işler. Geri yükleme sonrası numara tekrarı ve çift webhook riski düşük olasılıklı ama yüksek etkilidir (§19 R15).

## Karar

- Geri yükleme şu sırayla yapılır:
  1. Tenant `MAINTENANCE` durumuna alınır.
  2. Yedek geri yüklenir.
  3. Yarım kalmış olay yayınları **yeniden gönderilmez, karantinaya alınır**; operatör tek tek inceler.
  4. Boşluksuz sayaçlar dış kaynaklarla (e-belge entegratörünün son numarası, gönderilmiş belgeler) **mutabakata** sokulur. Gönderilmiş bir numara asla yeniden kullanılmaz.
  5. Platform DB'sindeki o tenant'a ait işler temizlenir ya da yeniden planlanır.
  6. Kontroller tamamlanınca tenant `ACTIVE` olur.
- Kayıp penceresini küçültmek için on-prem'de de WAL arşivleme (pgBackRest ile PITR) varsayılan olarak açıktır.
- Yedekleme varsayılanları (§11.4): on-prem'de gece mantıksal yedek, 14 gün rotasyon, WAL arşivleme, opsiyonel uzak kopya (S3); SaaS'ta pgBackRest PITR (en az 14 gün) + tenant bazında mantıksal yedek (30–90 gün). Otomatik geri yükleme denemesi on-prem'de haftalık, SaaS'ta günlük (+ aylık tatbikat). Yedekler şifrelidir.

## Sonuçlar

- Olumlu: Numara tekrarı ve çift webhook önlenir; on-prem'de de PITR mümkündür.
- Olumsuz: Geri yükleme kendiliğinden bitmez; karantinadaki yayınlar operatör incelemesi ister.
- Takip: Yedekler de kişisel veri içerir; saklama politikasında ve VERBIS kaydında yer alır (§10.3).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

Faz 2 çıkış kriteri: geri yükleme el kitabı otomatik testle doğrulandı (karantina, sayaç mutabakatı, iş temizliği). Faz 7: WAL arşivleme kapsamda; çıkış kriteri geri yükleme tatbikatının otomatik çalışması.
