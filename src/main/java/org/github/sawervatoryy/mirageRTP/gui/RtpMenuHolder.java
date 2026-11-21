package org.github.sawervatoryy.mirageRTP.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Держатель инвентаря главного меню РТП: хранит соответствие слот → режим. */
public final class RtpMenuHolder implements InventoryHolder {

    private final Map<Integer, String> slotToMode = new HashMap<>();
    private Inventory inventory;

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    void map(int slot, String modeId) {
        slotToMode.put(slot, modeId);
    }

    public String modeAt(int slot) {
        return slotToMode.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
