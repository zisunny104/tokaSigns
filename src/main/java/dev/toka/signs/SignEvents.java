package dev.toka.signs;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.event.*;
import cn.nukkit.event.block.*;
import cn.nukkit.event.player.*;
import java.util.*;

/** Event ownership is independent from business providers; unavailable policy fails closed. */
public final class SignEvents implements Listener {
    private final Main main;
    private static final Set<SignChangeEvent> prepared=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<UUID,String> modes=new HashMap<>();
    private record Pending(String world,int x,int y,int z,cn.nukkit.form.window.FormWindowCustom form,String[] text,SignRegistry.SignRecord record){}
    private record Setup(int type,String code){}
    private static final Map<UUID,Pending> edits=new HashMap<>();
    private static final Map<UUID,Setup> setups=new HashMap<>();
    private static final Map<UUID,String[]> clips=new HashMap<>();
    private record SetWindow(cn.nukkit.form.window.FormWindowCustom form,List<Integer> types){}
    private static final Map<UUID,SetWindow> setWindows=new HashMap<>();
    private static final Map<String,Integer> TYPES=Map.of("tpr",10,"tpp",11,"tpl",12,"tps",13,"pay",21,"b",31,"s",32,"item",33,"status",41);
    private static final UseNotice notice=new UseNotice(2000);
    private void notifyFailed(Player p){
        int seconds=Math.max(1,Math.min(60,main.getConfig().getInt("messages.use-failed-seconds",5)));
        notice.notice(p.getUniqueId(),System.currentTimeMillis(),()->UseNotice.displayFlash(p,Main.tr("msg.useFailed"),seconds),()->p.sendMessage(Main.tr("err.cannotUse")));
    }
    SignEvents(Main main){this.main=main;}
    static void setMode(Player p,String mode){if(mode.equals("paste")&&!clips.containsKey(p.getUniqueId())){p.sendMessage(Main.tr("msg.copyFirst"));return;}modes.put(p.getUniqueId(),mode);p.sendMessage(Main.tr("msg.clickToDo",Main.tr(switch(mode){case "copy"->"status.mode.copy";case "paste"->"status.mode.paste";case "edit"->"status.mode.edit";default->"status.mode.set";})));}
    static void firePrepared(Main main,SignChangeEvent event){prepared.add(event);try{main.getServer().getPluginManager().callEvent(event);}finally{prepared.remove(event);}}
    static void cancel(Player p){modes.remove(p.getUniqueId());edits.remove(p.getUniqueId());setups.remove(p.getUniqueId());setWindows.remove(p.getUniqueId());}
    static void clear(){notice.clear();modes.clear();clips.clear();edits.clear();setups.clear();setWindows.clear();}
    /** The set window lists only the types this player may define and whose provider is ready; it then runs the same path as /sign set. */
    static void openSetWindow(Main main,Player p){
        var types=new ArrayList<Integer>();var labels=new ArrayList<String>();
        for(var entry:SignTypeSelector.NAMES.entrySet())if(main.canDefine(p,entry.getKey())){types.add(entry.getKey());labels.add(entry.getValue()+" ("+entry.getKey()+")");}
        if(types.isEmpty()){p.sendMessage(Main.tr("err.typeNotAuthorized"));return;}
        var form=new cn.nukkit.form.window.FormWindowCustom(Main.tr("ui.set.title"));
        form.addElement(new cn.nukkit.form.element.ElementHeader(Main.tr("ui.set.title")));
        form.addElement(new cn.nukkit.form.element.ElementLabel(Main.tr("ui.set.hint")));
        form.addElement(new cn.nukkit.form.element.ElementDropdown(Main.tr("ui.set.type"),labels));
        form.addElement(new cn.nukkit.form.element.ElementInput(Main.tr("ui.set.code"),Main.tr("ui.set.codeHint"),""));
        setWindows.put(p.getUniqueId(),new SetWindow(form,types));p.showFormWindow(form);
    }
    static void setDefinitionMode(Player p,String type,String code){
        Integer n=TYPES.get(type.toLowerCase(Locale.ROOT));if(type.equalsIgnoreCase("buy"))n=31;if(type.equalsIgnoreCase("sell"))n=32;
        if(n==null)try{n=Integer.parseInt(type);}catch(NumberFormatException ignored){}
        if(n==null||n<=0||code.isBlank()||code.length()>8192){p.sendMessage(Main.tr("err.usage",Main.tr("cmd.sign.usageSetShort")));return;}
        if(!mainInstanceCanDefine(p,n)){p.sendMessage(Main.tr("err.typeNotAuthorized"));return;}
        setups.put(p.getUniqueId(),new Setup(n,code));setMode(p,"set");
    }
    private static boolean mainInstanceCanDefine(Player p,int type){return ((Main)p.getServer().getPluginManager().getPlugin("tokaSigns")).canDefine(p,type);}
    private void openEdit(Player p,Block b,BlockEntitySign sign,SignRegistry.SignRecord r){
        if(!main.allowed(p,r==null?plain(b):r,"edit")){p.sendMessage(Main.tr("err.noEditHere"));return;}
        var form=new cn.nukkit.form.window.FormWindowCustom(Main.tr("ui.edit.title"));String[] text=sign.getText().clone();form.addElement(new cn.nukkit.form.element.ElementHeader(Main.tr("ui.edit.title")));
        if(r==null)for(int i=0;i<4;i++)form.addElement(new cn.nukkit.form.element.ElementInput(Main.tr("ui.edit.line",i+1),"",text[i]));
        else form.addElement(new cn.nukkit.form.element.ElementInput(Main.tr("ui.edit.code"),"",r.code()==null?"":r.code()));
        edits.put(p.getUniqueId(),new Pending(r==null?b.getLevel().getFolderName():r.world(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),form,text,r));p.showFormWindow(form);
    }
    private void applyDefinition(Player p,Block b,BlockEntitySign sign,int type,String code)throws Exception{
        if(!main.canDefine(p,type)){p.sendMessage(Main.tr("err.cannotDefine"));return;}
        if(code==null||code.length()>8192){p.sendMessage(Main.tr("err.input.badFormat"));return;}
        var old=record(b);var r=new SignRegistry.SignRecord(old==null?main.getRegistry().nextId():old.id(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),b.getLevel().getFolderName(),type,code,old==null?null:old.info(),old==null?-1:old.ticks());
        if(!main.allowed(p,old==null?r:old,old==null?"create":"edit")||!main.allowed(p,r,old==null?"create":"edit")){p.sendMessage(Main.tr("err.noSetHere"));return;}
        String[] text=main.prepare(p,r,new String[]{"",code,"",""});if(text==null||text.length!=4){p.sendMessage(Main.tr("err.providerNotReady"));return;}
        var event=new SignChangeEvent(b,p,text);firePrepared(main,event);if(event.isCancelled())return;
        main.getRegistry().upsert(r);main.applySignText(sign,event.getLines());p.sendMessage(Main.tr("msg.setDone"));
    }
    @EventHandler public void form(PlayerFormRespondedEvent event){
        Player p=event.getPlayer();
        SetWindow window=setWindows.get(p.getUniqueId());
        if(window!=null&&event.getWindow()==window.form()){
            setWindows.remove(p.getUniqueId());
            if(event.wasClosed()||!(event.getResponse() instanceof cn.nukkit.form.response.FormResponseCustom answer))return;
            int index=answer.getDropdownResponse(2).getElementID();String code=answer.getInputResponse(3);
            if(index<0||index>=window.types().size()||code==null||code.isBlank()){p.sendMessage(Main.tr("err.usage",Main.tr("cmd.sign.usageSetShort")));return;}
            setDefinitionMode(p,String.valueOf(window.types().get(index)),code.trim());return;
        }Pending pending=edits.get(p.getUniqueId());if(pending==null||event.getWindow()!=pending.form())return;edits.remove(p.getUniqueId());
        if(event.wasClosed()||!main.ownsEvents()||!Main.permits(p,"edit")||!(event.getResponse() instanceof cn.nukkit.form.response.FormResponseCustom response))return;
        var world=main.getServer().getLevelByName(pending.world());if(world==null||world!=p.getLevel()||!world.isChunkLoaded(pending.x()>>4,pending.z()>>4))return;
        var b=world.getBlock(pending.x(),pending.y(),pending.z());if(p.distance(b)>8)return;
        var entity=world.getBlockEntity(b);if(!(entity instanceof BlockEntitySign sign)||!Arrays.equals(sign.getText(),pending.text())||!Objects.equals(record(b),pending.record())){p.sendMessage(Main.tr("err.signChanged"));return;}
        if(!main.allowed(p,pending.record()==null?plain(b):pending.record(),"edit"))return;
        try{
            if(pending.record()!=null){applyDefinition(p,b,sign,pending.record().type(),response.getInputResponse(1));return;}
            String[] lines=new String[4];for(int i=0;i<4;i++){lines[i]=response.getInputResponse(i+1);if(lines[i]==null||lines[i].length()>2048)return;}
            var change=new SignChangeEvent(b,p,lines);main.getServer().getPluginManager().callEvent(change);if(!change.isCancelled())sign.setText(change.getLines());
        }catch(Exception|LinkageError error){main.getLogger().warning(Main.tr("log.formFailed",error.getClass().getSimpleName()));}
    }
    private void scheduleGlow(Block b){
        String worldName=b.getLevel().getFolderName();int x=b.getFloorX(),y=b.getFloorY(),z=b.getFloorZ();
        main.getServer().getScheduler().scheduleDelayedTask(main,new cn.nukkit.scheduler.Task(){public void onRun(int tick){
            var world=main.getServer().getLevelByName(worldName);if(world==null||!world.isChunkLoaded(x>>4,z>>4))return;
            var entity=world.getBlockEntity(new cn.nukkit.math.Vector3(x,y,z));
            if(entity instanceof BlockEntitySign sign&&main.getRegistry().at(worldName,x,y,z).isPresent())main.applyGlow(sign);
        }},1);
    }
    private SignRegistry.SignRecord record(Block b){return main.getRegistry().at(b.getLevel().getFolderName(),b.getFloorX(),b.getFloorY(),b.getFloorZ()).orElse(null);}
    private SignRegistry.SignRecord plain(Block b){return new SignRegistry.SignRecord(-1,b.getFloorX(),b.getFloorY(),b.getFloorZ(),b.getLevel().getFolderName(),0,null,null,0);}
    // Using a sign is allowed inside protected places, so a cancelled event is still handled; editing modes are not.
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event){
        if(!main.ownsEvents())return;
        if(event.getAction()!=PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)return;
        Block b=event.getBlock();var r=record(b);Player p=event.getPlayer();String mode=modes.get(p.getUniqueId());
        if(event.isCancelled()&&(r==null||mode!=null))return;
        var entity=b.getLevel().getBlockEntity(b);
        if(!(entity instanceof BlockEntitySign sign)){if(r!=null){event.setCancelled();p.sendMessage(Main.tr("err.signDataNotReady"));}return;}
        if(mode!=null){
            event.setCancelled();
            if(!Main.permits(p,mode.equals("set")?"set":mode)){modes.remove(p.getUniqueId());setups.remove(p.getUniqueId());return;}
            if(mode.equals("edit")){modes.remove(p.getUniqueId());openEdit(p,b,sign,r);return;}
            if(mode.equals("set")){modes.remove(p.getUniqueId());var setup=setups.remove(p.getUniqueId());if(setup!=null)try{applyDefinition(p,b,sign,setup.type(),setup.code());}catch(Exception|LinkageError error){main.getLogger().warning(Main.tr("log.setupFailed",error.getClass().getSimpleName()));}return;}
            // Plain text must never be pasted onto managed signs to alter business presentation.
            if(r!=null||!main.allowed(p,plain(b),mode)){p.sendMessage(Main.tr("err.noCopyPaste"));return;}
            if(mode.equals("copy"))clips.put(p.getUniqueId(),sign.getText().clone());
            else{String[] text=clips.get(p.getUniqueId());if(text==null)return;sign.setText(text.clone());}
            modes.remove(p.getUniqueId());p.sendMessage(Main.tr("msg.done"));return;
        }
        if(r!=null){event.setCancelled();try{if(!Main.activate(p,r))notifyFailed(p);}catch(Exception|LinkageError error){main.getLogger().warning(Main.tr("log.actionFailed",r.id(),error.getClass().getSimpleName()));}}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(SignChangeEvent event){
        if(prepared.contains(event)||!main.ownsEvents())return;
        var old=record(event.getBlock());Integer type=TYPES.get(event.getLine(0).toLowerCase(Locale.ROOT));
        if(old==null&&type==null)return;
        if(old==null){String issue=SignInput.validate(type,event.getLines());if(issue!=null){event.setCancelled();event.getPlayer().sendMessage(Main.tr(issue));return;}}
        var b=event.getBlock();var r=old==null?new SignRegistry.SignRecord(main.getRegistry().nextId(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),b.getLevel().getFolderName(),type,event.getLine(1),null,-1):old;
        if(!main.canDefine(event.getPlayer(),r.type())||!main.allowed(event.getPlayer(),r,old==null?"create":"edit")){event.setCancelled();return;}
        // Managed edits go through providers; never silently convert a business sign to plain text.
        try{String[] lines=main.prepare(event.getPlayer(),r,event.getLines().clone());if(lines==null||lines.length!=4){event.setCancelled();return;}main.getRegistry().upsert(r);for(int i=0;i<4;i++)event.setLine(i,lines[i]);scheduleGlow(b);}
        catch(Exception|LinkageError error){event.setCancelled();main.getLogger().warning(Main.tr("log.updateFailed",error.getClass().getSimpleName()));}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void broken(BlockBreakEvent event){if(!main.ownsEvents())return;var r=record(event.getBlock());if(r==null)return;if(!main.allowed(event.getPlayer(),r,"break")){event.setCancelled();return;}try{main.getRegistry().removeRecord(r.id());}catch(Exception error){event.setCancelled();main.getLogger().warning(Main.tr("log.deleteFailed",error.getClass().getSimpleName()));}}
    @EventHandler public void quit(PlayerQuitEvent e){modes.remove(e.getPlayer().getUniqueId());clips.remove(e.getPlayer().getUniqueId());edits.remove(e.getPlayer().getUniqueId());setups.remove(e.getPlayer().getUniqueId());setWindows.remove(e.getPlayer().getUniqueId());}
}






