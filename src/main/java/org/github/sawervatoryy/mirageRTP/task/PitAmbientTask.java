package org.github.sawervatoryy.mirageRTP.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Settings;
import org.github.sawervatoryy.mirageRTP.effect.Effect;
import org.github.sawervatoryy.mirageRTP.model.Pit;

import java.util.concurrent.ThreadLocalRandom;

/** Фоновые частицы внутри ям. Спавнятся только если рядом есть игроки. */
public final class PitAmbientTask extends BukkitRunnable {

    private final MirageRTP plugin;

    public PitAmbientTask(MirageRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Settings settings = plugin.settings();
        Effect effect = settings.effect("pit-ambient");
        if (effect.particles().isEmpty()) {
            return;
        }
        Settings.Ambient ambient = settings.pit().ambient();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (Pit pit : plugin.pits().all()) {
            if (!pit.isComplete()) {
                continue;
            }
            World world = Bukkit.getWorld(pit.world());
            if (world == null) {
                continue;
            }
            double cx = (pit.minX() + pit.maxX() + 1) / 2.0;
            double cy = (pit.minY() + pit.maxY() + 1) / 2.0;
            double cz = (pit.minZ() + pit.maxZ() + 1) / 2.0;
            double halfDiag = Math.sqrt(Math.pow(pit.maxX() - pit.minX() + 1, 2) + Math.pow(pit.maxZ() - pit.minZ() + 1, 2)) / 2.0;
            double range = ambient.viewDistance() + halfDiag;

            boolean watched = false;
            for (Player p : world.getPlayers()) {
                var l = p.getLocation();
                double dx = l.getX() - cx;
                double dy = l.getY() - cy;
                double dz = l.getZ() - cz;
                if (dx * dx + dy * dy + dz * dz <= range * range) {
                    watched = true;
                    break;
                }
            }
            if (!watched) {
                continue;
            }
            for (int i = 0; i < ambient.particlesPerPulse(); i++) {
                double x = pit.minX() + rnd.nextDouble() * (pit.maxX() - pit.minX() + 1);
                double y = pit.minY() + rnd.nextDouble() * (pit.maxY() - pit.minY() + 1);
                double z = pit.minZ() + rnd.nextDouble() * (pit.maxZ() - pit.minZ() + 1);
                effect.spawnParticles(world, x, y, z);
            }
        }
    }
}
