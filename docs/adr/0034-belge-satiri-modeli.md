# ADR-0034: Belge satırı modeli ve satır bazında bağlantılar

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Tek yön |
| **Kaynak** | v4-platform.md §6.7.7 |

## Bağlam

Satırlar her modülde yeniden icat edilmemeli; v1'de jenerik akışların kopyalanması her hatanın birden çok yerde düzeltilmesini gerektirdi (§2.2 H7). Kumaşta metre ve kg, gıdada adet ve kg gibi ikincil miktarlar gerekir. Toplu faturalamada bir fatura birden çok irsaliyenin satırlarını karşılar ve UBL'de her irsaliyeye referans verilir (§10.4). Karşılanma ekseni (§6.7.1) bir kaynaktan hesaplanmalı.

## Karar

- Standart satır sözleşmesi:
  - Kalem: kaynak referansı (malzeme, hizmet, masraf…), açıklama.
  - Miktar: girilen miktar + girilen birim; temel miktar (temel birime çevrilmiş, dönüşüm katsayısı kesinleştirmede sabitlenir); opsiyonel ikincil miktar + birim.
  - Fiyat ve tutar: birim fiyat, iskontolar, vergi satırları (alt koleksiyon), üç para birimli satır tutarları (§6.7.8).
  - Boyutlar: analitik boyut seti (§6.7.10).
  - Bağlantı: kaynak satır referansları.
- Belge bağlantıları **satır bazında ve çoka-çoktur**: `document_line_link(source_line, target_line, quantity, link_type)`.
- Karşılanma ekseni bu bağlantılardan hesaplanır. "Kısa kapat" (short-close) eylemi kalan miktarı iptal eder.

## Sonuçlar

- Olumlu: Satın alma, satış, envanter ve e-belge modülleri aynı satır modelini kullanır; toplu faturalama ve kısmi sevk aynı bağlantı yapısıyla karşılanır.
- Olumsuz: Model sonradan değiştirilemez; eksik tasarım ilk modüllerde yeniden mimari ihtiyacı doğurur (§19 R16).
- Takip: R16'nın azaltması: Faz 5'te referans modülün ticari zinciri ve mali müşavir incelemesi.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

S3 spike'ı: satırlı bir belge ve AG Grid satır ızgarası (§15.3). Faz 5 çıkış kriteri: satır bazında bağlantılar ve karşılanma durumu doğru; birim dönüşümü ve ikincil miktar doğru taşınıyor.
