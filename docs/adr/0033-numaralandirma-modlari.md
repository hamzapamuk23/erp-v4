# ADR-0033: Numaralandırma modları, tarih monotonluğu, seri seçim SPI'ı

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.7.4 |

## Bağlam

Yasal belgeler (fatura, irsaliye, e-belge) boşluksuz numara ister; GİB formatı 3 karakter seri + yıl + 9 hanedir ve tarih sırasına uyar (§10.4). İç belgeler yüksek hacimlidir ve boşluk kabul eder. e-Defter yevmiye madde numarası gibi numaralar dönem içinde tarih sırasıyla verilmelidir. Business Central ve Odoo da aynı ikiliyi kullanıyor: boşluksuz ama yavaş, boşluklu ama hızlı.

## Karar

- Dört mod:
  - **Boşluksuz:** sayaç satırı `SELECT … FOR UPDATE` ile kilitlenir; numara kesinleştirme anında, geçişin son adımında verilir; taslak iptal edilirse numara harcanmaz.
  - **Dizi:** PostgreSQL `nextval` doğrudan çağrılır (Hibernate optimizer'ı yok); boşluk olabilir.
  - **Harici:** numarayı dış sistem verir, alanı entegrasyon doldurur.
  - **Ertelenmiş toplu:** kesinleştirmede verilmez; dönem kapanışında (ya da talep üzerine) belge tarihine göre sıralanıp toplu verilir, sonra kilitlenir.
- Her belgenin dizi modunda bir taslak referans numarası vardır; yasal numara ayrıdır ve kesinleştirmede verilir.
- Seri tanımı: belge tipi × şirket × (opsiyonel) tesis × yıl × desen. Desendeki yıl belge tarihinin takvim yılıdır. GİB formatı: `{SERI:3}{YIL:4}{SIRA:9}` → `ABC2026000000001`.
- **Tarih monotonluğu:** yasal serilerde yeni numaralanan belgenin tarihi serideki son numaralı belgenin tarihinden geride olamaz; boşluksuz serilerde geriye tarihli kesinleştirme seri ayarıyla engellenir.
- **Seri seçim SPI'ı:** seri çalışma zamanında belirlenebilir (ör. alıcı e-Fatura mükellefiyse e-Fatura, değilse e-Arşiv serisi; compliance-tr sağlar).
- Sayacın kilidi katılımcıların ledger işleri bittikten sonra alınır ve sadece commit'e kadar tutulur (§6.7.2); kilit sırası belge → bakiye projeksiyonları → numara sayacıdır (§7.7).

## Sonuçlar

- Olumlu: Aynı serideki kesinleştirmeler birbirini gereksiz yere beklemez. Ertelenmiş toplu modda geriye tarihli kesinleştirme sorun yaratmaz.
- Olumsuz: Boşluksuz mod sayaç satırında kilitlenir. Boşluksuz serilerde geriye tarihli kesinleştirme engellenir.
- Takip: Geri yüklemede boşluksuz sayaçlar dış kaynaklarla mutabakata sokulur; gönderilmiş numara asla yeniden kullanılmaz (§4.7.1).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Dizi modunda Hibernate sequence optimizer'ı | Bir tenant'ın sequence değerlerini başka bir tenant'a verebilir (§4.5) |

## Doğrulama

Faz 5 çıkış kriteri: 50 paralel kesinleştirmede boşluk ya da çift numara oluşmuyor. Boşluksuz numaranın ilk hali Faz 1'de (belge v0, §15.3).
