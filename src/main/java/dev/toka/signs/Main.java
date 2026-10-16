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
    private static Main instance;
    private SignRegistry registry;
    private Policy policy;
    private String policyOwner;
    private final Map<Integer,Action> actions=new HashMap<>();
    public void onEnable(){
        saveDefaultConfig();
        try{
            var backend=Backend.valueOf(getConfig().getString("storage.backend","sqlite").toUpperCase(Locale.ROOT));
            registry=new SignRegistry(new StoreConfig(backend,getDataFolder().toPath().resolve("storage").resolve(backend.name().toLowerCase(Locale.ROOT)),1));
            instance=this;
            getLogger().info("§b載入告示牌共 §a"+registry.records().size()+" §b個");
        }catch(Exception error){throw new IllegalStateException("告示牌儲存無法載入；請保留原始資料",error);}
    }
    private static Main service(){
        if(instance==null||!instance.isEnabled())throw new IllegalStateException("tokaSigns unavailable");
        if(!instance.getServer().isPrimaryThread())throw new IllegalStateException("Sign API requires server thread");
        return instance;
    }
    public static SignRegistry getRegistry(){return service().registry;}
    public static void importLegacySigns(String json)throws Exception{service().registry.importSnapshot(json);}
    public static void registerPolicy(String owner,Policy policy){
        Main main=service();if(owner==null||owner.isBlank())throw new IllegalArgumentException("owner");
        if(main.policyOwner!=null&&!main.policyOwner.equals(owner))throw new IllegalStateException("A policy provider already exists");
        main.policyOwner=owner;main.policy=Objects.requireNonNull(policy);
    }
    public static void unregisterPolicy(String owner){Main main=service();if(Objects.equals(main.policyOwner,owner)){main.policyOwner=null;main.policy=null;}}
    public static void registerAction(int type,Action action){if(service().actions.putIfAbsent(type,Objects.requireNonNull(action))!=null)throw new IllegalStateException("Handler already registered");}
    public static void unregisterAction(int type){service().actions.remove(type);}
    public static boolean activate(Player player,SignRegistry.SignRecord sign){
        Main main=service();
        if(!main.getConfig().getBoolean("actions.enabled",false)||main.policy==null)return false;
        var zero=main.getServer().getPluginManager().getPlugin("zero");
        if(zero!=null&&zero.isEnabled())try{if((Boolean)zero.getClass().getMethod("isModuleActive",String.class).invoke(zero,"sign"))return false;}catch(Exception|LinkageError error){return false;}
        if(main.policy.authorize(player,sign,"activate")!=Decision.ALLOW)return false;
        Action action=main.actions.get(sign.type());return action!=null&&action.execute(player,sign);
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("tokaSigns.admin"))return false;
        sender.sendMessage("tokaSigns：資料副本 "+registry.records().size()+" 筆；首版未接管舊事件。");return true;
    }
    public void onDisable(){instance=null;actions.clear();policy=null;if(registry!=null)try{registry.close();}catch(Exception error){getLogger().warning("告示牌儲存關閉失敗");}}
}
