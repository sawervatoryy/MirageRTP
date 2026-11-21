package org.github.sawervatoryy.mirageRTP.effect;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Набор частиц и звук, настраиваемые в секции {@code effects} конфига. */
public record Effect(List<ParticleSpec> particles, SoundSpec sound) {

    public static final Effect EMPTY = new Effect(List.of(), null);

    public static Effect parse(ConfigurationSection section, Logger log) {
        List<ParticleSpec> list = new ArrayList<>();
        ConfigurationSection ps = section.getConfigurationSection("particles");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection p = ps.getConfigurationSection(key);
                if (p == null) {
                    continue;
                }
                ParticleSpec spec = ParticleSpec.parse(p, log);
                if (spec != null) {
                    list.add(spec);
                }
            }
        }
        return new Effect(List.copyOf(list), SoundSpec.parse(section.getConfigurationSection("sound")));
    }

    public void spawnParticles(World world, double x, double y, double z) {
        for (ParticleSpec spec : particles) {
            spec.spawn(world, x, y, z);
        }
    }

    /** Частицы на уровне торса и звук в точке. */
    public void play(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        spawnParticles(location.getWorld(), location.getX(), location.getY() + 1.0, location.getZ());
        if (sound != null) {
            sound.playAt(location);
        }
    }

    /** Только звук — для одного игрока. */
    public void playSound(Player player) {
        if (sound != null) {
            sound.playTo(player);
        }
    }
}
