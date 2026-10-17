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
        try{return policy!=null&&policy.authorize(p,sign,operation)==Decision.ALLOW;}
        catch(Exception|LinkageError error){getLogger().warning("告示牌權限服務失敗，已拒絕此次操作："+error.getClass().getSimpleName());return false;}
    }
    String[] prepare(Player p,SignRegistry.SignRecord r,String[] lines){var d=definitions.get(r.type());return d==null?null:d.prepare(p,r,lines);}
    public static boolean activate(Player player,SignRegistry.SignRecord sign){
        Main m=service();if(!m.ownsEvents()||!m.allowed(player,sign,"activate"))return false;
        Action a=m.actions.get(sign.type());return a!=null&&a.execute(player,sign);
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("tokaSigns.admin")){sender.sendMessage("[告示]需要 tokaSigns.admin 權限（預設 OP）。");return true;}
        if(args.length==0||args[0].equalsIgnoreCase("help")){sendHelp(sender);return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("status")){
            sender.sendMessage("[告示]登記 "+registry.records().size()+" 筆；事件接管="+ownsEvents()+"；動作開關="+getConfig().getBoolean("actions.enabled",true)+"；保護 provider="+(policyOwner==null?"未就緒":policyOwner));
            sender.sendMessage("[告示]動作類型："+new TreeSet<>(actions.keySet())+"；設定類型："+new TreeSet<>(definitions.keySet()));return true;
        }
        if(args.length==1&&args[0].equalsIgnoreCase("cancel")){
            if(sender instanceof Player p){SignEvents.cancel(p);sender.sendMessage("[告示]已取消待選取與待提交操作。");}else sender.sendMessage("[告示]此操作限遊戲內玩家使用。");return true;
        }
        if(!(sender instanceof Player)){sender.sendMessage("[告示]編輯、複製、貼上與設定限遊戲內玩家使用。");sendHelp(sender);return true;}
        if(args.length==1&&(args[0].equalsIgnoreCase("copy")||args[0].equalsIgnoreCase("paste"))&&sender instanceof Player p){
            if(!ownsEvents()){sender.sendMessage("[告示]舊告示牌模組仍在管理中。");return true;}
            SignEvents.setMode(p,args[0].toLowerCase(Locale.ROOT));return true;
        }
        if(sender instanceof Player p&&args.length==1&&args[0].equalsIgnoreCase("edit")){
            if(ownsEvents())SignEvents.setMode(p,"edit");else sender.sendMessage("[告示]舊告示牌模組仍在管理中。");return true;
        }
        if(sender instanceof Player p&&args.length>=3&&args[0].equalsIgnoreCase("set")){
            if(ownsEvents())SignEvents.setDefinitionMode(p,args[1],String.join(" ",Arrays.copyOfRange(args,2,args.length)));else sender.sendMessage("[告示]舊告示牌模組仍在管理中。");return true;
        }
        sender.sendMessage("[告示]未知指令或參數不正確。");sendHelp(sender);return true;
    }
    private void sendHelp(CommandSender sender){
        sender.sendMessage("§b── tokaSigns 指令說明 ──");
        sender.sendMessage("/sign help — 顯示說明（別名 /signs）");
        sender.sendMessage("/sign status — 登記數、接管狀態與 provider（後台可用）");
        sender.sendMessage("/sign edit — 點選一般牌編輯四行；功能牌編輯代碼");
        sender.sendMessage("/sign copy、/sign paste — 點選一般牌複製／貼上文字");
        sender.sendMessage("/sign set <類型> <代碼> — 點選牌設定功能；buy/b=31、sell/s=32、tpr=10、tpp=11、item=33、status=41");
        sender.sendMessage("/sign cancel — 取消待選取／表單操作，保留剪貼簿");
        sender.sendMessage("[告示]需要 tokaSigns.admin（預設 OP）及位置保護授權；功能須 provider 就緒。tpl/tps/pay 缺 provider 時不建立。");
        sender.sendMessage("[告示]缺少或損壞的方塊實體不會自動重建，請保留世界備份。");
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
    public void onDisable(){instance=null;actions.clear();definitions.clear();SignEvents.clear();policy=null;if(registry!=null)try{registry.close();}catch(Exception error){getLogger().warning("告示牌儲存關閉失敗");}}
}


