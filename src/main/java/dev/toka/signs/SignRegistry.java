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
    /** General replacement API: callers own extraction and handoff. */
    public synchronized void replaceRecords(String json)throws IOException {
        JsonArray rows=JsonParser.parseString(json).getAsJsonArray();
        var oldIds=ids;var oldLocations=locations;
        try {
            load(rows);var doc=new JsonObject();doc.addProperty("schema",1);doc.add("records",rows);
            store.save("registry",doc);
            if(!store.load("registry").orElseThrow().equals(doc))throw new IOException("Registry readback mismatch");
            source=rows.deepCopy();
        }catch(IOException|RuntimeException error){ids=oldIds;locations=oldLocations;throw error;}
    }
    public synchronized void replaceIfEmptyOrEqual(String json)throws IOException{
        JsonArray rows=JsonParser.parseString(json).getAsJsonArray();
        if(source!=null&&!source.isEmpty()&&!source.equals(rows))throw new IllegalStateException("Destination differs; no overwrite");
        replaceRecords(json);
    }
    public synchronized String recordsJson(){return source==null?"[]":source.toString();}
    public synchronized void upsert(SignRecord record)throws IOException{
        JsonArray rows=source==null?new JsonArray():source.deepCopy();
        for(int i=rows.size()-1;i>=0;i--)if(rows.get(i).getAsJsonObject().get("id").getAsInt()==record.id())rows.remove(i);
        rows.add(encode(record));replaceRecords(rows.toString());
    }
    public synchronized void upsertRecord(String json)throws IOException{upsert(parse(JsonParser.parseString(json).getAsJsonObject()));}
    public synchronized void removeRecord(int id)throws IOException{
        JsonArray rows=source==null?new JsonArray():source.deepCopy();
        for(int i=rows.size()-1;i>=0;i--)if(rows.get(i).getAsJsonObject().get("id").getAsInt()==id)rows.remove(i);
        replaceRecords(rows.toString());
    }
    public int nextId(){return ids.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1)+1;}
    public static JsonObject encode(SignRecord r){var row=new JsonObject();row.addProperty("id",r.id());row.addProperty("x",r.x());row.addProperty("y",r.y());row.addProperty("z",r.z());row.addProperty("level",r.world());row.addProperty("type",r.type());row.addProperty("code",r.code());row.addProperty("info",r.info());row.addProperty("ticks",r.ticks());return row;}    public Optional<SignRecord> at(String world,int x,int y,int z){return Optional.ofNullable(locations.get(key(world,x,y,z)));}
    public Collection<SignRecord> records(){return List.copyOf(ids.values());}
    public Set<Integer> referencesWorld(String world){var result=new HashSet<Integer>();for(var row:ids.values())if(row.world().equals(world))result.add(row.id());return Set.copyOf(result);}
    public void close()throws IOException{store.close();}
}


