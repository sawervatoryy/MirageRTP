package org.github.sawervatoryy.mirageRTP.model;

import org.github.sawervatoryy.mirageRTP.config.Settings;

/**
 * Параметры конкретного запроса телепортации.
 *
 * @param instant          пропустить задержку и запрет на движение
 * @param ignorePermission не проверять право на режим
 * @param ignoreCooldown   не проверять кулдаун
 * @param ignoreCost       не списывать стоимость
 * @param setCooldown      выставить кулдаун после успешной телепортации
 */
public record TeleportOptions(
        boolean instant,
        boolean ignorePermission,
        boolean ignoreCooldown,
        boolean ignoreCost,
        boolean setCooldown) {

    public static TeleportOptions normal() {
        return new TeleportOptions(false, false, false, false, true);
    }

    public static TeleportOptions force() {
        return new TeleportOptions(true, true, true, true, false);
    }

    public static TeleportOptions pit(Settings.PitSettings pit) {
        return new TeleportOptions(true, false, !pit.respectCooldown(), false, pit.setCooldown());
    }
}
