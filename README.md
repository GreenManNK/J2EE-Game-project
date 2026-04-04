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

## Chay trong IDE

### IntelliJ

Project giu 2 run configurations trong thu muc `.run/`:

- `Start (Default Public)`
- `Stop (All)`

Cac nut nay deu goi chung `scripts/manual-start.cmd`.

### Visual Studio Code

Project giu 2 task trong `.vscode/tasks.json`:

- `Game: Start (Default Public)`
- `Game: Stop (All)`

Cach de nhat:

1. Mo folder project trong VS Code
2. Bam `Ctrl+Shift+B`
3. VS Code se chay default build task `Game: Start (Default Public)`

Khi can dung:

1. `Terminal -> Run Task...`
2. Chon `Game: Stop (All)`

Neu muon chay bang Command Palette:

- `Tasks: Run Build Task` -> start
- `Tasks: Run Task` -> `Game: Stop (All)` -> stop

Sau khi start xong, task se in ra link public. Neu can xem lai trang thai dang song, chay:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\runtime\print-runtime-status.ps1
```

Chi gui cho nguoi choi gia tri `ACTIVE_PUBLIC_GAME_URL`.

### Visual Studio 2022 (Open Folder)

Visual Studio 2022 khong dung `.run/` cua IntelliJ va khong doc `tasks.json` cua VS Code lam launcher chinh cho repo nay. Cach chay dung la mo project bang `File -> Open -> Folder`, sau do mo `View -> Terminal` va chay lenh ngay tai root repo.

Neu terminal la PowerShell:

```powershell
.\scripts\manual-start.cmd --no-pause
.\scripts\manual-start.cmd stop --no-pause
```

Neu terminal la Command Prompt:

```cmd
cmd /c scripts\manual-start.cmd
cmd /c scripts\manual-start.cmd stop
```

Sau khi start xong, co the kiem tra link public dang song bang:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\runtime\print-runtime-status.ps1
```

Chi gui cho nguoi choi gia tri `ACTIVE_PUBLIC_GAME_URL`.

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

## Dang ky / dang nhap / lien ket bang Facebook

Flow Facebook duoc dung chung cho 3 tac vu:

- dang ky nhanh tu trang `register`
- dang nhap tu trang `login`
- lien ket them provider trong trang `settings`

Can cau hinh day du ca 2 bien:

```env
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET=...
```

Scope mac dinh:

```env
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_SCOPE=public_profile,email
```

Callback URL trong Meta/Facebook developer console:

```text
https://<domain>/Game/login/oauth2/code/facebook
```

Neu test local:

```text
http://localhost:8080/Game/login/oauth2/code/facebook
```

Luu y:

- UI chi hien nut Facebook khi da co du `CLIENT_ID` va `CLIENT_SECRET`
- Neu muon link tai khoan dang nhap san co, vao `Settings -> Social login`
- Neu server chay qua public tunnel, callback phai trung domain dang song ban gui cho nguoi choi

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
