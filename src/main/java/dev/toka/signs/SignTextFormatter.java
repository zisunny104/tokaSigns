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
        return switch (type) {
            case 10 -> {
                require(data, "info");
                // Match the active legacy region sign after its first successful use.
                yield new String[]{"§r§a⦋傳送⦌§r", "§f" + data.get("info").getAsString(),
                        "§e-", "§b點擊傳送"};
            }
            case 11 -> {
                require(data, "info");
                // The legacy public-point sign repeats its display text on both lines.
                String info = "§f" + data.get("info").getAsString();
                yield new String[]{"§r§a⦋傳送⦌§r", info, info, "§b點擊傳送"};
            }
            case 31 -> {
                require(data, "itemName", "quantity", "amount");
                yield new String[]{"§r§6⦋購買⦌§r", "§f" + data.get("itemName").getAsString(),
                        "§f§a" + integer(data, "quantity") + "個 §b" + integer(data, "amount") + "銀幣", "§e^"};
            }
            case 32 -> {
                require(data, "itemName", "quantity", "amount");
                yield new String[]{"§r§e⦋回收⦌§r", "§f" + data.get("itemName").getAsString(),
                        "§f§e" + integer(data, "quantity") + "個 §b" + integer(data, "amount") + "銀幣", "§e^"};
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
