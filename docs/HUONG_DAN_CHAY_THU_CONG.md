# Huong Dan Chay Thu Cong (Khong Can AI)

Tai lieu nay giup ban tu chay/dung project bang cach don gian tren Windows, khong can AI ho tro.

## Quy uoc chuan cua du an (bat buoc)

- `Chay` = `Chay PUBLIC` (app + public tunnel + link gui nguoi choi o mang khac).
- Script public phai chi bao thanh cong khi:
  - app local truy cap duoc
  - endpoint websocket local san sang
  - URL public truy cap duoc
  - endpoint websocket qua public URL san sang
- Neu may khong co MySQL local, script public se tu dong fallback sang H2 file local de van chay duoc tren may khac.

## Chay da nen tang (Windows / macOS / Linux) - script moi

Bo script moi giup check moi truong, phat hien thieu cong cu va co the thu cai tu dong (best effort) tren nhieu he dieu hanh.

### 1) Kiem tra moi truong (khong cai)

Windows (PowerShell):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-env-doctor.ps1 -Mode local -Db auto -CheckOnly
```

macOS / Linux (bash):

```bash
bash ./scripts/dev-env-doctor.sh --mode local --db auto --check-only
```

### 2) Tu dong cai cong cu thieu (best effort)

Windows (PowerShell):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-env-setup.ps1 -Mode local -Db auto
```

macOS / Linux (bash):

```bash
bash ./scripts/dev-env-setup.sh --mode local --db auto
```

Ghi chu:
- Script se co gang cai `Java 17+` (thuong cai ban 21 LTS), `Maven`, `Gradle`, `Git`, `Node.js` (khuyen nghi), `Docker` (optional), va `cloudflared` neu chon `Mode public`.
- Viec cai tu dong phu thuoc package manager cua may (`winget/choco/scoop`, `brew`, `apt/dnf/yum/pacman/zypper`).
- Neu script bao da cai xong nhung terminal van khong nhan lenh, hay mo terminal moi va chay lai script.

### 3) Chay local da nen tang (Maven/Gradle + H2 fallback)

Windows (PowerShell):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-run-local.ps1
```

macOS / Linux (bash):

```bash
bash ./scripts/dev-run-local.sh
```

Neu muon chay truc tiep bang wrapper:

```powershell
.\mvnw.cmd spring-boot:run
.\gradlew.bat bootRun
```

```bash
./mvnw spring-boot:run
./gradlew bootRun
```

Script local moi se:
- tu dong `bootstrap` moi truong o lan chay dau tien (doctor + auto-install best effort)
- ghi file trang thai vao `.data/dev-env/` de cac lan sau bo qua buoc chuan doan/cai dat
- co the bo qua bootstrap bang `-SkipDoctor` / `--skip-doctor` (giu tuong thich ten cu)
- co the ep bootstrap lai bang `-ForceBootstrap` (PowerShell) / `--force-bootstrap` (bash)
- tu tao thu muc `.data/`
- bat `H2 fallback` + `APP_EMAIL_MODE=log` de de chay tren may moi
- tu dong chon build tool theo thu tu uu tien: `mvnw` -> `mvn` -> `gradlew` -> `gradle`
- chay app voi profile `prod` va port `8080` (co the doi qua tham so script)

Vi du ep chay lai bootstrap:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-run-local.ps1 -ForceBootstrap
```

```bash
bash ./scripts/dev-run-local.sh --force-bootstrap
```

### 4) Chay bang Docker (khong phu thuoc JDK/Maven/Gradle local)

Neu may khac version Java/tool hoac khong muon cai moi truong local, co the chay bang Docker.

Windows (PowerShell):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-run-docker.ps1
```

macOS / Linux (bash):

```bash
bash ./scripts/dev-run-docker.sh
```

Lenh nhanh bang entrypoints (Windows):

- `scripts\entrypoints\RUN_DOCKER.cmd` (start)
- `scripts\entrypoints\STOP_DOCKER.cmd` (stop)

Link sau khi start:

- `http://127.0.0.1:8080/Game`

Mac dinh Docker mode se dung profile `prod` + H2 fallback (du lieu luu o `./.data-docker`).
Neu can xem log:

```bash
docker compose logs -f --tail=200
```

## Chay bang nut bam trong IntelliJ (khuyen nghi)

Project da co san run configurations dung chung trong thu muc `.run/`.

Trong IntelliJ:

1. Mo menu Run configuration (goc tren ben phai)
2. Chon 1 trong cac muc sau
3. Bam nut `Run` (tam giac xanh)

Run configurations da them:
- `Start (Default Public)` (mac dinh khuyen nghi, tu build + mo quick tunnel + in link cong cong)
- `Start Public (Quick Tunnel)` (cho nguoi choi truy cap tu xa tu moi mang)
- `Start Public (MySQL Standard)` (cho may khong dung Laragon, dung MySQL Server thuong)
- `Start Public (PostgreSQL)` (chay public bang PostgreSQL)
- `Start Local (J2EE)` (chi test may nay / LAN)
- `Status (App + Tunnel)` (xem PID/trang thai/link hien tai)
- `Stop All (App + Tunnel)` (dung app + tunnel)

Tu ban cap nhat hien tai:
- Khi bam cac nut `Start ...`, he thong se tu `bootstrap` moi truong lan dau (kiem tra + co gang cai thieu).
- Cac lan chay sau se bo qua bootstrap neu da co state trong `.data/dev-env/`.

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
   - `Game: Start Public (MySQL Standard)`
   - `Game: Start Public (PostgreSQL)`
   - `Game: Start Local (J2EE)`
   - `Game: Status (App + Tunnel)`
   - `Game: Stop All (App + Tunnel)`
   - `Game: Start Docker`
   - `Game: Stop Docker`

Meo:
- Co the mo `Command Palette` (`Ctrl+Shift+P`) -> `Tasks: Run Task`
- Task se mo terminal va hien log/URL public de ban copy gui nguoi choi
- VS Code tasks cung chi goi cac file `scripts/manual-*.cmd`, nen giong cach chay thu cong
- Tuong tu IntelliJ, cac task `Start ...` nay da duoc huong co che bootstrap-moi-truong-lan-dau.

Luu y:
- Thu muc `.vscode/` dang bi ignore trong `.gitignore`, nen file nay chu yeu dung local.
- Neu ban muon chia se task VS Code qua Git cho ca nhom, toi co the sua `.gitignore` de cho phep track `.vscode/tasks.json`.

## Chay bang Visual Studio 2022 (Open Folder)

Visual Studio 2022 khong co project system Java/Spring Boot native nhu IntelliJ,
nhung van chay on dinh theo cach `Open Folder + Terminal`.

### 1) Dieu kien

- Da cai Java 17+ (khuyen nghi Temurin 17/21)
- Windows co PowerShell
- Mo duoc terminal trong Visual Studio 2022 (`View -> Terminal`)

### 2) Mo project

1. Trong Visual Studio 2022: `File -> Open -> Folder...`
2. Chon thu muc root cua project (`Game`)
3. Mo terminal trong Visual Studio 2022

### 3) Chay / Kiem tra / Dung

Da them san script danh rieng cho VS2022 (khong pause terminal):

- `scripts\entrypoints\RUN_VS2022.cmd` (Start public mode mac dinh)
- `scripts\entrypoints\STATUS_VS2022.cmd` (Xem trang thai app + tunnel)
- `scripts\entrypoints\STOP_VS2022.cmd` (Dung app + tunnel)

Lenh:

```powershell
.\scripts\entrypoints\RUN_VS2022.cmd
.\scripts\entrypoints\STATUS_VS2022.cmd
.\scripts\entrypoints\STOP_VS2022.cmd
```

Neu ban chi muon chay local (khong tunnel):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-run-local.ps1
```

Ghi chu:
- Cac script VS2022 tren chi goi lai luong chay chuan cua project (`scripts/entrypoints/*` -> `scripts/manual-*`),
  nen hanh vi giong IntelliJ/VS Code tasks.

## Cach nhanh nhat (truy cap tu xa moi mang)

Chay file:

- `scripts/entrypoints/RUN.cmd` (alias moi, mac dinh = PUBLIC, khuyen nghi de "bam Run" nhanh)
- `scripts/entrypoints/RUN_PUBLIC.cmd`
- `scripts/entrypoints/RUN_DOCKER.cmd` (neu muon chay nhanh theo Docker)
- `scripts/manual-start.cmd` (mac dinh, khuyen nghi)
- `scripts/manual-start-public.cmd`

Neu dung PowerShell:

- `.\scripts\entrypoints\RUN.ps1`

Script se:
- tu `AutoBuild` de lay code giao dien/chuc nang moi nhat
- chay app (`prod`; uu tien MySQL local, neu khong co se fallback H2 local)
- mo Cloudflare Quick Tunnel
- in ra `PUBLIC_GAME_URL=...`
- in khung tom tat link cong cong de copy nhanh
- luu link vao `public-game-url.txt` (de script status hien lai)

Gui link `https://...trycloudflare.com/Game` cho nguoi choi.

## May khong co Laragon (chi co MySQL server thuong hoac PostgreSQL)

Ban van chay duoc, khong bat buoc Laragon.

Luu y:
- `MySQL Workbench`, `DBeaver`, `HeidiSQL`, `phpMyAdmin`... chi la cong cu quan ly (management tool), khong phai DB server.
- Ban can `MySQL Server` hoac `PostgreSQL Server` dang chay va co thong tin ket noi (host/port/db/user/pass).

### Chay PUBLIC voi MySQL Server thuong (khong Laragon)

1. Copy file mau:
   - `.env.public.mysql.example` -> `.env.public.mysql.local`
2. Dien thong tin MySQL vao `.env.public.mysql.local`
3. Chay mot trong cac cach:
   - `scripts/entrypoints/RUN_PUBLIC_MYSQL.cmd`
   - `scripts/manual-start-public-mysql.cmd`
   - IntelliJ: `Start Public (MySQL Standard)`
   - VS Code Task: `Game: Start Public (MySQL Standard)`

Ghi chu:
- Script nay ep `APP_DATASOURCE_KIND=mysql`
- Script nay tat fallback H2 de neu sai cau hinh MySQL thi se bao loi ro rang (khong fallback am tham)

### Chay PUBLIC voi PostgreSQL

1. Tao san database (vi du: `caro`) tren PostgreSQL
2. Copy file mau:
   - `.env.public.postgres.example` -> `.env.public.postgres.local`
3. Dien thong tin PostgreSQL vao `.env.public.postgres.local`
4. Chay mot trong cac cach:
   - `scripts/entrypoints/RUN_PUBLIC_POSTGRES.cmd`
   - `scripts/manual-start-public-postgres.cmd`
   - IntelliJ: `Start Public (PostgreSQL)`
   - VS Code Task: `Game: Start Public (PostgreSQL)`

Ghi chu:
- Script nay ep `APP_DATASOURCE_KIND=postgres`
- Script nay tat fallback H2 de bat loi cau hinh PostgreSQL som
- PostgreSQL mode hien tai khong tu tao database moi; hay tao DB truoc

### Dung he thong

Chay file:

- `scripts/entrypoints/STOP_PUBLIC.cmd`
- `scripts/entrypoints/STOP_DOCKER.cmd` (neu dang chay Docker mode)
- `scripts/manual-stop-all.cmd`
- `scripts/manual-stop-docker.cmd`

## Chi test tren may nay / LAN

Chay file:

- `scripts/manual-start-local.cmd`

Link local:
- `http://J2EE/Game`
- `http://127.0.0.1:8080/Game`

## Xem trang thai hien tai

Chay file:

- `scripts/entrypoints/STATUS_PUBLIC.cmd`
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
cmd /c scripts\manual-start-public-mysql.cmd
cmd /c scripts\manual-start-public-postgres.cmd
cmd /c scripts\manual-start-docker.cmd
cmd /c scripts\manual-status.cmd
cmd /c scripts\manual-stop-all.cmd
cmd /c scripts\manual-stop-docker.cmd
cmd /c scripts\entrypoints\RUN_PUBLIC.cmd
cmd /c scripts\entrypoints\RUN_PUBLIC_MYSQL.cmd
cmd /c scripts\entrypoints\RUN_PUBLIC_POSTGRES.cmd
cmd /c scripts\entrypoints\RUN_DOCKER.cmd
cmd /c scripts\entrypoints\STATUS_PUBLIC.cmd
cmd /c scripts\entrypoints\STOP_PUBLIC.cmd
cmd /c scripts\entrypoints\STOP_DOCKER.cmd
```
