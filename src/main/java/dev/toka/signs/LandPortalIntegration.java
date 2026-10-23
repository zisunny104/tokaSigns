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
            registerRegionAndPoint(signs, portal);
            signs.getLogger().info(Main.tr("log.landConnected"));
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            close(signs);
            signs.getLogger().warning(Main.tr("log.landUnavailable", error.getClass().getSimpleName()));
            return false;
        }
    }


    private static Method portalRegion, portalPoint, publicPoints, findPoint, regionId;

    /** Region signs (10) and public point signs (11): the sign holds the name; tokaPortal does the teleport. */
    private static void registerRegionAndPoint(Main signs, Plugin portal) throws ReflectiveOperationException {
        Class<?> portalApi = Class.forName("dev.toka.pl.tokaPortal.utils.Portal", false, portal.getClass().getClassLoader());
        portalRegion = portalApi.getMethod("toRegion", String.class, Player.class);
        portalPoint = portalApi.getMethod("toPoint", String.class, Player.class);
        publicPoints = portal.getClass().getMethod("getPublicPoints");
        Plugin regions = enabled("tokaRegions");
        if (regions != null) regionId = Class.forName("dev.toka.regions.Main", false, regions.getClass().getClassLoader()).getMethod("regionIdByName", String.class);
        signs.registerDisplayDataProvider(10, (player, sign) -> regionDisplay(sign.code()));
        signs.registerAction(10, (player, sign) -> regionKnown(sign.code()) && call(portalRegion, sign.code(), player));
        signs.registerDisplayDataProvider(11, (player, sign) -> pointDisplay(sign.code()));
        signs.registerAction(11, (player, sign) -> pointJson(sign.code()) != null && call(portalPoint, sign.code(), player));
        extraRegistered = true;
    }

    private static boolean extraRegistered;

    private static boolean call(Method method, String name, Player player) {
        try { method.invoke(null, name, player); return true; }
        catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return false; }
    }

    private static boolean regionKnown(String name) {
        if (regionId == null || name == null) return false;
        try { return regionId.invoke(null, name) != null; } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return false; }
    }

    private static String regionDisplay(String name) {
        if (!regionKnown(name)) return null;
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        data.addProperty("info", name);
        return data.toString();
    }

    private static JsonObject pointJson(String name) {
        if (publicPoints == null || name == null) return null;
        try {
            Object service = publicPoints.invoke(Server.getInstance().getPluginManager().getPlugin("tokaPortal"));
            Object json = service.getClass().getMethod("findJson", String.class).invoke(service, name);
            return json == null ? null : JsonParser.parseString(json.toString()).getAsJsonObject();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return null; }
    }

    private static String pointDisplay(String name) {
        JsonObject point = pointJson(name);
        if (point == null) return null;
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        data.addProperty("info", point.has("info") && !point.get("info").isJsonNull() ? point.get("info").getAsString() : "");
        return data.toString();
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
        if (extraRegistered) for (int type : new int[]{10, 11}) {
            try { signs.unregisterAction(type); } catch (RuntimeException | LinkageError ignored) { }
            try { signs.unregisterDisplayDataProvider(type); } catch (RuntimeException | LinkageError ignored) { }
        }
        extraRegistered = false;
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
