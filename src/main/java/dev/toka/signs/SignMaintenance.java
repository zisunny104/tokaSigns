package dev.toka.signs;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.math.Vector3;
import com.google.gson.*;
import java.util.*;

/** Incremental main-thread world inspection: never loads chunks or touches storage. */
final class SignMaintenance {
    private final Main main;
    private Job job;
    private long sequence;
    private final MaintenanceBudget pacing=new MaintenanceBudget();
    private boolean waiting;
    private static final class Job {
        boolean force; long id; String mode,state="RUNNING"; int total,done,changed; CommandSender owner;
        Iterator<SignRegistry.SignRecord> records;
        Map<String,Integer> counts=new TreeMap<>(); JsonArray samples=new JsonArray();
    }
    SignMaintenance(Main main){this.main=main;}
    JsonObject inspect(int id,CommandSender actor,boolean refresh,boolean force){
        var out=new JsonObject();out.addProperty("id",id);
        if(force&&!Main.permits(actor,"force"))return result(out,"DENIED");
        var r=Main.getRegistry().byId(id).orElse(null);
        if(r==null)return result(out,"NOT_REGISTERED");
        out.add("record",SignRegistry.encode(r));
        if(!Main.permits(actor,refresh?"refresh":"inspect")&&!Main.permits(actor,"check"))return result(out,"DENIED");
        if(refresh&&!Main.permits(actor,"refresh"))return result(out,"DENIED");
        if(refresh&&!main.ownsEvents())return result(out,"NOT_OWNER");
        var world=main.getServer().getLevelByName(r.world());
        if(world==null)return result(out,"WORLD_UNLOADED");
        if(!world.isChunkLoaded(r.x()>>4,r.z()>>4)){
            if(!force)return result(out,"CHUNK_UNLOADED");
            try{
                if(!world.loadChunk(r.x()>>4,r.z()>>4,false))return result(out,"CHUNK_NOT_FOUND");
                return inspect(id,actor,refresh,false);
            }finally{world.unloadChunkRequest(r.x()>>4,r.z()>>4,true);}
        }
        var pos=new Vector3(r.x(),r.y(),r.z());
        var block=world.getBlock(r.x(),r.y(),r.z());out.addProperty("block",block.getName());
        var entity=world.getBlockEntity(pos);
        if(!(entity instanceof BlockEntitySign sign))return result(out,"SIGN_ENTITY_MISSING");
        String[] before=sign.getText().clone();out.add("actualText",new Gson().toJsonTree(before));
        if(!main.hasDefinition(r.type()))return result(out,"DEFINITION_UNAVAILABLE");
        Player player=actor instanceof Player p?p:null;
        try {
            String[] expected=main.prepare(player,r,new String[]{"",r.code()==null?"":r.code(),"",""});
            if(expected==null||expected.length!=4||Arrays.stream(expected).anyMatch(Objects::isNull))return result(out,"REFERENCE_UNAVAILABLE");
            out.add("expectedText",new Gson().toJsonTree(expected));
            if(Arrays.equals(before,expected))return result(out,"MATCH");
            if(!refresh)return result(out,"TEXT_DIFFERS");
            if(!Main.permits(actor,"type."+r.type())||!main.hasPolicy())return result(out,"DENIED");
            if(player!=null&&(!player.isOnline()||!main.allowed(player,r,"edit")))return result(out,"DENIED");
            // Interactive refresh respects other protection listeners; console is explicit administrator maintenance.
            if(player!=null){
                var change=new cn.nukkit.event.block.SignChangeEvent(block,player,expected.clone());
                SignEvents.firePrepared(main,change);
                if(change.isCancelled())return result(out,"CANCELLED_BY_PLUGIN");
                expected=change.getLines();
            }
            if(!Objects.equals(Main.getRegistry().byId(id).orElse(null),r)||world.getBlockEntity(pos)!=sign||!Arrays.equals(before,sign.getText()))return result(out,"CHANGED_DURING_CHECK");
            sign.setText(expected);
            out.add("afterText",new Gson().toJsonTree(sign.getText()));
            return result(out,Arrays.equals(expected,sign.getText())?"UPDATED":"UPDATE_REJECTED");
        }catch(Exception|LinkageError error){out.addProperty("error",error.getClass().getSimpleName());return result(out,"PROVIDER_ERROR");}
    }
    private JsonObject result(JsonObject out,String status){out.addProperty("status",status);return out;}
    long start(String mode,Integer id,CommandSender actor,boolean force){
        if(!Set.of("check","refresh").contains(mode))throw new IllegalArgumentException("mode must be check or refresh");
        if(!Main.permits(actor,mode))throw new IllegalArgumentException("沒有工作權限");
        if(force&&!Main.permits(actor,"force"))throw new IllegalArgumentException("沒有強制載入權限");
        if(job!=null&&job.state.equals("RUNNING"))throw new IllegalStateException("已有維護工作執行中，請查看 /sign jobs");
        if(id!=null&&Main.getRegistry().byId(id).isEmpty())throw new IllegalArgumentException("找不到告示牌 ID");
        Job next=new Job();next.id=++sequence;next.mode=mode;next.owner=actor;next.force=force;
        next.total=id==null?Main.getRegistry().size():1;
        next.records=id==null?Main.getRegistry().maintenanceIterator():List.of(Main.getRegistry().byId(id).orElseThrow()).iterator();job=next;
        return next.id;
    }
    void tick(){
        if(job==null||!job.state.equals("RUNNING"))return;
        if((job.owner instanceof Player p&&!p.isOnline())||!Main.permits(job.owner,job.mode)||(job.force&&!Main.permits(job.owner,"force"))){finish("CANCELLED");return;}
        String prefix=job.force?"maintenance.force.":"maintenance.normal.";
        var slice=pacing.plan(job.force,main.getServer().getTicksPerSecond(),main.getServer().getTickUsage(),
            main.getConfig().getDouble(prefix+"min-tps",job.force?18:17),
            main.getConfig().getDouble(prefix+"budget-ms",job.force?1:2),
            main.getConfig().getInt(prefix+"max-records",job.force?2:32));
        waiting=slice.records()==0;if(waiting)return;
        int limit=slice.records();long budget=slice.nanos();
        long start=System.nanoTime();
        for(int n=0;n<limit&&System.nanoTime()-start<budget;n++){
            if(!job.records.hasNext()){finish("DONE");return;}
            var r=job.records.next();JsonObject report;long recordStart=System.nanoTime();
            try{report=inspect(r.id(),job.owner,job.mode.equals("refresh"),job.force);}
            catch(Exception|LinkageError error){report=result(new JsonObject(),"INSPECTION_ERROR");report.addProperty("id",r.id());}
            pacing.observe(job.force,System.nanoTime()-recordStart);
            String status=report.get("status").getAsString();job.done++;job.counts.merge(status,1,Integer::sum);
            if(status.equals("UPDATED"))job.changed++;
            if(!status.equals("MATCH")&&job.samples.size()<20)job.samples.add(report);
        }
        if(!job.records.hasNext())finish("DONE");
    }
    private void finish(String state){job.state=state;job.records=Collections.emptyIterator();job.owner.sendMessage("[告示]維護工作 #"+job.id+" "+state+"："+job.done+"/"+job.total+"，更新 "+job.changed+"；/sign jobs 查看結果。");}
    String status(){
        var out=new JsonObject();if(job==null){out.addProperty("state","IDLE");return out.toString();}
        out.addProperty("waitingForPerformance",waiting&&job.state.equals("RUNNING"));out.addProperty("force",job.force);out.addProperty("jobId",job.id);out.addProperty("mode",job.mode);out.addProperty("state",job.state);
        out.addProperty("processed",job.done);out.addProperty("total",job.total);out.addProperty("updated",job.changed);
        out.add("counts",new Gson().toJsonTree(job.counts));out.add("samples",job.samples.deepCopy());out.addProperty("sampleLimit",20);return out.toString();
    }
    boolean cancel(){if(job==null||!job.state.equals("RUNNING"))return false;finish("CANCELLED");return true;}
    void close(){if(job!=null){job.state="CANCELLED";job.records=Collections.emptyIterator();}job=null;}
}
