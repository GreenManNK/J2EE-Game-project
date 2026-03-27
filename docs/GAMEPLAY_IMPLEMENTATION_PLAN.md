# Gameplay Implementation Plan

Tai lieu nay mo ta:

- gameplay chuan can dat cho tung game;
- hien trang thuc te trong codebase;
- do lech giua implementation hien tai va gameplay chuan;
- ke hoach lap trinh de dua tung game den muc `gameplay-complete`.

Muc tieu cua tai lieu nay khong phai la wishlist mo ho. Muc tieu la de nguoi moi vao du an co the:

- hieu game nao da on, game nao con MVP;
- biet dung file nao de sua;
- biet nen lam theo thu tu nao de khong vo logic hien co.

## 1. Tieu chi "gameplay-complete"

Mot game duoc xem la `gameplay-complete` khi dat du 5 nhom:

1. `Rule fidelity`
   - core rule dung voi gameplay chuan cua game do.
   - cac edge case quan trong da duoc xu ly.
2. `Session flow`
   - tao phong / vao phong / roi phong / rematch / reconnect hop ly.
3. `State integrity`
   - backend hoac engine trung tam quyet dinh luat, khong de client tu suy logic de gian lan.
4. `UX completeness`
   - thong bao luot, ket qua, action hop le/khong hop le, room state, viewer state.
5. `Test coverage`
   - co unit test + integration test cho rule quan trong va room flow.

## 2. Tong quan portfolio

| Game | Muc do hoan thien hien tai | Dung gameplay chuan | Uu tien | Do lon |
| --- | --- | --- | --- | --- |
| Caro | Tot cho MVP online/offline | Trung binh | Trung binh | M |
| Chess | Tot cho MVP 1v1 | Chua day du luat chuan | Cao | L |
| Xiangqi | Tot cho MVP 1v1 | Chua dung hoan toan luat co tuong chuan | Cao | L |
| Tien Len Mien Nam | Core rule kha day | Can audit edge case va room polish | Trung binh | M-L |
| Blackjack | Choi duoc | Chua dung day du blackjack table flow | Cao | M-L |
| Quiz | MVP room da on | Thieu timer/content/rule config | Trung binh | M |
| Typing Battle | MVP race da on | Thieu countdown/WPM/anti-cheat | Trung binh | M |
| Minesweeper | Local client-side kha day du | Gan dung classic local | Thap-Trung binh | S-M |
| Puzzle Pack | Local mini-games chay duoc | Gan dung casual gameplay | Thap-Trung binh | M |
| Monopoly | Local engine lon + room MVP | Chua day du luat va multiplayer authoritative | Rat cao | XL |

## 3. Uu tien chung de lam truoc

### 3.1 Nhom uu tien cao nhat

- `Monopoly`
- `Chess`
- `Xiangqi`
- `Blackjack`

Ly do:

- day la cac game co nhieu luat chuan, va hien con do lech ro giua gameplay that va implementation.
- neu khong chot rule som, UI/room/persistence lam sau se phai sua lai nhieu.

### 3.2 Nhom uu tien tiep theo

- `Tien Len`
- `Quiz`
- `Typing`
- `Caro`

Ly do:

- da co engine/flow su dung duoc, can nang cap de thanh san pham on dinh.

### 3.3 Nhom polish cuoi

- `Minesweeper`
- `Puzzle Pack`

Ly do:

- local casual games da chay duoc, chu yeu can luu state, xep hang, UX, content va polish.

## 4. Phan tich va ke hoach theo tung game

## 4.1 Caro

### Gameplay chuan can dat

- ban co kich thuoc hop ly cho Caro/Gomoku (`15x15` hoac configurable)
- luat thang 5 lien tiep ro rang
- neu chon bien the Caro co chan hai dau / Renju thi phai khai bao ro va enforce dung
- online 2 nguoi, spectate, surrender, rematch, reconnect
- lich su van dau va score/ranking neu la phong rank

### Hien trang code

- room service: `src/main/backend/java/com/game/hub/games/caro/service/GameRoomService.java`
- websocket controller: `src/main/backend/java/com/game/hub/games/caro/websocket/GameWebSocketController.java`
- bot: `src/main/backend/java/com/game/hub/games/caro/controller/BotController.java`
- da co:
  - online room 2 nguoi
  - spectate
  - guest mode
  - chat room
  - surrender
  - history + score update cho room rank
  - bot easy/hard
- hien dang dung `BOARD_SIZE = 10`

### Do lech / van de

- `10x10` khong phu hop voi ban Caro/Gomoku pho bien.
- chua co tai lieu chot bien the luat:
  - 5 lien tiep tu do
  - chan hai dau
  - Renju foul rules
- room flow chua thay ro:
  - rematch/ready check
  - reconnect vao dung state
  - clock/timeout

### Ke hoach lap trinh

`Dot 1 - Chot rule contract`

- quyet dinh ro target:
  - `Caro tu do`
  - hay `Gomoku/Renju`
- doi board size thanh config room setting, default `15x15`
- viet test matrix cho:
  - horizontal / vertical / diagonal win
  - blocked-end rule neu bat
  - draw/full-board

`Dot 2 - Room completeness`

- them `rematch`
- them `reconnect` vao room dang choi
- them `turn timer` va `leave/disconnect policy`

`Dot 3 - Product polish`

- replay move list
- room option: board size, ranked/unranked, first move option
- bot skill tuning

### Definition of done

- chot duoc mot rule set ro rang
- board size va win detection khop rule set do
- online room co rematch va reconnect
- co unit/integration test cho all win edge cases

## 4.2 Chess

### Gameplay chuan can dat

- du 6 loai quan va nuoc di hop le
- `check`, `checkmate`, `stalemate`
- `castling`
- `en passant`
- `promotion` day du
- draw rules:
  - threefold repetition
  - 50-move rule
  - insufficient material
- online 1v1 + spectate + reconnect + rematch
- notation / lich su nuoc di co cau truc

### Hien trang code

- room service: `src/main/backend/java/com/game/hub/games/chess/service/ChessOnlineRoomService.java`
- move rules: `src/main/backend/java/com/game/hub/games/chess/service/ChessMoveRules.java`
- websocket: `src/main/backend/java/com/game/hub/games/chess/websocket/ChessWebSocketController.java`
- da co:
  - join room / spectate
  - move validation co ban
  - turn enforcement
  - check / checkmate / stalemate
  - promotion
  - surrender / reset
  - offline + bot page

### Do lech / van de

- `ChessMoveRules` hien khong co:
  - castling
  - en passant
  - threefold repetition
  - 50-move rule
  - insufficient material
- move history hien van la text log, chua du de suy FEN/PGN chuan.
- chua co game clock / timeout / reconnect state.

### Ke hoach lap trinh

`Dot 1 - Rule correctness`

- mo rong board state de luu:
  - castling rights
  - en passant target
  - half-move clock
  - full-move number
- bo sung move generator cho:
  - king-side / queen-side castling
  - en passant
- chot promotion UI va backend validation cho `Q/R/B/N`

`Dot 2 - Draw engine`

- them state hash / FEN fingerprint
- detect threefold repetition
- detect 50-move rule
- detect insufficient material

`Dot 3 - Match UX`

- rematch
- reconnect vao van dang choi
- chess clock
- export PGN / import FEN cho debug

### Definition of done

- move rules dat toi thieu bang chess online thong thuong
- khong con mat luat chuan co ban
- test du cho castling, en passant, promotion, mate, stalemate, draw rules

## 4.3 Xiangqi

### Gameplay chuan can dat

- di quan dung luat co tuong
- enforce:
  - palace
  - river
  - horse leg
  - elephant eye
  - cannon screen
  - flying general
- `check`, `mate`
- xu ly dung luat cho tinh huong het nuoc di
- repetition / perpetual check / perpetual chase theo ruleset da chon

### Hien trang code

- room service: `src/main/backend/java/com/game/hub/games/xiangqi/service/XiangqiOnlineRoomService.java`
- move rules: `src/main/backend/java/com/game/hub/games/xiangqi/service/XiangqiMoveRules.java`
- websocket: `src/main/backend/java/com/game/hub/games/xiangqi/websocket/XiangqiWebSocketController.java`
- da co:
  - online 1v1
  - spectate
  - turn enforcement
  - move legality co ban
  - flying general
  - surrender / reset

### Do lech / van de

- service hien dang coi `khong con nuoc hop le va khong bi chieu` la `hoa`.
- trong co tuong chuan, ben den luot ma khong co nuoc hop le thuong bi tinh la `thua`, khong phai hoa.
- chua co repetition/perpetual chase/perpetual check adjudication.
- chua co reconnect / rematch / time control.

### Ke hoach lap trinh

`Dot 1 - Sua luat adjudication`

- doi xu ly `no legal move` theo dung co tuong target ruleset
- viet test cho:
  - flying general win
  - checkmate
  - bi khoa het nuoc

`Dot 2 - Repetition rules`

- luu fingerprint board + current turn
- detect loop state
- chot policy:
  - perpetual check
  - perpetual chase
  - manual adjudication hay auto-loss

`Dot 3 - Match UX`

- rematch
- reconnect
- time control
- notation lich su nuoc di co cau truc hon

### Definition of done

- dung luat co tuong o cac tinh huong ket van
- khong con tra ket qua hoa sai luat
- co test regression cho repetition va no-legal-move

## 4.4 Tien Len Mien Nam

### Gameplay chuan can dat

- room 4 nguoi
- chia 13 la / 3 bich di truoc
- danh bo hop le:
  - rac, doi, sam, tu quy, sanh, doi thong
- pass / reset vong
- chat 2 / chat hang
- toi trang
- tinh `thoi 2`, `thoi hang`, `cong`
- ket van, settlement, rematch

### Hien trang code

- room service: `src/main/backend/java/com/game/hub/games/cards/tienlen/service/TienLenRoomService.java`
- websocket: `src/main/backend/java/com/game/hub/games/cards/tienlen/websocket/TienLenWebSocketController.java`
- da co kha nhieu:
  - 4 nguoi
  - auto-fill bot
  - 13 la moi nguoi
  - 3S mo dau
  - pass / round reset
  - double straight
  - chat 2
  - toi trang
  - penalty settlement
  - bot turn

### Do lech / van de

- core rule engine da kha day, nhung can audit toan bo edge case de tranh sai luat.
- chua thay spectator flow.
- reconnect/rematch/room recovery chua ro rang.
- can xac nhan toan bo business rules TLMN dang dung:
  - thang trang tie-break
  - den bai / bo luot / roi phong giua tran
  - tat ca case `thoi`

### Ke hoach lap trinh

`Dot 1 - Rule audit`

- lap bang test rule cho:
  - moi loai combination
  - combo beat matrix
  - toi trang
  - thoi 2 / thoi hang / cong
  - roi phong giua van
- tao doc `rule contract` rieng cho TLMN de tranh sua chay.

`Dot 2 - Room completeness`

- rematch
- reconnect
- co the them spectator neu muon stream/xem ban
- room setting: bot fill on/off, auto-start delay

`Dot 3 - UX polish`

- scoreboard nhieu van
- history tung van
- bot difficulty tuning

### Definition of done

- core TLMN duoc test rat day
- room online on dinh khi co bot va khi human roi phong
- rematch/reconnect su dung duoc

## 4.5 Blackjack

### Gameplay chuan can dat

- multi-player table theo seat order
- bet -> initial deal -> player turns -> dealer turn -> settlement
- natural blackjack / bust / push
- `double down`
- `split`
- `insurance`
- `surrender`
- dealer rule config:
  - stand/hit soft 17
  - blackjack payout

### Hien trang code

- room logic: `src/main/backend/java/com/game/hub/games/cards/blackjack/logic/BlackjackRoom.java`
- raw socket: `src/main/backend/java/com/game/hub/games/cards/blackjack/websocket/BlackjackSocket.java`
- da co:
  - create / join / spectate
  - bet
  - hit / stand / double
  - dealer turn
  - natural blackjack
  - push / win / lose

### Do lech / van de

- chua co `split`
- chua co `insurance`
- chua co `surrender`
- hien tai model cho phep nhieu player cung act trong `PLAYER_TURN`, chua enforce `seat order` theo table blackjack chuan.
- chua co explicit `ready / next round` flow va room host control.

### Ke hoach lap trinh

`Dot 1 - Rule contract`

- chot bo luat table:
  - so bo bai
  - blackjack payout
  - dealer soft 17
  - split toi da bao nhieu tay
  - insurance bat/tat
  - surrender bat/tat

`Dot 2 - Turn engine`

- them current seat acting
- chi cho nguoi dung turn hien tai hit/stand/double/split/surrender
- sau cung moi sang dealer turn

`Dot 3 - Missing actions`

- split hand
- insurance
- surrender
- next round / ready state

### Definition of done

- table flow dung thu tu blackjack chuan
- co test cho split/insurance/surrender
- multiplayer khong con act song song sai luat

## 4.6 Quiz

### Gameplay chuan can dat

- tao room / join / spectate
- host start
- question pack co category / difficulty / amount
- timer tung cau hoi
- score formula ro rang
- result board / highscores / rematch

### Hien trang code

- controller: `src/main/backend/java/com/game/hub/games/quiz/controller/QuizController.java`
- room logic: `src/main/backend/java/com/game/hub/games/quiz/logic/QuizRoom.java`
- socket: `src/main/backend/java/com/game/hub/games/quiz/websocket/QuizSocket.java`
- da co:
  - create / join / spectate / leave
  - host start
  - score per answer
  - highscores
  - room state va question number

### Do lech / van de

- question bank hien hard-code 4 cau trong `QuizService`.
- khong co timer thuc su trong room flow.
- khong co category pack / round settings / random shuffle.
- score hien rat MVP, chua co toc do/thoi gian/do kho.

### Ke hoach lap trinh

`Dot 1 - Content architecture`

- tach question bank sang DB hoac JSON packs
- them category / difficulty / active flag
- cho room config so cau hoi

`Dot 2 - Turn/timer flow`

- them timer tung cau
- auto-submit/no-answer khi het gio
- lock answer sau khi gui

`Dot 3 - Product polish`

- rematch
- room settings UI
- result screen chi tiet
- moderation/import question packs

### Definition of done

- khong con phu thuoc 4 cau hard-code
- moi room co timer va config round
- co test cho timer, host start, scoring, final ranking

## 4.7 Typing Battle

### Gameplay chuan can dat

- lobby cho 2-4 nguoi
- countdown bat dau
- race theo cung mot doan text
- `WPM`, `accuracy`, `finish time`
- bang ket qua
- rematch / next text / reconnect

### Hien trang code

- room logic: `src/main/backend/java/com/game/hub/games/typing/logic/TypingRoom.java`
- socket: `src/main/backend/java/com/game/hub/games/typing/websocket/TypingSocket.java`
- service: `src/main/backend/java/com/game/hub/games/typing/service/TypingService.java`
- da co:
  - create / join / leave
  - random text
  - progress update
  - accuracy
  - winner by first finisher

### Do lech / van de

- game auto start ngay khi du 2 nguoi, chua co countdown/lobby readiness.
- chua tinh `WPM`.
- chua co anti-cheat co ban.
- chua co rematch / next round state.
- chua co spectate.

### Ke hoach lap trinh

`Dot 1 - Race UX`

- countdown 3-2-1
- explicit ready/start
- WPM + CPM + finish time

`Dot 2 - Anti-cheat va room state`

- validate typed progression khong nhay bat hop ly
- reconnect vao room dang race
- rematch voi text moi

`Dot 3 - Product polish`

- spectate
- multiple round series
- lane/ranking UI dep hon

### Definition of done

- race co countdown, WPM, accuracy va result board
- room state ro rang waiting / countdown / playing / finished
- co test cho winner, rematch, reconnect

## 4.8 Minesweeper

### Gameplay chuan can dat

- beginner / intermediate / expert
- custom board
- first-click safe
- flag / question / clear
- flood reveal
- timer + mine counter
- best times / stats

### Hien trang code

- controller: `src/main/backend/java/com/game/hub/games/minesweeper/controller/MinesweeperController.java`
- UI chinh: `src/main/frontend/templates/minesweeper/index.html`
- portal page: `src/main/frontend/templates/games/minesweeper.html`
- da co:
  - preset level
  - local JS board
  - first-click safe
  - flag mode
  - win -> achievement
  - custom grid UI da xuat hien tren template

### Do lech / van de

- engine nam chu yeu o client-side page JS, chua co layer state/tai lieu ro.
- chua co persisted best time / difficulty stats chuan.
- mobile interaction va keyboard accessibility co the chua tot.
- neu muon daily challenge/seed thi chua co.

### Ke hoach lap trinh

`Dot 1 - On dinh classic mode`

- tach engine JS thanh module rieng
- viet test JS/unit cho mine placement, flood reveal, first-click safe
- xac nhan custom board hoat dong dung

`Dot 2 - Meta progression`

- best time theo level
- stats local + sync account
- replay seed / daily board neu can

`Dot 3 - UX polish`

- long-press mobile
- keyboard accessibility
- animation/audio nhe

### Definition of done

- classic mode chay on dinh va test duoc
- custom board + best time + stats co luu

## 4.9 Puzzle Pack

### Gameplay chuan can dat

`Jigsaw`

- random board
- placement progress
- timer / moves
- win detect

`Sliding Puzzle`

- size/difficulty
- always-solvable shuffle
- moves / timer / streak

`Sudoku`

- puzzle generation / validate / solve / mistakes
- difficulty tuning

`Word Puzzle`

- grid generation
- word list
- timer / found states

### Hien trang code

- controllers: `src/main/backend/java/com/game/hub/games/puzzle/controller/*`
- templates:
  - `src/main/frontend/templates/games/puzzle/jigsaw.html`
  - `src/main/frontend/templates/games/puzzle/sliding.html`
  - `src/main/frontend/templates/games/puzzle/sudoku.html`
  - `src/main/frontend/templates/games/puzzle/word.html`
- da co local gameplay cho ca 4 mini-game.

### Do lech / van de

- nhieu logic dang nam trong template JS inline, kho test va kho maintain.
- chua co save/resume.
- chua co seed/daily challenge/leaderboard.
- Sudoku chua co content pipeline ro rang cho nhieu muc do kho.
- Jigsaw/Word co the chua co them content pack/image pack.

### Ke hoach lap trinh

`Dot 1 - Tach engine`

- tach JS inline cua tung puzzle thanh file/module rieng
- viet unit test cho generator/shuffle/validate

`Dot 2 - Content va persistence`

- save local progress
- sync account state
- seed/daily challenge neu can
- puzzle content packs

`Dot 3 - Product polish`

- leaderboard / best time
- achievements chi tiet theo sub-game
- challenge modes

### Definition of done

- 4 mini-game co engine tach rieng, de test
- save/resume hoat dong
- difficulty/content khong con hard-code tan man trong template

## 4.10 Monopoly

### Gameplay chuan can dat

- lobby room:
  - create / join / leave
  - host start
  - token khong trung
- board 40 o dung rule
- roll 2 dice, double, 3 doubles -> jail
- property / railroad / utility / tax / chance / community / jail / free parking / go to jail
- buy / skip / auction
- rent / mortgage / unmortgage
- build/sell house/hotel dung rule even-build
- jail flow day du
- trade
- bankruptcy
- save/load
- reconnect
- backend-authoritative online state

### Hien trang code

- room service: `src/main/backend/java/com/game/hub/games/monopoly/service/MonopolyRoomService.java`
- engine: `src/main/backend/java/com/game/hub/games/monopoly/service/MonopolyGameEngine.java`
- room API: `src/main/backend/java/com/game/hub/games/monopoly/controller/MonopolyRoomApiController.java`
- client UI: `src/main/frontend/static/js/monopoly-game.js`
- da co:
  - local 2-4 nguoi
  - room create/join/leave/token/start
  - board 40 o
  - chance/community
  - rent
  - jail
  - build/sell house
  - mortgage/unmortgage
  - debt/bankruptcy
  - room action API cho:
    - roll
    - buy
    - skip purchase
    - end turn
    - pay bail
    - use escape card
    - declare bankruptcy

### Do lech / van de

- room mode chua ho tro server-side cho:
  - trade
  - auction
  - house/hotel build flow day du
  - mortgage flow day du
  - save/load
  - reconnect
- room action API hien van chua bao toan full action surface cua local engine.
- sync state va authoritative logic van dang trong qua trinh chuyen doi.
- day la game co complexity lon nhat trong repo.

### Ke hoach lap trinh

`Dot 1 - Rule contract va state model`

- dong bang schema `GameRoom`, `Player`, `BoardCell`, `Card`, `Debt`, `Auction`, `TradeOffer`
- chot room settings:
  - starting cash
  - pass GO amount
  - free parking rule
  - auction on/off
  - turn timeout

`Dot 2 - Backend-authoritative action surface`

- bo sung action commands cho:
  - build
  - sell house
  - mortgage
  - unmortgage
  - auction
  - trade
  - accept/reject/counter
- khong de client tu publish snapshot de quyet dinh luat

`Dot 3 - Missing gameplay pillars`

- auction full flow
- trade full flow
- save/load
- reconnect restore
- timeout/disconnect policy

`Dot 4 - Product polish`

- bot
- replay/log
- room series/history
- better UI/animation

### Definition of done

- online room mode khong phu thuoc vao client-side rule decision
- auction/trade/jail/build/mortgage co API + tests day du
- reconnect/save-load hoat dong

## 5. Ke hoach thuc hien theo dot

## Dot A - Chot rule contracts

- Caro: xac dinh variant chinh thuc
- Chess: chot full FIDE-lite scope
- Xiangqi: chot ruleset adjudication
- Blackjack: chot house rules
- Tien Len: dong bang TLMN rulebook noi bo
- Monopoly: dong bang domain model + settings

Deliverable:

- 1 file rule contract / game
- test matrix edge case

## Dot B - Sua core rule engines

- Chess
- Xiangqi
- Blackjack
- Monopoly
- Caro neu doi board size/rule variant

Deliverable:

- unit tests rules
- integration tests room flow khong do vo sau khi sua

## Dot C - Hoan thien room/session flow

- rematch
- reconnect
- timer / timeout
- spectate neu can
- ready/start flow ro rang

Deliverable:

- room lifecycle chung on dinh tren toan bo game online

## Dot D - Content / progression / polish

- Quiz packs
- Typing WPM + rematch
- Minesweeper stats
- Puzzle save/resume
- history/replay/export cho cac game can

## 6. Cross-game workstreams can lam dung chung

### 6.1 Unified room foundation

Nen co abstraction dung chung cho cac game online:

- room metadata
- player join/leave/reconnect
- spectator
- rematch
- timeout
- host permissions

### 6.2 Unified match result pipeline

Mot service chung cho:

- history
- achievements
- ranking
- result snapshot

### 6.3 Shared testing strategy

Moi game online nen co 3 lop test:

1. rule engine test
2. room service test
3. websocket/api integration test

### 6.4 Shared client state conventions

Can thong nhat JSON payload:

- `roomId`
- `gameState`
- `currentTurnUserId`
- `statusMessage`
- `winnerUserId`
- `version`
- `canAct`

## 7. Thu tu de xuat de code tiep

1. `Monopoly`
   - vi dang do va anh huong lon nhat den roadmap
2. `Chess`
   - bo sung castling/en passant/draw rules
3. `Xiangqi`
   - sua adjudication cho dung luat
4. `Blackjack`
   - seat order + split/insurance/surrender
5. `Tien Len`
   - audit edge case + reconnect/rematch
6. `Quiz`
   - timer + content pack
7. `Typing`
   - countdown + WPM + rematch
8. `Caro`
   - chot variant + rematch/reconnect
9. `Minesweeper`
   - best times + stats
10. `Puzzle Pack`
   - tach engine + save/resume

## 8. Ket luan

Neu nhin theo gameplay dung chuan:

- `Tien Len` la game co core rule da tien gan muc hoan chinh nhat.
- `Caro`, `Quiz`, `Typing`, `Minesweeper`, `Puzzle` da co MVP cho nguoi dung thuc su choi duoc.
- `Chess`, `Xiangqi`, `Blackjack` can sua tiep de dung voi luat chuan hon.
- `Monopoly` la backlog lon nhat va can lam theo domain-driven phases, khong nen tiep tuc sua chong cheo o UI truoc khi khoa server action model.

Tai lieu nay nen duoc cap nhat lai sau moi dot lon de onboarding cho nguoi moi luon bam sat codebase thuc te.

