package org.github.sawervatoryy.mirageRTP.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.config.Lang;
import org.github.sawervatoryy.mirageRTP.config.MenuSettings;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;
import org.github.sawervatoryy.mirageRTP.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Главное меню выбора режима с анимацией появления (волна фона → предметы по очереди). */
public final class RtpMenu {

    private final MirageRTP plugin;

    public RtpMenu(MirageRTP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        MenuSettings ms = plugin.menus();
        int size = ms.rows() * 9;
        RtpMenuHolder holder = new RtpMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, Text.parse(ms.title()));
        holder.bind(inv);

        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        for (MenuSettings.ModeItem mi : ms.items()) {
            RtpMode mode = plugin.settings().modes().get(mi.modeId());
            if (mode == null || mi.slot() < 0 || mi.slot() >= size) {
                continue;
            }
            items.put(mi.slot(), buildItem(player, mi, mode));
            holder.map(mi.slot(), mode.id());
        }

        ItemStack filler = buildFiller(ms);
        player.openInventory(inv);

        if (!ms.animated()) {
            for (int slot = 0; slot < size; slot++) {
                ItemStack it = items.get(slot);
                inv.setItem(slot, it != null ? it : (filler == null ? null : filler.clone()));
            }
            return;
        }
        animate(player, inv, ms, items, filler);
    }

    private void animate(Player player, Inventory inv, MenuSettings ms, Map<Integer, ItemStack> items, ItemStack filler) {
        final int rows = ms.rows();
        final List<Map.Entry<Integer, ItemStack>> queue = new ArrayList<>(items.entrySet());
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                    cancel();
                    return;
                }
                if (step < 9) {
                    if (filler != null) {
                        for (int r = 0; r < rows; r++) {
                            int slot = r * 9 + step;
                            if (!items.containsKey(slot)) {
                                inv.setItem(slot, filler.clone());
                            }
                        }
                    }
                    if (ms.stepSound() != null) {
                        ms.stepSound().playTo(player);
                    }
                } else {
                    int i = step - 9;
                    if (i >= queue.size()) {
                        cancel();
                        return;
                    }
                    var entry = queue.get(i);
                    inv.setItem(entry.getKey(), entry.getValue());
                    if (ms.itemSound() != null) {
                        ms.itemSound().playTo(player);
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 1L, ms.intervalTicks());
    }

    private ItemStack buildFiller(MenuSettings ms) {
        if (ms.filler() == null) {
            return null;
        }
        ItemStack item = new ItemStack(ms.filler().material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse(ms.filler().name()));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildItem(Player player, MenuSettings.ModeItem mi, RtpMode mode) {
        Lang lang = plugin.lang();
        long left = plugin.cooldowns().remainingSeconds(player.getUniqueId(), mode.id());
        String status;
        if (!mode.canUse(player)) {
            status = lang.raw("menu-status-no-permission");
        } else if (left > 0 && !player.hasPermission("miragertp.bypass.cooldown")) {
            status = lang.format("menu-status-cooldown", "time", lang.formatDuration(left));
        } else {
            status = lang.raw("menu-status-ready");
        }
        Object[] ph = {
                "mode_name", mode.displayName(),
                "min", mode.minRadius(),
                "max", mode.maxRadius(),
                "cost", lang.costText(mode.costType(), mode.costAmount()),
                "cooldown", lang.formatDuration(mode.cooldownFor(player)),
                "status", status,
                "world", mode.world()
        };

        ItemStack item = new ItemStack(mi.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse(Text.apply(mi.name(), ph)));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : mi.lore()) {
            lore.add(Text.parse(Text.apply(line, ph)));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (mi.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }
}
