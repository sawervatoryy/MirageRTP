package org.github.sawervatoryy.mirageRTP.hook;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.OfflinePlayer;

/** Валюта PlayerPoints. Класс загружается только при наличии PlayerPoints. */
public final class PlayerPointsHook implements CostHandler {

    private final PlayerPointsAPI api;

    public PlayerPointsHook() {
        PlayerPoints pp = PlayerPoints.getInstance();
        if (pp == null) {
            throw new IllegalStateException("PlayerPoints ещё не загружен");
        }
        this.api = pp.getAPI();
    }

    private static int toInt(double amount) {
        return (int) Math.ceil(amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return api.look(player.getUniqueId()) >= toInt(amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return api.take(player.getUniqueId(), toInt(amount));
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        api.give(player.getUniqueId(), toInt(amount));
    }
}
