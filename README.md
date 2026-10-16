# tokaSigns

Nukkit 告示牌服務，Java 21，版本 0.1.0。目的是讓 zero 裡的告示牌功能有獨立歸屬，整體的拆分進度只維護在 [zero 的拆分主文件](https://github.com/zisunny104/zero/blob/master/docs/ARCHITECTURE.md)。

## 目前做到的（首版）

- **資料副本與登記**：保存告示牌資料的副本，儲存格式可選 SQLite（預設）、JSON、YAML，由共用的 tokaStorage 提供。
- **匯入**：zero 1.7.18 可以把舊告示牌資料唯讀匯入這裡，來源 SQL 不更動。在 zero 的設定檔把 `signs.export-to-tokaSigns` 改成 `true` 後啟動，會自動匯入一次，成功後自動改回 `false`。
- **API**：其他插件可以註冊告示牌類型的處理者（`registerAction`）與授權者（`registerPolicy`），並呼叫 `activate` 觸發。預設關閉。
- `/signs` 查詢目前登記的筆數，權限 `tokaSigns.admin`，預設 OP。

## 還沒做到的

- **尚未接管舊事件**：點擊、建立、刪除告示牌的行為仍全部由 zero 處理。`actions.enabled` 預設是 `false`，而且 zero 的告示牌模組啟用時，這裡的 `activate` 一律不處理，避免兩邊重複觸發。
- 沒有在遊戲裡驗證過；尚未部署到正式伺服器。

## 設定

```yaml
storage:
  backend: sqlite   # 資料副本的儲存格式：sqlite、json 或 yaml
actions:
  enabled: false    # 是否讓這個插件處理告示牌的點擊，首版保持 false
```

## 編譯

需要 Java 21 與 Maven，核心以 provided 依賴，執行：

```powershell
./build.ps1 -Maven <mvn 的路徑> -Repository <本機 Maven 庫的路徑>
```

輸出在 `output/tokaSigns-0.1.0.jar`。

## 授權

MIT License，見 [LICENSE](LICENSE)。
