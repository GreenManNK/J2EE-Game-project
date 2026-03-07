# Account Sync API

API nay dung de tao moi, cap nhat, va dong bo toan bo du lieu lien quan toi tai khoan nguoi choi bang Postman hoac script backend.

## Cau hinh

Dat API key truoc khi chay ung dung:

```powershell
$env:APP_SYNC_API_KEY="replace-with-a-secret"
./mvnw spring-boot:run
```

Neu `APP_SYNC_API_KEY` de trong, endpoint sync se bi tat va tra ve `503`.

## Headers

- `Content-Type: application/json`
- `X-API-Key: <your-secret>`

Co the dung `Authorization: Bearer <your-secret>` thay cho `X-API-Key`.

## Endpoint

### 1. Tao moi hoac cap nhat tai khoan

`POST /Game/api/account-sync/accounts`

Mac dinh `replaceRelatedData = true`:
- preferences, stats, achievements, lich su dau, friendship trong payload se ghi de vao database cho tai khoan do
- neu muon merge them du lieu moi ma khong xoa du lieu cu, gui `"replaceRelatedData": false`

Body mau:

```json
{
  "email": "player-sync@example.com",
  "displayName": "Player Sync",
  "password": "Pass@123",
  "avatarPath": "/uploads/avatars/default-avatar.jpg",
  "emailConfirmed": true,
  "role": "User",
  "score": 120,
  "highestScore": 220,
  "online": false,
  "preferences": {
    "themeMode": "dark",
    "language": "vi",
    "sidebarDesktopVisibleByDefault": true,
    "sidebarMobileAutoClose": true,
    "homeMusicEnabled": false,
    "toastNotificationsEnabled": true,
    "showOfflineFriendsInSidebar": true,
    "autoRefreshFriendList": true,
    "friendListRefreshMs": 10000
  },
  "gameStats": {
    "chess-offline": {
      "whiteWins": 5,
      "blackWins": 3,
      "draws": 1
    },
    "xiangqi-offline": {
      "redWins": 2,
      "blackWins": 1,
      "draws": 0
    },
    "minesweeper": {
      "totalGames": 12,
      "wins": 8,
      "losses": 4,
      "bestTimes": {
        "easy": 42,
        "medium": 80
      }
    }
  },
  "gamesBrowserState": {
    "favorites": ["caro", "chess"],
    "recentGames": [
      {
        "code": "chess",
        "name": "Chess",
        "at": 1762291200000
      },
      {
        "code": "caro",
        "name": "Caro",
        "at": 1762291000000
      }
    ]
  },
  "puzzleCatalogState": {
    "favorites": ["sudoku", "jigsaw"],
    "ratings": {
      "sudoku": 5,
      "jigsaw": 4
    },
    "recentCodes": ["jigsaw", "sudoku"]
  },
  "achievements": [
    {
      "achievementName": "Bac",
      "achievedAt": "2026-03-08T09:00:00"
    },
    {
      "achievementName": "Chien thang khong tuong",
      "achievedAt": "2026-03-08T10:00:00"
    }
  ],
  "gameHistory": [
    {
      "gameCode": "caro",
      "player1Id": "player-sync-id",
      "player2Id": "friend-user-id",
      "firstPlayerId": "player-sync-id",
      "totalMoves": 22,
      "winnerId": "player-sync-id",
      "playedAt": "2026-03-08T11:00:00"
    }
  ],
  "friendships": [
    {
      "requesterId": "player-sync-id",
      "addresseeId": "friend-user-id",
      "accepted": true
    }
  ],
  "replaceRelatedData": true
}
```

Ghi chu:
- Neu cap nhat tai khoan da co, co the gui `userId` hoac `email`
- Neu tao moi, bat buoc co `email` va `password` hoac `passwordHash`
- `userId` trong `gameHistory` / `friendships` phai ton tai truoc trong database
- `gameStats` chi nhan 3 key:
  - `chess-offline`
  - `xiangqi-offline`
  - `minesweeper`
- `puzzleCatalogState` dung de dong bo:
  - `favorites`
  - `ratings`
  - `recentCodes`
- `gamesBrowserState` dung de dong bo:
  - `favorites`
  - `recentGames`
- Khi frontend dang nhap va luu thao tac Puzzle hub, he thong gui du lieu nay theo che do replace (`merge = false`) de bo bookmark/rating cu dung cach
- Khi frontend dang nhap va luu `games/index`, he thong gui `gamesBrowserState` theo che do replace (`merge = false`) de dong bo chinh xac favorite/recent cua tai khoan

### 2. Doc snapshot theo userId

`GET /Game/api/account-sync/accounts/{userId}`

### 3. Doc snapshot theo email

`GET /Game/api/account-sync/accounts/by-email?email=player-sync@example.com`

### 4. Import nhieu tai khoan trong mot request

`POST /Game/api/account-sync/accounts/bulk`

Body mau:

```json
{
  "continueOnError": false,
  "accounts": [
    {
      "userId": "bulk-a",
      "email": "bulk-a@example.com",
      "displayName": "Bulk A",
      "password": "Pass@123",
      "replaceRelatedData": true
    },
    {
      "userId": "bulk-b",
      "email": "bulk-b@example.com",
      "displayName": "Bulk B",
      "password": "Pass@123",
      "friendships": [
        {
          "requesterId": "bulk-b",
          "addresseeId": "bulk-a",
          "accepted": true
        }
      ],
      "replaceRelatedData": true
    }
  ]
}
```

Ghi chu:
- `continueOnError = false`: loi 1 account se rollback ca batch
- `continueOnError = true`: account loi bi bo qua, account hop le van duoc luu
- Batch import xu ly 2 pha: tao/cap nhat account truoc, roi moi ghi friendship/history/achievement

### 5. Export nhieu account snapshot

`GET /Game/api/account-sync/accounts/export`

Co the goi:
- Export tat ca: `GET /Game/api/account-sync/accounts/export`
- Export theo userId: `GET /Game/api/account-sync/accounts/export?userId=bulk-a&userId=bulk-b`
- Export theo email: `GET /Game/api/account-sync/accounts/export?email=bulk-a@example.com`

## Guest -> Account

Sau khi dang nhap, xac thuc email, hoac OAuth2 login thanh cong, frontend se tu dong day du lieu guest len database qua:

`POST /Game/account/migrate-guest-data`

Endpoint nay dung session dang nhap hien tai, khong dung API key. Du lieu duoc chuyen:
- preferences tu localStorage
- stats offline: chess, xiangqi, minesweeper
- games browser: favorites, recent games
- puzzle catalog: favorites, ratings, recent codes

Neu muon frontend doc/ghi truc tiep state giao dien theo session dang nhap hien tai:
- `GET /Game/account/games-browser-state`
- `POST /Game/account/games-browser-state`
- `GET /Game/account/puzzle-catalog-state`
- `POST /Game/account/puzzle-catalog-state`

## Postman

1. Tao request moi.
2. Chon method `POST` hoac `GET`.
3. Nhap URL theo endpoint tren.
4. Them header `X-API-Key`.
5. Chon tab `Body -> raw -> JSON` voi request `POST`.
6. Gui request va kiem tra field `success`.

Collection Postman mau: `docs/postman/account-sync.postman_collection.json`
