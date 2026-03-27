# Huong Dan Chay Thu Cong (Khong Can AI)

Tai lieu nay chi giu 2 cach van hanh chuan cho du an:

- `Start (Default Public)`
- `Stop (All)`

Khong con su dung cac launcher `local`, `docker`, `status`, `verify`, `mysql`, `postgres` rieng tu giao dien chay nua.

## Lenh dung chuan

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

- bootstrap moi truong lan dau neu can
- build va chay app profile `prod`
- tu chon DB kha dung theo thu tu uu tien:
  - PostgreSQL neu da cau hinh va truy cap duoc
  - MySQL neu truy cap duoc
  - H2 file local neu khong co DB server kha dung
- tu chon public tunnel:
  - uu tien `Cloudflare Named Tunnel` neu da co `CLOUDFLARE_TUNNEL_TOKEN` + `PUBLIC_BASE_URL`
  - neu chua du cau hinh thi fallback sang quick tunnel
- chi bao thanh cong khi landing page va websocket public deu san sang
- in `PUBLIC_GAME_URL=...` de gui cho nguoi choi

## Stop (All) lam gi

Khi chay `Stop (All)`, he thong se dung tat ca thanh phan runtime lien quan:

- quick tunnel
- Cloudflare named tunnel
- fallback public tunnel
- app production
- Docker runtime neu dang chay

Sau khi dung, script se in lai trang thai de ban biet he thong da tat het hay chua.

## IntelliJ

Project chi giu 2 run configurations trong thu muc `.run/`:

- `Start (Default Public)`
- `Stop (All)`

Cach dung:

1. Mo project trong IntelliJ
2. Chon run configuration o goc tren ben phai
3. Bam `Run`

Ca hai nut deu goi chung `scripts/manual-start.cmd`.

## Visual Studio Code

Project chi giu 2 task trong [tasks.json](C:\Users\GreenManNK\IdeaProjects\Game\.vscode\tasks.json):

- `Game: Start (Default Public)`
- `Game: Stop (All)`

Cach dung:

1. Mo project trong VS Code
2. `Terminal` -> `Run Task...`
3. Chon mot trong 2 task tren

## Visual Studio 2022 (Open Folder)

Visual Studio 2022 khong co launcher rieng cho Spring Boot, nen dung chung 2 lenh sau trong terminal:

```powershell
.\scripts\manual-start.cmd --no-pause
.\scripts\manual-start.cmd stop --no-pause
```

## Cau hinh public on dinh

Neu muon link public co dinh thay vi quick tunnel, mo [`.env.public.local`](C:\Users\GreenManNK\IdeaProjects\Game\.env.public.local) va dien:

- `CLOUDFLARE_TUNNEL_TOKEN`
- `PUBLIC_BASE_URL`

Khi 2 bien nay ton tai, `Start (Default Public)` se uu tien named tunnel tu dong. Ban khong can truyen them co `--named` nua.

## Cau hinh dang nhap/dang ky Google-Facebook

Mo [`.env.public.local`](C:\Users\GreenManNK\IdeaProjects\Game\.env.public.local) va dien:

- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET`

Callback URL tren provider console:

- `https://<domain>/Game/login/oauth2/code/google`
- `https://<domain>/Game/login/oauth2/code/facebook`

## Doctor va setup moi truong

Cac script `doctor/setup` van duoc giu de kiem tra hoac cai tool thieu, nhung khong phai launcher van hanh chinh.

Kiem tra moi truong:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-env-doctor.ps1 -Mode public -Db auto -CheckOnly
```

Thu cai cong cu thieu:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-env-setup.ps1 -Mode public -Db auto
```

## Log can xem khi gap loi

- `run-prod-public.out.log`
- `run-prod-public.err.log`
- `cloudflared.err.log`
- `cloudflared-named.err.log`

## Lenh nhanh de nho

```powershell
cmd /c scripts\manual-start.cmd
cmd /c scripts\manual-start.cmd stop
```
