package org.github.sawervatoryy.mirageRTP.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.github.sawervatoryy.mirageRTP.effect.Effect;
import org.github.sawervatoryy.mirageRTP.model.Center;
import org.github.sawervatoryy.mirageRTP.model.CostType;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Неизменяемый снимок {@code config.yml}. Безопасен для чтения из любых потоков. */
public record Settings(
        boolean useGui,
        String defaultMode,
        DelaySettings delay,
        SearchSettings search,
        PitSettings pit,
        int fallGraceMillis,
        int damageImmunitySeconds,
        boolean debug,
        Map<String, RtpMode> modes,
        Map<String, Effect> effects) {

    public enum Display { ACTIONBAR, TITLE, BOTH }

    public record DelaySettings(int seconds, double moveTolerance, boolean cancelOnMove,
                                boolean cancelOnDamage, Display display) {
    }

    public record SearchSettings(int maxAttempts, int netherMinY, int netherMaxY, boolean avoidRegions,
                                 Set<Material> unsafe, String exemptPermission) {
    }

    public record Ambient(boolean enabled, int intervalTicks, int particlesPerPulse, int viewDistance) {
    }

    public record PitSettings(int antiSpamMillis, boolean respectCooldown, boolean setCooldown, Ambient ambient) {
    }

    public Effect effect(String key) {
        return effects.getOrDefault(key, Effect.EMPTY);
    }

    public static Settings load(FileConfiguration c, Logger log) {
        DelaySettings delay = new DelaySettings(
                Math.max(0, c.getInt("delay.seconds", 3)),
                Math.max(0.0, c.getDouble("delay.move-tolerance", 0.0)),
                c.getBoolean("delay.cancel-on-move", true),
                c.getBoolean("delay.cancel-on-damage", true),
                parseEnum(Display.class, c.getString("delay.display"), Display.BOTH));

        Set<Material> unsafe = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("search.unsafe-blocks")) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                log.warning("search.unsafe-blocks: неизвестный материал " + name);
            } else {
                unsafe.add(m);
            }
        }
        SearchSettings search = new SearchSettings(
                Math.max(1, c.getInt("search.max-attempts", 30)),
                c.getInt("search.nether-min-y", 32),
                c.getInt("search.nether-max-y", 120),
                c.getBoolean("search.avoid-worldguard-regions", true),
                unsafe,
                c.getString("search.exempt-permission", "miragertp.exempt"));

        Ambient ambient = new Ambient(
                c.getBoolean("pit.ambient.enabled", true),
                Math.max(1, c.getInt("pit.ambient.interval-ticks", 10)),
                Math.max(1, c.getInt("pit.ambient.particles-per-pulse", 8)),
                Math.max(8, c.getInt("pit.ambient.view-distance", 48)));
        PitSettings pit = new PitSettings(
                Math.max(0, c.getInt("pit.anti-spam-millis", 1500)),
                c.getBoolean("pit.respect-cooldown", true),
                c.getBoolean("pit.set-cooldown", true),
                ambient);

        Map<String, RtpMode> modes = new LinkedHashMap<>();
        ConfigurationSection ms = c.getConfigurationSection("modes");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection m = ms.getConfigurationSection(id);
                if (m == null) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                int min = Math.max(0, m.getInt("min-radius", 100));
                int max = Math.max(min + 1, m.getInt("max-radius", 1000));
                Map<String, Integer> cooldowns = new LinkedHashMap<>();
                ConfigurationSection cs = m.getConfigurationSection("cooldowns");
                if (cs != null) {
                    for (String g : cs.getKeys(false)) {
                        cooldowns.put(g.toLowerCase(Locale.ROOT), Math.max(0, cs.getInt(g)));
                    }
                }
                cooldowns.putIfAbsent("default", 0);
                modes.put(key, new RtpMode(
                        key,
                        m.getString("display-name", key),
                        m.getString("permission", ""),
                        m.getString("world", "current"),
                        parseEnum(Center.class, m.getString("center"), Center.SPAWN),
                        min, max,
                        parseEnum(CostType.class, m.getString("cost.type"), CostType.NONE),
                        Math.max(0.0, m.getDouble("cost.amount", 0.0)),
                        Map.copyOf(cooldowns)));
            }
        }

        Map<String, Effect> effects = new LinkedHashMap<>();
        ConfigurationSection es = c.getConfigurationSection("effects");
        if (es != null) {
            for (String key : es.getKeys(false)) {
                ConfigurationSection e = es.getConfigurationSection(key);
                if (e != null) {
                    effects.put(key, Effect.parse(e, log));
                }
            }
        }

        return new Settings(c.getBoolean("use-gui", true),
                c.getString("default-mode", "default").toLowerCase(Locale.ROOT),
                delay, search, pit, Math.max(0, c.getInt("protection.fall-grace-millis", 15000)),
                Math.max(0, c.getInt("protection.damage-immunity-seconds", 5)),
                c.getBoolean("debug", false), Map.copyOf(modes), Map.copyOf(effects));
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
