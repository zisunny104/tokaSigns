# tokaSigns

Nukkit 告示牌管理。專案沿用複數名稱 `tokaSigns`；玩家指令保留 `/sign`，`/signs` 為別名。

## 0.2.0

- 接管告示牌點擊、建立、編輯、破壞及 `/sign copy`、`/sign paste`。
- zero 的 `sign` 模組仍啟用時，不處理事件，避免兩套邏輯同時寫入。
- 每次都檢查 BlockEntitySign 是否存在；暫時缺少方塊實體只拒絕該次操作，不停用整個模組。
- copy／paste 只複製一般告示牌的文字，不複製業務功能，且不得操作受管理告示牌；需要保護 provider 授權，OP 也不能略過。
- 尊重其他插件已取消的事件。業務或保護 provider 缺少、拒絕或失敗時，保守拒絕該次操作。
- 狀態牌 type 41 原生每 25 ticks 更新已載入區塊中的登錄牌，顯示 TPS、載入及上傳／下載數值，不載入或掃描整個世界。

## 儲存

使用 tokaStorage 0.1.3；預設 SQLite，也支援 JSON、YAML。SQLite driver 隔離載入。檔案位於 `plugins/tokaSigns/storage/<backend>/`。改設定不會自行搬移既有資料，切換前請備份並使用外部工具確認讀回結果。

插件沒有舊資料搬遷、SQL 讀取或 zero 設定切換功能。交接由 zero 執行：匯出舊資料、呼叫通用 API、核對讀回、確認 provider，再以設定停用舊功能；新插件不自行改寫 zero 設定。

## 通用 API

所有 API 在伺服器主執行緒呼叫。`Main` 提供：

- `serviceReady()`、`readyForTypes(int[])`。
- `recordsJson()`、`replaceRecords(String)`：欄位 `id,x,y,z,level,type,code,info,ticks`；批次操作僅接受空目的地或完全相同資料，不覆蓋不同既有資料，寫入後讀回驗證。
- `upsertRecord(String)`、`removeRecord(int)`：一般 CRUD。
- `registerPolicy(String owner, Policy)`／`unregisterPolicy(String owner)`。
- `registerAction(int type, Action)`／`unregisterAction(int type)`。
- `registerDefinition(int type, Definition)`／`unregisterDefinition(int type)`。

`Policy.authorize(Player, SignRecord, String operation)` 回傳 ALLOW／DENY／UNAVAILABLE；operation 為 activate/create/edit/break/copy/paste。一般文字 copy/paste 使用 id=-1、type=0 的暫時記錄，不寫入儲存。

`Action.execute(Player, SignRecord)` 執行功能；`Definition.prepare(Player, SignRecord, String[] lines)` 回傳 4 行已確認文字，null 表示拒絕。新建立記錄 ID 由 registry 分配。`SignRecord` 保留業務代碼、說明、ticks 與未知 type，不將 code 當成後台指令執行。

type 10/11/31/32/33 等傳送、交易、物品功能由對應插件 provider 負責，並非此插件內建；type 54 等舊版未實作類型保留但不可執行。提供下述表單編輯與功能設定；不提供原生功能資料複製。

## 權限與建置

`tokaSigns.admin` 預設 OP，可由權限插件控制。業務使用與位置保護仍由 Policy 判定。

執行 `build.ps1`，JAR 放入本專案 `output` 與 `plugin-modernization/output`。建置不會部署 VPS。

完整拆分進度以 [zero 的架構文件](https://github.com/zisunny104/zero/blob/master/docs/ARCHITECTURE.md) 為準。

### 編輯與商店告示牌

`/sign edit` 後點選告示牌：一般牌提供四行文字表單；受管理牌只編輯功能代碼，由原 type 的 Definition 重新產生顯示，不直接修改業務標題。提交時重新檢查世界、已載入區塊、距離、方塊實體、文字／記錄是否已變更、管理權限及位置 Policy，並發出可取消的 SignChangeEvent。

`/sign set buy <商品代碼>`、`/sign set sell <商品代碼>`，或 `/sign set <type> <code>`，然後點選目標告示牌，可建立或修改功能牌。這取代舊 ShopBoxSetMode 專用列表；舊設定模式棄用，不再需要特殊商店模式。建立與修改都必須有 Definition、位置 Policy，其他保護插件取消 SignChangeEvent 時不寫入。實際交易仍由對應 Action 驗證商品、金額與玩家權限。

交接工具可先呼叫 `Main.isActionsEnabled()` 確認設定，不會自動改成啟用。

## 指令與損壞資料處理（0.2.1）

`/sign help`（別名 `/signs help`）列出所有指令與限制；`/sign status` 可於後台查接管、保護 provider 及可用類型；`/sign cancel` 取消待選取與待提交操作，保留剪貼簿。未知參數顯示說明。

一般牌保留四行編輯，功能牌編輯代碼。現在不自動修復 NBT：登記資料不包含全部原始文字、雙面文字及其他 NBT，建立空牌可能掩蓋遺失。後續先做唯讀診斷、原始備份與明確單牌修復，再考慮可完整重建類型的自動模式。預設不覆寫既有實體、不強制載入世界。

## 依權限顯示指令（0.2.2）

help 與執行使用相同節點：`tokaSigns.help`、`status`、`edit`、`copy`、`paste`、`set`、`cancel`、`break`；皆以前綴 `tokaSigns.` 組成完整名稱。help/cancel 預設所有人，其他預設 OP。既有 `tokaSigns.admin` 保留整體管理權。權限插件可將節點授予群組或玩家，不比對群組名稱；位置保護仍另行判斷。

功能牌另需 `tokaSigns.type.<正整數ID>`（已知類型預設 OP），help 只列出已授權且 Definition provider 就緒的類型 ID。未知擴充類型須明確授權或 admin。編輯功能牌需 edit＋類型權限，設定需 set＋類型權限；表單送出與點選時重新檢查。

`/sign set` 嚴格接受兩個參數：類型為已知名稱或正整數 ID，代碼為單一非空字串（最長 8192 字元，不含空白）；狀態牌可填 `-`。代碼對應商品／傳送點等的有效性由 Definition provider 驗證，不將所有代碼一律轉成數字。其他指令不接受額外參數。尚未提供輸入自動完成或全型別 API，這裡的型別為指令解析規約。

## 直接填寫四行建立功能牌（0.2.3）

統一語法：`<參數>` 必填、`[參數]` 選填；固定文字原樣輸入。遊戲中最外層以金色 `[ … ]` 表示整面告示牌，與內層選填標記區分。四行以 ` | ` 分隔，分隔符及參數標記不輸入告示牌。「留空」表示該行不能填字。目前這些格式沒有選填參數，不為了展示符號而新增可忽略的行。

- `tpr | <區域代碼> | 留空 | 留空`
- `tpp | <傳送點名稱> | 留空 | 留空`
- `b | <商品代碼> | 留空 | 留空`（購買）
- `s | <商品代碼> | 留空 | 留空`（回收）
- `item | 留空 | 留空 | 留空`
- `status | 留空 | 留空 | 留空`

help 將指令與告示牌輸入分區，依 set／type 權限及 Definition 註冊狀態列出格式。儲存後 provider 產生外觀與功能。第 3、4 行必須留空，避免默默丟棄文字；item/status 第 2 行也留空，其餘需有效代碼。功能牌已有記錄時用 `/sign edit` 編輯代碼。缺少權限、保護或 provider 時取消；Definition 已註冊不保證外部業務資料可用，提交仍會驗證。
