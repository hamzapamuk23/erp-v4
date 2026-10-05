# ADR-0031: Para birimi ve kur modeli

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Tek yön |
| **Kaynak** | v4-platform.md §6.7.8 |

## Bağlam

Dövizli işlemler ve TCMB kurları Türkiye mevzuatında çekirdek ihtiyaçtır (§10.4). İhracatçı firmalar ayrı bir raporlama dövizi kullanır (ör. tekstilde EUR). Sonradan değişen bir kur geçmiş belgeyi değiştirmemeli. TCMB bazı dövizleri (ör. JPY) 100 birim için ilan eder.

## Karar

- Tutar taşıyan her başlık, satır ve ledger kaydı şunları saklar: işlem tutarı + para birimi; yerel tutar (şirketin fonksiyonel para birimi, genelde TRY); opsiyonel raporlama tutarı + para birimi (şirket ayarı); kur bilgisi (değer, tip, tarih ve kaynak: TCMB, sözleşme, elle).
- Kur ve karşılık tutarlar **kesinleştirmede sabitlenir**.
- Kur seçme kuralı belge tipine göre konfigüre edilir. Fatura için varsayılan: sözleşmede kur yoksa belge tarihinden bir önceki iş günü TCMB'nin saat 15:30'da ilan ettiği döviz alış kuru; tatil ve hafta sonunda son ilan edilen kur.
- Kur hassasiyeti `numeric(24,10)` + birim çarpanıdır; ters kurlar (TRY→USD) hassasiyet kaybetmez.
- Kur farkı sadece değer taşıyan (miktarsız) bir ledger kaydıdır; kur farkı faturası bir düzeltici belgedir (§6.7.3).
- Fiyat endeksleri referans verilerde tutulur: enflasyon düzeltmesi 2025–2027 için askıya alındı (VUK geçici 37. madde) ama geri gelebilir (§6.17).

## Sonuçlar

- Olumlu: Geçmiş belgeler kur değişiminden etkilenmez; raporlama dövizi ve TCMB kurları çekirdekte karşılanır.
- Olumsuz: Tutar taşıyan her tablo üç tutar ve kur bilgisi taşır; model sonradan değiştirilemez.
- Takip: Kesin kur kuralı mali müşavirle Faz 5'te teyit edilir (§20 soru 8). Ticari çekirdeğin eksik tasarlanması riski (§19 R16).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

Faz 5 çıkış kriteri: iki para birimli sipariş → kısmi sevk → fatura zinciri çalışıyor; kur ve karşılık tutarlar kesinleştirmede sabitleniyor. Kur kuralının mali müşavir teyidi Faz 5'te.
