package dev.toka.signs;
import org.junit.Test;
import static org.junit.Assert.*;
public class MaintenanceBudgetTest {
 @Test public void pausesAndAdaptsModesIndependently(){
  var b=new MaintenanceBudget();
  assertEquals(0,b.plan(false,16,20,17,2,32).records());
  assertEquals(0,b.plan(true,20,96,18,1,2).records());
  var healthy=b.plan(false,20,10,17,2,32);
  var busy=b.plan(false,20,85,17,2,32);
  assertTrue(healthy.records()>busy.records());assertTrue(healthy.nanos()>busy.nanos());
  int normal=healthy.records();b.observe(true,30_000_000);
  assertEquals(normal,b.plan(false,20,10,17,2,32).records());
  assertEquals(1,b.plan(true,20,10,18,1,2).records());
  b.observe(false,10_000_000);assertTrue(b.plan(false,20,10,17,2,32).records()<normal);
 }
}
