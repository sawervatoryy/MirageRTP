package org.github.sawervatoryy.mirageRTP.effect;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Звук по строковому ключу (например {@code entity.enderman.teleport}). */
public record SoundSpec(String key, float volume, float pitch) {

    public static SoundSpec parse(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String key = section.getString("sound", "");
        if (key.isBlank()) {
            return null;
        }
        return new SoundSpec(key, (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
    }

    public void playAt(Location location) {
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, key, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    public void playTo(Player player) {
        player.playSound(player.getLocation(), key, SoundCategory.PLAYERS, volume, pitch);
    }
}
