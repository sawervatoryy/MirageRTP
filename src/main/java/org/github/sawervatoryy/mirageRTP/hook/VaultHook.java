package org.github.sawervatoryy.mirageRTP.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Экономика через Vault. Класс загружается только при наличии Vault. */
public final class VaultHook implements CostHandler {

    private final Economy economy;

    public VaultHook() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            throw new IllegalStateException("Vault найден, но провайдер экономики не зарегистрирован");
        }
        this.economy = rsp.getProvider();
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
