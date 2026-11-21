package org.github.sawervatoryy.mirageRTP.hook;

import org.bukkit.OfflinePlayer;

/** Абстракция над валютой (Vault / PlayerPoints). */
public interface CostHandler {

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    void deposit(OfflinePlayer player, double amount);
}
