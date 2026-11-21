package org.github.sawervatoryy.mirageRTP.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

/** Проверка приватов WorldGuard. Класс загружается только при наличии WorldGuard. */
public final class WorldGuardHook {

    /** @return {@code true}, если точка находится хотя бы в одном регионе */
    public boolean isInsideRegion(Location location) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return !query.getApplicableRegions(BukkitAdapter.adapt(location)).getRegions().isEmpty();
    }
}
