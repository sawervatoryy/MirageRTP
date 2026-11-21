package org.github.sawervatoryy.mirageRTP.effect;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Logger;

/** Описание одного типа частиц из конфига. */
public record ParticleSpec(Particle type, int count, double spread, double speed, Color color, float size) {

    private static final Color DEFAULT_COLOR = Color.fromRGB(0x00, 0xAC, 0xFF);

    /** @return спецификация либо {@code null}, если тип частиц неизвестен или требует данных, которые мы не поддерживаем */
    public static ParticleSpec parse(ConfigurationSection s, Logger log) {
        String name = s.getString("type", "DUST").toUpperCase(Locale.ROOT);
        Particle particle;
        try {
            particle = Particle.valueOf(name);
        } catch (IllegalArgumentException ex) {
            log.warning("Неизвестный тип частиц: " + name);
            return null;
        }
        if (particle.getDataType() != Void.class && particle != Particle.DUST) {
            log.warning("Тип частиц требует дополнительных данных и не поддерживается: " + name);
            return null;
        }
        Color color = null;
        String hex = s.getString("color");
        if (hex != null) {
            try {
                int rgb = Integer.parseInt(hex.replace("#", ""), 16);
                color = Color.fromRGB(rgb);
            } catch (IllegalArgumentException ex) {
                log.warning("Неверный цвет частиц: " + hex);
            }
        }
        return new ParticleSpec(particle, Math.max(1, s.getInt("count", 1)), s.getDouble("spread", 0.0),
                s.getDouble("speed", 0.0), color, (float) s.getDouble("size", 1.0));
    }

    public void spawn(World world, double x, double y, double z) {
        Object data = null;
        if (type == Particle.DUST) {
            data = new Particle.DustOptions(color != null ? color : DEFAULT_COLOR, size);
        }
        world.spawnParticle(type, x, y, z, count, spread, spread, spread, speed, data);
    }
}
