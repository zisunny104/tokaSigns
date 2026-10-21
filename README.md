# tokaSigns

告示牌管理插件：管理員可複製貼上告示牌文字、編輯牌面、建立點一下就能傳送或交易的功能牌，並批次檢查與更新全服告示牌。指令為 `/sign`（別名 `/signs`）。

## 主要功能

- **複製貼上**：複製一般告示牌的四行文字，貼到其他告示牌上。
- **編輯牌面**：點選告示牌後用表單修改；一般牌改四行文字，功能牌改代碼。
- **功能牌**：在告示牌上寫入類型與代碼，玩家點一下就能執行，例如傳送到區域、前往公共傳送點、購買或回收商品、開啟物品管理、顯示伺服器狀態。實際動作由對應插件提供。
- **維護檢查**：分批檢查或重繪全服告示牌，依伺服器負載自動調整速度，不會造成卡頓。

## 玩家指令

玩家直接點擊功能牌即可，沒有需要輸入的指令。`/sign help` 可查看說明。

## 管理指令

| 指令 | 用法 | 說明 | 權限 |
| --- | --- | --- | --- |
| `/sign help` | `/sign help` | 顯示說明 | `tokaSigns.help`（所有人） |
| `/sign cancel` | `/sign cancel` | 取消操作，保留剪貼簿 | `tokaSigns.cancel`（所有人） |
| `/sign status` | `/sign status` | 登記數、事件接管與保護服務狀態 | `tokaSigns.status` |
| `/sign edit` | `/sign edit` | 點選告示牌後編輯 | `tokaSigns.edit` |
| `/sign copy` | `/sign copy` | 點選一般告示牌複製文字 | `tokaSigns.copy` |
| `/sign paste` | `/sign paste` | 點選一般告示牌貼上文字 | `tokaSigns.paste` |
| `/sign set` | `/sign set <類型名稱或ID> <代碼>` | 點選告示牌設為功能牌；代碼不可含空白，狀態牌填 `-` | `tokaSigns.set` 及 `tokaSigns.type.<ID>` |
| `/sign inspect` | `/sign inspect <ID> [--force]` | 對照單一告示牌的位置、牌面與功能設定 | `tokaSigns.inspect` |
| `/sign check` | `/sign check <ID｜all> [--force]` | 分批唯讀檢查 | `tokaSigns.check` |
| `/sign refresh` | `/sign refresh <ID｜all> [--force]` | 分批依設定更新牌面 | `tokaSigns.refresh` |
| `/sign jobs` | `/sign jobs [cancel]` | 查看維護進度／取消後續處理 | `tokaSigns.jobs`、`tokaSigns.jobs.cancel` |

除標示「所有人」者，權限預設皆為 OP；`tokaSigns.admin` 視為擁有全部權限。`--force` 會載入既有區塊，另需 `tokaSigns.force`；破壞功能牌需 `tokaSigns.break`。

也可以直接在告示牌上寫四行建立功能牌（外框與 `|` 不用輸入，「留空」就不填字）：

（圖：寫好四行的功能告示牌範例）

| 第 1 行 | 第 2 行 | 類型 | 權限 |
| --- | --- | --- | --- |
| `tpr` | 區域代碼 | 區域傳送 | `tokaSigns.type.10` |
| `tpp` | 傳送點名稱 | 公共傳送點 | `tokaSigns.type.11` |
| `b` | 商品代碼 | 購買商品 | `tokaSigns.type.31` |
| `s` | 商品代碼 | 回收商品 | `tokaSigns.type.32` |
| `item` | 留空 | 物品管理入口 | `tokaSigns.type.33` |
| `status` | 留空 | 伺服器狀態 | `tokaSigns.type.41` |

## 設定（`config.yml`，變更後需重新啟動）

| key | 預設 | 說明 |
| --- | --- | --- |
| `actions.enabled` | `true` | 是否由本插件接管告示牌的點擊、建立、編輯與破壞；zero 的 sign 模組仍啟用時不會接管 |
| `messages.use-failed-seconds` | `5` | 告示牌無法使用時，tokaDisplay 頂部訊息的停留秒數（1–60）；未安裝 tokaDisplay 時改用聊天室，同一玩家 2 秒內只提示一次 |
| `maintenance.normal.max-records` | `32` | 一般維護每 tick 最多處理幾筆（1–128） |
| `maintenance.normal.budget-ms` | `2` | 一般維護每 tick 最多花費毫秒 |
| `maintenance.normal.min-tps` | `17` | TPS 低於此值就暫停維護 |
| `maintenance.force.max-records` / `budget-ms` / `min-tps` | `2` / `1` / `18` | `--force` 維護的對應上限，因為要載入區塊所以較保守 |

## 串接

其他插件於啟用後呼叫 `dev.toka.signs.Main` 的靜態方法，提供功能牌的實際動作。

| 方法 | 參數 | 用途 |
| --- | --- | --- |
| `registerPolicy` / `unregisterPolicy` | 擁有者名稱、`Policy` | 提供保護判斷（允許、拒絕、尚未就緒），沒有 Policy 時功能牌動作會被拒絕 |
| `registerAction` / `unregisterAction` | 類型 ID、`Action` | 提供該類型功能牌被點擊時的動作 |
| `registerDefinition` / `unregisterDefinition` | 類型 ID、`Definition` | 提供該類型建立／編輯時的牌面內容 |
| `serviceReady` | 無 | 確認本插件已就緒 |
| `isActionsEnabled` | 無 | 目前是否由本插件接管事件 |

## 疑難排解

- **功能牌點了沒反應？** 用 `/sign status` 確認事件接管為是、保護服務已就緒，且該類型的插件已啟用。
- **看不到某個類型可以設定？** 需要該類型的 `tokaSigns.type.<ID>` 權限，且提供該類型的插件已就緒。

目前版本 0.4.5

### Bedrock 指令候選

遊戲輸入 /sign 後，第一個參數會顯示本插件支援的子指令候選。候選只協助輸入；伺服器仍會逐項檢查權限。參數值目前由一般用法說明引導。

維護工作 efresh 目前只重建登記記錄所定義的四行文字；它不會猜測或更改告示牌文字色彩、發光等 NBT 樣式。
