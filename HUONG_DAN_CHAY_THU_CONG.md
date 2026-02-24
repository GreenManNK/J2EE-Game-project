# Huong Dan Chay Thu Cong (Khong Can AI)

Tai lieu nay giup ban tu chay/dung project bang cach don gian tren Windows, khong can AI ho tro.

## Chay bang nut bam trong IntelliJ (khuyen nghi)

Project da co san run configurations dung chung trong thu muc `.run/`.

Trong IntelliJ:

1. Mo menu Run configuration (goc tren ben phai)
2. Chon 1 trong cac muc sau
3. Bam nut `Run` (tam giac xanh)

Run configurations da them:
- `Start (Default Public)` (mac dinh khuyen nghi, tu build + mo quick tunnel + in link cong cong)
- `Start Public (Quick Tunnel)` (cho nguoi choi truy cap tu xa tu moi mang)
- `Start Local (J2EE)` (chi test may nay / LAN)
- `Status (App + Tunnel)` (xem PID/trang thai/link hien tai)
- `Stop All (App + Tunnel)` (dung app + tunnel)

Luu y:
- Neu IntelliJ yeu cau plugin `Shell Script`, hay bat plugin nay (thuong da co san).
- Cac nut tren chi goi cac file `scripts/manual-*.cmd`, nen van dung cung logic voi cach double-click.

## Chay bang nut bam trong Visual Studio Code

Project da duoc them `VS Code Tasks` tai:

- `.vscode/tasks.json`

Cach chay:

1. Mo project trong VS Code
2. Vao `Terminal` -> `Run Task...`
3. Chon 1 task:
   - `Game: Start (Default Public)` (khuyen nghi)
   - `Game: Start Public (Quick Tunnel)`
   - `Game: Start Local (J2EE)`
   - `Game: Status (App + Tunnel)`
   - `Game: Stop All (App + Tunnel)`

Meo:
- Co the mo `Command Palette` (`Ctrl+Shift+P`) -> `Tasks: Run Task`
- Task se mo terminal va hien log/URL public de ban copy gui nguoi choi
- VS Code tasks cung chi goi cac file `scripts/manual-*.cmd`, nen giong cach chay thu cong

Luu y:
- Thu muc `.vscode/` dang bi ignore trong `.gitignore`, nen file nay chu yeu dung local.
- Neu ban muon chia se task VS Code qua Git cho ca nhom, toi co the sua `.gitignore` de cho phep track `.vscode/tasks.json`.

## Cach nhanh nhat (truy cap tu xa moi mang)

Chay file:

- `scripts/manual-start.cmd` (mac dinh, khuyen nghi)
- `scripts/manual-start-public.cmd`

Script se:
- tu `AutoBuild` de lay code giao dien/chuc nang moi nhat
- chay app (`prod` + MySQL local)
- mo Cloudflare Quick Tunnel
- in ra `PUBLIC_GAME_URL=...`
- in khung tom tat link cong cong de copy nhanh
- luu link vao `public-game-url.txt` (de script status hien lai)

Gui link `https://...trycloudflare.com/Game` cho nguoi choi.

### Dung he thong

Chay file:

- `scripts/manual-stop-all.cmd`

## Chi test tren may nay / LAN

Chay file:

- `scripts/manual-start-local.cmd`

Link local:
- `http://J2EE/Game`
- `http://127.0.0.1:8080/Game`

## Xem trang thai hien tai

Chay file:

- `scripts/manual-status.cmd`

Script se hien:
- PID app
- app co dang listen cong `8080` khong
- PID quick tunnel
- quick tunnel URL hien tai (neu co)
- `LAST_PUBLIC_GAME_URL` (link lan chay gan nhat da luu; co the het hieu luc neu da dung tunnel)

## Luu y quan trong

- Quick Tunnel la link tam thoi, co the doi sau moi lan chay lai.
- Muon nguoi khac truy cap duoc (4G/5G/Wi-Fi khac), may cua ban phai:
  - dang bat
  - co Internet
  - app va tunnel dang chay
- Khong tat may / sleep / hibernate khi dang choi.
- Neu thay loi, xem log:
  - `run-prod-public.out.log`
  - `run-prod-public.err.log`
  - `cloudflared.err.log`

## Cach chay bang command (khong can double-click)

```powershell
cmd /c scripts\manual-start.cmd
cmd /c scripts\manual-start-public.cmd
cmd /c scripts\manual-status.cmd
cmd /c scripts\manual-stop-all.cmd
```
