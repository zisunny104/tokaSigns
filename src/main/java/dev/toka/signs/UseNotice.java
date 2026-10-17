package dev.toka.signs;

import cn.nukkit.Player;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Tells a player that a sign could not be used: Display message first, chat as fallback, throttled per player. */
final class UseNotice {
    private final long windowMs;
    private final Map<UUID,Long> last=new HashMap<>();
    UseNotice(long windowMs){this.windowMs=windowMs;}

    /** Returns false when skipped by the throttle. The display attempt never throws; any failure falls back to chat. */
    boolean notice(UUID id,long nowMs,BooleanSupplier display,Runnable chat){
        Long before=last.get(id);
        if(before!=null&&nowMs-before<windowMs&&nowMs>=before)return false;
        if(last.size()>256)last.values().removeIf(t->nowMs-t>=windowMs);
        last.put(id,nowMs);
        boolean shown=false;
        try{shown=display.getAsBoolean();}catch(Exception|LinkageError ignored){}
        if(!shown)chat.run();
        return true;
    }
    void clear(){last.clear();}

    /** Calls tokaDisplay's flash by reflection; false when Display is missing, disabled or has no such method. */
    static boolean displayFlash(Player p,String text,int seconds){
        try{
            var plugin=p.getServer().getPluginManager().getPlugin("tokaDisplay");
            if(plugin==null||!plugin.isEnabled())return false;
            plugin.getClass().getMethod("flash",Player.class,String.class,int.class,float.class,String.class).invoke(null,p,text,seconds,-1f,null);
            return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}
    }
}
