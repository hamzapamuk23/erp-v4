# ADR-0010: Şema kapsamı ve modül durumları

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §4.6, §5.6 |

## Bağlam

Tüm kod tek imajda bulunur (ADR-0002); "bu tenant'ta bu modül açık mı?" sorusu tek bir yerde cevaplanmalı. Kullanılmayan bir paketin bozuk migration'ı başka tenant'ları etkilememeli, müşteri uzantısının tabloları başka müşterinin veritabanında oluşmamalı. Lisansı biten ya da kapatılan bir modülün kesinleştirme katılımcısı devre dışı kaldığında yarım ters kayıt oluşmamalı.

## Karar

- Platform ve iş modüllerinin şemaları **tüm tenant'larda** kurulur. Aktivasyon çalışma zamanı kararıdır; modül açmak migration gerektirmez.
- Sektör paketleri ve müşteri uzantıları **sadece aktif oldukları tenant'larda** kurulur.
- Tek bilinçli şema farkı tenant overlay'inin ürettiği özel alan index'leridir (ADR-0008).
- Tenant bazında modül durumları: `INACTIVE` (kapalı; paket ve uzantılarda şeması da yok), `ACTIVE` (tam kullanım), `RETIRED` (yeni belge oluşturulamaz; mevcut veriler okunur ve daha önce kesinleştirilmiş belgeler ters çevrilebilir).
- Modül `ACTIVE` olduğunda backfill kancası çalışır. Modüller aktivasyondan önceki olayları kaçırmış olduklarını varsayarak tasarlanır.
- Kapı (`ModuleActivation`) altyapıda uygulanır ve testlenir: menü ve ekranlar (rotalar 404), REST uç noktaları (filtre seviyesinde 403), uzatma noktası katkıları ve olay dinleyicileri (atlanan dinleyici olayı tamamlandı olarak işaretler), paket overlay'leri, işler.

## Sonuçlar

- Olumlu: Platform ve iş modülü açmak deploy ya da migration gerektirmez. Paket ve uzantı migration hataları kendi tenant'larında kalır. `RETIRED` ile yarım ters kayıt oluşmaz.
- Olumsuz: Platform ve iş modüllerinin şemaları kullanılmasalar da her tenant DB'sinde bulunur. Paket ve uzantılarda aktivasyon migration çalıştırır.
- Takip: Kapı her geliştiricinin elle `if` yazmasına bırakılmaz.

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| — | Doküman bu karar için bir alternatif adlandırmıyor. |

## Doğrulama

İlgili fazın çıkış kriteri: Faz 2 (kapsam: migration orkestratöründe paketler için aktivasyonda kurulum, modül durumları ve aktivasyon kapısı; §15.3). Çıkış maddesi: bir tenant'taki migration hatası diğerlerini etkilemiyor.
