package org.github.sawervatoryy.mirageRTP.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** Неизменяемая позиция блока. */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new BlockPos(section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    public void write(ConfigurationSection section) {
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
