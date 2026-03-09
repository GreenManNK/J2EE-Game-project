# External Game Modules

He thong da ho tro 2 nhom mo rong:

- `external-module`: game/module viet bang ngon ngu khac, co trang choi rieng hoac iframe.
- `external-api`: game/module chi cap API, frontend trong hub se goi qua gateway cung domain.

## Registry file

Mac dinh registry duoc luu tai:

- `config/external-game-modules.json`

Co the doi bang env:

- `APP_EXTERNAL_GAME_MODULES_FILE`

## Admin API

Tat ca endpoint ben duoi nam duoi `/Game/admin/**` va can quyen `ADMIN`.

### Xem danh sach module da cau hinh

`GET /Game/admin/game-modules/api`

### Tai backup registry hien tai

`GET /Game/admin/game-modules/api/export`

Tra ve file JSON attachment de backup truoc khi dung `replaceAll`.

### Import/upsert module tu JSON

`POST /Game/admin/game-modules/api/import`

### Preview module tu JSON ma khong ghi registry

`POST /Game/admin/game-modules/api/preview`

Body:

```json
{
  "replaceAll": false,
  "modules": [
    {
      "code": "python-blast",
      "displayName": "Python Blast",
      "shortLabel": "Py Blast",
      "description": "Game module viet bang Python.",
      "iconClass": "bi-rocket-takeoff-fill",
      "availableNow": true,
      "supportsOnline": true,
      "supportsOffline": false,
      "supportsGuest": true,
      "primaryActionLabel": "Mo module Python",
      "primaryActionUrl": "https://games.example.com/python-blast",
      "roadmapItems": [
        "Frontend embed duoc qua iframe",
        "API se di qua gateway cung domain"
      ],
      "sourceType": "external-module",
      "runtime": "python",
      "detailMode": "embed",
      "embedUrl": "https://games.example.com/python-blast/embed",
      "apiBaseUrl": "https://api.example.com/python-blast",
      "manifestUrl": "https://games.example.com/python-blast/manifest.json",
      "overrideExisting": false
    }
  ]
}
```

### Import tu manifest URL

`POST /Game/admin/game-modules/api/import-url`

### Preview manifest URL ma khong ghi registry

`POST /Game/admin/game-modules/api/preview-url`

Body:

```json
{
  "manifestUrl": "https://games.example.com/python-blast/manifest.json",
  "replaceAll": false
}
```

Manifest co the la:

- mot object module
- mot array module
- object gom `modules: [...]`

## Admin UI

Trang `/Game/admin` co khu `Quan ly module game ngoai`:

- `Nhap tu URL`: import that
- `Xem truoc URL`: tai va normalize manifest, nhung khong ghi registry
- `Kiem tra form`: normalize payload trong form, nhung khong ghi registry
- `Nhap toan bo preview`: ghi tat ca module dang preview vao registry sau khi Admin xac nhan
- `Thay toan bo registry khi nhap preview`: xoa registry cu va ghi lai theo batch dang import
- `Xu ly module trung code`: giu theo manifest, bat override cho tat ca, hoac tat override cho tat ca
- `Tai backup registry`: tai file JSON registry hien tai truoc khi import batch lon
- Preview hien ro:
  - module nao la `Them moi`
  - module nao la `Cap nhat`
  - module nao co `overrideExisting`
  - module nao se bi bo khi bat `replaceAll`
- Neu bat `replaceAll`, UI se hien hop xac nhan cuoi cung kem danh sach ma module se bi bo truoc khi import

Toan bo UI va API nay chi danh cho `ADMIN`.

### Xoa module

`DELETE /Game/admin/game-modules/api/{code}`

## Detail route

Sau khi import, module se xuat hien trong:

- `/Game/games`
- `/Game/games/{code}`

Neu module la external module, trang detail se hien:

- runtime / sourceType
- nut mo module ngoai
- iframe neu co `embedUrl`
- gateway API neu co `apiBaseUrl`

## API gateway cung domain

Neu module co `apiBaseUrl`, hub se mo gateway:

- `/Game/games/external/{code}/api/**`

Vi du:

- module `python-blast`
- `apiBaseUrl = https://api.example.com/python-blast`

Thi request:

- `GET /Game/games/external/python-blast/api/rooms?status=open`

Se duoc proxy toi:

- `GET https://api.example.com/python-blast/rooms?status=open`

## Truong quan trong

- `code`: bat buoc, route-safe, dang `a-z`, `0-9`, `-`
- `sourceType`: `external-module` hoac `external-api`
- `runtime`: `python`, `nodejs`, `go`, `dotnet`, `php`, ...
- `detailMode`: `embed`, `redirect`, `api`
- `embedUrl`: trang iframe neu muon nhung module
- `apiBaseUrl`: base URL cua API module de gateway proxy
- `overrideExisting`: `true` neu muon ghi de game native cung `code`

## Timeout

- `APP_EXTERNAL_GAME_MODULES_IMPORT_TIMEOUT`
- `APP_EXTERNAL_GAME_MODULES_PROXY_TIMEOUT`
