# ADR-0035: Hesaplama hattı SPI'ı, yuvarlama politikası, simülasyon

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.7.9 |

## Bağlam

Satın alma ve satış modülleri KDV, tevkifat, ÖTV ve zincir iskontoyu ayrı ayrı yazarsa v1'deki kopyala-yapıştır sorunu (§2.2 H7) tekrarlanır. Adımların sırası önemlidir, çünkü ÖTV KDV matrahına girer. Sunucu doğrunun kaynağıdır (§3.1 madde 4).

## Karar

- Çekirdek sıralı bir **hesaplama hattı SPI'ı** sağlar, adımları modüller katkı olarak ekler. Tipik sıra: fiyat → zincir iskonto (10+5+2) → ÖTV → KDV matrahı → KDV → tevkifat → yuvarlama → toplamlar.
- **Yuvarlama politikası** çekirdektedir: tutarlar para biriminin ölçeğinde saklanır (TRY için 2 hane). Varsayılan kural: önce satır yuvarlanır, toplam yuvarlanmış satırlardan hesaplanır (UBL-TR doğrulamasıyla uyumlu). Belge tipi bazında değiştirilebilir.
- **Vergi satırları** satırın alt koleksiyonudur: vergi tipi, matrah, oran, tutar, istisna veya tevkifat kodu.
- **Simülasyon uç noktası:** taslak belge kaydedilmeden `POST …/simulate` ile hesaplanır; UI toplamları bu uç noktadan alır (debounce ile). İstemci sadece önizleme yapar.
- Türkiye'ye özgü vergi kuralları ve fiyat listeleri `trade-base` modülündedir ve satın alma ile satıştan önce yazılır (§16.2). Çekirdek sadece hattı, yuvarlama politikasını ve vergi satırı modelini verir.

## Sonuçlar

- Olumlu: Tek hesaplama yolu; simülasyon ve kesinleştirme aynı hattı kullanır; vergi kuralları modüller arasında tekrarlanmaz.
- Olumsuz: UI toplamları için sunucuya istek gider; bütçe ≤ 300 ms (§9.9).
- Takip: `trade-base` çekirdekten sonraki 3. modüldür (§16.2).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Her modülün KDV, tevkifat, ÖTV ve iskontoyu kendisinin yazması | v1'deki kopyala-yapıştır sorunu (§2.2 H7) tekrarlanır |

## Doğrulama

Faz 5 çıkış kriteri: hesaplama hattına takılan örnek bir vergi adımı simülasyonda ve kesinleştirmede aynı sonucu veriyor; yuvarlama kuralı testli. Performans bütçesi: toplamların yeniden hesaplanması ≤ 300 ms (§9.9).
