package dev.toka.signs;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class SignTextFormatterTest {
    private static SignRegistry.SignRecord sign(int type, String code) {
        return new SignRegistry.SignRecord(1, 0, 64, 0, "world", type, code, null, 0);
    }

    @Test public void teleportStylesMatchLegacySigns() {
        JsonObject data = new JsonObject();
        data.addProperty("name", "中央廣場");
        data.addProperty("info", "歡迎光臨");
        assertArrayEquals(new String[]{"§r§a⦋傳送⦌§r", "§f歡迎光臨", "§e-", "§b點擊傳送"},
                SignTextFormatter.format(sign(10, "town"), data));
        assertArrayEquals(new String[]{"§r§a⦋傳送⦌§r", "§f歡迎光臨", "§f歡迎光臨", "§b點擊傳送"},
                SignTextFormatter.format(sign(11, "spawn"), data));
    }

    @Test public void shopAndManagementStylesMatchLegacySigns() {
        JsonObject data = new JsonObject();
        data.addProperty("itemName", "玻璃");
        data.addProperty("quantity", 16);
        data.addProperty("amount", 25);
        assertArrayEquals(new String[]{"§r§6⦋購買⦌§r", "§f玻璃", "§f§a16個 §b25銀幣", "§e^"},
                SignTextFormatter.format(sign(31, "glass"), data));
        assertArrayEquals(new String[]{"§r§e⦋回收⦌§r", "§f玻璃", "§f§e16個 §b25銀幣", "§e^"},
                SignTextFormatter.format(sign(32, "glass"), data));
        assertArrayEquals(new String[]{"§r§a⦋物品管理⦌§r", "", "", ""},
                SignTextFormatter.format(sign(33, null), null));
    }
}
