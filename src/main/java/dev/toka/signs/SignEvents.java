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
    private static final Map<String,Integer> TYPES=Map.of("tpr",10,"tpp",11,"tpl",12,"tps",13,"pay",21,"b",31,"s",32,"item",33,"status",41);
    SignEvents(Main main){this.main=main;}
    static void setMode(Player p,String mode){if(mode.equals("paste")&&!clips.containsKey(p.getUniqueId())){p.sendMessage("[告示]請先複製告示牌。");return;}modes.put(p.getUniqueId(),mode);p.sendMessage("[告示]請點選告示牌進行"+switch(mode){case "copy"->"複製";case "paste"->"貼上";case "edit"->"編輯";default->"功能設定";});}
    static void firePrepared(Main main,SignChangeEvent event){prepared.add(event);try{main.getServer().getPluginManager().callEvent(event);}finally{prepared.remove(event);}}
    static void cancel(Player p){modes.remove(p.getUniqueId());edits.remove(p.getUniqueId());setups.remove(p.getUniqueId());}
    static void clear(){modes.clear();clips.clear();edits.clear();setups.clear();}
    static void setDefinitionMode(Player p,String type,String code){
        Integer n=TYPES.get(type.toLowerCase(Locale.ROOT));if(type.equalsIgnoreCase("buy"))n=31;if(type.equalsIgnoreCase("sell"))n=32;
        if(n==null)try{n=Integer.parseInt(type);}catch(NumberFormatException ignored){}
        if(n==null||n<=0||code.isBlank()||code.length()>8192){p.sendMessage("[告示]用法：/sign set buy|sell|type 商品代碼");return;}
        if(!mainInstanceCanDefine(p,n)){p.sendMessage("[告示]類型沒有授權或 provider 尚未就緒。");return;}
        setups.put(p.getUniqueId(),new Setup(n,code));setMode(p,"set");
    }
    private static boolean mainInstanceCanDefine(Player p,int type){return ((Main)p.getServer().getPluginManager().getPlugin("tokaSigns")).canDefine(p,type);}
    private void openEdit(Player p,Block b,BlockEntitySign sign,SignRegistry.SignRecord r){
        if(!main.allowed(p,r==null?plain(b):r,"edit")){p.sendMessage("[告示]沒有此位置的編輯權限。");return;}
        var form=new cn.nukkit.form.window.FormWindowCustom("告示牌編輯");String[] text=sign.getText().clone();
        if(r==null)for(int i=0;i<4;i++)form.addElement(new cn.nukkit.form.element.ElementInput("第 "+(i+1)+" 行","",text[i]));
        else form.addElement(new cn.nukkit.form.element.ElementInput("功能代碼","",r.code()==null?"":r.code()));
        edits.put(p.getUniqueId(),new Pending(r==null?b.getLevel().getFolderName():r.world(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),form,text,r));p.showFormWindow(form);
    }
    private void applyDefinition(Player p,Block b,BlockEntitySign sign,int type,String code)throws Exception{
        if(!main.canDefine(p,type)){p.sendMessage("[告示]功能類型無法設定。");return;}
        var old=record(b);var r=new SignRegistry.SignRecord(old==null?main.getRegistry().nextId():old.id(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),b.getLevel().getFolderName(),type,code,old==null?null:old.info(),old==null?-1:old.ticks());
        if(!main.allowed(p,old==null?r:old,old==null?"create":"edit")||!main.allowed(p,r,old==null?"create":"edit")){p.sendMessage("[告示]沒有此位置的設定權限。");return;}
        String[] text=main.prepare(p,r,new String[]{"",code,"",""});if(text==null||text.length!=4){p.sendMessage("[告示]此功能的 provider 尚未就緒。");return;}
        var event=new SignChangeEvent(b,p,text);prepared.add(event);try{main.getServer().getPluginManager().callEvent(event);}finally{prepared.remove(event);}if(event.isCancelled())return;
        main.getRegistry().upsert(r);sign.setText(event.getLines());p.sendMessage("[告示]功能設定完成。");
    }
    @EventHandler public void form(PlayerFormRespondedEvent event){
        Player p=event.getPlayer();Pending pending=edits.get(p.getUniqueId());if(pending==null||event.getWindow()!=pending.form())return;edits.remove(p.getUniqueId());
        if(event.wasClosed()||!main.ownsEvents()||!Main.permits(p,"edit")||!(event.getResponse() instanceof cn.nukkit.form.response.FormResponseCustom response))return;
        var world=main.getServer().getLevelByName(pending.world());if(world==null||world!=p.getLevel()||!world.isChunkLoaded(pending.x()>>4,pending.z()>>4))return;
        var b=world.getBlock(pending.x(),pending.y(),pending.z());if(p.distance(b)>8)return;
        var entity=world.getBlockEntity(b);if(!(entity instanceof BlockEntitySign sign)||!Arrays.equals(sign.getText(),pending.text())||!Objects.equals(record(b),pending.record())){p.sendMessage("[告示]告示牌已變更，請重新開啟。");return;}
        if(!main.allowed(p,pending.record()==null?plain(b):pending.record(),"edit"))return;
        try{
            if(pending.record()!=null){applyDefinition(p,b,sign,pending.record().type(),response.getInputResponse(0));return;}
            String[] lines=new String[4];for(int i=0;i<4;i++){lines[i]=response.getInputResponse(i);if(lines[i]==null||lines[i].length()>2048)return;}
            var change=new SignChangeEvent(b,p,lines);main.getServer().getPluginManager().callEvent(change);if(!change.isCancelled())sign.setText(change.getLines());
        }catch(Exception|LinkageError error){main.getLogger().warning("告示牌表單更新失敗："+error.getClass().getSimpleName());}
    }
    private SignRegistry.SignRecord record(Block b){return main.getRegistry().at(b.getLevel().getFolderName(),b.getFloorX(),b.getFloorY(),b.getFloorZ()).orElse(null);}
    private SignRegistry.SignRecord plain(Block b){return new SignRegistry.SignRecord(-1,b.getFloorX(),b.getFloorY(),b.getFloorZ(),b.getLevel().getFolderName(),0,null,null,0);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event){
        if(!main.ownsEvents())return;
        if(event.getAction()!=PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)return;
        Block b=event.getBlock();var r=record(b);Player p=event.getPlayer();String mode=modes.get(p.getUniqueId());
        var entity=b.getLevel().getBlockEntity(b);
        if(!(entity instanceof BlockEntitySign sign)){if(r!=null){event.setCancelled();p.sendMessage("[告示]告示牌資料尚未就緒，請稍後再試。");}return;}
        if(mode!=null){
            event.setCancelled();
            if(!Main.permits(p,mode.equals("set")?"set":mode)){modes.remove(p.getUniqueId());setups.remove(p.getUniqueId());return;}
            if(mode.equals("edit")){modes.remove(p.getUniqueId());openEdit(p,b,sign,r);return;}
            if(mode.equals("set")){modes.remove(p.getUniqueId());var setup=setups.remove(p.getUniqueId());if(setup!=null)try{applyDefinition(p,b,sign,setup.type(),setup.code());}catch(Exception|LinkageError error){main.getLogger().warning("告示牌功能設定失敗："+error.getClass().getSimpleName());}return;}
            // Plain text must never be pasted onto managed signs to alter business presentation.
            if(r!=null||!main.allowed(p,plain(b),mode)){p.sendMessage("[告示]此處無法複製或貼上；需要保護服務授權。");return;}
            if(mode.equals("copy"))clips.put(p.getUniqueId(),sign.getText().clone());
            else{String[] text=clips.get(p.getUniqueId());if(text==null)return;sign.setText(text.clone());}
            modes.remove(p.getUniqueId());p.sendMessage("[告示]操作完成。");return;
        }
        if(r!=null){event.setCancelled();try{if(!Main.activate(p,r))p.sendMessage("[告示]目前無法使用此告示牌。");}catch(Exception|LinkageError error){main.getLogger().warning("告示牌動作失敗 id="+r.id()+"："+error.getClass().getSimpleName());}}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(SignChangeEvent event){
        if(prepared.contains(event)||!main.ownsEvents())return;
        var old=record(event.getBlock());Integer type=TYPES.get(event.getLine(0).toLowerCase(Locale.ROOT));
        if(old==null&&type==null)return;
        if(old==null){String issue=SignInput.validate(type,event.getLines());if(issue!=null){event.setCancelled();event.getPlayer().sendMessage("[告示]"+issue);return;}}
        var b=event.getBlock();var r=old==null?new SignRegistry.SignRecord(main.getRegistry().nextId(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),b.getLevel().getFolderName(),type,event.getLine(1),null,-1):old;
        if(!main.canDefine(event.getPlayer(),r.type())||!main.allowed(event.getPlayer(),r,old==null?"create":"edit")){event.setCancelled();return;}
        // Managed edits go through providers; never silently convert a business sign to plain text.
        try{String[] lines=main.prepare(event.getPlayer(),r,event.getLines().clone());if(lines==null||lines.length!=4){event.setCancelled();return;}main.getRegistry().upsert(r);for(int i=0;i<4;i++)event.setLine(i,lines[i]);}
        catch(Exception|LinkageError error){event.setCancelled();main.getLogger().warning("告示牌更新失敗："+error.getClass().getSimpleName());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void broken(BlockBreakEvent event){if(!main.ownsEvents())return;var r=record(event.getBlock());if(r==null)return;if(!main.allowed(event.getPlayer(),r,"break")){event.setCancelled();return;}try{main.getRegistry().removeRecord(r.id());}catch(Exception error){event.setCancelled();main.getLogger().warning("告示牌刪除失敗："+error.getClass().getSimpleName());}}
    @EventHandler public void quit(PlayerQuitEvent e){modes.remove(e.getPlayer().getUniqueId());clips.remove(e.getPlayer().getUniqueId());edits.remove(e.getPlayer().getUniqueId());setups.remove(e.getPlayer().getUniqueId());}
}






