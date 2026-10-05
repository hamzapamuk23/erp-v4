# ADR-0041: Küresel referans veriler platform DB'sinde

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.17 |

## Bağlam

Para birimleri, TCMB kurları, GİB kod listeleri, UN/ECE birim kodları ve GTİP gibi veriler herkese açıktır ve tüm tenant'lar için aynıdır. UN/ECE Rec 20 birim kodları UBL'de zorunludur. Tenant'a ait iş verisi asla platform DB'ye yazılmaz (§4.3).

## Karar

- Küresel referans veriler platform DB'sinde (`global_refdata`) **tek kopya** tutulur; tenant'lar bunları platform API'si ve önbellek üzerinden salt okunur kullanır.
- Kapsam: para birimleri (ISO 4217); döviz kurları (tarih, kur tipi, birim çarpanı, ilan zamanı; TCMB connector'ı: `today.xml` günlük, EVDS geçmiş); ölçü birimi kodları ve UN/ECE Rec 20 eşlemesi; ülke, il, ilçe; GİB kod listeleri; GTİP kodları; fiyat endeksleri; resmi tatiller.
- Belgeler bu verilere FK vermez; kodu ve gerekiyorsa değeri (kur gibi) kopyalayarak saklar.
- Tenant referans verileri (tenant'a özel birimler ve dönüşümler, vergi ayarları, çalışma takvimleri) tenant DB'sindedir. Malzemeye özel birim dönüşümleri `catalog`'da, vardiya desenleri üretim ve İK modüllerindedir.
- On-prem'de de platform DB'si vardır; küresel referans veriler sürümle birlikte gelir ve connector'larla güncellenir.

## Sonuçlar

- Olumlu: Tek kopya ve tek güncelleme noktası.
- Olumsuz: Belgeler ile küresel veriler arasındaki bağ FK değil, kopyalanan kod ve değerdir.
- Takip: TCMB dışındaki connector'lar kesme listesindedir (§15.4 madde 6).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

Faz 6 çıkış kriteri: TCMB kurları her gün otomatik çekiliyor (kapsam: `refdata` küresel veriler, TCMB connector'ı, UN/ECE, GTİP, fiyat endeksleri; §15.3).
