# ADR-0014: db-scheduler platform DB'sinde; tenant başına iş örnekleri

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §6.12 |

## Bağlam

v1'de 12 dosyada `@Scheduled` iş vardı ve hiçbiri kilitli değildi; sonuç çoklu instance'ta çift çalışma ve transaction'sız yeniden hesaplamada veri kaybıydı (§2.2 H10). K12'ye göre zamanlanmış işler kümede güvenli zamanlayıcıyla, tenant bağlamı açıkça verilerek çalışır; `@Scheduled` yasaktır. İş verisi tenant DB'sinde, iş kuyruğu platform DB'sindedir ve iki DB arasında atomik yazma yoktur.

## Karar

- Motor **db-scheduler**'dır (Apache-2.0, + db-scheduler-ui). Platform DB'sinde çalışır (`scheduled_tasks`, §4.3) ve kümede güvenlidir.
- Her iş örneği `tenantKey` taşır; çalışmadan önce bağlam kurulur, bitince temizlenir (§4.4).
- Tekrarlayan işler tenant başına dağıtılır: küresel bir zamanlayıcı işi sadece **işi olan** tenant'lar için (son aktiviteye ve bekleyen iş göstergelerine göre) iş örnekleri oluşturur; tüm tenant DB'lerini sırayla taramaz. Tenant başına kota uygulanır.
- Transaction ile kuyruğa alma: İş, tenant transaction'ında yazılan bir olayla (Modulith outbox) tetiklenir; dinleyici commit sonrası işi kuyruğa alır. İş örneği kimliği olayın `eventId`'sidir ve `scheduleIfNotExists` kullanılır. İşler idempotent yazılır.
- İş geçmişi platform DB'sindeki `job_run` tablosunda tutulur (tenant, tip, süre, sonuç, hata), saklama süreli.

## Sonuçlar

- Olumlu: Çift çalışma olmaz. Boşta duran tenant'lar için havuz açılmaz ve iş tek düğümde seri çalışmaz. Bir tenant'ın ağır importu diğerlerini bekletmez. Olay iki kez teslim edilse bile iş bir kez planlanır.
- Olumsuz: İş tetikleme outbox'a bağlıdır; işlerin idempotent yazılması zorunludur.
- Takip: Aktif olmayan modülün işleri o tenant için atlanır (§5.6).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| JobRunr | Açık kaynak sürümünde en fazla 100 tekrarlayan iş; transaction eklentisi ve öncelik kuyrukları ücretli Pro'da; Pro üretim kümesi başına ücretlendiriliyor ve her on-prem kurulum ayrı bir küme sayılıyor |
| Spring `@Scheduled` (v1) | Kilitsiz, çoklu instance'ta çift çalışma (H10); K12 ile yasak |

## Doğrulama

S1 spike'ı: db-scheduler, `tenantKey` ve `eventId` ile tekilleştirme (§15.3). Faz 2 çıkış kriteri (kapsam: tenant başına iş örnekleri ve kotalar; izolasyon paketinin iş seviyesi). K12 Faz 0A'dan itibaren ArchUnit ile build'de denetleniyor (`docs/guides/coding-rules.md`).
