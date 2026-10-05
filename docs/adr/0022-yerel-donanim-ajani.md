# ADR-0022: Yerel donanım ajanı: protokol ve kimlik çekirdekte

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §6.18 |

## Bağlam

v1'de `smart-serial` adlı, Windows'ta paketlenmiş bir Node ajanı seri porttan örgü makinesi verisi okuyor ve `SumatraPDF` ile sessiz yazdırıyordu; tarayıcı ajanı 3 saniyede bir yokluyordu (§2.3). Cihaz soket namespace'i kimliksiz çalışıyordu (§2.2 H14). Yerel donanım ajanının kendisi çekirdeğin kapsamı dışındadır (§1.2).

## Karar

- Yerel donanım ajanı **protokolü** çekirdektedir: giden WebSocket, cihaz token'ı, komut ve sonuç mesajları (yazdır, tartım oku, seri port verisi).
- Ajanın kimliği uygulamanın ürettiği, döndürülebilir ve hash'li saklanan cihaz token'ıdır (ADR-0040).
- **Ajanın kendisi çekirdekte yazılmaz**; ilk ihtiyaç duyan modülle (üretim ya da depo) yazılır.
- Sessiz yazdırma ve yerel yazıcılar ajan üzerinden yapılır; çekirdek iş kuyruğunu sağlar (§6.13).
- WebSocket sadece çift yönlü gerçek zamanlı ihtiyaç olduğunda eklenir; bu protokol onun örneğidir (§6.11).

## Sonuçlar

- Olumlu: Ajan sonra yazılsa da cihaz kimliği ve protokol baştan çekirdektedir; saha gereksinimlerinin hafife alınması riski azalır (§19 R12).
- Olumsuz: Ajan yazılana kadar sessiz yazdırma ve seri port verisi kullanılamaz.
- Takip: Ajan `production` modülü ve kiosk saha ekranlarıyla birlikte gelir (§16.2 sıra 8).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

İlgili fazın çıkış kriteri: cihaz kaydı (kiosk cihaz çerezi + native token) Faz 3 kapsamındadır (§15.3). Protokolün bir ajanla uçtan uca kanıtı, ajanı ilk yazan modülün işidir (§6.18).
