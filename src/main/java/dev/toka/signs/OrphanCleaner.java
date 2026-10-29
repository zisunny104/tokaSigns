package dev.toka.signs;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockSignPost;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.math.Vector3;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/**
 * Keeps the registry free of records whose sign is gone, without anyone asking. A pass looks at the records in loaded chunks only, a bit
 * at a time. A record is removed when its place held no sign block in several passes in a row; a sign block with lost data is never removed,
 * and a pass that finds more than the limit (a restored or damaged world) removes nothing. Every removed record is written to removed-signs.log.
 */
final class OrphanCleaner {
    private enum Look { ALIVE, SKIPPED, GONE, DATA_LOST }

    private final Main main;
    private final OrphanTracker tracker = new OrphanTracker();
    private final WarnOnce warned = new WarnOnce();
    private Iterator<SignRegistry.SignRecord> records;
    private long nextPass = System.currentTimeMillis() + 5 * 60_000L;

    OrphanCleaner(Main main) { this.main = main; }

    /** A block that is a sign, whatever its wood; the place of a record that holds one is never free. */
    static boolean isSignBlock(Block block) {
        return block instanceof BlockSignPost || block.getName().toLowerCase(Locale.ROOT).contains("sign");
    }

    private Look look(SignRegistry.SignRecord record) {
        var world = main.getServer().getLevelByName(record.world());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) return Look.SKIPPED;
        if (world.getBlockEntity(new Vector3(record.x(), record.y(), record.z())) instanceof BlockEntitySign) return Look.ALIVE;
        return isSignBlock(world.getBlock(record.x(), record.y(), record.z())) ? Look.DATA_LOST : Look.GONE;
    }

    void tick() {
        var config = main.getConfig();
        if (!config.getBoolean("maintenance.auto-clean.enabled", true) || !main.ownsEvents()) { records = null; return; }
        long now = System.currentTimeMillis();
        if (records == null) {
            if (now < nextPass) return;
            if (main.maintenanceRunning() || main.getServer().getTicksPerSecond() < config.getDouble("maintenance.normal.min-tps", 17)) { nextPass = now + 60_000L; return; }
            tracker.beginPass();
            records = Main.getRegistry().records().iterator();
            return;
        }
        long start = System.nanoTime();
        for (int n = 0; n < 32 && System.nanoTime() - start < 1_000_000L; n++) {
            if (!records.hasNext()) { finish(now); return; }
            var record = records.next();
            switch (look(record)) {
                case ALIVE, DATA_LOST -> tracker.alive(record.id());
                case GONE -> tracker.missing(record);
                case SKIPPED -> { }
            }
        }
        if (!records.hasNext()) finish(now);
    }

    private void finish(long now) {
        records = null;
        var config = main.getConfig();
        nextPass = now + Math.max(1, config.getInt("maintenance.auto-clean.interval-minutes", 10)) * 60_000L;
        var registry = Main.getRegistry();
        Set<Integer> ids = new HashSet<>();
        for (var record : registry.records()) ids.add(record.id());
        tracker.retain(ids);
        var confirmed = tracker.confirmed(Math.max(2, config.getInt("maintenance.auto-clean.confirm-passes", 3)));
        if (confirmed.isEmpty()) return;
        int limit = Math.max(1, config.getInt("maintenance.auto-clean.max-per-pass", 20));
        if (confirmed.size() > limit) {
            if (warned.first("held")) main.getLogger().warning(Main.tr("log.autoCleanHeld", confirmed.size(), limit));
            return;
        }
        List<SignRegistry.SignRecord> remove = new ArrayList<>();
        for (var record : confirmed) {
            if (look(record) == Look.GONE && registry.byId(record.id()).filter(record::equals).isPresent()) remove.add(record);
        }
        if (remove.isEmpty()) return;
        removeAndArchive(remove, "orphan");
    }

    /** Writes the records to removed-signs.log first and only then takes them out of the registry; a failed write removes nothing. */
    boolean removeAndArchive(Collection<SignRegistry.SignRecord> remove, String reason) {
        try {
            StringBuilder text = new StringBuilder();
            for (var record : remove) text.append(Instant.now()).append(' ').append(reason).append(' ').append(SignRegistry.encode(record)).append('\n');
            Files.createDirectories(main.getDataFolder().toPath());
            Files.writeString(main.getDataFolder().toPath().resolve("removed-signs.log"), text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            List<Integer> ids = new ArrayList<>();
            for (var record : remove) ids.add(record.id());
            Main.getRegistry().removeRecords(ids);
            main.getLogger().info(Main.tr("log.autoClean", remove.size(), ids.toString()));
            return true;
        } catch (IOException | RuntimeException failure) {
            main.getLogger().warning(Main.tr("log.autoCleanFailed", failure.getClass().getSimpleName()));
            return false;
        }
    }

    void reset() { records = null; warned.clear(); }
}
