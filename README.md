# tokaSigns

告示牌複製貼上、編輯、功能牌設定、批次檢查與重繪。使用 `/sign` 或 `/signs` 操作。

## 主要功能

- **複製貼上**：複製一般告示牌的四行文字，貼到其他告示牌上。
- **編輯牌面**：點選告示牌後編輯文字或功能代碼。
- **功能牌**：在告示牌上寫入類型與代碼，玩家點一下就能執行，例如傳送到區域、前往公共傳送點、購買或回收商品、開啟物品管理、顯示伺服器狀態。實際動作由對應插件提供。
- **維護檢查與修復**：`/sign check`、`/sign refresh`、`/sign repair` 以佇列分批檢查或重繪功能牌，依伺服器負載調整速度；修復可選類別或 `all`，並校正牌面文字與發光狀態。
  牌面更新會使用 provider 提供的「使用後」內容（最新區域、傳送點或商品資訊）；沒有專用更新格式時沿用新建牌面。

### Bedrock 指令候選

遊戲輸入 `/sign` 後，第一個參數會顯示本插件支援的子指令候選，包含 `repair` 與別名 `fix`。候選只協助輸入；伺服器仍會逐項檢查權限。其他參數依指令用法輸入。

## 玩家指令

玩家直接點擊功能牌即可，沒有需要輸入的指令。`/sign help` 可查看說明。

## 管理指令

| 指令 | 用法 | 說明 | 權限 |
| --- | --- | --- | --- |
| `/sign help` | `/sign help` | 顯示說明 | `tokaSigns.help`，所有人 |
| `/sign cancel` | `/sign cancel` | 取消操作，保留剪貼簿 | `tokaSigns.cancel`，所有人 |
| `/sign status` | `/sign status` | 登記數、事件接管與保護服務狀態 | `tokaSigns.status` |
| `/sign edit` | `/sign edit` | 點選告示牌後編輯 | `tokaSigns.edit` |
| `/sign copy` | `/sign copy` | 點選一般告示牌複製文字 | `tokaSigns.copy` |
| `/sign paste` | `/sign paste` | 點選一般告示牌貼上文字 | `tokaSigns.paste` |
| `/sign set` | `/sign set <類型名稱或ID> <代碼>` | 點選告示牌設為功能牌；代碼不可含空白，狀態牌填 `-` | `tokaSigns.set` 及 `tokaSigns.type.<ID>` |
| `/sign inspect` | `/sign inspect <ID> [--force]` | 對照單一告示牌的位置、牌面與功能設定 | `tokaSigns.inspect` |
| `/sign check` | `/sign check <ID｜all> [--force]` | 分批唯讀檢查 | `tokaSigns.check` |
| `/sign refresh` | `/sign refresh <ID｜all> [--force]` | 分批依設定更新牌面文字與發光狀態 | `tokaSigns.refresh` |
| `/sign repair`、`/sign fix` | `/sign repair <類別｜all> [--force]` | 排隊檢查並修復指定類別或全部功能牌 | `tokaSigns.repair`，預設 OP |
| `/sign jobs` | `/sign jobs [cancel]` | 查看維護進度／取消後續處理 | `tokaSigns.jobs`、`tokaSigns.jobs.cancel` |

修復類別可填 `tpr`、`tpp`、`tpl`、`tps`、`pay`、`buy`、`sell`、`item`、`status` 或類型 ID；`all` 會檢查所有已登記功能牌。

`fix`／`repair`／`refresh` 每次都會重新套用發光狀態並重送牌面，即使伺服器記錄已相符，也不會因此略過客戶端更新。結果中的 `UPDATED` 是資料有修正，`RESENT` 是狀態已相符但再次送出；這表示伺服器已發送，不代表客戶端已確認收到。一般工作略過未載入區塊，完成時會列出 `CHUNK_UNLOADED` 等原因；需要處理所有已掛載世界內的既有區塊時使用 `/sign fix all --force`，仍依效能分批執行。

除標示「所有人」者，權限預設皆為 OP；`tokaSigns.admin` 視為擁有全部權限。`--force` 會載入既有區塊，另需 `tokaSigns.force`；破壞功能牌需 `tokaSigns.break`。

也可以依下表填寫告示牌建立功能牌，「留空」欄位不需填字：

| 第 1 行 | 第 2 行 | 類型 | 權限 |
| --- | --- | --- | --- |
| `tpr` | 區域代碼 | 區域傳送 | `tokaSigns.type.10` |
| `tpp` | 傳送點名稱 | 公共傳送點 | `tokaSigns.type.11` |
| `tpl` | tokaLand 領地 UUID | 領地傳送（顯示名稱由 tokaLand 即時提供；動作經 tokaPortal） | `tokaSigns.type.12` |
| `b` | 商品代碼 | 購買商品 | `tokaSigns.type.31` |
| `s` | 商品代碼 | 回收商品 | `tokaSigns.type.32` |
| `item` | 留空 | 物品管理入口 | `tokaSigns.type.33` |
| `status` | 留空 | 伺服器狀態 | `tokaSigns.type.41` |

## 設定 config.yml

變更後需重新啟動。

| key | 預設 | 說明 |
| --- | --- | --- |
| `actions.enabled` | `true` | 是否由本插件接管告示牌的點擊、建立、編輯與破壞；zero 的 sign 模組仍啟用時不會接管 |
| `messages.use-failed-seconds` | `5` | 告示牌無法使用時，tokaDisplay 頂部訊息的停留秒數（1–60）；未安裝 tokaDisplay 時改用聊天室，同一玩家 2 秒內只提示一次 |
| `signs.glow` | `true` | 讓功能牌文字發光並顯示外框；建立與修復會同步狀態給線上玩家 |
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
| `registerDisplayDataProvider` / `unregisterDisplayDataProvider` | 類型 ID、`DisplayDataProvider` | 提供 JSON 業務資料；牌面文字與色碼由 tokaSigns 按舊版格式產生 |
| `registerDefinition` / `unregisterDefinition` | 類型 ID、`Definition` | 相容舊版或自訂功能牌格式；內建類型 10、11、12、31、32、33 由 tokaSigns 格式器優先處理 |
| `serviceReady` | 無 | 確認本插件已就緒 |
| `isActionsEnabled` | 無 | 目前是否由本插件接管事件 |

`DisplayDataProvider.resolve` 回傳 JSON 字串。傳送類型 10、11 使用 `info`，類型 12 使用 `name`；購買／回收類型 31、32 使用 `itemName`、`quantity`、`amount`。provider 只負責提供仍由其他插件管理的資料，不應回傳已排版的告示牌文字。tokaSigns 產生與舊版成功使用後相同的動態版型，修復時也會重新讀取 provider 資料。類型 12 以 claim UUID 作為代碼、由 tokaLand 供應名稱、由 tokaPortal 執行傳送；任一 API 不可用時該類型不會出現在設定候選中。Zero 仍只依交接驗證結果決定何時停用舊事件與指令。

## 疑難排解

- **功能牌點了沒反應？** 用 `/sign status` 確認事件接管為是、保護服務已就緒，且該類型的插件已啟用。
- **看不到某個類型可以設定？** 需要該類型的 `tokaSigns.type.<ID>` 權限，且提供該類型的插件已就緒。
