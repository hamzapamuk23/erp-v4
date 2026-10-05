# ADR-0019: BPMN yok, onay politikaları

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.7.6 |

## Bağlam

Belgeler tutar eşiği gibi koşullara göre onay gerektirir (onay ekseni, §6.7.1). Görevler ayrılığı ("oluşturan onaylayamaz"), vekalet ve mobil ya da e-posta üzerinden onay gerekiyor. İlke sıkıcı teknolojidir; gerekmedikçe yeni bileşen eklenmez (§3.1 madde 7).

## Karar

- Onaylar **onay politikalarıyla** yapılır: belge tipi + koşullar (tutar eşiği, şirket, tip, özel alan) → adımlar (rol veya kullanıcı; "biri yeter" ya da "hepsi gerekli"; sıralı ya da paralel).
- Vekalet, zaman aşımında yükseltme ve "oluşturan onaylayamaz" seçeneği vardır. Onay zaman çizelgesi belgede görünür.
- Bildirim gönderilir. Mobil ya da e-posta bağlantısıyla güvenli onay yapılabilir (tek kullanımlık, süreli, oturum gerektiren bağlantı).
- BPMN motoru kullanılmaz. İhtiyaç bunun ötesine geçerse ayrı bir ADR açılır.

## Sonuçlar

- Olumlu: Ek motor yok; onay, belge çatısının onay ekseniyle çalışır.
- Olumsuz: Karmaşık, kullanıcı tanımlı süreçler desteklenmez.
- Takip: Vekalet ve zaman aşımı yükseltmesi kesme listesindedir (§15.4 madde 5).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| BPMN motoru | Onay politikaları yeterli; karmaşık, kullanıcı tanımlı süreç ihtiyacı kanıtlanırsa yeniden değerlendirilir (§12.4) |

## Doğrulama

İlgili fazın çıkış kriteri: Faz 5 (kapsam: onaylar; §15.3). Çekirdek tamam kriteri §16.1 madde 3: belge tipi onayı hazır alıyor.
