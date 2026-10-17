# tokaSigns

**tokaSigns 0.4.1** 是 Nukkit 告示牌管理插件：接管告示牌的點擊、建立、編輯與破壞，提供複製貼上、功能牌設定，以及告示牌檢查與重繪的維護工作。玩家指令為 `/sign`，別名 `/signs`。

## 安裝

- 需要 Java 21；已驗證 Nukkit PM1E 4511。
- 將 `tokaSigns-0.4.1.jar` 放入 `plugins/`，重新啟動伺服器。
- 選用相依（softdepend）：zero。zero 的 `sign` 模組仍啟用時，本插件不接管事件。
- 使用共用套件 tokaStorage 與 tokaI18n，皆已內嵌於 JAR；登記資料放在 `plugins/tokaSigns/storage/<格式>/`，預設 SQLite，改設定不會自動搬移資料。
- 傳送、交易等功能牌由對應插件透過 API（Policy、Action、Definition）提供。

## 指令與權限

| 指令 | 用途 | 權限（預設） |
| --- | --- | --- |
| `/sign help`、`/sign cancel` | 說明、取消待選取操作 | `tokaSigns.help`、`tokaSigns.cancel`（所有人） |
| `/sign status` | 查看登記數與 provider 狀態 | `tokaSigns.status`（OP） |
| `/sign edit` | 點選告示牌後編輯（一般牌四行、功能牌代碼） | `tokaSigns.edit` |
| `/sign copy`、`/sign paste` | 複製貼上一般告示牌文字 | `tokaSigns.copy`、`tokaSigns.paste` |
| `/sign set <類型> <代碼>`（或 `set buy｜sell <商品代碼>`） | 建立或修改功能牌 | `tokaSigns.set` 及 `tokaSigns.type.<ID>` |
| `/sign inspect <ID> [--force]` | 檢查單一告示牌 | `tokaSigns.inspect` |
| `/sign check｜refresh <ID｜all> [--force]` | 排入唯讀檢查／重繪牌面 | `tokaSigns.check`、`tokaSigns.refresh` |
| `/sign jobs [cancel]` | 維護工作進度／取消 | `tokaSigns.jobs`、`tokaSigns.jobs.cancel` |

`--force` 另需 `tokaSigns.force`；`tokaSigns.break` 控制破壞功能牌；`tokaSigns.admin` 視為擁有全部權限（OP）。未標示者預設 OP。也可直接在告示牌填四行建立功能牌，格式見 `/sign help`。

## 設定（`config.yml`）

| key | 預設 | 說明 |
| --- | --- | --- |
| `language` | `zh-TW` | 語言 |
| `storage.backend` | `sqlite` | `sqlite`、`json`、`yaml` |
| `actions.enabled` | `true` | 是否接管告示牌事件 |
| `maintenance.normal.max-records` / `budget-ms` / `min-tps` | `32` / `2` / `17` | 一般維護每 tick 上限與 TPS 下限 |
| `maintenance.force.max-records` / `budget-ms` / `min-tps` | `2` / `1` / `18` | `--force` 維護的上限 |

維護預設不載入世界或區塊；`--force` 只載入既有區塊。

## 版本紀錄

- 0.4.1：文件整理。
- 0.4.0：所有玩家可見文字移入 tokaI18n `zh-TW` 語系檔。
- 0.3.0：新增 `inspect`、`check`、`refresh`、`jobs` 維護指令，依 TPS 自動調整處理量。
- 0.2.3：可直接填寫四行建立功能牌。
- 0.2.2：指令與功能牌類型依權限顯示與執行。

授權：MIT。
