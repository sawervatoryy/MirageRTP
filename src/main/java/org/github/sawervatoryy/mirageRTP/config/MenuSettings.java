package org.github.sawervatoryy.mirageRTP.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.github.sawervatoryy.mirageRTP.effect.SoundSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Неизменяемый снимок {@code menus.yml}. */
public record MenuSettings(
        String title,
        int rows,
        Filler filler,
        boolean animated,
        int intervalTicks,
        SoundSpec stepSound,
        SoundSpec itemSound,
        List<ModeItem> items) {

    public record Filler(Material material, String name) {
    }

    public record ModeItem(String modeId, int slot, Material material, String name, List<String> lore, boolean glow) {
    }

    public static MenuSettings load(FileConfiguration c) {
        Filler filler = null;
        String fillerMat = c.getString("main.filler.material", "BLACK_STAINED_GLASS_PANE");
        Material fm = Material.matchMaterial(fillerMat);
        if (fm != null && !fm.isAir()) {
            filler = new Filler(fm, c.getString("main.filler.name", " "));
        }

        List<ModeItem> items = new ArrayList<>();
        ConfigurationSection is = c.getConfigurationSection("main.items");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ConfigurationSection s = is.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Material mat = Material.matchMaterial(s.getString("material", "GRASS_BLOCK"));
                items.add(new ModeItem(
                        id.toLowerCase(Locale.ROOT),
                        s.getInt("slot", 0),
                        mat == null ? Material.GRASS_BLOCK : mat,
                        s.getString("name", id),
                        s.getStringList("lore"),
                        s.getBoolean("glow", false)));
            }
        }

        return new MenuSettings(
                c.getString("main.title", "RTP"),
                Math.max(1, Math.min(6, c.getInt("main.rows", 3))),
                filler,
                c.getBoolean("main.animation.enabled", true),
                Math.max(1, c.getInt("main.animation.interval-ticks", 2)),
                SoundSpec.parse(c.getConfigurationSection("main.animation.step-sound")),
                SoundSpec.parse(c.getConfigurationSection("main.animation.item-sound")),
                List.copyOf(items));
    }
}
