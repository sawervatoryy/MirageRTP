package org.github.sawervatoryy.mirageRTP.service;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.hook.CostHandler;
import org.github.sawervatoryy.mirageRTP.hook.PlayerPointsHook;
import org.github.sawervatoryy.mirageRTP.hook.VaultHook;
import org.github.sawervatoryy.mirageRTP.model.CostType;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Единая точка работы со стоимостью режимов. Хуки создаются лениво при первом обращении. */
public final class EconomyService {

    private final MirageRTP plugin;
    private final Map<CostType, CostHandler> handlers = new ConcurrentHashMap<>();
    private final Set<CostType> warned = ConcurrentHashMap.newKeySet();

    public EconomyService(MirageRTP plugin) {
        this.plugin = plugin;
    }

    private CostHandler handler(CostType type) {
        CostHandler cached = handlers.get(type);
        if (cached != null) {
            return cached;
        }
        CostHandler created = null;
        try {
            created = switch (type) {
                case VAULT -> Bukkit.getPluginManager().isPluginEnabled("Vault") ? new VaultHook() : null;
                case POINTS -> Bukkit.getPluginManager().isPluginEnabled("PlayerPoints") ? new PlayerPointsHook() : null;
                default -> null;
            };
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключить " + type + ": " + t.getMessage());
        }
        if (created == null) {
            if (warned.add(type)) {
                plugin.getLogger().warning("Режим требует оплату " + type + ", но провайдер недоступен — платные режимы будут отклонять игроков.");
            }
            return null;
        }
        handlers.put(type, created);
        return created;
    }

    public boolean has(OfflinePlayer player, RtpMode mode) {
        if (!mode.hasCost()) {
            return true;
        }
        CostHandler h = handler(mode.costType());
        return h != null && h.has(player, mode.costAmount());
    }

    public boolean withdraw(OfflinePlayer player, RtpMode mode) {
        if (!mode.hasCost()) {
            return true;
        }
        CostHandler h = handler(mode.costType());
        return h != null && h.withdraw(player, mode.costAmount());
    }

    public void refund(OfflinePlayer player, RtpMode mode) {
        if (!mode.hasCost()) {
            return;
        }
        CostHandler h = handler(mode.costType());
        if (h != null) {
            h.deposit(player, mode.costAmount());
        }
    }
}
