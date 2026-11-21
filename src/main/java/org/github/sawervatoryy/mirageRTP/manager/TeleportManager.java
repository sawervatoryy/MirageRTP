package org.github.sawervatoryy.mirageRTP.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Lang;
import org.github.sawervatoryy.mirageRTP.config.Settings;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;
import org.github.sawervatoryy.mirageRTP.model.TeleportOptions;
import org.github.sawervatoryy.mirageRTP.service.LocationFinder;
import org.github.sawervatoryy.mirageRTP.util.Scheduling;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Жизненный цикл телепортации: проверки → (отсчёт) → асинхронный поиск → списание → телепорт → эффекты.
 * Активные сессии хранятся в {@link ConcurrentHashMap}.
 */
public final class TeleportManager {

    private static final class Session {
        final UUID id;
        final RtpMode mode;
        final TeleportOptions options;
        volatile boolean counting;
        volatile BukkitTask task;

        Session(UUID id, RtpMode mode, TeleportOptions options) {
            this.id = id;
            this.mode = mode;
            this.options = options;
        }
    }

    private final MirageRTP plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    /** До какого момента (мс) игрок защищён от урона от падения после телепортации. */
    /** Игроки, за которыми сейчас следит «страж падения» (чтобы не запускать несколько задач). */
    private final java.util.Set<UUID> fallGuards = ConcurrentHashMap.newKeySet();
    /** Эффекты, которые выдали мы (чужие эффекты игрока не трогаем). */
    /** Игроки, которых мы «заморозили» (отключена гравитация) на время поиска точки. */
    private final java.util.Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> ownedSlowFalling = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> ownedResistance = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> ownedInvulnerable = ConcurrentHashMap.newKeySet();
    /** До какого момента (мс) игрок полностью неуязвим после телепортации. */
    private final Map<UUID, Long> damageImmunity = new ConcurrentHashMap<>();
    private final Map<UUID, long[]> fallGrace = new ConcurrentHashMap<>(); // {действует до, выдан в}

    public TeleportManager(MirageRTP plugin) {
        this.plugin = plugin;
    }

    public boolean hasSession(UUID id) {
        return sessions.containsKey(id);
    }

    /**
     * Защита от урона падением: пока идёт поиск точки (игрок может падать в яме)
     * и короткое время после телепортации.
     */
    /** Полный иммунитет к любому урону в течение N секунд после телепортации. */
    public boolean isDamageProtected(UUID id) {
        Long until = damageImmunity.get(id);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            damageImmunity.remove(id, until);
            return false;
        }
        return true;
    }

    private void grantDamageImmunity(Player p) {
        int seconds = plugin.settings().damageImmunitySeconds();
        if (seconds <= 0) {
            return;
        }
        UUID id = p.getUniqueId();
        damageImmunity.put(id, System.currentTimeMillis() + seconds * 1000L);
        if (!p.isInvulnerable()) {
            p.setInvulnerable(true);
            ownedInvulnerable.add(id);
        }
        startFallGuard(p); // страж снимет неуязвимость, когда иммунитет и защита от падения закончатся
    }

    public boolean isFallProtected(UUID id) {
        if (isDamageProtected(id)) {
            return true;
        }
        Session s = sessions.get(id);
        if (s != null && !s.counting) {
            return true;
        }
        if (plugin.pits().isInside(id)) {
            return true; // пока игрок в яме, урон от падения не наносится
        }
        long[] grace = fallGrace.get(id);
        if (grace == null) {
            return false;
        }
        if (grace[0] < System.currentTimeMillis()) {
            fallGrace.remove(id, grace);
            return false;
        }
        return true;
    }

    /** Замораживает игрока на месте: без гравитации, скорость и дистанция падения обнулены. */
    public void freeze(Player p) {
        if (frozen.add(p.getUniqueId())) {
            p.setVelocity(new Vector(0, 0, 0));
            p.setFallDistance(0f);
            p.setGravity(false);
        }
    }

    /** Возвращает гравитацию и ещё раз обнуляет скорость и падение. */
    public void unfreeze(Player p) {
        if (frozen.remove(p.getUniqueId())) {
            p.setGravity(true);
            p.setVelocity(new Vector(0, 0, 0));
            p.setFallDistance(0f);
        }
    }

    /** Строка состояния защиты — для отладочного лога. */
    public String describe(Player p) {
        UUID id = p.getUniqueId();
        return "session=" + sessions.containsKey(id) + ", inPit=" + plugin.pits().isInside(id)
                + ", grace=" + fallGrace.containsKey(id) + ", frozen=" + frozen.contains(id)
                + ", immunity=" + damageImmunity.containsKey(id) + ", invulnerable=" + p.isInvulnerable() + ", slowFalling=" + p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                + ", resistance=" + p.hasPotionEffect(PotionEffectType.RESISTANCE)
                + ", fallDistance=" + p.getFallDistance() + ", y=" + p.getLocation().getBlockY();
    }

    /** Выдаёт кратковременный иммунитет к урону от падения. */
    public void grantFallGrace(UUID id) {
        int grace = plugin.settings().fallGraceMillis();
        if (grace > 0) {
            long now = System.currentTimeMillis();
            fallGrace.put(id, new long[]{now + grace, now});
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            startFallGuard(player);
        }
    }

    /**
     * Эффекты Minecraft как страховка: «Плавное падение» сбрасывает дистанцию падения на уровне игры,
     * «Сопротивление V» гасит урон. Без частиц и иконки; снимаются, когда защита закончилась.
     */
    private void applyFallEffects(Player p) {
        int graceMillis = plugin.settings().fallGraceMillis();
        if (graceMillis <= 0) {
            return;
        }
        int ticks = Math.max(200, graceMillis / 50 + 40);
        // жёсткая защита на уровне игры: неуязвимый игрок не получает урон от падения
        if (!p.isInvulnerable()) {
            p.setInvulnerable(true);
            ownedInvulnerable.add(p.getUniqueId());
        }
        if (!p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0, false, false, false));
            ownedSlowFalling.add(p.getUniqueId());
        }
        if (!p.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 4, false, false, false));
            ownedResistance.add(p.getUniqueId());
        }
    }

    /** Полностью снимает всё, что выдавал плагин (заморозка, эффекты, неуязвимость). */
    public void releaseAll(Player p) {
        unfreeze(p);
        removeFallEffects(p);
    }

    private void removeFallEffects(Player p) {
        if (ownedInvulnerable.remove(p.getUniqueId())) {
            p.setInvulnerable(false);
        }
        if (ownedSlowFalling.remove(p.getUniqueId())) {
            p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
        if (ownedResistance.remove(p.getUniqueId())) {
            p.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }

    /**
     * Каждый тик обнуляет дистанцию падения, пока игрок защищён. Работает независимо от события урона:
     * нет накопленного падения — нечему наносить урон при приземлении.
     */
    private void startFallGuard(Player player) {
        UUID id = player.getUniqueId();
        if (!fallGuards.add(id)) {
            return;
        }
        applyFallEffects(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline() || !isFallProtected(id)) {
                    fallGuards.remove(id);
                    if (p != null) {
                        unfreeze(p);
                        removeFallEffects(p);
                    } else {
                        ownedSlowFalling.remove(id);
                        ownedResistance.remove(id);
                        ownedInvulnerable.remove(id);
                    }
                    cancel();
                    return;
                }
                p.setFallDistance(0f);
                releaseGraceOnLanding(p);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Снимает иммунитет после первого приземления (игрок стоит на земле и прошло >1.5 с с выдачи).
     * Вызывается из обработчика движения.
     */
    public void releaseGraceOnLanding(Player player) {
        long[] grace = fallGrace.get(player.getUniqueId());
        if (grace != null && player.isOnGround() && System.currentTimeMillis() - grace[1] > 1500) {
            fallGrace.remove(player.getUniqueId(), grace);
        }
    }

    public boolean isCountingDown(UUID id) {
        Session s = sessions.get(id);
        return s != null && s.counting;
    }

    /** Основная точка входа. Вызывать из главного потока. */
    public void request(Player player, RtpMode mode, TeleportOptions options) {
        Lang lang = plugin.lang();
        Settings settings = plugin.settings();
        UUID id = player.getUniqueId();

        if (sessions.containsKey(id)) {
            lang.send(player, "already-active");
            return;
        }
        if (!options.ignorePermission() && !mode.canUse(player)) {
            lang.send(player, "no-mode-permission", "mode", mode.displayName());
            return;
        }
        World world = LocationFinder.resolveWorld(player, mode);
        if (world == null) {
            lang.send(player, "world-not-found", "world", mode.world());
            return;
        }
        if (!options.ignoreCooldown() && !player.hasPermission("miragertp.bypass.cooldown")) {
            long left = plugin.cooldowns().remainingSeconds(id, mode.id());
            if (left > 0) {
                lang.send(player, "cooldown", "time", lang.formatDuration(left), "mode", mode.displayName());
                return;
            }
        }
        boolean pays = !options.ignoreCost() && !player.hasPermission("miragertp.bypass.cost") && mode.hasCost();
        if (pays && !plugin.economy().has(player, mode)) {
            lang.send(player, "not-enough-funds", "cost", lang.costText(mode.costType(), mode.costAmount()));
            return;
        }

        Session session = new Session(id, mode, options);
        if (sessions.putIfAbsent(id, session) != null) {
            lang.send(player, "already-active");
            return;
        }

        boolean instant = options.instant() || settings.delay().seconds() <= 0
                || player.hasPermission("miragertp.bypass.delay");
        if (instant) {
            beginSearch(player, session);
        } else {
            beginCountdown(player, session, settings);
        }
    }

    // ---------------------------------------------------------------- отсчёт

    private void beginCountdown(Player player, Session session, Settings settings) {
        final int totalTicks = settings.delay().seconds() * 20;
        final int seconds = settings.delay().seconds();
        session.counting = true;
        session.task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(session.id);
                if (p == null || !p.isOnline()) {
                    cancel();
                    sessions.remove(session.id, session);
                    return;
                }
                Settings cur = plugin.settings();
                if (tick % 20 == 0) {
                    int left = seconds - tick / 20;
                    showCountdown(p, cur, left, session.mode);
                    cur.effect("countdown-tick").playSound(p);
                }
                spawnRing(p, cur, tick);
                tick += 2;
                if (tick >= totalTicks) {
                    cancel();
                    session.counting = false;
                    beginSearch(p, session);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void showCountdown(Player p, Settings settings, int left, RtpMode mode) {
        Lang lang = plugin.lang();
        Settings.Display d = settings.delay().display();
        if (d == Settings.Display.ACTIONBAR || d == Settings.Display.BOTH) {
            lang.actionBar(p, "countdown-actionbar", "seconds", left, "mode", mode.displayName());
        }
        if (d == Settings.Display.TITLE || d == Settings.Display.BOTH) {
            Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO);
            lang.title(p, times, "countdown-title", "countdown-subtitle", "seconds", left, "mode", mode.displayName());
        }
    }

    /** Вращающиеся вокруг игрока частицы. */
    private void spawnRing(Player p, Settings settings, int tick) {
        var effect = settings.effect("countdown-ring");
        if (effect.particles().isEmpty()) {
            return;
        }
        Location base = p.getLocation();
        double angle = tick * 0.35;
        double height = (tick % 20) / 10.0;
        for (int i = 0; i < 3; i++) {
            double a = angle + i * (Math.PI * 2 / 3);
            effect.spawnParticles(base.getWorld(),
                    base.getX() + Math.cos(a) * 0.9, base.getY() + height, base.getZ() + Math.sin(a) * 0.9);
        }
    }

    /** Отмена отсчёта (движение, урон и т.п.). Ничего не делает, если отсчёта нет. */
    public void cancelCountdown(Player player, String langKey) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.counting) {
            return;
        }
        if (!sessions.remove(s.id, s)) {
            return;
        }
        BukkitTask task = s.task;
        if (task != null) {
            task.cancel();
        }
        player.clearTitle();
        player.sendActionBar(Component.empty());
        if (langKey != null) {
            plugin.lang().send(player, langKey);
            plugin.lang().actionBar(player, "cancelled-actionbar");
        }
        plugin.settings().effect("cancel").play(player.getLocation());
    }

    /** Полная очистка при выходе игрока. */
    public void cleanup(UUID id) {
        Session s = sessions.remove(id);
        if (s != null && s.task != null) {
            s.task.cancel();
        }
        fallGrace.remove(id);
        frozen.remove(id);
        damageImmunity.remove(id);
        ownedInvulnerable.remove(id);
        ownedSlowFalling.remove(id);
        ownedResistance.remove(id);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            unfreeze(p);
            removeFallEffects(p);
        }
        sessions.values().forEach(s -> {
            if (s.task != null) {
                s.task.cancel();
            }
        });
        sessions.clear();
    }

    // ---------------------------------------------------------------- поиск и телепорт

    private void beginSearch(Player player, Session session) {
        Lang lang = plugin.lang();
        startFallGuard(player);
        if (!session.options.instant()) {
            lang.actionBar(player, "searching");
        }
        plugin.finder().find(player, session.mode).whenComplete((result, error) ->
                Scheduling.main(plugin, () -> complete(session, result, error)));
    }

    private void complete(Session s, LocationFinder.SearchResult result, Throwable error) {
        Lang lang = plugin.lang();
        Player p = Bukkit.getPlayer(s.id);
        if (p == null || !p.isOnline()) {
            sessions.remove(s.id, s);
            return;
        }
        if (error != null || result == null || !result.success()) {
            sessions.remove(s.id, s);
            unfreeze(p);
            grantFallGrace(s.id);
            p.setFallDistance(0f);
            LocationFinder.Failure f = result == null ? LocationFinder.Failure.NO_SAFE_SPOT : result.failure();
            lang.send(p, switch (f) {
                case NO_PLAYERS -> "no-players";
                case NO_WORLD -> "world-not-found";
                default -> "search-failed";
            }, "world", s.mode.world());
            return;
        }

        boolean charge = !s.options.ignoreCost() && !p.hasPermission("miragertp.bypass.cost") && s.mode.hasCost();
        if (charge && !plugin.economy().withdraw(p, s.mode)) {
            sessions.remove(s.id, s);
            unfreeze(p);
            grantFallGrace(s.id);
            lang.send(p, "not-enough-funds", "cost", lang.costText(s.mode.costType(), s.mode.costAmount()));
            return;
        }

        Location dest = result.location();
        dest.setYaw(p.getYaw());
        dest.setPitch(p.getPitch());
        Location from = p.getLocation();

        p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, ex) ->
                Scheduling.main(plugin, () -> {
                    sessions.remove(s.id, s);
                    Player pl = Bukkit.getPlayer(s.id);
                    boolean success = ex == null && Boolean.TRUE.equals(ok);
                    if (pl != null) {
                        unfreeze(pl); // вернуть гравитацию и обнулить скорость уже на новом месте
                    }
                    if (!success) {
                        if (charge) {
                            plugin.economy().refund(Bukkit.getOfflinePlayer(s.id), s.mode);
                        }
                        if (pl != null) {
                            lang.send(pl, "teleport-failed");
                        }
                        return;
                    }
                    if (pl == null) {
                        return;
                    }
                    // сбрасываем накопленное падение и скорость, даём короткий иммунитет к урону от падения
                    pl.setFallDistance(0f);
                    pl.setVelocity(new Vector(0, 0, 0));
                    grantFallGrace(s.id);
                    grantDamageImmunity(pl);
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("[debug] " + pl.getName() + " -> " + dest.getWorld().getName() + " "
                                + dest.getBlockX() + " " + dest.getBlockY() + " " + dest.getBlockZ()
                                + ", highestY=" + dest.getWorld().getHighestBlockYAt(dest.getBlockX(), dest.getBlockZ())
                                + ", mode=" + s.mode.id());
                    }
                    if (s.options.setCooldown()) {
                        plugin.cooldowns().set(s.id, s.mode.id(), s.mode.cooldownFor(pl));
                    }
                    Settings cur = plugin.settings();
                    cur.effect("teleport-out").play(from);
                    cur.effect("teleport-in").play(dest);
                    Object[] ph = {"x", dest.getBlockX(), "y", dest.getBlockY(), "z", dest.getBlockZ(),
                            "world", dest.getWorld().getName(), "mode", s.mode.displayName()};
                    lang.send(pl, "teleported", ph);
                    lang.title(pl, "teleported-title", "teleported-subtitle", ph);
                }));
    }
}
