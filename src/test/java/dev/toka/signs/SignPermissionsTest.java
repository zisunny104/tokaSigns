package dev.toka.signs;
import cn.nukkit.command.CommandSender;
import java.lang.reflect.Proxy;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
public class SignPermissionsTest {
 private CommandSender sender(Set<String> granted){return (CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class<?>[]{CommandSender.class},(p,m,a)->m.getName().equals("hasPermission")&&granted.contains(a[0]));}
 @Test public void granularGrantDoesNotGrantOtherOperations(){
  var user=sender(Set.of("tokaSigns.help","tokaSigns.copy","tokaSigns.type.31"));
  assertTrue(Main.permits(user,"help"));assertTrue(Main.permits(user,"copy"));assertTrue(Main.permits(user,"type.31"));
  assertFalse(Main.permits(user,"paste"));assertFalse(Main.permits(user,"set"));assertFalse(Main.permits(user,"type.32"));
  assertTrue(Main.permits(sender(Set.of("tokaSigns.admin")),"edit"));
  assertFalse(Main.permits(sender(Set.of()),"status"));
 }
}
