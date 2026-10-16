package dev.toka.signs;

import com.google.gson.*;
import dev.toka.storage.*;
import java.io.IOException;
import java.util.*;

/** Immutable records and indexed coordinates; unknown business types remain opaque. */
public final class SignRegistry implements AutoCloseable {
    public record SignRecord(int id,int x,int y,int z,String world,int type,String code,String info,int ticks){}
    private final DocumentStore store;
    private JsonArray source;
    private Map<Integer,SignRecord> ids=Map.of();
    private Map<String,SignRecord> locations=Map.of();
    public SignRegistry(StoreConfig config)throws IOException{
        store=DocumentStore.open(config);
        try{
            var doc=store.load("registry");
            if(doc.isPresent()){
                if(doc.get().get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unknown schema");
                source=doc.get().getAsJsonArray("records");load(source);
            }
        }catch(IOException|RuntimeException error){try{store.close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private static String key(String world,int x,int y,int z){return world+"\u0000"+x+","+y+","+z;}
    private static SignRecord parse(JsonObject row){
        String world=row.get("level").getAsString();
        var record=new SignRecord(row.get("id").getAsBigDecimal().intValueExact(),row.get("x").getAsBigDecimal().intValueExact(),row.get("y").getAsBigDecimal().intValueExact(),row.get("z").getAsBigDecimal().intValueExact(),world,row.get("type").getAsBigDecimal().intValueExact(),row.has("code")&&!row.get("code").isJsonNull()?row.get("code").getAsString():null,row.has("info")&&!row.get("info").isJsonNull()?row.get("info").getAsString():null,row.get("ticks").getAsBigDecimal().intValueExact());
        if(record.id()<0||world.isBlank()||world.length()>255||(record.code()!=null&&record.code().length()>8192)||(record.info()!=null&&record.info().length()>8192))throw new IllegalArgumentException("Invalid sign");
        return record;
    }
    private void load(JsonArray rows){
        if(rows.size()>100000)throw new IllegalArgumentException("Registry too large");
        var nextIds=new HashMap<Integer,SignRecord>();var nextLocations=new HashMap<String,SignRecord>();
        for(var element:rows){var row=parse(element.getAsJsonObject());
            if(nextIds.putIfAbsent(row.id(),row)!=null||nextLocations.putIfAbsent(key(row.world(),row.x(),row.y(),row.z()),row)!=null)throw new IllegalArgumentException("Duplicate id or location");
        }
        ids=Map.copyOf(nextIds);locations=Map.copyOf(nextLocations);
    }
    public synchronized void importSnapshot(String json)throws IOException{
        JsonArray rows=JsonParser.parseString(json).getAsJsonArray();
        if(source!=null){if(!source.equals(rows))throw new IllegalStateException("Destination differs; no overwrite");return;}
        var oldIds=ids;var oldLocations=locations;
        try{
            load(rows);var doc=new JsonObject();doc.addProperty("schema",1);doc.add("records",rows);
            store.save("registry",doc);source=rows.deepCopy();
        }catch(IOException|RuntimeException error){ids=oldIds;locations=oldLocations;throw error;}
    }
    public Optional<SignRecord> at(String world,int x,int y,int z){return Optional.ofNullable(locations.get(key(world,x,y,z)));}
    public Collection<SignRecord> records(){return List.copyOf(ids.values());}
    public Set<Integer> referencesWorld(String world){var result=new HashSet<Integer>();for(var row:ids.values())if(row.world().equals(world))result.add(row.id());return Set.copyOf(result);}
    public void close()throws IOException{store.close();}
}
