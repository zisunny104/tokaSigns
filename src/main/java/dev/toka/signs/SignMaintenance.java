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
    private static final Gson GSON=new Gson();
    private static final class Job {
        boolean force; long id; String mode,state="RUNNING"; Integer type; int total,done,changed; CommandSender owner;
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
        if(!Main.permits(actor,refresh?"refresh":"inspect")&&!Main.permits(actor,"check")&&!(refresh&&Main.permits(actor,"repair")))return result(out,"DENIED");
        if(refresh&&!Main.permits(actor,"refresh")&&!Main.permits(actor,"repair"))return result(out,"DENIED");
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
        String[] before=sign.getText().clone();boolean beforeGlowing=sign.isGlowing(),expectedGlowing=main.glowEnabled();out.add("actualText",GSON.toJsonTree(before));out.addProperty("actualGlowing",beforeGlowing);out.addProperty("expectedGlowing",expectedGlowing);
        if(!main.hasDefinition(r.type()))return result(out,"DEFINITION_UNAVAILABLE");
        Player player=actor instanceof Player p?p:null;
        try {
            String[] input={"",r.code()==null?"":r.code(),"",""};
            String[] created=main.prepare(player,r,input);
            if(!validText(created))return result(out,"REFERENCE_UNAVAILABLE");
            String[] refreshed=main.prepareRefresh(player,r,input);
            if(refresh&&!validText(refreshed))return result(out,"REFERENCE_UNAVAILABLE");
            String[] expected=refresh?refreshed:created;
            out.add("expectedText",GSON.toJsonTree(created));
            if(validText(refreshed))out.add("expectedRefreshText",GSON.toJsonTree(refreshed));
            boolean textMatches=Arrays.equals(before,expected)||(!refresh&&validText(refreshed)&&Arrays.equals(before,refreshed)),glowMatches=beforeGlowing==expectedGlowing;if(textMatches&&glowMatches)return result(out,"MATCH");
            if(!refresh)return result(out,textMatches?"STYLE_DIFFERS":"TEXT_DIFFERS");
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
            if(!textMatches)sign.setText(expected);sign.setGlowing(expectedGlowing);
            out.add("afterText",GSON.toJsonTree(sign.getText()));out.addProperty("afterGlowing",sign.isGlowing());
            return result(out,Arrays.equals(expected,sign.getText())&&sign.isGlowing()==expectedGlowing?"UPDATED":"UPDATE_REJECTED");
        }catch(Exception|LinkageError error){out.addProperty("error",error.getClass().getSimpleName());return result(out,"PROVIDER_ERROR");}
    }
    private static boolean validText(String[] lines){return lines!=null&&lines.length==4&&Arrays.stream(lines).noneMatch(Objects::isNull);}
    private JsonObject result(JsonObject out,String status){out.addProperty("status",status);return out;}
    long start(String mode,Integer id,CommandSender actor,boolean force){return start(mode,id,null,actor,force);}
    long start(String mode,Integer id,Integer type,CommandSender actor,boolean force){
        if(!Set.of("check","refresh","repair").contains(mode))throw new IllegalArgumentException("mode must be check, refresh or repair");
        if(!Main.permits(actor,mode))throw new IllegalArgumentException(Main.tr("err.ex.noJobPermission"));
        if(force&&!Main.permits(actor,"force"))throw new IllegalArgumentException(Main.tr("err.ex.noForcePermission"));
        if(job!=null&&job.state.equals("RUNNING"))throw new IllegalStateException(Main.tr("err.ex.jobRunning"));
        if(id!=null&&Main.getRegistry().byId(id).isEmpty())throw new IllegalArgumentException(Main.tr("err.ex.signNotFound"));
        if(id!=null&&type!=null)throw new IllegalArgumentException("Choose id or type");
        Job next=new Job();next.id=++sequence;next.mode=mode;next.owner=actor;next.force=force;next.type=type;
        if(id!=null){next.total=1;next.records=List.of(Main.getRegistry().byId(id).orElseThrow()).iterator();}
        else if(type==null){next.total=Main.getRegistry().size();next.records=Main.getRegistry().maintenanceIterator();}
        else{var selected=Main.getRegistry().records().stream().filter(record->record.type()==type).toList();next.total=selected.size();next.records=selected.iterator();}
        job=next;return next.id;
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
            try{report=inspect(r.id(),job.owner,!job.mode.equals("check"),job.force);}
            catch(Exception|LinkageError error){report=result(new JsonObject(),"INSPECTION_ERROR");report.addProperty("id",r.id());}
            pacing.observe(job.force,System.nanoTime()-recordStart);
            String status=report.get("status").getAsString();job.done++;job.counts.merge(status,1,Integer::sum);
            if(status.equals("UPDATED"))job.changed++;
            if(!status.equals("MATCH")&&job.samples.size()<20)job.samples.add(report);
        }
        if(!job.records.hasNext())finish("DONE");
    }
    private void finish(String state){job.state=state;job.records=Collections.emptyIterator();job.owner.sendMessage(Main.tr("msg.jobFinished",job.id,state,job.done,job.total,job.changed));}
    String status(){
        var out=new JsonObject();if(job==null){out.addProperty("state","IDLE");return out.toString();}
        out.addProperty("waitingForPerformance",waiting&&job.state.equals("RUNNING"));out.addProperty("force",job.force);out.addProperty("jobId",job.id);out.addProperty("mode",job.mode);if(job.type!=null)out.addProperty("type",job.type);out.addProperty("state",job.state);
        out.addProperty("processed",job.done);out.addProperty("total",job.total);out.addProperty("updated",job.changed);
        out.add("counts",GSON.toJsonTree(job.counts));out.add("samples",job.samples.deepCopy());out.addProperty("sampleLimit",20);return out.toString();
    }
    boolean cancel(){if(job==null||!job.state.equals("RUNNING"))return false;finish("CANCELLED");return true;}
    void close(){if(job!=null){job.state="CANCELLED";job.records=Collections.emptyIterator();}job=null;}
}
