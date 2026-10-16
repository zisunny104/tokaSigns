package dev.toka.signs;
import org.junit.Test;
import static org.junit.Assert.*;
import dev.toka.storage.*;
import java.nio.file.*;
public class SignRegistryTest {
 private static final String DATA="[{\"id\":7,\"x\":-1,\"y\":64,\"z\":16,\"level\":\"世界\",\"type\":11,\"code\":\"spawn\",\"info\":null,\"ticks\":25}]";
 @Test public void formatsAndIndexPreserveLegacyData()throws Exception{
  for(Backend backend:Backend.values()){
   var config=new StoreConfig(backend,Files.createTempDirectory("signs-"),1);
   try(var signs=new SignRegistry(config)){signs.importSnapshot(DATA);assertEquals(7,signs.at("世界",-1,64,16).orElseThrow().id());assertNull(signs.at("世界",-1,64,16).orElseThrow().info());assertTrue(signs.at("其他世界",-1,64,16).isEmpty());}
   try(var signs=new SignRegistry(config)){assertEquals(java.util.Set.of(7),signs.referencesWorld("世界"));signs.importSnapshot(DATA);assertThrows(IllegalStateException.class,()->signs.importSnapshot(DATA.replace("spawn","other")));}
  }
 }
 @Test public void duplicateCoordinatesRejectWholeSnapshot()throws Exception{
  try(var signs=new SignRegistry(new StoreConfig(Backend.SQLITE,Files.createTempDirectory("signs-duplicate-"),1))){assertThrows(IllegalArgumentException.class,()->signs.importSnapshot("["+DATA.substring(1,DATA.length()-1)+","+DATA.substring(1,DATA.length()-1).replace("\"id\":7","\"id\":8")+"]"));assertTrue(signs.records().isEmpty());}
 }
}
