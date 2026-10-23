package dev.toka.signs;

import cn.nukkit.Player;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.Server;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Method;
import java.util.Objects;

/** Optional stable-ID bridge; without both services, tpl signs are unavailable. */
final class LandPortalIntegration {
    private static Method displayLookup;
    private static Method portalTeleport;
    private static boolean actionRegistered;
    private static boolean displayRegistered;

    private LandPortalIntegration() {}

    static boolean connect(Main signs) {
        Plugin land = enabled("tokaLand");
        Plugin portal = enabled("tokaPortal");
        if (land == null || portal == null) return false;
        try {
            Class<?> landApi = Class.forName("dev.toka.land.Main", false, land.getClass().getClassLoader());
            displayLookup = landApi.getMethod("queryTeleportDisplayJson", String.class);
            Class<?> portalApi = Class.forName("dev.toka.pl.tokaPortal.utils.Portal", false, portal.getClass().getClassLoader());
            portalTeleport = portalApi.getMethod("toLandClaim", Player.class, String.class);

            signs.registerDisplayDataProvider(12, (player, sign) -> display(sign.code()));
            displayRegistered = true;
            signs.registerAction(12, (player, sign) -> teleport(player, sign.code()));
            actionRegistered = true;
            signs.getLogger().info("已連接 tokaLand／tokaPortal；tpl 告示牌使用穩定領地 ID。zero 舊功能仍由 zero 設定控制切換。");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            close(signs);
            signs.getLogger().warning("tokaLand／tokaPortal API 不可用；tpl 告示牌暫停：" + error.getClass().getSimpleName());
            return false;
        }
    }

    private static String display(String claimId) {
        if (displayLookup == null) return null;
        try {
            JsonObject reply = JsonParser.parseString(Objects.toString(displayLookup.invoke(null, claimId), ""))
                    .getAsJsonObject();
            if (!"OK".equals(reply.get("status").getAsString())) return null;
            JsonObject target = reply.getAsJsonArray("targets").get(0).getAsJsonObject();
            JsonObject result = new JsonObject();
            result.addProperty("name", target.get("name").getAsString());
            return result.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return null;
        }
    }

    private static boolean teleport(Player player, String claimId) {
        if (portalTeleport == null) return false;
        try { return Boolean.TRUE.equals(portalTeleport.invoke(null, player, claimId)); }
        catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return false; }
    }

    static void close(Main signs) {
        if (actionRegistered) try { signs.unregisterAction(12); } catch (RuntimeException | LinkageError ignored) { }
        if (displayRegistered) try { signs.unregisterDisplayDataProvider(12); } catch (RuntimeException | LinkageError ignored) { }
        actionRegistered = false;
        displayRegistered = false;
        displayLookup = null;
        portalTeleport = null;
    }

    private static Plugin enabled(String name) {
        Plugin plugin = Server.getInstance().getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }
}
