# Game Hub / J2EE Game Project

Du an nay la mot `game portal` viet bang `Spring Boot + Thymeleaf + WebSocket`, chay duoi context path `/Game`, gom nhieu game, he thong tai khoan, tinh nang social, room realtime, admin tools va bo script van hanh local/public.

README nay duoc viet de:

- giai thich nhanh cho nguoi moi vao du an;
- liet ke ro toan bo tinh nang da co;
- ghi lai lich su thay doi de de trace theo commit;
- chi ra cac tai lieu/pham vi can doc tiep truoc khi code.

## 1. Tong quan nhanh

- Backend: `Spring Boot 3.3.8`
- Java runtime: `Java 17+`
- Build tools: `Maven Wrapper` va `Gradle Wrapper`
- UI: `Thymeleaf`, CSS/JS thu cong, PWA install flow
- Realtime:
  - STOMP/SockJS endpoint: `/Game/ws`
  - Raw WebSocket endpoints: `/Game/game/quiz`, `/Game/game/blackjack`, `/Game/game/typing`
- Database:
  - `MySQL` / `Laragon MySQL`
  - `PostgreSQL`
  - `H2 file fallback` cho local/public mode
- Security: session auth, role auth, CSRF cookie, OAuth2 Google/Facebook
- Deploy/runtime:
  - local
  - public quick tunnel
  - Cloudflare named tunnel
  - Docker

## 2. Muc tieu san pham

He thong khong chi la mot game don le. Day la mot hub co 3 lop:

1. `Game platform`: catalog, room, bot, offline, online, history, achievements.
2. `Account/social layer`: dang ky, dang nhap, OAuth2, profile, friendship, chat, thong bao.
3. `Ops/admin/runtime layer`: user management, export, access logs, external module registry, public tunnel, docker, docs, account sync API.

## 3. Tinh nang toan he thong

### 3.1 Frontend / portal

- Trang chu dang storefront, co game rail, feed bai viet, card hinh anh, CTA nhanh.
- Card art trang chu da duoc cap nhat rieng cho `Minesweeper` va `Quiz`.
- Catalog `/games` va detail page cho tung game.
- Shell chung, storefront va play surface da duoc refresh lai theo mot he palette/thiet ke thong nhat.
- Branding `Game Hub` da duoc dong bo lai giua topbar, sidebar, favicon web va icon PWA/app.
- Cac room page/lobby page uu tien dung chung he `shared UI` thay vi style roi rac tung man hinh.
- Cac page mode/lobby/catalog da duoc rut gon tiep: uu tien summary card + action grid, loai bo cac khoi gioi thieu lap lai khong can thiet.
- Theme light/dark dong bo giua shell va cac trang.
- Ho tro chuyen doi ngon ngu `vi/en`.
- PWA install flow va onboarding UI.
- Responsive layout cho desktop, tablet, mobile.

### 3.2 Tai khoan va xac thuc

- Dang ky / dang nhap bang session.
- Xac thuc email.
- Quen mat khau / reset password.
- OAuth2 Google/Facebook neu da cau hinh env.
- Link/unlink tai khoan social.
- Guest mode va migrate du lieu guest sang account sau khi dang nhap.
- Luu preferences, stats, game browser state, puzzle catalog state vao database.
- Avatar upload va avatar binary luu trong DB.

### 3.3 Social / community

- Friendship: gui loi moi, chap nhan, tu choi, xoa ban.
- Search user, xem user detail, friend list, notifications.
- Feed bai viet + comment tren home.
- Private chat qua WebSocket/STOMP.
- Leaderboard va lich su choi.

### 3.4 Admin / manager / ops

- Admin va manager pages de quan ly user.
- Ban / unban user.
- Export user list CSV / Excel.
- Access logs page + export CSV / Excel.
- Notification admin.
- Settings center.
- Account sync API co API key.
- Registry / import / preview / proxy cho external game modules.

## 4. Danh sach game va trang thai hien tai

| Game / module | Route chinh | Che do da co | Ghi chu hien tai |
| --- | --- | --- | --- |
| Caro | `/Game/games/caro` | offline 2 nguoi, bot Easy/Hard, online room, guest | Co realtime move/chat/spectate, lich su tran, room invite, room page rieng |
| Chess | `/Game/games/chess` | offline, bot, online 1v1 | Dong bo nuoc di realtime, room/spectate, ban co da refresh giao dien |
| Xiangqi | `/Game/games/xiangqi` | offline, bot, online 1v1 | Dong bo nuoc di realtime, room/spectate, ban co da refresh giao dien |
| Cards hub | `/Game/games/cards` | hub dieu huong | Gom Tien Len va Blackjack |
| Tien Len | `/Game/cards/tien-len/rooms` | online 4 nguoi, bot | Sảnh phong rieng, room realtime tach biet, ban bai 4 huong |
| Blackjack | `/Game/games/cards/blackjack` | realtime room, spectate | Dat cuoc, hit, stand, double co ban, room page rieng |
| Quiz | `/Game/games/quiz` | room, spectate, highscores | Ho tro single / multiple / typed question, room page rieng |
| Typing Battle | `/Game/games/typing` | room realtime | Theo doi progress + accuracy + winner, room page rieng |
| Minesweeper | `/Game/games/minesweeper` | offline | Beginner / Intermediate / Expert, flag, first-click safe, win award |
| Puzzle Pack | `/Game/games/puzzle` | offline | Gom Jigsaw, Sliding, Sudoku, Word Puzzle |
| Monopoly / Co ty phu | `/Game/games/monopoly` | lobby room, local 2-4 nguoi, room mode MVP | Tach 3 route: lobby, local page rieng, room page rieng; board 40 o, chance/community, rent, nha/hotel, mortgage, jail, bankruptcy |
| External modules | `/Game/games/{code}` | tuy module | Import qua registry, co the embed / redirect / API proxy |

## 5. Chi tiet gameplay / module theo nhom

### 5.1 Cac game co online realtime hoan chinh nhat

- `Caro`
  - join / spectate / surrender / leave / chat room
  - server move validation
  - bot Easy/Hard
- `Chess`
  - online room + spectate
  - offline local + bot
- `Xiangqi`
  - online room + spectate
  - offline local + bot
- `Tien Len`
  - room 4 nguoi
  - auto bot fill / automation
  - board layout 4 huong
- `Blackjack`
  - create / join / spectate
  - bet / hit / stand / double
- `Quiz`
  - create / join / spectate
  - highscores
  - room state + multi-question formats
- `Typing`
  - create / join
  - race realtime
  - winner broadcast khi ket thuc

### 5.2 Cac game offline / local

- `Minesweeper`
  - board classic
  - custom / progressive mode da tung duoc mo rong
  - win -> achievement
- `Puzzle`
  - Jigsaw
  - Sliding
  - Sudoku
  - Word Puzzle
- `Monopoly`
  - local 2-4 nguoi o page rieng
  - room mode MVP qua REST backend
  - lobby va room page da tach rieng

### 5.3 Trang thai Monopoly de nguoi moi vao lam tiep

Phan Monopoly hien da co:

- lobby room rieng, local page rieng, room page rieng
- tao / join / leave room
- host start
- chon token khong trung
- local board 40 o
- roll / move / pass Go / jail / go to jail
- property / railroad / utility / tax / chance / community chest
- buy property / rent / mortgage / unmortgage
- build house / hotel
- debt / bankruptcy / ranking
- room API backend cho action flow co ban

Phan chua day du theo spec lon:

- trade day du
- auction day du
- save / load game
- reconnect state chuan cho multiplayer
- backend-authoritative cho toan bo room gameplay
- timeout / anti-cheat online day du

## 6. Luong room / online hien tai

Du an dang dung 2 kieu realtime:

1. `STOMP + SockJS` cho cac game co room flow truyen thong:
   - Caro
   - Chess
   - Xiangqi
   - Tien Len
   - private chat

2. `Raw WebSocket` cho:
   - Quiz
   - Blackjack
   - Typing

Ngoai ra:

- route chuan vao room cua game la `/Game/games/{code}/rooms`
- tat ca room page hien uu tien trang choi rieng, tach khoi lobby/catalog
- trang room chi giu ban choi, danh sach nguoi choi, thao tac can thiet va trang thai tran
- create/join tu sanh phai nhay sang route room native thay vi render tren cung trang
- `Tien Len` dung sanh rieng tai `/Game/cards/tien-len/rooms`
- `Monopoly` da tach thanh:
  - lobby: `/Game/games/monopoly`
  - local: `/Game/games/monopoly/local`
  - room: `/Game/games/monopoly/room/{roomId}`
- game co page room rieng se redirect ve page native
- game chua co room engine rieng co the di qua room hub chung

## 7. External modules va mo rong he thong

Du an da ho tro nap module game ngoai qua registry JSON:

- source type: `external-module` hoac `external-api`
- runtime co the la `python`, `nodejs`, `go`, `dotnet`, ...
- detail mode:
  - `native`
  - `embed`
  - `redirect`
  - `api`
- co API proxy cung domain qua `/Game/games/external/{code}/api/**`

Admin co the:

- list module
- preview/import JSON
- preview/import tu manifest URL
- export registry
- delete module
- replace toan bo registry

Tai lieu chi tiet: [docs/EXTERNAL_GAME_MODULES.md](docs/EXTERNAL_GAME_MODULES.md)

## 8. Account sync va migration

He thong co `Account Sync API` de import/export snapshot account:

- tao/cap nhat account
- bulk import
- export snapshot
- sync preferences / achievements / friendships / history / stats
- migrate guest data -> account data

Tai lieu chi tiet: [docs/ACCOUNT_SYNC_API.md](docs/ACCOUNT_SYNC_API.md)

## 9. Cau truc codebase

| Thu muc | Vai tro |
| --- | --- |
| `src/main/java/com/game/hub/config` | Security, datasource, WebSocket, MVC |
| `src/main/java/com/game/hub/controller` | Page controller, API controller, admin, social, catalog |
| `src/main/java/com/game/hub/games` | Tung game/module rieng |
| `src/main/java/com/game/hub/service` | Service layer, catalog, external modules, achievements |
| `src/main/java/com/game/hub/repository` | JPA repository |
| `src/main/java/com/game/hub/entity` | Entity DB |
| `src/main/resources/templates` | Thymeleaf pages |
| `src/main/resources/static` | CSS, JS, images, PWA assets |
| `src/test/java` | Unit test, MVC test, integration test, websocket test |
| `scripts` | launcher, build, runtime, tunnel, env bootstrap |
| `docs` | tai lieu van hanh, account sync, deploy, external module |

## 10. Cau hinh va runtime can nho

- Context path mac dinh: `/Game`
- Port mac dinh: `8080`
- Data source auto:
  - `APP_DATASOURCE_KIND=auto|mysql|postgres|h2|laragon`
  - `APP_DATASOURCE_ALLOW_H2_FALLBACK=true|false`
- OAuth2:
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_*`
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_*`
- Public runtime:
  - `PUBLIC_BASE_URL`
  - `CLOUDFLARE_TUNNEL_TOKEN`
- Sync API:
  - `APP_SYNC_API_KEY`
- External modules:
  - `APP_EXTERNAL_GAME_MODULES_FILE`

Main config:

- [src/main/resources/application.yml](src/main/resources/application.yml)
- [src/main/resources/application-prod.yml](src/main/resources/application-prod.yml)

## 11. Cach chay nhanh

### 11.1 Local bang Maven

```powershell
.\mvnw.cmd spring-boot:run
```

### 11.2 Local bang Gradle

```powershell
.\gradlew.bat bootRun
```

### 11.3 Compile / test

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd test
```

Neu `target/` dang bi lock boi process khac tren Windows, co the dung:

```powershell
.\mvnw.cmd -q "-Dproject.build.directory=target-verify" test
```

### 11.4 Smoke test ket noi online

Khi can verify online flow ngoai pham vi test trong JVM, nen chay them mot dot smoke test tren app that:

- boot app local voi `test profile` de tat canonical URL guard va dung H2
- probe HTTP cho lobby/page room cua tung game online
- test `online-hub/api/create-room`
- test `Monopoly room API`
- test `Raw WebSocket` cho `Typing`, `Quiz`, `Blackjack`
- test `SockJS/STOMP` cho `Caro`, `Chess`, `Xiangqi`, `Tien Len`

### 11.5 Chay bang script tong

```powershell
.\scripts\manual-start.cmd start --local
.\scripts\manual-start.cmd start --public
.\scripts\manual-start.cmd start --docker
```

### 11.6 Docker

```powershell
docker compose up --build
```

Tai lieu run/deploy:

- [docs/HUONG_DAN_CHAY_THU_CONG.md](docs/HUONG_DAN_CHAY_THU_CONG.md)
- [docs/DEPLOY_REMOTE_STABLE.md](docs/DEPLOY_REMOTE_STABLE.md)

## 12. Cau truc tiep quan cho nguoi moi vao lam

Neu ban moi vao du an, thu tu doc/soi code nen la:

1. Doc file `QUAN TRONG ( Phai doc ).md` nay de nam pham vi chung.
2. Doc `docs/HUONG_DAN_CHAY_THU_CONG.md` de chay du an.
3. Doc `src/main/resources/application.yml` de nam env/runtime.
4. Doc `src/main/java/com/game/hub/controller/HomeController.java` va `GameCatalogController.java` de nam flow storefront + catalog.
5. Doc game module ma ban sap sua trong `src/main/java/com/game/hub/games/...`.
6. Doc template cung ten trong `src/main/resources/templates/...`.
7. Chay test lien quan truoc khi sua.

## 13. Cac diem can luu y khi tiep tuc phat trien

- Co ca `STOMP` va `raw WebSocket`, khong nen nham handler.
- Project dang co nhiu route legacy duoc redirect de giu tuong thich.
- Context path `/Game` can duoc tinh toi khi them route/frontend asset.
- Public runtime co logic whitelist WebSocket origin, can can nhac khi them domain/tunnel moi.
- Monopoly da co 3 mode route (`lobby/local/room`), nen khi sua UI/logic phai check dung mode dang boot.
- Test websocket `Blackjack` da duoc co dinh deck trong test de tranh flaky do random shuffle; neu sua luong chia bai thi cap nhat ca test deck.
- Neu sua UI toan app, uu tien check 4 diem goc: `templates/fragments.html`, `static/css/cg-market.css`, `static/css/unified-app.css`, `static/css/play-surfaces.css`.
- Neu sua page mode/lobby, uu tien giu bo cuc ngan: 1 summary card + cum action vao tran; tranh them lai highlight/intro strip neu khong phuc vu thao tac.
- Neu sua logo/icon, check them `static/images/brand`, `static/icons`, `static/manifest.webmanifest` va `static/service-worker.js` de dong bo web icon + PWA cache.
- External module registry co the override game native neu manifest bat `overrideExisting`.
- Full suite hien tai dang o muc `253` test pass.
- Online flow hien da duoc verify them bang smoke test chay tren app jar local that, khong chi dua vao surefire test trong JVM.
- Khi sua room flow/websocket, uu tien re-run ca `.\mvnw.cmd test` va mot dot smoke test local cho create/join/spectate/rejoin/action.

## 14. Moc lich su phat trien

### 14.1 Tom tat theo giai doan

- `2026-02-23 -> 2026-02-24`
  - import code goc
  - chuan hoa runtime `/Game`
  - them launcher/public tunnel
  - bat dau hub da game va guest multiplayer
- `2026-02-25 -> 2026-02-26`
  - them Tien Len automation
  - sua room state va server validation
  - doi package sang `com.game.hub`
  - them Minesweeper
  - them bootstrap runtime + diagnostics
- `2026-02-27 -> 2026-03-03`
  - dai tu UI/UX, i18n, mobile, sidebar
  - them Docker, settings, export, access logs
  - nang cap puzzle/minesweeper/home market
- `2026-03-04 -> 2026-03-11`
  - mo rong auth/profile/admin
  - avatar binary trong DB
  - friendship / account-only pages / guest migration
  - account sync API
  - external game module registry
  - thong nhat luong room, quick random, dedicated room pages
- `2026-03-13 -> 2026-03-14`
  - thong nhat storefront + PWA/onboarding
  - them Monopoly gameplay
  - them Monopoly room support
  - thong nhat route `/games/{code}/rooms`
- `2026-03-14 -> 2026-03-15`
  - tach lobby va room page rieng cho cac game online chinh
  - lam moi giao dien ban co `Chess` va `Xiangqi`
  - cap nhat anh gioi thieu `Minesweeper` va `Quiz`
  - on dinh test websocket `Blackjack` de loai flaky do deck ngau nhien
- `2026-03-15`
  - refresh shell chung, storefront va play surfaces tren toan app
  - dong bo lai palette, glass surface, typography va HUD/room panels
  - cache-bust CSS chung qua version query moi trong `fragments`
  - dong bo brand `Game Hub` giua logo web, favicon va bo icon app/PWA
  - tach dut diem sanh phong va room page rieng cho `Typing`, `Quiz`, `Blackjack`, `Tien Len`, `Monopoly`
  - tach `Monopoly` thanh 3 route ro rang: lobby, local, room
  - rut gon them cac page mode/lobby cua `Caro`, `Chess`, `Xiangqi`, `Cards`, `Minesweeper`, `Tien Len` va bo meta-strip du tren catalog/puzzle hub
  - nang full suite len `253` test pass sau khi bo sung test route/page mode moi
  - verify them ket noi online tren app that cho `Caro`, `Chess`, `Xiangqi`, `Tien Len`, `Typing`, `Quiz`, `Blackjack`, `Monopoly`
  - smoke test da cover HTTP room pages, create-room API, room API va ca 2 stack `SockJS/STOMP` + `Raw WebSocket`

### 14.2 Raw commit timeline

Ben duoi la timeline commit de de truy vet theo hash:

- 2026-03-15 `5da25f3` Separate lobbies from dedicated game room pages
- 2026-03-15 `56f8dc5` Update Game Hub branding assets
- 2026-03-15 `c9cf29f` Refresh shared interface styling across app
- 2026-03-15 `32b6f69` Update project README and stabilize blackjack websocket tests
- 2026-03-15 `2e914a0` Refine dedicated game room pages and board visuals
- 2026-03-14 `99e11d5` Add Monopoly room support and per-game room routes
- 2026-03-14 `3bfe621` Add Monopoly gameplay and refresh game portal
- 2026-03-13 `78c83d7` Sync storefront styling across site
- 2026-03-13 `c0072cc` Fix account test contracts
- 2026-03-13 `63beccd` Refine game portal storefront surfaces
- 2026-03-13 `89daf34` Ship unified UI, onboarding, and PWA install flow
- 2026-03-13 `56c4ae2` Fix online room flow and public runtime fallback
- 2026-03-11 `42321de` feat(cards): update deck sprite and route blackjack via cards hub
- 2026-03-11 `5a54fa0` feat(account): add facebook link flow and social entry points
- 2026-03-11 `bb86528` feat(account): store avatar binary in DB and raise upload limit to 406MB
- 2026-03-11 `2c9c597` Improve online room backend flow across games
- 2026-03-11 `86d3c82` Add missing game features: puzzle win awards and random quick-join
- 2026-03-11 `f5d8f8b` Fix Vietnamese text encoding in shared fragments
- 2026-03-11 `d6a11ce` Refine global button, form, and section layout consistency
- 2026-03-11 `b7998ff` Standardize button sizing and action layout across UI
- 2026-03-11 `b303ba3` Optimize global UI layout, controls, and mobile rendering
- 2026-03-11 `5cf8f9e` Standardize online lobby flow and add random quick join
- 2026-03-11 `04f7bb6` Unify per-game online flow and remove shared lobby redirects
- 2026-03-11 `07bffa3` Unify online game entry routes via online hub
- 2026-03-11 `7918db5` Add realtime room list updates for online hub
- 2026-03-11 `604eb6a` Fix profile/settings auth fallback to SecurityContext
- 2026-03-11 `a91b56e` Update achievements for online game modes
- 2026-03-11 `9604151` Consolidate project launcher and account settings
- 2026-03-10 `5da9593` Auto-report runtime status and harden public DNS checks
- 2026-03-10 `d41acdf` Harden public websocket origins for VPN access
- 2026-03-09 `d03b23b` Streamline direct room entry flow and UI
- 2026-03-09 `345054f` Add external game module admin registry tooling
- 2026-03-09 `bdadd31` Polish game hub UX and harden online/public flows
- 2026-03-08 `364db2d` Add account sync and dedicated online room pages
- 2026-03-07 `26410b9` Direct room entry and remove redundant UI cards
- 2026-03-07 `724233c` Fix UI language consistency and shell display synchronization
- 2026-03-07 `93ccadb` Reorganize project tree: docs and script entrypoints
- 2026-03-07 `aba9086` Clean project tree and remove legacy imported snapshot
- 2026-03-07 `383ef6f` Fix online reconnect flow and light-mode UI readability
- 2026-03-07 `ce3fa9e` Fix theme sync across shell and home/market surfaces
- 2026-03-07 `fbc0ee3` Fix friendship test for required authenticated session
- 2026-03-07 `bb7a391` Enforce account-only visibility and migrate guest offline stats to DB
- 2026-03-06 `183979d` feat(profile): upload avatar and persist account preferences in database
- 2026-03-06 `fe33091` chore(vs2022): add open-folder run scripts and docs
- 2026-03-06 `70ab4ac` fix(mobile): prevent content clipping across shared layouts
- 2026-03-06 `d9c11f9` fix(layout): prevent content overlap and simplify sidebar shell
- 2026-03-04 `e5f3057` Fix build dependency and deduplicate lobby route
- 2026-03-04 `748ea05` feat(ui): refresh game identity visuals and animation-first mode pages
- 2026-03-04 `0dc2c2a` feat: improve online session resilience and gameplay polish
- 2026-03-04 `c37fc4a` feat: expand auth, admin center, and gameplay UX updates
- 2026-03-03 `9427d88` Expand core web features: filters/exports, access logs, sidebar polish, feed moderation
- 2026-03-03 `56780c1` feat: add settings center and excel export for user management
- 2026-03-03 `bc1ba5d` fix(ui): unify sidebar default behavior and theme toggle reliability
- 2026-03-02 `68c8253` feat(ui): align market layout and sidebar hover behavior
- 2026-03-02 `38987bc` feat(ui): upgrade puzzle category UX and sync minesweeper theme
- 2026-03-02 `0b488ff` feat: refine sidebar scrolling and live friend status updates
- 2026-03-02 `4b9eea3` feat: refresh minesweeper with classic board layout
- 2026-03-02 `0eccb21` feat: add docker runtime and cross-platform build matrix
- 2026-03-02 `522e03b` feat: update game modules and cross-platform build tooling
- 2026-02-28 `e6028d8` fix(ui): normalize Vietnamese copy on account, home, and i18n
- 2026-02-27 `cefc2c5` fix(ui): correct sidebar vietnamese labels and mobile toggle positioning
- 2026-02-27 `68f2eb1` fix(ui): restore button interactions and harden public tunnel scripts
- 2026-02-27 `3dfb294` feat(i18n): add global vi/en language toggle and UI text normalization
- 2026-02-27 `3667d66` fix(ui): make sidebar toggle visible in overlay state
- 2026-02-27 `227efd5` fix(ui): improve overlay sidebar toggle and friend list
- 2026-02-27 `36373dd` fix(ui,account): sidebar toggle and email verification flow
- 2026-02-27 `c7e7146` fix(run): use absolute cmd interpreter path
- 2026-02-27 `29423d3` feat(ui): refresh game portal layouts across pages
- 2026-02-26 `88b7563` Stabilize public tunnel readiness checks
- 2026-02-26 `2daf6fd` Sync portal UI across game catalog pages
- 2026-02-26 `b8eaa31` Refresh Caro page UI inspired by game portal layout
- 2026-02-26 `b21b4da` Add default public run aliases
- 2026-02-26 `ec40fba` Add run-time environment bootstrap and diagnostics
- 2026-02-26 `4c2649f` Enhance gameplay and integrity checks across games
- 2026-02-26 `d82428e` Enhance Minesweeper with progressive and custom board modes
- 2026-02-26 `504fa67` Add Minesweeper and reorganize game modules
- 2026-02-26 `5b961f4` Refactor to com.game.hub and reorganize Caro modules
- 2026-02-26 `7eed1b1` Fix online game room state and server move validation
- 2026-02-25 `883509b` Redesign Tien Len tables to 4-direction board layout
- 2026-02-25 `8ecf06a` Add public run tooling and Tien Len auto-bot automation
- 2026-02-25 `35285e6` chore: sync local project updates
- 2026-02-24 `1efce17` Add chess/xiangqi online MVP and complete multi-game mode flow
- 2026-02-24 `52cc96d` Add multi-game hub, bots, and public launch defaults
- 2026-02-24 `c0f3f1e` Add IDE button run configs and manual launchers
- 2026-02-24 `60e54f1` Improve responsive layouts across mobile and tablet
- 2026-02-24 `3e7a5f7` Add one-command remote quick tunnel launcher
- 2026-02-24 `26acb10` Add guest multiplayer support and remote deploy scripts
- 2026-02-23 `6251de2` Unify legacy templates with shared UI fragments
- 2026-02-23 `79c83a7` Enforce J2EE/Game runtime requirements
- 2026-02-23 `515a2e6` Add remaining project changes and tests
- 2026-02-23 `8d48e18` Fix bot win handling and support /Game context path
- 2026-02-23 `c1d9333` Require Laragon MySQL datasource for runtime
- 2026-02-23 `ac6f35e` Add all remaining project files
- 2026-02-23 `0b40f80` Initial import

## 15. Tai lieu lien quan

- [docs/GAMEPLAY_IMPLEMENTATION_PLAN.md](docs/GAMEPLAY_IMPLEMENTATION_PLAN.md)
- [docs/HUONG_DAN_CHAY_THU_CONG.md](docs/HUONG_DAN_CHAY_THU_CONG.md)
- [docs/DEPLOY_REMOTE_STABLE.md](docs/DEPLOY_REMOTE_STABLE.md)
- [docs/ACCOUNT_SYNC_API.md](docs/ACCOUNT_SYNC_API.md)
- [docs/EXTERNAL_GAME_MODULES.md](docs/EXTERNAL_GAME_MODULES.md)
- [docs/MIGRATION_REPORT.md](docs/MIGRATION_REPORT.md)

## 16. Ket luan cho nguoi vao tiep quan

Neu ban vao du an de tiep tuc phat trien, hay hieu no la:

- mot `multi-game portal` da chay duoc that;
- co day du `auth + social + admin + room + runtime`;
- co mot so game da on dinh online;
- `Monopoly` la phan dang duoc mo rong manh nhat hien nay;
- va he thong da duoc script hoa kha ky de chay local/public ma khong can tu setup tay qua nhieu.

Neu can xem lich su day du hon nua, co the chay:

```powershell
git log --date=short --pretty=format:"- %ad `%h` %s"
```
