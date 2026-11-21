package org.github.sawervatoryy.mirageRTP.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Settings;

/** Отмена отсчёта при движении и детектор входа в РТП-яму. */
public final class MoveListener implements Listener {

    private final MirageRTP plugin;

    public MoveListener(MirageRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        Player player = e.getPlayer();
        Settings settings = plugin.settings();
        plugin.teleports().releaseGraceOnLanding(player);

        // поворот головы не учитываем — сравниваем только X/Y/Z
        if (settings.delay().cancelOnMove() && plugin.teleports().isCountingDown(player.getUniqueId())) {
            double tol = settings.delay().moveTolerance();
            double dx = from.getX() - to.getX();
            double dy = from.getY() - to.getY();
            double dz = from.getZ() - to.getZ();
            if (dx * dx + dy * dy + dz * dz > tol * tol) {
                plugin.teleports().cancelCountdown(player, "cancelled-move");
            }
        }

        // яму проверяем только при смене блока
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        plugin.pits().handleMove(player, to);
    }
}
