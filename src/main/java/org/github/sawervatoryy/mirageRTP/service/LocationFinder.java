package org.github.sawervatoryy.mirageRTP.service;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Settings;
import org.github.sawervatoryy.mirageRTP.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Поиск безопасной точки. Чанки подгружаются/генерируются асинхронно
 * ({@code World#getChunkAtAsync}); в главном потоке остаётся только дешёвая проверка одного столбца блоков.
 */
public final class LocationFinder {

    public enum Failure { NONE, NO_WORLD, NO_PLAYERS, NO_SAFE_SPOT }

    public record SearchResult(Location location, Failure failure) {
        public static SearchResult ok(Location l) { return new SearchResult(l, Failure.NONE); }
        public static SearchResult fail(Failure f) { return new SearchResult(null, f); }
        public boolean success() { return location != null; }
    }

    private record Context(World world, double cx, double cz, RtpMode mode, Settings settings) {
    }

    private final MirageRTP plugin;

    public LocationFinder(MirageRTP plugin) {
        this.plugin = plugin;
    }

    /** Мир, в котором режим будет искать точку; {@code null}, если мира нет. */
    public static World resolveWorld(Player player, RtpMode mode) {
        return mode.isCurrentWorld() ? player.getWorld() : Bukkit.getWorld(mode.world());
    }

    /** Вызывать из главного потока. Future завершается тоже в главном потоке. */
    public CompletableFuture<SearchResult> find(Player player, RtpMode mode) {
        Settings settings = plugin.settings();
        World world = resolveWorld(player, mode);
        if (world == null) {
            return CompletableFuture.completedFuture(SearchResult.fail(Failure.NO_WORLD));
        }

        double cx;
        double cz;
        switch (mode.center()) {
            case ZERO -> { cx = 0; cz = 0; }
            case PLAYER -> {
                Location base = player.getWorld().equals(world) ? player.getLocation() : world.getSpawnLocation();
                cx = base.getX();
                cz = base.getZ();
            }
            case RANDOM_PLAYER -> {
                List<Player> candidates = world.getPlayers().stream()
                        .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                        .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                        .filter(p -> !p.hasPermission(settings.search().exemptPermission()))
                        .toList();
                if (candidates.isEmpty()) {
                    return CompletableFuture.completedFuture(SearchResult.fail(Failure.NO_PLAYERS));
                }
                Location target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).getLocation();
                cx = target.getX();
                cz = target.getZ();
            }
            default -> {
                Location spawn = world.getSpawnLocation();
                cx = spawn.getX();
                cz = spawn.getZ();
            }
        }

        return attempt(new Context(world, cx, cz, mode, settings), 0)
                .exceptionally(t -> SearchResult.fail(Failure.NO_SAFE_SPOT));
    }

    private CompletableFuture<SearchResult> attempt(Context ctx, int n) {
        if (n >= ctx.settings().search().maxAttempts()) {
            return CompletableFuture.completedFuture(SearchResult.fail(Failure.NO_SAFE_SPOT));
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double min = ctx.mode().minRadius();
        double max = ctx.mode().maxRadius();
        // равномерное распределение по площади кольца
        double radius = Math.sqrt(rnd.nextDouble(min * min, max * max + 1));
        double angle = rnd.nextDouble(Math.PI * 2);
        int x = (int) Math.floor(ctx.cx() + Math.cos(angle) * radius);
        int z = (int) Math.floor(ctx.cz() + Math.sin(angle) * radius);

        if (!ctx.world().getWorldBorder().isInside(new Location(ctx.world(), x, 64, z))) {
            return attempt(ctx, n + 1);
        }

        CompletableFuture<SearchResult> out = new CompletableFuture<>();
        ctx.world().getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) -> {
            // доступ к блокам — только из главного потока
            Runnable task = () -> {
                if (err != null || chunk == null) {
                    attempt(ctx, n + 1).whenComplete((r, e) -> out.complete(e == null ? r : SearchResult.fail(Failure.NO_SAFE_SPOT)));
                    return;
                }
                Location found = evaluate(ctx, x, z);
                if (found != null) {
                    out.complete(SearchResult.ok(found));
                } else {
                    attempt(ctx, n + 1).whenComplete((r, e) -> out.complete(e == null ? r : SearchResult.fail(Failure.NO_SAFE_SPOT)));
                }
            };
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        });
        return out;
    }

    private Location evaluate(Context ctx, int x, int z) {
        World w = ctx.world();
        Settings.SearchSettings s = ctx.settings().search();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();

        Location result = null;
        if (w.getEnvironment() == World.Environment.NETHER) {
            int top = Math.min(s.netherMaxY(), maxY - 3);
            int bottom = Math.max(s.netherMinY(), minY + 2);
            for (int y = top; y > bottom; y--) {
                if (isStandable(ctx, x, y, z)) {
                    result = new Location(w, x + 0.5, y, z + 0.5);
                    break;
                }
            }
        } else {
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (y > minY + 1 && y < maxY - 2 && isStandable(ctx, x, y, z)) {
                result = new Location(w, x + 0.5, y, z + 0.5);
            }
        }
        if (result == null) {
            return null;
        }
        WorldGuardHook wg = plugin.worldGuard();
        if (s.avoidRegions() && wg != null && wg.isInsideRegion(result)) {
            return null;
        }
        return result;
    }

    /** Стоять можно, если под ногами твёрдый безопасный блок, а ноги и голова — в свободном месте. */
    private boolean isStandable(Context ctx, int x, int y, int z) {
        World w = ctx.world();
        Set<Material> unsafe = ctx.settings().search().unsafe();
        Block ground = w.getBlockAt(x, y - 1, z);
        Block feet = w.getBlockAt(x, y, z);
        Block head = w.getBlockAt(x, y + 1, z);

        Material gm = ground.getType();
        if (!gm.isSolid() || ground.isLiquid() || unsafe.contains(gm) || Tag.LEAVES.isTagged(gm)) {
            return false;
        }
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return false;
        }
        if (unsafe.contains(feet.getType()) || unsafe.contains(head.getType())) {
            return false;
        }
        // соседние блоки: лава/огонь/кактусы рядом тоже опасны
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    if (unsafe.contains(w.getBlockAt(x + dx, y + dy, z + dz).getType())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
