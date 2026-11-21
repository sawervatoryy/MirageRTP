package org.github.sawervatoryy.mirageRTP.model;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Настраиваемый режим РТП.
 *
 * @param world     имя мира либо {@code current} (мир, в котором стоит игрок)
 * @param cooldowns кулдауны в секундах по группам; ключ {@code default} — базовый
 */
public record RtpMode(
        String id,
        String displayName,
        String permission,
        String world,
        Center center,
        int minRadius,
        int maxRadius,
        CostType costType,
        double costAmount,
        Map<String, Integer> cooldowns) {

    public boolean hasCost() {
        return costType != CostType.NONE && costAmount > 0;
    }

    public boolean isCurrentWorld() {
        return world.equalsIgnoreCase("current");
    }

    public boolean canUse(Player player) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    /**
     * Эффективный кулдаун игрока: минимум из базового значения и значений групп,
     * на которые у игрока есть право {@code miragertp.cooldown.<группа>}.
     */
    public int cooldownFor(Player player) {
        int result = cooldowns.getOrDefault("default", 0);
        for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            if (entry.getKey().equals("default")) {
                continue;
            }
            if (player.hasPermission("miragertp.cooldown." + entry.getKey())) {
                result = Math.min(result, entry.getValue());
            }
        }
        return Math.max(0, result);
    }
}
