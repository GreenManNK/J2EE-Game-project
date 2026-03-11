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
  - thong tin MySQL (`LARAGON_DB_*`)

Luu y:
- Khong dung profile `test` cho production vi du lieu H2 se mat khi restart
- Profile `prod` da tat rang buoc canonical URL noi bo `J2EE`
- App se tu dong them origin public tu `PUBLIC_BASE_URL`/`PUBLIC_GAME_URL` vao whitelist WebSocket.
- `WS_ALLOWED_ORIGINS` chi can khi muon override danh sach mac dinh.
- `APP_WEBSOCKET_EXTRA_ORIGINS` dung khi can bo sung them origin dac biet.
- Neu dung dang nhap/dang ky Google-Facebook, dien them trong `.env.public.local`:
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID`
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET`
- Callback URL tren provider console:
  - `https://<domain>/Game/login/oauth2/code/google`
  - `https://<domain>/Game/login/oauth2/code/facebook`

## 2) Build JAR production

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-prod.ps1
```

## 3) Chay app production

```powershell
.\scripts\manual-start.cmd start --local
```

Script se:
- nap bien moi truong tu `.env.public.local`
- chay file JAR voi profile `prod`
- ghi log ra `run-prod-public.out.log`, `run-prod-public.err.log`
- ghi PID ra `app-prod.pid`

Dung app:

```powershell
.\scripts\manual-start.cmd stop
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
.\scripts\manual-start.cmd stop
```

## 5) Link gui cho nguoi choi

- `https://game.example.com/Game`

## 5.2) Khi mot hay nhieu may dang bat VPN

De 3 tinh huong sau cung hoat dong:

1. May chu khong bat VPN, may nguoi choi bat VPN
2. May chu bat VPN, may nguoi choi khong bat VPN
3. Tat ca may deu bat VPN

can dung chung mot `PUBLIC_GAME_URL`/domain public, khong dung:

- `http://J2EE/Game`
- IP LAN noi bo
- link local `127.0.0.1`

Ly do:

- VPN thuong doi route/DNS cua tung may
- LAN host/IP co the mat khi hai may khong cung mang
- tunnel/domain public giu cho moi may mot duong truy cap giong nhau ra Internet

Khuyen nghi van hanh:

- uu tien `Cloudflare Named Tunnel` + `PUBLIC_BASE_URL` co dinh
- neu VPN chan `*.trycloudflare.com`, tranh quick tunnel; dung named tunnel hoac provider fallback/domain rieng
- neu can them origin dac biet cho WebSocket, dat `APP_WEBSOCKET_EXTRA_ORIGINS`

## 5.1) Chay nhanh bang script tong (uu tien named tunnel)

Khi da dien `CLOUDFLARE_TUNNEL_TOKEN` + `PUBLIC_BASE_URL` trong `.env.public.local`, chi can:

```cmd
cmd /c scripts\manual-start.cmd start --public --no-pause
```

Script se:
- uu tien chay named tunnel (domain co dinh)
- neu chua du cau hinh thi moi fallback quick tunnel

Neu muon ep dung mode phu hop cho VPN/public on dinh, dung script hien co voi co `--named`:

```cmd
cmd /c scripts\manual-start.cmd start --public --named --no-pause
```

Mode nay chi chap nhan `named tunnel`, khong fallback quick tunnel.

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
