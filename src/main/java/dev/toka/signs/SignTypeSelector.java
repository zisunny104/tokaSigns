package dev.toka.signs;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** CLI aliases for the functional sign types understood by the legacy sign system. */
final class SignTypeSelector {
    private static final Map<String,Integer> TYPES=Map.ofEntries(
        Map.entry("tpr",10),Map.entry("tpp",11),Map.entry("tpl",12),Map.entry("tps",13),
        Map.entry("pay",21),Map.entry("b",31),Map.entry("buy",31),Map.entry("s",32),
        Map.entry("sell",32),Map.entry("item",33),Map.entry("status",41)
    );
    /** Canonical word of each type, by id; used for Tab completion and the set window. */
    static final Map<Integer,String> NAMES=new java.util.TreeMap<>(Map.of(10,"tpr",11,"tpp",12,"tpl",13,"tps",21,"pay",31,"buy",32,"sell",33,"item",41,"status"));
    private static final Set<Integer> IDS=Set.copyOf(TYPES.values());
    private SignTypeSelector(){}
    static Integer parse(String value){
        if(value.equalsIgnoreCase("all"))return null;
        Integer type=TYPES.get(value.toLowerCase(Locale.ROOT));
        if(type!=null)return type;
        try{type=Integer.valueOf(value);}catch(NumberFormatException ignored){throw new IllegalArgumentException(Main.tr("err.ex.badType"));}
        if(!IDS.contains(type))throw new IllegalArgumentException(Main.tr("err.ex.badType"));
        return type;
    }
}