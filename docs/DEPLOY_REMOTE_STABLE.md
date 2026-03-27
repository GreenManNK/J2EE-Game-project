# Deploy Remote Stable Access (Windows + Cloudflare Tunnel + Database)

Muc tieu: giu mot cach van hanh duy nhat cho public runtime. Tu nay chi dung:

- `Start (Default Public)`
- `Stop (All)`

## Nguyen tac van hanh

`Start (Default Public)` se tu dong:

- bootstrap moi truong neu can
- chay app profile `prod`
- uu tien PostgreSQL neu da cau hinh va truy cap duoc
- neu khong co PostgreSQL thi thu MySQL
- neu khong co DB server kha dung thi fallback H2 file local
- uu tien Cloudflare Named Tunnel neu da co `CLOUDFLARE_TUNNEL_TOKEN` + `PUBLIC_BASE_URL`
- neu chua du cau hinh named tunnel thi fallback quick tunnel
- chi bao thanh cong khi `/Game`, `ping` va websocket public san sang

`Stop (All)` se dung toan bo app + tunnel + Docker runtime neu co.

## 1) Chuan bi `.env.public.local`

Tao file `.env.public.local` tu `.env.public.example`, sau do dien cac bien can thiet:

- `CLOUDFLARE_TUNNEL_TOKEN`
- `PUBLIC_BASE_URL` vi du `https://game.example.com`
- thong tin DB neu muon uu tien PostgreSQL/MySQL
- thong tin OAuth neu dung Google/Facebook login

Luu y:

- Khong can co them co `--named` nua. Chi can cau hinh env dung, launcher se tu uu tien named tunnel.
- Neu chua co `CLOUDFLARE_TUNNEL_TOKEN` va `PUBLIC_BASE_URL`, launcher van chay quick tunnel de lay link tam thoi.

## 2) Build production neu muon chuan bi truoc

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-prod.ps1
```

Buoc nay la optional. `Start (Default Public)` van tu build lai khi can.

## 3) Start public

```powershell
cmd /c scripts\manual-start.cmd
```

Hoac:

```powershell
.\scripts\manual-start.cmd --no-pause
```

Ket qua mong doi:

- app chay tren `prod`
- public URL duoc in ra man hinh duoi dang `PUBLIC_GAME_URL=...`
- link duoc ghi vao `public-game-url.txt`

## 4) Stop toan bo runtime

```powershell
cmd /c scripts\manual-start.cmd stop
```

Hoac:

```powershell
.\scripts\manual-start.cmd stop --no-pause
```

## 5) Link gui cho nguoi choi

- Named tunnel: `https://game.example.com/Game`
- Quick tunnel: `https://<random>.trycloudflare.com/Game`

## 6) Khi may dung VPN

Van hanh public phai gui cung mot `PUBLIC_GAME_URL` cho moi nguoi choi. Khong gui:

- `http://J2EE/Game`
- `http://127.0.0.1:8080/Game`
- IP LAN noi bo

Ly do:

- VPN co the doi DNS/route tung may
- dia chi local va LAN khong on dinh voi nguoi o mang khac
- named tunnel/public URL moi la duong truy cap chung

## 7) Kiem tra nhanh khi loi

- App log: `run-prod-public.err.log`
- Quick tunnel log: `cloudflared.err.log`
- Named tunnel log: `cloudflared-named.err.log`
- Local app: `http://127.0.0.1:8080/Game`
- Public app: `https://<domain>/Game`

## 8) Van hanh lau dai

Neu muon chay 24/7, van nen dung chung cung workflow nay, sau do boc `Start (Default Public)` vao scheduler/service. Khong tao them launcher rieng cho local/docker/database-specific nua.
