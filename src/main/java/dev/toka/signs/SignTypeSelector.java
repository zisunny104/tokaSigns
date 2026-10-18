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