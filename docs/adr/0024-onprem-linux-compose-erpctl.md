# ADR-0024: On-prem: Linux + compose + `erpctl`, zorunlu TLS; Windows için appliance VM

| | |
|---|---|
| **Durum** | Önerildi |
| **Tarih** | 2026-10-05 |
| **Geri dönüş maliyeti** | Pahalı |
| **Kaynak** | v4-platform.md §11.1 |

## Bağlam

On-prem kurulum ve güncelleme dakikalar sürmeli ve uzman gerektirmemeli; temiz kurulum ≤ 30 dk (§1.3 hedef 5). v1'de `smart-update` merkezi sunucudan soketle komut alıp müşteri sunucusunda Docker'ı uzaktan yönetiyordu (§2.2 H18). Windows Server Türk KOBİ'lerinde yaygındır ve üzerinde Linux konteyner çalıştırmak desteklenmez. `Secure` çerezler, `__Host-` öneki ve HTTP/2 TLS gerektirir.

## Karar

- Paketleme: tek OCI imajı (Spring Boot katmanlı jar + gömülü SPA, Java 25 JRE tabanlı minimal imaj, cosign ile imzalı); realm şablonu ve temayla üretilmiş türev Keycloak imajı; resmi PostgreSQL 18 imajı; ters proxy Caddy.
- On-prem paketi `docker compose` dosyaları + **`erpctl`**'dir: `install` (ön kontroller, sır üretme, ilk kurulum, tenant ve lisans yükleme), `update` (§11.2), `backup`/`restore`, `status`, `support-bundle`, `license install`.
- **TLS her kurulumda zorunludur.** Alan adı olmayan kurulumlarda Caddy'nin iç sertifika otoritesi kullanılır ve kök sertifika istemcilere dağıtılır.
- Desteklenen işletim sistemi Linux'tur (Ubuntu LTS, Debian, RHEL uyumlu). Windows Server müşterilerine hazır Linux sanal makine imajı (Hyper-V VHDX / VMware OVA, içinde `erpctl` kurulu) sunulur.
- İnternetsiz kurulumda imajlar ve imzalı manifest tek bir çevrimdışı arşivde gelir.

## Sonuçlar

- Olumlu: SaaS ile aynı imaj. Faz 0'dan itibaren her commit on-prem ile aynı compose paketiyle CI'da ayağa kaldırılır (§11).
- Olumsuz: Windows Server müşterisi bir sanal makine işletir. Alan adı olmayan kurulumlarda kök sertifikanın istemcilere dağıtılması gerekir.
- Takip: Air-gapped paket ve appliance VM kesme listesindedir (§15.4 madde 3 ve 4; appliance ilk Windows Server müşterisiyle gelir).

## Değerlendirilen alternatifler

| Seçenek | Neden seçilmedi |
|---|---|
| Windows Server üzerinde Linux konteyner | Desteklenmez |
| Kubernetes | Compose ve tek düğüm on-prem için yeterli; SaaS başlangıçta VM + compose ile işletilir (§12.4) |

## Doğrulama

Faz 7 çıkış kriteri: temiz bir Linux makinede kurulum ≤ 30 dakika sürüyor; Windows Server üzerinde appliance VM ile kurulum doğrulandı (bu yaklaşım gerçek bir müşteri ortamında doğrulanır, §11.1).
