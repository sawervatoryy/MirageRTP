package org.github.sawervatoryy.mirageRTP.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.github.sawervatoryy.mirageRTP.MirageRTP;
import org.github.sawervatoryy.mirageRTP.gui.RtpMenuHolder;
import org.github.sawervatoryy.mirageRTP.model.RtpMode;
import org.github.sawervatoryy.mirageRTP.model.TeleportOptions;

public final class MenuListener implements Listener {

    private final MirageRTP plugin;

    public MenuListener(MirageRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof RtpMenuHolder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)
                || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        String modeId = holder.modeAt(e.getSlot());
        if (modeId == null) {
            return;
        }
        RtpMode mode = plugin.settings().modes().get(modeId);
        if (mode == null) {
            return;
        }
        // закрываем и стартуем на следующий тик, чтобы не менять инвентарь внутри события клика
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            plugin.teleports().request(player, mode, TeleportOptions.normal());
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof RtpMenuHolder) {
            e.setCancelled(true);
        }
    }
}
