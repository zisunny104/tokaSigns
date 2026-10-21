package dev.toka.signs;

import com.google.gson.JsonObject;

/** Canonical presentation for the functional sign types migrated from Zero. */
final class SignTextFormatter {
    private SignTextFormatter() {}

    static boolean supports(int type) {
        return type == 10 || type == 11 || type == 31 || type == 32 || type == 33;
    }

    static String[] format(SignRegistry.SignRecord sign, JsonObject data) {
        int type = sign.type();
        String code = sign.code() == null ? "" : sign.code();
        return switch (type) {
            case 10, 11 -> {
                require(data, "name", "info");
                yield new String[]{"§r§a⦋傳送⦌§r", data.get("name").getAsString(),
                        "§f" + data.get("info").getAsString(), "§b點擊傳送"};
            }
            case 31 -> {
                require(data, "itemName", "quantity", "amount");
                yield new String[]{"§r§6⦋購買⦌§r", code, data.get("itemName").getAsString(),
                        "§a" + integer(data, "quantity") + "個 §b" + integer(data, "amount") + "銀幣"};
            }
            case 32 -> {
                require(data, "itemName", "quantity", "amount");
                yield new String[]{"§r§e⦋回收⦌§r", code, data.get("itemName").getAsString(),
                        "§e" + integer(data, "quantity") + "個 §b" + integer(data, "amount") + "銀幣"};
            }
            case 33 -> new String[]{"§r§a⦋物品管理⦌§r", "", "", ""};
            default -> throw new IllegalArgumentException("No built-in sign style for type " + type);
        };
    }

    private static void require(JsonObject data, String... keys) {
        if (data == null) throw new IllegalArgumentException("Display data unavailable");
        for (String key : keys) {
            if (!data.has(key) || data.get(key).isJsonNull())
                throw new IllegalArgumentException("Missing display field " + key);
        }
    }

    private static String integer(JsonObject data, String key) {
        int value = data.get(key).getAsInt();
        if (value < 0) throw new IllegalArgumentException("Negative display field " + key);
        return Integer.toString(value);
    }
}
