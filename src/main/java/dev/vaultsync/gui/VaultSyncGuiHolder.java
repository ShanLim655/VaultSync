package dev.vaultsync.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class VaultSyncGuiHolder implements InventoryHolder {
    private final GuiView view;
    private final UUID targetUuid;
    private final String targetName;
    private final int page;
    private Inventory inventory;

    public VaultSyncGuiHolder(GuiView view, UUID targetUuid, String targetName) {
        this(view, targetUuid, targetName, 0);
    }

    public VaultSyncGuiHolder(GuiView view, UUID targetUuid, String targetName, int page) {
        this.view = view;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.page = Math.max(0, page);
    }

    public GuiView view() {
        return view;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public int page() {
        return page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
