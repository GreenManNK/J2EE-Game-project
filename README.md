# J2EE Game Hub

Du an game hub chay tren Spring Boot, gom backend va frontend tach trong cung repo de de bao tri.

## Tong quan

- Backend: Spring Boot 3 / Java 17+
- Frontend: Thymeleaf + static JS/CSS
- Context path runtime: `/Game`
- Default runtime mode: `Start (Default Public)`

Repo nay hien uu tien 1 workflow van hanh chuan:

- `Start (Default Public)`
- `Stop (All)`

Khong con duy tri launcher `local`, `docker`, `mysql`, `postgres`, `status` rieng cho UI run config.

## Cau truc thu muc

- Backend source: `src/main/backend/java`
- Backend resources: `src/main/backend/resources`
- Frontend templates: `src/main/frontend/templates`
- Frontend static assets: `src/main/frontend/static`
- Tests: `src/test/java`
- Runtime scripts: `scripts/runtime`
- Tai lieu van hanh: `docs`

## Chay nhanh

Windows CMD:

```cmd
cmd /c scripts\manual-start.cmd
cmd /c scripts\manual-start.cmd stop
```

Windows PowerShell:

```powershell
.\scripts\manual-start.cmd --no-pause
.\scripts\manual-start.cmd stop --no-pause
```

## Start (Default Public) lam gi

Khi chay `Start (Default Public)`, he thong se tu dong:

- bootstrap moi truong neu can
- build va chay app profile `prod`
- tu chon DB kha dung:
  - PostgreSQL neu truy cap duoc
  - MySQL neu truy cap duoc
  - H2 file local neu khong co DB server kha dung
- tu chon public tunnel:
  - uu tien `Cloudflare Named Tunnel` neu da co `CLOUDFLARE_TUNNEL_TOKEN` + `PUBLIC_BASE_URL`
  - neu chua du cau hinh named tunnel thi thu lan luot `quick -> runlocal -> localtunnel`
- chi bao thanh cong khi ca 3 probe deu dat:
  - landing page
  - `ping`
  - `ws/info`
- in ra `PUBLIC_GAME_URL=...`

Luu y:

- Public URL hien tai duoc phat o dang `https://<domain>/Game/`
- Hay gui dung link co dau `/` cuoi sau `Game/` de tranh redirect entry URL sai scheme

## Lay link public dang song

Sau khi start, kiem tra trang thai:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\runtime\print-runtime-status.ps1
```

Chi gui cho nguoi choi gia tri:

- `ACTIVE_PUBLIC_GAME_URL`

Khong gui:

- `STALE_PUBLIC_GAME_URL`
- `LAST_PUBLIC_GAME_URL`
- `http://127.0.0.1:8080/Game`
- `http://J2EE/Game`

`print-runtime-status.ps1` hien co them 3 health flag:

- `ACTIVE_PUBLIC_PING_OK`
- `ACTIVE_PUBLIC_WS_INFO_OK`
- `ACTIVE_PUBLIC_PAGE_OK`

Neu ca 3 deu bang `1`, public session dang on.

## Cau hinh public on dinh

Mo `.env.public.local` va dien:

- `CLOUDFLARE_TUNNEL_TOKEN`
- `PUBLIC_BASE_URL`

Vi du:

```env
PUBLIC_BASE_URL=https://game.example.com
CLOUDFLARE_TUNNEL_TOKEN=...
```

Neu da co 2 bien nay, launcher se uu tien named tunnel tu dong.

## Email xac thuc dang ky

SMTP/public mail config dat trong `.env.public.local`.

Nhung bien chinh:

- `APP_EMAIL_MODE`
- `APP_EMAIL_FROM`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`

## Test va verify

Chay test:

```powershell
.\gradlew.bat test
.\mvnw.cmd -q -DskipTests compile
```

Kiem tra runtime/public launcher:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\runtime\verify-public-all.ps1 -NoLive
```

## Log can xem khi gap loi

- `run-prod-public.out.log`
- `run-prod-public.err.log`
- `cloudflared.err.log`
- `cloudflared-named.err.log`
- `public-fallback-tunnel.err.log`

## Tai lieu chi tiet

- `docs/HUONG_DAN_CHAY_THU_CONG.md`
- `docs/DEPLOY_REMOTE_STABLE.md`
- `docs/ACCOUNT_SYNC_API.md`
- `docs/EXTERNAL_GAME_MODULES.md`

## Ghi chu van hanh

- Quick tunnel la tam thoi, link se doi sau moi lan restart
- Named tunnel moi la cach van hanh on dinh cho nhieu nguoi choi
- Neu muon dung public session hien tai, chi can start xong roi gui `ACTIVE_PUBLIC_GAME_URL`
