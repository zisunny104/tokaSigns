package dev.toka.signs;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.command.*;
import dev.toka.storage.*;
import java.util.*;

public final class Main extends PluginBase {
    public enum Decision { ALLOW,DENY,UNAVAILABLE }
    public interface Policy { Decision authorize(Player player,SignRegistry.SignRecord sign,String operation); }
    public interface Action { boolean execute(Player player,SignRegistry.SignRecord sign); }
    public interface Definition { String[] prepare(Player player,SignRegistry.SignRecord sign,String[] lines); }
    private static Main instance;
    private SignRegistry registry;
    private SignMaintenance maintenance;
    private Policy policy;
    private String policyOwner;
    private final Map<Integer,Action> actions=new HashMap<>();
    private final Map<Integer,Definition> definitions=new HashMap<>();
    public void onEnable(){
        saveDefaultConfig();
        try{
            var backend=Backend.valueOf(getConfig().getString("storage.backend","sqlite").toUpperCase(Locale.ROOT));
            registry=new SignRegistry(new StoreConfig(backend,getDataFolder().toPath().resolve("storage").resolve(backend.name().toLowerCase(Locale.ROOT)),1));
            actions.put(41,(p,r)->true);definitions.put(41,(p,r,lines)->statusLines());
            maintenance=new SignMaintenance(this);
            getServer().getScheduler().scheduleRepeatingTask(this,new cn.nukkit.scheduler.Task(){public void onRun(int tick){maintenance.tick();}},1);
            instance=this;getServer().getPluginManager().registerEvents(new SignEvents(this),this);
            getServer().getScheduler().scheduleRepeatingTask(this,new cn.nukkit.scheduler.Task(){public void onRun(int tick){updateStatusSigns();}},25);
            getLogger().info("§b載入告示牌共 §a"+registry.records().size()+" §b個");
        }catch(Exception error){throw new IllegalStateException("告示牌儲存無法載入；請保留原始資料",error);}
    }
    private static Main service(){
        if(instance==null||!instance.isEnabled())throw new IllegalStateException("tokaSigns unavailable");
        if(!instance.getServer().isPrimaryThread())throw new IllegalStateException("Sign API requires server thread");
        return instance;
    }
    public static boolean isActionsEnabled(){return service().getConfig().getBoolean("actions.enabled",true);}
    public static boolean serviceReady(){return instance!=null&&instance.isEnabled()&&instance.registry!=null;}
    public static boolean readyForTypes(int[] types){Main m=service();if(m.policy==null)return false;for(int t:types)if(!m.actions.containsKey(t))return false;return true;}
    public static SignRegistry getRegistry(){return service().registry;}
    public static void replaceRecords(String json)throws Exception{service().registry.replaceIfEmptyOrEqual(json);}
    public static String recordsJson(){return service().registry.recordsJson();}
    public static void upsertRecord(String json)throws Exception{service().registry.upsertRecord(json);}
    public static void removeRecord(int id)throws Exception{service().registry.removeRecord(id);}
    public static void registerPolicy(String owner,Policy policy){
        Main main=service();if(owner==null||owner.isBlank())throw new IllegalArgumentException("owner");
        if(main.policyOwner!=null&&!main.policyOwner.equals(owner))throw new IllegalStateException("A policy provider already exists");
        main.policyOwner=owner;main.policy=Objects.requireNonNull(policy);
    }
    public static void unregisterPolicy(String owner){Main m=service();if(Objects.equals(m.policyOwner,owner)){m.policyOwner=null;m.policy=null;}}
    public static void registerAction(int type,Action action){if(service().actions.putIfAbsent(type,Objects.requireNonNull(action))!=null)throw new IllegalStateException("Handler already registered");}
    public static void unregisterAction(int type){service().actions.remove(type);}
    public static void registerDefinition(int type,Definition definition){if(service().definitions.putIfAbsent(type,Objects.requireNonNull(definition))!=null)throw new IllegalStateException("Definition already registered");}
    public static void unregisterDefinition(int type){service().definitions.remove(type);}
    boolean ownsEvents(){
        if(!getConfig().getBoolean("actions.enabled",true))return false;
        var zero=getServer().getPluginManager().getPlugin("zero");
        if(zero!=null&&zero.isEnabled())try{if((Boolean)zero.getClass().getMethod("isModuleActive",String.class).invoke(zero,"sign"))return false;}catch(Exception|LinkageError error){return false;}
        return true;
    }
    boolean allowed(Player p,SignRegistry.SignRecord sign,String operation){
        if(!operation.equals("activate")&&!permits(p,operation.equals("create")?"set":operation))return false;
        try{return policy!=null&&policy.authorize(p,sign,operation)==Decision.ALLOW;}
        catch(Exception|LinkageError error){getLogger().warning("告示牌權限服務失敗，已拒絕此次操作："+error.getClass().getSimpleName());return false;}
    }
    String[] prepare(Player p,SignRegistry.SignRecord r,String[] lines){var d=definitions.get(r.type());return d==null?null:d.prepare(p,r,lines);}
    public static boolean activate(Player player,SignRegistry.SignRecord sign){
        Main m=service();if(!m.ownsEvents()||!m.allowed(player,sign,"activate"))return false;
        Action a=m.actions.get(sign.type());return a!=null&&a.execute(player,sign);
    }
    boolean hasDefinition(int type){return definitions.containsKey(type);}
    boolean hasPolicy(){return policy!=null;}
    /** Single-record inspection; force may synchronously load one existing chunk. Prefer jobs for bulk work. */
    public static String inspectSign(int id,CommandSender actor,boolean force){
        Main m=service();if(!permits(actor,"inspect"))throw new IllegalArgumentException("沒有檢查權限");
        return m.maintenance.inspect(id,actor,false,force).toString();
    }
    public static long startMaintenance(String mode,Integer id,CommandSender actor,boolean force){return service().maintenance.start(mode,id,actor,force);}
    public static String maintenanceStatus(CommandSender actor){if(!permits(actor,"jobs"))throw new IllegalArgumentException("沒有工作查詢權限");return service().maintenance.status();}
    public static boolean cancelMaintenance(CommandSender actor){if(!permits(actor,"jobs.cancel"))throw new IllegalArgumentException("沒有取消工作權限");return service().maintenance.cancel();}
    static boolean permits(CommandSender sender,String operation){return sender.hasPermission("tokaSigns.admin")||sender.hasPermission("tokaSigns."+operation);}
    boolean canDefine(Player p,int type){return permits(p,"type."+type)&&definitions.containsKey(type);}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String op=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        if(!Set.of("help","status","edit","copy","paste","set","cancel","inspect","check","refresh","jobs").contains(op)){sender.sendMessage("[告示]未知指令，請使用 /sign help。");return true;}
        if(!permits(sender,op)){sender.sendMessage("[告示]沒有此指令的權限。");return true;}
        if(Set.of("inspect","check","refresh","jobs").contains(op)){
            try{
                if(op.equals("jobs")){
                    if(args.length==2&&args[1].equalsIgnoreCase("cancel")){sender.sendMessage("[告示]取消工作："+cancelMaintenance(sender));return true;}
                    if(args.length!=1)throw new IllegalArgumentException("用法：/sign jobs [cancel]");
                    var report=com.google.gson.JsonParser.parseString(maintenanceStatus(sender)).getAsJsonObject();
                    var samples=report.remove("samples");sender.sendMessage("[告示]"+report);
                    if(samples!=null)for(var sample:samples.getAsJsonArray()){var entry=sample.getAsJsonObject();sender.sendMessage("[告示]#"+entry.get("id")+" "+entry.get("status").getAsString());}return true;
                }
                if(args.length<2||args.length>3||(args.length==3&&!args[2].equals("--force")))throw new IllegalArgumentException("用法：/sign "+op+" <"+(op.equals("inspect")?"非負整數ID":"非負整數ID|all")+"> [--force]");
                Integer id=null;if(!args[1].equalsIgnoreCase("all")||op.equals("inspect")){try{id=Integer.valueOf(args[1]);}catch(NumberFormatException e){throw new IllegalArgumentException("ID 必須為非負整數。");}if(id<0)throw new IllegalArgumentException("ID 必須為非負整數。");}
                boolean force=args.length==3;
                if(op.equals("inspect")){sender.sendMessage("[告示]"+inspectSign(id,sender,force));return true;}
                sender.sendMessage("[告示]已排入工作 #"+startMaintenance(op,id,sender,force)+"；/sign jobs 查看進度。");
            }catch(IllegalArgumentException|IllegalStateException e){sender.sendMessage("[告示]"+e.getMessage());}return true;
        }
        if(!op.equals("set")&&args.length>1){sender.sendMessage("[告示]此指令不接受其他參數。");return true;}
        if(op.equals("help")){sendHelp(sender);return true;}
        if(op.equals("status")){sender.sendMessage("[告示]登記 "+registry.records().size()+" 筆；事件接管="+ownsEvents()+"；保護 provider="+(policyOwner==null?"未就緒":policyOwner));return true;}
        if(!(sender instanceof Player p)){sender.sendMessage("[告示]此指令限遊戲內玩家使用。");return true;}
        if(op.equals("cancel")){SignEvents.cancel(p);sender.sendMessage("[告示]已取消操作，保留剪貼簿。");return true;}
        if(!ownsEvents()){sender.sendMessage("[告示]事件接管尚未啟用，請以 /sign status 查看。");return true;}
        if(op.equals("set")){
            if(args.length!=3){sender.sendMessage("[告示]用法：/sign set <類型名稱或正整數ID> <單一代碼>（狀態牌填 -）");return true;}
            SignEvents.setDefinitionMode(p,args[1],args[2]);return true;
        }
        SignEvents.setMode(p,op);return true;
    }
    private void sendHelp(CommandSender sender){
        sender.sendMessage("§b── tokaSigns 可用指令（別名 /signs；<必填>、[選填]）──");
        if(permits(sender,"help"))sender.sendMessage("/sign help — 此說明");
        if(permits(sender,"status"))sender.sendMessage("/sign status — 登記數、接管及保護 provider");
        if(permits(sender,"inspect"))sender.sendMessage("/sign inspect <ID> [--force] — 位置、牌面與功能設定對照");
        if(permits(sender,"check"))sender.sendMessage("/sign check <ID|all> [--force] — 分批唯讀檢查");
        if(permits(sender,"refresh"))sender.sendMessage("/sign refresh <ID|all> [--force] — 分批依設定更新牌面，不修復 NBT");
        if(permits(sender,"jobs"))sender.sendMessage(permits(sender,"jobs.cancel")?"/sign jobs [cancel] — 進度與統計／取消":"/sign jobs — 進度與統計");
        if(permits(sender,"force"))sender.sendMessage("[告示]--force 可載入既有區塊（依效能動態分批）；不產生新區塊，不略過權限。");
        if(sender instanceof Player){
            if(permits(sender,"edit"))sender.sendMessage("/sign edit — 點選一般牌改四行；功能牌改代碼");
            if(permits(sender,"copy"))sender.sendMessage("/sign copy — 點選一般牌複製文字");
            if(permits(sender,"paste"))sender.sendMessage("/sign paste — 點選一般牌貼上文字");
            if(permits(sender,"cancel"))sender.sendMessage("/sign cancel — 取消操作，保留剪貼簿");
            if(permits(sender,"set")){
                sender.sendMessage("/sign set <類型名稱或正整數ID> <單一代碼> — 點選設定功能；代碼不可含空白，狀態牌填 -");
                var types=new TreeSet<Integer>();for(int type:definitions.keySet())if(permits(sender,"type."+type))types.add(type);
                sender.sendMessage("[告示]目前有設定權限且 provider 已就緒的類型："+types+"（可直接填數字ID）");
            }
        }
        if(permits(sender,"set")){
            sender.sendMessage("§b── 直接填寫告示牌：§6[ §f第1行 | 第2行 | 第3行 | 第4行§6 ] ──");
            sender.sendMessage("[告示]<參數> 必填，[參數] 選填。外框、標記及 | 不用輸入，「留空」不填字。");
            signInputHelp(sender,10,"tpr | <區域代碼> | 留空 | 留空 — 區域傳送");
            signInputHelp(sender,11,"tpp | <傳送點名稱> | 留空 | 留空 — 公共傳送點");
            signInputHelp(sender,31,"b | <商品代碼> | 留空 | 留空 — 購買商品");
            signInputHelp(sender,32,"s | <商品代碼> | 留空 | 留空 — 回收商品");
            signInputHelp(sender,33,"item | 留空 | 留空 | 留空 — 物品管理入口");
            signInputHelp(sender,41,"status | 留空 | 留空 | 留空 — 伺服器狀態");
        }
        sender.sendMessage("[告示]顯示指令權限不代表任何位置都可修改；仍檢查保護服務、功能 provider 與接管狀態。");
    }
    private void signInputHelp(CommandSender sender,int type,String example){
        if(definitions.containsKey(type)&&permits(sender,"type."+type))sender.sendMessage("§6[ §f"+example.substring(0,example.indexOf(" — "))+"§6 ]§r"+example.substring(example.indexOf(" — ")));
    }
    private String[] statusLines(){return new String[]{"§r§a⦋狀態⦌§r","TPS "+String.format(Locale.ROOT,"%.1f",getServer().getTicksPerSecond()),"載入 "+String.format(Locale.ROOT,"%.1f%%",getServer().getTickUsage()),"↑ "+String.format(Locale.ROOT,"%.1f",getServer().getNetwork().getUpload())+" ↓ "+String.format(Locale.ROOT,"%.1f",getServer().getNetwork().getDownload())};}
    private void updateStatusSigns(){
        if(!ownsEvents())return;
        for(var r:registry.records())if(r.type()==41){
            var world=getServer().getLevelByName(r.world());
            if(world==null||!world.isChunkLoaded(r.x()>>4,r.z()>>4))continue;
            var entity=world.getBlockEntity(new cn.nukkit.math.Vector3(r.x(),r.y(),r.z()));
            if(entity instanceof cn.nukkit.blockentity.BlockEntitySign sign)try{sign.setText(statusLines());}catch(RuntimeException error){getLogger().warning("狀態牌更新失敗 id="+r.id());}
        }
    }
    public void onDisable(){if(maintenance!=null)maintenance.close();instance=null;actions.clear();definitions.clear();SignEvents.clear();policy=null;if(registry!=null)try{registry.close();}catch(Exception error){getLogger().warning("告示牌儲存關閉失敗");}}
}


