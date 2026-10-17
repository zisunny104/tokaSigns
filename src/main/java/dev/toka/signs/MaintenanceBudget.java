package dev.toka.signs;
/** Pure adaptive scheduling model; limits are ceilings, not fixed work counts. */
final class MaintenanceBudget {
    private double normalNanos=250_000,forceNanos=2_000_000;
    record Slice(int records,long nanos){}
    Slice plan(boolean force,double tps,double usage,double minTps,double millis,int ceiling){
        if(!Double.isFinite(tps)||!Double.isFinite(usage)||tps<minTps||usage>=95)return new Slice(0,0);
        double headroom=Math.max(.05,Math.min(1,1-usage/100.0));
        long budget=(long)(Math.max(.1,Math.min(10,millis))*1_000_000*headroom*Math.min(1,tps/20));
        double estimate=force?forceNanos:normalNanos;
        int records=Math.max(1,Math.min(Math.max(1,Math.min(128,ceiling)),(int)(budget/estimate)));
        return new Slice(records,budget);
    }
    void observe(boolean force,long nanos){double value=Math.max(1,nanos);if(force)forceNanos=forceNanos*.75+value*.25;else normalNanos=normalNanos*.75+value*.25;}
}
