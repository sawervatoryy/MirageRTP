package org.github.sawervatoryy.mirageRTP.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Кулдауны режимов (хранятся в памяти). Потокобезопасно. */
public final class CooldownManager {

    private final Map<UUID, Map<String, Long>> expiry = new ConcurrentHashMap<>();

    public void set(UUID player, String modeId, long seconds) {
        if (seconds <= 0) {
            Map<String, Long> map = expiry.get(player);
            if (map != null) {
                map.remove(modeId);
            }
            return;
        }
        expiry.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(modeId, System.currentTimeMillis() + seconds * 1000L);
    }

    public long remainingMillis(UUID player, String modeId) {
        Map<String, Long> map = expiry.get(player);
        if (map == null) {
            return 0;
        }
        Long until = map.get(modeId);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            map.remove(modeId, until);
            return 0;
        }
        return left;
    }

    public long remainingSeconds(UUID player, String modeId) {
        return (remainingMillis(player, modeId) + 999) / 1000;
    }

    /** Удаляет истёкшие записи игрока (при выходе), чтобы карта не росла. */
    public void purge(UUID player) {
        Map<String, Long> map = expiry.get(player);
        if (map == null) {
            return;
        }
        long now = System.currentTimeMillis();
        map.values().removeIf(until -> until <= now);
        if (map.isEmpty()) {
            expiry.remove(player, map);
        }
    }
}
