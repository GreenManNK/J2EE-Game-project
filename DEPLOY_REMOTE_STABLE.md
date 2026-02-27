# Deploy Remote Stable Access (Windows + Cloudflare Tunnel + MySQL)

Muc tieu: cho phep nguoi choi o mang khac / du lieu di dong truy cap on dinh, khong dung quick tunnel tam thoi.

## Kien truc khuyen nghi

- Spring Boot app chay profile `prod` tren may chu (Windows nay)
- MySQL persistent (Laragon MySQL hoac MySQL rieng)
- Cloudflare Named Tunnel (co token, domain co dinh)
- Domain public, vi du: `https://game.example.com/Game`

## Vi sao cach nay on dinh hon Quick Tunnel

- Co domain co dinh (khong doi link moi lan)
- Khong can mo port router / khong phu thuoc NAT
- Hoat dong qua HTTPS
- Co the chay 24/7 neu may chu luon bat

## 1) Chuan bi

- Tao file `.env.public.local` tu `.env.public.example`
- Dien:
  - `CLOUDFLARE_TUNNEL_TOKEN`
  - `PUBLIC_BASE_URL` (vi du `https://game.example.com`)
  - `WS_ALLOWED_ORIGINS` (trung domain public)
  - thong tin MySQL (`LARAGON_DB_*`)

Luu y:
- Khong dung profile `test` cho production vi du lieu H2 se mat khi restart
- Profile `prod` da tat rang buoc canonical URL noi bo `J2EE`

## 2) Build JAR production

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-prod.ps1
```

## 3) Chay app production

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-prod-app.ps1 -AutoBuild
```

Script se:
- nap bien moi truong tu `.env.public.local`
- chay file JAR voi profile `prod`
- ghi log ra `run-prod-public.out.log`, `run-prod-public.err.log`
- ghi PID ra `app-prod.pid`

Dung app:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-prod-app.ps1
```

## 4) Chay Cloudflare Named Tunnel (domain co dinh)

Truoc do, trong Cloudflare Zero Trust / Tunnel:
- Tao Named Tunnel
- Gan Public Hostname (vi du `game.example.com`) -> `http://localhost:8080`
- Lay token `CLOUDFLARE_TUNNEL_TOKEN`

Chay tunnel:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-cloudflare-named-tunnel.ps1
```

Dung tunnel:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-cloudflare-named-tunnel.ps1
```

## 5) Link gui cho nguoi choi

- `https://game.example.com/Game`

## 5.1) Chay nhanh bang script tong (uu tien named tunnel)

Khi da dien `CLOUDFLARE_TUNNEL_TOKEN` + `PUBLIC_BASE_URL` trong `.env.public.local`, chi can:

```cmd
cmd /c scripts\manual-start-public.cmd --no-pause
```

Script se:
- uu tien chay named tunnel (domain co dinh)
- neu chua du cau hinh thi moi fallback quick tunnel

## 6) Van hanh 24/7 (khuyen nghi)

De chay lau dai va tu khoi dong lai sau reboot:
- Chay app + cloudflared duoi dang Windows Service (NSSM/WinSW) hoac Task Scheduler
- Cau hinh auto-start khi boot
- Giam sat log va backup MySQL dinh ky

## 7) Kiem tra nhanh khi loi

- App log: `run-prod-public.err.log`
- Tunnel log: `cloudflared-named.err.log`
- Kiem tra local app:
  - `http://127.0.0.1:8080/Game`
- Kiem tra domain public:
  - `https://game.example.com/Game`

## Ghi chu ve J2EE

- `http://J2EE/Game` van co the giu de test local trong LAN
- Link public cho nguoi o xa nen dung domain that (`https://game.example.com/Game`)
