package org.github.sawervatoryy.mirageRTP.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.github.sawervatoryy.mirageRTP.MirageRTP;

import java.util.UUID;

/** Урон, внешние телепорты и выход игрока. */
public final class PlayerListener implements Listener {

    private final MirageRTP plugin;

    public PlayerListener(MirageRTP plugin) {
        this.plugin = plugin;
    }

    /** Полный иммунитет к любому урону (мобы, игроки, огонь, падение...) после телепортации. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player && plugin.teleports().isDamageProtected(player.getUniqueId())) {
            e.setCancelled(true);
            switch (e.getCause()) {
                case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> player.setFireTicks(0);
                case FALL -> player.setFallDistance(0f);
                default -> { }
            }
        }
    }

    /** Урон от падения не должен убивать при прыжке в яму и сразу после телепортации. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player player
                && plugin.teleports().isFallProtected(player.getUniqueId())) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[debug] отменён урон от падения " + e.getFinalDamage() + " у " + player.getName()
                        + " (fallDistance=" + player.getFallDistance() + ", y=" + player.getLocation().getBlockY() + ")");
            }
            e.setCancelled(true);
            player.setFallDistance(0f);
        } else if (plugin.isDebug() && e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player player) {
            plugin.getLogger().info("[debug] урон от падения НЕ отменён у " + player.getName() + ": "
                    + plugin.teleports().describe(player) + ", damage=" + e.getFinalDamage());
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (plugin.isDebug()) {
            Player p = e.getPlayer();
            plugin.getLogger().info("[debug] смерть " + p.getName() + ": " + plugin.teleports().describe(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player
                && plugin.settings().delay().cancelOnDamage()
                && plugin.teleports().isCountingDown(player.getUniqueId())) {
            plugin.teleports().cancelCountdown(player, "cancelled-damage");
        }
    }

    /** Телепорт другим плагином во время отсчёта сбрасывает ожидание. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (plugin.teleports().isCountingDown(e.getPlayer().getUniqueId())) {
            plugin.teleports().cancelCountdown(e.getPlayer(), "cancelled-move");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.teleports().releaseAll(e.getPlayer());
        plugin.teleports().cleanup(id);
        plugin.pits().forget(id);
        plugin.cooldowns().purge(id);
    }
}
