# ADR-0023: Saha: PWA ve kiosk arketipi; gerekirse Capacitor

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §9.10 |

## Bağlam

v1'de 5 ayrı Android (Kotlin) kiosk tablet uygulaması, WebView + JS köprüsü ve TV paneli vardı; kalite kontrol tableti sunucudan konfigüre edilen "SmartButton" düğme panelleriyle çalışıyordu (§2.3). Operatör şifreleri tablete gönderiliyor ve istemcide doğrulanıyordu (§2.2 H14).

## Karar

1. **Varsayılan:** duyarlı (responsive) web + PWA. Onaylar, basit sorgular ve depo işlemleri mobil tarayıcıda çalışır.
2. **Kiosk arketipi:** tabletlerde tam ekran web. Cihaz oturum çereziyle (§6.18) ve operatör oturumuyla çalışır. Çevrimdışı kuyruk IndexedDB'de tutulur ve sadece taslak/olay komutlarını içerir (idempotency anahtarlarıyla). Düğme panelleri metadata'dan gelir (§9.4).
3. **Native kabuk** gerekirse (kiosk kilidi, donanım barkod tarayıcı, NFC kart okuyucu): Capacitor ile aynı web kodunu sarmalayan bir kabuk. Bu karar ilk saha modülünde verilir.

- Operatör kart ya da PIN ile sunucuda doğrulanır. Çevrimdışıyken numaralandırma, dönem kontrolü ve kesinleştirme yapılmaz; senkronizasyonda sunucuda yapılır (§6.18).

## Sonuçlar

- Olumlu: Tüm saha cihazları tek web kodunu kullanır; v1'deki ayrı native uygulamalar tekrarlanmaz.
- Olumsuz: Çevrimdışı çalışma taslak ve olay komutlarıyla sınırlıdır. Senkron anında dönem kapanmışsa işlem istisna kuyruğuna düşer ve yetkili onayına gider (§6.18).
- Takip: Kiosk arketipi ve operatör oturumu kesme listesinde 2. sıradadır; ilk saha modülüyle gelebilir (§15.4).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Ayrı native uygulamalar + WebView + JS köprüsü (v1) | 5 ayrı Kotlin uygulaması; doküman bu yaklaşımın tekrarlanmayacağını söylüyor |

## Doğrulama

İlgili fazın çıkış kriteri: kiosk arketipi Faz 7 kapsamındadır (§15.3; kesme listesine göre ilk saha modülüne kayabilir). Native kabuk kararı ilk saha modülünde verilir.
