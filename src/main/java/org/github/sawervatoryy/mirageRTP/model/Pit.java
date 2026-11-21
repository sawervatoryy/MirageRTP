package org.github.sawervatoryy.mirageRTP.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * РТП-яма: кубоид между двумя углами, привязанный к режиму.
 * Запись неизменяема — для правок создаётся копия через {@code with*}.
 */
public record Pit(String name, String mode, String world, BlockPos pos1, BlockPos pos2) {

    public boolean isComplete() {
        return world != null && pos1 != null && pos2 != null;
    }

    public int minX() { return Math.min(pos1.x(), pos2.x()); }
    public int maxX() { return Math.max(pos1.x(), pos2.x()); }
    public int minY() { return Math.min(pos1.y(), pos2.y()); }
    public int maxY() { return Math.max(pos1.y(), pos2.y()); }
    public int minZ() { return Math.min(pos1.z(), pos2.z()); }
    public int maxZ() { return Math.max(pos1.z(), pos2.z()); }

    /** Находится ли блок, в котором стоит {@code location}, внутри ямы. */
    public boolean contains(Location location) {
        if (!isComplete() || location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX() && x <= maxX()
                && y >= minY() && y <= maxY()
                && z >= minZ() && z <= maxZ();
    }

    public Pit withMode(String newMode) {
        return new Pit(name, newMode, world, pos1, pos2);
    }

    /** Если мир сменился, вторая точка сбрасывается, чтобы яма не «растянулась» между мирами. */
    public Pit withPos1(String newWorld, BlockPos pos) {
        BlockPos other = (world != null && !world.equals(newWorld)) ? null : pos2;
        return new Pit(name, mode, newWorld, pos, other);
    }

    public Pit withPos2(String newWorld, BlockPos pos) {
        BlockPos other = (world != null && !world.equals(newWorld)) ? null : pos1;
        return new Pit(name, mode, newWorld, other, pos);
    }

    public void write(ConfigurationSection section) {
        section.set("mode", mode);
        if (world != null) {
            section.set("world", world);
        }
        if (pos1 != null) {
            pos1.write(section.createSection("pos1"));
        }
        if (pos2 != null) {
            pos2.write(section.createSection("pos2"));
        }
    }

    public static Map<String, Pit> parseAll(FileConfiguration config) {
        Map<String, Pit> result = new HashMap<>();
        ConfigurationSection root = config.getConfigurationSection("pits");
        if (root == null) {
            return result;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            String name = key.toLowerCase(Locale.ROOT);
            result.put(name, new Pit(
                    name,
                    s.getString("mode", "default").toLowerCase(Locale.ROOT),
                    s.getString("world"),
                    BlockPos.read(s.getConfigurationSection("pos1")),
                    BlockPos.read(s.getConfigurationSection("pos2"))));
        }
        return result;
    }
}
