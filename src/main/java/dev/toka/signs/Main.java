package dev.toka.signs;

import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.command.*;
import cn.nukkit.command.data.CommandParameter;
import dev.toka.storage.*;
import dev.toka.i18n.Messages;
import java.util.*;

public final class Main extends PluginBase implements cn.nukkit.event.Listener {
    public enum Decision { ALLOW,DENY,UNAVAILABLE }
    public interface Policy { Decision authorize(Player player,SignRegistry.SignRecord sign,String operation); }
    public interface Action { boolean execute(Player player,SignRegistry.SignRecord sign); }
    public interface Definition { String[] prepare(Player player,SignRegistry.SignRecord sign,String[] lines); default String[] prepareRefresh(Player player,SignRegistry.SignRecord sign,String[] lines){return prepare(player,sign,lines);} }
    /** Optional business data source. Presentation and legacy sign wording stay in tokaSigns. */
    public interface DisplayDataProvider { String resolve(Player player,SignRegistry.SignRecord sign); }
    private static Main instance;
    private SignRegistry registry;
    private SignMaintenance maintenance;
    private OrphanCleaner cleaner;
    private Messages messages;
    private Policy policy;
    private String policyOwner;
    private final Map<Integer,Action> actions=new HashMap<>();
    private final Map<Integer,Definition> definitions=new HashMap<>();
    private final Map<Integer,DisplayDataProvider> displayDataProviders=new HashMap<>();
    public void onEnable(){
        saveDefaultConfig();
        messages=new Messages(getClass().getClassLoader(),getDataFolder().toPath(),getConfig().getString("language","zh-TW"),line->getLogger().warning(line));
        try{
            var backend=Backend.valueOf(getConfig().getString("storage.backend","sqlite").toUpperCase(Locale.ROOT));
            registry=new SignRegistry(new StoreConfig(backend,getDataFolder().toPath().resolve("storage").resolve(backend.name().toLowerCase(Locale.ROOT)),1),error->getLogger().warning(messages.text("log.saveFailed",error.getClass().getSimpleName())));
            actions.put(41,(p,r)->true);definitions.put(41,(p,r,lines)->statusLines());
            maintenance=new SignMaintenance(this);cleaner=new OrphanCleaner(this);
            getServer().getScheduler().scheduleRepeatingTask(this,new cn.nukkit.scheduler.Task(){public void onRun(int tick){maintenance.tick();cleaner.tick();}},1);
            instance=this;LandPortalIntegration.connect(this);getServer().getPluginManager().registerEvents(new SignEvents(this),this);getServer().getPluginManager().registerEvents(this,this);configureCommandSuggestions();
            getServer().getScheduler().scheduleRepeatingTask(this,new cn.nukkit.scheduler.Task(){public void onRun(int tick){updateStatusSigns();}},25);
            getLogger().info(messages.text("log.loaded",registry.records().size()));
        }catch(Exception error){getLogger().error(messages.text("log.storageFailed"));throw new IllegalStateException("Sign storage could not be loaded; keep the original data",error);}
    }
    com.google.gson.JsonObject inspect(int id,CommandSender actor,boolean refresh){return maintenance.inspect(id,actor,refresh,false);}
    boolean maintenanceRunning(){return maintenance!=null&&maintenance.running();}
    OrphanCleaner cleaner(){return cleaner;}
    private void configureCommandSuggestions(){
        var command=getCommand("sign");
        if(command==null){getLogger().warning(messages.text("log.noCommand"));return;}
        var names=new ArrayList<String>();
        for(var entry:SignTypeSelector.NAMES.entrySet())if(hasDefinition(entry.getKey()))names.add(entry.getValue());
        var repairNames=new ArrayList<String>(List.of("all"));repairNames.addAll(names);
        cn.nukkit.command.Command registered=(cn.nukkit.command.Command)command;
        registered.getCommandParameters().clear();
        registered.addCommandParameters("default",new CommandParameter[]{
            CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsAction",new String[]{"help","status","edit","copy","paste","set","cancel","inspect","check","refresh","repair","fix","jobs","reload"})),
            CommandParameter.newType("arguments",true,cn.nukkit.command.data.CommandParamType.RAWTEXT)
        });
        registered.addCommandParameters("jobs-cancel",new CommandParameter[]{CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsJobs",new String[]{"jobs"})),CommandParameter.newEnum("operation",new cn.nukkit.command.data.CommandEnum("TokaSignsJobsAction",new String[]{"cancel"}))});
        registered.addCommandParameters("inspect",new CommandParameter[]{CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsInspect",new String[]{"inspect"})),CommandParameter.newType("id",cn.nukkit.command.data.CommandParamType.INT),CommandParameter.newEnum("force",true,new cn.nukkit.command.data.CommandEnum("TokaSignsForce",new String[]{"--force"}))});
        registered.addCommandParameters("maintenance",new CommandParameter[]{CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsMaintenance",new String[]{"check","refresh"})),CommandParameter.newEnum("target",new cn.nukkit.command.data.CommandEnum("TokaSignsTarget",new String[]{"all"})),CommandParameter.newEnum("force",true,new cn.nukkit.command.data.CommandEnum("TokaSignsForce",new String[]{"--force"}))});
        registered.addCommandParameters("repair",new CommandParameter[]{CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsRepair",new String[]{"repair","fix"})),CommandParameter.newEnum("type",new cn.nukkit.command.data.CommandEnum("TokaSignsType",repairNames.toArray(new String[0]))),CommandParameter.newEnum("force",true,new cn.nukkit.command.data.CommandEnum("TokaSignsForce",new String[]{"--force"}))});
        registered.addCommandParameters("set-id",new CommandParameter[]{CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsSet",new String[]{"set"})),CommandParameter.newType("type",cn.nukkit.command.data.CommandParamType.INT),CommandParameter.newType("code",cn.nukkit.command.data.CommandParamType.STRING)});
        if(!names.isEmpty())registered.addCommandParameters("set",new CommandParameter[]{CommandParameter.newEnum("action",new cn.nukkit.command.data.CommandEnum("TokaSignsSet",new String[]{"set"})),CommandParameter.newEnum("type",new cn.nukkit.command.data.CommandEnum("TokaSignsSetType",names.toArray(new String[0]))),CommandParameter.newType("code",cn.nukkit.command.data.CommandParamType.STRING)});
        // Plugin reloads do not automatically refresh Bedrock's cached command list.
        getServer().getScheduler().scheduleTask(this,new cn.nukkit.scheduler.Task(){
            public void onRun(int tick){
                for(Player player:getServer().getOnlinePlayers().values())player.sendCommandData();
            }
        });
    }
    private boolean refreshQueued;
    /** Providers register at different times; one refresh after the burst rebuilds the type words and resends the command list. */
    private void queueCommandRefresh(){
        if(refreshQueued||!isEnabled())return;
        refreshQueued=true;
        getServer().getScheduler().scheduleTask(this,()->{refreshQueued=false;if(isEnabled())configureCommandSuggestions();});
    }
    @cn.nukkit.event.EventHandler public void onPluginEnabled(cn.nukkit.event.plugin.PluginEnableEvent event){
        String name=event.getPlugin().getName();
        if(name.equals("tokaPortal")||name.equals("tokaLand")||name.equals("tokaRegions"))reconnectIntegrations();
    }
    @cn.nukkit.event.EventHandler public void onPluginDisabled(cn.nukkit.event.plugin.PluginDisableEvent event){
        String name=event.getPlugin().getName();
        if(name.equals("tokaPortal")||name.equals("tokaLand")||name.equals("tokaRegions"))LandPortalIntegration.close(this);
    }
    /** Runs on the next tick so the plugin that just enabled has finished its own start. */
    private void reconnectIntegrations(){
        getServer().getScheduler().scheduleTask(this,()->{if(!isEnabled())return;LandPortalIntegration.connect(this);queueCommandRefresh();});
    }
    private final WarnOnce warned=new WarnOnce();
    private void reloadSettings(){
        reloadConfig();warned.clear();if(cleaner!=null)cleaner.reset();
        messages.language(getConfig().getString("language","zh-TW"));
        LandPortalIntegration.close(this);LandPortalIntegration.connect(this);
        configureCommandSuggestions();
    }
    private static Main service(){
        if(instance==null||!instance.isEnabled())throw new IllegalStateException("tokaSigns unavailable");
        if(!instance.getServer().isPrimaryThread())throw new IllegalStateException("Sign API requires server thread");
        return instance;
    }
    public static SignRegistry getRegistry(){return service().registry;}
    // API: other plugins, see README
    public static void registerPolicy(String owner,Policy policy){
        Main main=service();if(owner==null||owner.isBlank())throw new IllegalArgumentException("owner");
        if(main.policyOwner!=null&&!main.policyOwner.equals(owner))throw new IllegalStateException("A policy provider already exists");
        main.policyOwner=owner;main.policy=Objects.requireNonNull(policy);
    }
    public static void unregisterPolicy(String owner){Main m=service();if(Objects.equals(m.policyOwner,owner)){m.policyOwner=null;m.policy=null;}}
    public static void registerAction(int type,Action action){if(service().actions.putIfAbsent(type,Objects.requireNonNull(action))!=null)throw new IllegalStateException("Handler already registered");service().queueCommandRefresh();}
    public static void unregisterAction(int type){service().actions.remove(type);service().queueCommandRefresh();}
    // API: other plugins, see README
    public static void registerDefinition(int type,Definition definition){if(service().definitions.putIfAbsent(type,Objects.requireNonNull(definition))!=null)throw new IllegalStateException("Definition already registered");service().queueCommandRefresh();}
    public static void unregisterDefinition(int type){service().definitions.remove(type);service().queueCommandRefresh();}
    public static void registerDisplayDataProvider(int type,DisplayDataProvider provider){
        Main main=service();if(!SignTextFormatter.supports(type)||type==33)throw new IllegalArgumentException("No external display data is used for type "+type);
        if(main.displayDataProviders.putIfAbsent(type,Objects.requireNonNull(provider))!=null)throw new IllegalStateException("Display data provider already registered");
        main.queueCommandRefresh();
    }
    public static void unregisterDisplayDataProvider(int type){service().displayDataProviders.remove(type);service().queueCommandRefresh();}
    boolean ownsEvents(){return getConfig().getBoolean("actions.enabled",true);}
    boolean allowed(Player p,SignRegistry.SignRecord sign,String operation){
        if(!operation.equals("activate")&&!permits(p,operation.equals("create")?"set":operation))return false;
        // the permission above is the rule when no policy is registered; a policy can only add restrictions
        try{return policy==null||policy.authorize(p,sign,operation)==Decision.ALLOW;}
        catch(Exception|LinkageError error){getLogger().warning(tr("log.policyFailed",error.getClass().getSimpleName()));return false;}
    }
    private String[] format(SignRegistry.SignRecord r,Player p){
        if(!SignTextFormatter.supports(r.type()))return null;
        if(r.type()==33)return SignTextFormatter.format(r,null);
        var provider=displayDataProviders.get(r.type());
        if(provider!=null){String json=provider.resolve(p,r);return json==null?null:SignTextFormatter.format(r,com.google.gson.JsonParser.parseString(json).getAsJsonObject());}
        // Compatibility for third-party providers using the former formatted-lines API.
        var legacy=definitions.get(r.type());return legacy==null?null:legacy.prepare(p,r,new String[]{"",r.code(),"",r.info()});
    }
    String[] prepare(Player p,SignRegistry.SignRecord r,String[] lines){String[] canonical=format(r,p);if(canonical!=null)return canonical;var d=definitions.get(r.type());return d==null?null:d.prepare(p,r,lines);}
    String[] prepareRefresh(Player p,SignRegistry.SignRecord r,String[] lines){String[] canonical=format(r,p);if(canonical!=null)return canonical;var d=definitions.get(r.type());return d==null?null:d.prepareRefresh(p,r,lines);}
    /** What the face of this sign should read now, or null when the sign has nothing to show. For plugins that check a sign before acting on it. */
    public static String[] currentText(Player player,SignRegistry.SignRecord sign){
        return service().prepareRefresh(player,sign,new String[]{"",sign.code()==null?"":sign.code(),"",""});
    }
    public static boolean activate(Player player,SignRegistry.SignRecord sign){
        Main m=service();if(!m.ownsEvents()||!m.allowed(player,sign,"activate"))return false;
        Action a=m.actions.get(sign.type());return a!=null&&a.execute(player,sign);
    }
    boolean hasDefinition(int type){if(type==12&&!ownsEvents())return false;return type==41||type==33||displayDataProviders.containsKey(type)||definitions.containsKey(type);}
    /** Single-record inspection; force may synchronously load one existing chunk. Prefer jobs for bulk work. */
    public static String inspectSign(int id,CommandSender actor,boolean force){
        Main m=service();if(!permits(actor,"inspect"))throw new IllegalArgumentException(tr("err.ex.noInspectPermission"));
        return m.maintenance.inspect(id,actor,false,force).toString();
    }
    public static long startMaintenance(String mode,Integer id,CommandSender actor,boolean force){return startMaintenance(mode,id,null,actor,force);}
    public static long startMaintenance(String mode,Integer id,Integer type,CommandSender actor,boolean force){return service().maintenance.start(mode,id,type,actor,force);}
    public static String maintenanceStatus(CommandSender actor){if(!permits(actor,"jobs"))throw new IllegalArgumentException(tr("err.ex.noJobsPermission"));return service().maintenance.status();}
    public static boolean cancelMaintenance(CommandSender actor){if(!permits(actor,"jobs.cancel"))throw new IllegalArgumentException(tr("err.ex.noCancelPermission"));return service().maintenance.cancel();}
    static String tr(String key,Object... args){return instance==null||instance.messages==null?key:instance.messages.text(key,args);}
    private String yesNo(boolean value){return messages.text(value?"status.yes":"status.no");}
    private void reply(CommandSender sender,String text){sender.sendMessage(messages.text("msg.reply",text));}
    static boolean permits(CommandSender sender,String operation){return sender.hasPermission("tokaSigns.admin")||sender.hasPermission("tokaSigns."+operation);}
    boolean canDefine(Player p,int type){return permits(p,"type."+type)&&hasDefinition(type);}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String op=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);if(op.equals("fix"))op="repair";
        if(!Set.of("help","status","edit","copy","paste","set","cancel","inspect","check","refresh","repair","jobs","reload").contains(op)){sender.sendMessage(messages.text("err.unknownCommand"));return true;}
        if(!permits(sender,op)){sender.sendMessage(messages.text("err.noPermission"));return true;}
        if(Set.of("inspect","check","refresh","repair","jobs").contains(op)){
            try{
                if(op.equals("jobs")){
                    if(args.length==2&&args[1].equalsIgnoreCase("cancel")){sender.sendMessage(messages.text("msg.jobCancelResult",yesNo(cancelMaintenance(sender))));return true;}
                    if(args.length!=1)throw new IllegalArgumentException(messages.text("err.usage",messages.text("cmd.sign.usageJobs")));
                    var report=com.google.gson.JsonParser.parseString(maintenanceStatus(sender)).getAsJsonObject();
                    var samples=report.remove("samples");reply(sender,report.toString());
                    if(samples!=null)for(var sample:samples.getAsJsonArray()){var entry=sample.getAsJsonObject();sender.sendMessage(messages.text("msg.jobSample",entry.get("id"),entry.get("status").getAsString()));}return true;
                }
                if(args.length<2||args.length>3||(args.length==3&&!args[2].equals("--force")))throw new IllegalArgumentException(messages.text("err.usage",op.equals("inspect")?messages.text("cmd.sign.usageInspect"):op.equals("repair")?messages.text("cmd.sign.usageRepair"):messages.text("cmd.sign.usageMaint",op)));
                Integer id=null,type=null;if(op.equals("repair")){type=SignTypeSelector.parse(args[1]);}else if(!args[1].equalsIgnoreCase("all")||op.equals("inspect")){try{id=Integer.valueOf(args[1]);}catch(NumberFormatException e){throw new IllegalArgumentException(messages.text("err.ex.badId"));}if(id<0)throw new IllegalArgumentException(messages.text("err.ex.badId"));}
                boolean force=args.length==3;
                if(op.equals("inspect")){reply(sender,inspectSign(id,sender,force));return true;}
                sender.sendMessage(messages.text("msg.jobQueued",startMaintenance(op,id,type,sender,force)));
            }catch(IllegalArgumentException|IllegalStateException e){reply(sender,e.getMessage());}return true;
        }
        if(!op.equals("set")&&args.length>1){sender.sendMessage(messages.text("err.noExtraArgs"));return true;}
        if(op.equals("help")){sendHelp(sender);return true;}
        if(op.equals("reload")){reloadSettings();sender.sendMessage(messages.text("msg.reloaded"));return true;}
        if(op.equals("status")){sender.sendMessage(messages.text("msg.status",registry.records().size(),yesNo(ownsEvents()),policyOwner==null?messages.text("status.notReady"):policyOwner));return true;}
        if(!(sender instanceof Player p)){sender.sendMessage(messages.text("err.playerOnly"));return true;}
        if(op.equals("cancel")){SignEvents.cancel(p);sender.sendMessage(messages.text("msg.cancelled"));return true;}
        if(!ownsEvents()){sender.sendMessage(messages.text("err.eventsNotOwned"));return true;}
        if(op.equals("set")){
            if(args.length==1){SignEvents.openSetWindow(this,p);return true;}
            if(args.length!=3){sender.sendMessage(messages.text("err.usage",messages.text("cmd.sign.usageSet")));return true;}
            SignEvents.setDefinitionMode(p,args[1],args[2]);return true;
        }
        SignEvents.setMode(p,op);return true;
    }
    /** Lines of help.sign look like "tag|text"; the tag decides who sees the line (see lang/zh-TW.yml). */
    private void sendHelp(CommandSender sender){
        var types=new TreeSet<Integer>(definitions.keySet());types.addAll(displayDataProviders.keySet());types.add(33);
        types.removeIf(type->!hasDefinition(type)||!permits(sender,"type."+type));
        for(String line:messages.lines("help.sign",types)){
            int bar=line.indexOf('|');if(bar<0){sender.sendMessage(line);continue;}
            String tag=line.substring(0,bar);boolean playerOnly=tag.startsWith("p:");if(playerOnly)tag=tag.substring(2);
            if(playerOnly&&!(sender instanceof Player))continue;
            boolean show;
            if(tag.equals("*"))show=true;
            else if(tag.startsWith("type.")){
                int type;try{type=Integer.parseInt(tag.substring(5));}catch(NumberFormatException e){continue;}
                show=permits(sender,"set")&&hasDefinition(type)&&permits(sender,tag);
            }else show=permits(sender,tag);
            if(show)sender.sendMessage(line.substring(bar+1));
        }
    }
    boolean glowEnabled(){return getConfig().getBoolean("signs.glow",true);}
    void applyGlow(cn.nukkit.blockentity.BlockEntitySign sign){sign.setGlowing(glowEnabled());sign.spawnToAll();}
    void applySignText(cn.nukkit.blockentity.BlockEntitySign sign,String[] lines){sign.setGlowing(glowEnabled());sign.setText(lines);sign.spawnToAll();}
    private String[] statusLines(){return new String[]{messages.text("ui.statusSign.title"),messages.text("ui.statusSign.tps",String.format(Locale.ROOT,"%.1f",getServer().getTicksPerSecond())),messages.text("ui.statusSign.load",String.format(Locale.ROOT,"%.1f",getServer().getTickUsage())),messages.text("ui.statusSign.net",String.format(Locale.ROOT,"%.1f",getServer().getNetwork().getUpload()),String.format(Locale.ROOT,"%.1f",getServer().getNetwork().getDownload()))};}
    private void updateStatusSigns(){
        if(!ownsEvents())return;
        for(var it=registry.maintenanceIterator();it.hasNext();){
            var r=it.next();if(r.type()!=41)continue;
            var world=getServer().getLevelByName(r.world());
            if(world==null||!world.isChunkLoaded(r.x()>>4,r.z()>>4))continue;
            var entity=world.getBlockEntity(new cn.nukkit.math.Vector3(r.x(),r.y(),r.z()));
            if(entity instanceof cn.nukkit.blockentity.BlockEntitySign sign)try{applySignText(sign,statusLines());}catch(RuntimeException error){if(warned.first("status:"+r.id()))getLogger().warning(messages.text("log.statusUpdateFailed",r.id()));}
        }
    }
    public void onDisable(){LandPortalIntegration.close(this);if(maintenance!=null)maintenance.close();instance=null;actions.clear();definitions.clear();displayDataProviders.clear();SignEvents.clear();policy=null;if(registry!=null)try{registry.close();}catch(Exception error){getLogger().warning(messages.text("log.storageCloseFailed"));}}
}


