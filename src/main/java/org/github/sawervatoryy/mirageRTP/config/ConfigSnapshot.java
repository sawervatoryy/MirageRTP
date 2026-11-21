package org.github.sawervatoryy.mirageRTP.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.model.Pit;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Полностью разобранное состояние всех конфигов; читается в любом потоке, применяется в главном. */
public record ConfigSnapshot(Settings settings, Lang lang, MenuSettings menus, Map<String, Pit> pits) {

    private static final List<String> FILES = List.of("config.yml", "lang.yml", "menus.yml", "pits.yml");

    public static ConfigSnapshot read(MirageRTP plugin) {
        File dir = plugin.getDataFolder();
        for (String name : FILES) {
            if (!new File(dir, name).exists()) {
                plugin.saveResource(name, false);
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(dir, "config.yml"));
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(new File(dir, "lang.yml"));
        YamlConfiguration menus = YamlConfiguration.loadConfiguration(new File(dir, "menus.yml"));
        YamlConfiguration pits = YamlConfiguration.loadConfiguration(new File(dir, "pits.yml"));
        return new ConfigSnapshot(
                Settings.load(config, plugin.getLogger()),
                Lang.load(lang),
                MenuSettings.load(menus),
                Pit.parseAll(pits));
    }
}
