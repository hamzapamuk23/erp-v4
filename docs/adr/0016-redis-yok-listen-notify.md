# ADR-0016: Redis yok; LISTEN/NOTIFY + TTL ve sürüm kontrolü

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Kolay |
| **Kaynak** | v4-platform.md §5.2, §6.11 |

## Bağlam

Uygulama durumsuzdur ve N instance olarak çalışır (§5.2). SSE olayları ile metadata ve izin önbelleklerinin geçersizleştirilmesi instance'lar arasında yayılmalı. İlke, gerekmedikçe yeni altyapı bileşeni (Kafka, Redis, Kubernetes) eklememektir (§3.1 madde 7).

## Karar

- Redis kullanılmaz. Instance'lar arası yayın (SSE ve önbellek geçersizleştirme) platform DB'sinde PostgreSQL `LISTEN/NOTIFY` ile yapılır.
- Önbellek instance içinde Caffeine'dir (§12.1); oturumlar DB'dedir (ADR-0039).
- `LISTEN/NOTIFY` mesajı yeniden bağlanma ya da çökme anında kaybolabilir. Bu yüzden metadata ve izin önbelleklerinde TTL vardır, yeniden bağlanmada tüm önbellek temizlenir ve sürüm numarası kontrol edilir.
- Dinleyici bağlantısı havuz dışıdır: instance başına tek bağlantı (§4.5).

## Sonuçlar

- Olumlu: Ek altyapı bileşeni yok; SaaS ve on-prem aynı yapıyla çalışır.
- Olumsuz: Bildirim kaybolabilir; önbellek doğruluğu TTL, yeniden bağlanmada temizleme ve sürüm kontrolüne dayanır.
- Takip: SaaS'ta ölçülmüş bir darboğaz olursa Redis yeniden değerlendirilir (§12.4).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Redis | LISTEN/NOTIFY + Caffeine + DB oturumları yeterli (§12.4) |

## Doğrulama

İlgili fazların çıkışı: Faz 4 (overlay önbelleği: TTL, yeniden bağlanmada temizleme, sürüm kontrolü) ve Faz 6 (tek bağlantılı SSE, LISTEN/NOTIFY) (§15.3). Kanıt: çekirdek tamam kriteri §16.1 madde 8, tenant izolasyonunun cache ve SSE seviyesinde testle kanıtlanması.
