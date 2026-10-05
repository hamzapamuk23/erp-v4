# ADR-0036: Analitik boyut seti

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.7.10 |

## Bağlam

Raporlar masraf merkezi, proje, bölge ve satış kanalı gibi analitik boyutlara göre kırılabilmeli. Ledger kayıtları ve bakiye projeksiyonu boyut taşır (§6.8). Ticari çekirdeğin eksik tasarlanması ilk modüllerde yeniden mimari ihtiyacı doğurur (§19 R16).

## Karar

- Tenant analitik boyutlar tanımlayabilir: masraf merkezi, proje, bölge, satış kanalı vb.
- Satırlar bir **boyut seti** taşır. Boyutlar satırdan ledger'a taşınır; raporlar boyutlara göre kırılır.
- Organizasyon birimleri (şirket, tesis) zorunlu boyutlardır, diğerleri tenant tanımlıdır.
- Bakiye projeksiyonu boyut kırılımında tutulur (§6.8).

## Sonuçlar

- Olumlu: Boyutlar tüm modüllerde aynı modelle satırdan ledger'a akar.
- Olumsuz: Boyut seti satır ve ledger sözleşmesinin parçasıdır; değiştirmek tüm modüllere dokunur.
- Takip: R16'nın azaltması: Faz 5'te referans modülün ticari zinciri ve mali müşavir incelemesi.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

İlgili fazın çıkış kriteri: Faz 5 (kapsam: analitik boyutlar; §15.3); referans modülün ticari zinciriyle kanıtlanır (§19 R16).
