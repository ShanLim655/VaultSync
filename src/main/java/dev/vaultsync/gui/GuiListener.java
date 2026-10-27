package dev.vaultsync.gui;

import dev.vaultsync.VaultSync;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class GuiListener implements Listener {
    private final VaultSync plugin;
    private final ManagerGui gui;

    public GuiListener(VaultSync plugin, ManagerGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof VaultSyncGuiHolder guiHolder)) {
            return;
        }

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.managerAccess().canUseGui(player)) {
            player.closeInventory();
            player.sendMessage("§cYou are not allowed to use the VaultSync manager GUI.");
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }

        switch (guiHolder.view()) {
            case PLAYERS -> handlePlayersClick(player, event.getSlot());
            case ACTIONS -> handleActionsClick(player, guiHolder, event.getSlot());
            case CONFIRM_RESTORE -> handleConfirmClick(player, guiHolder, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof VaultSyncGuiHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onCreative(InventoryCreativeEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof VaultSyncGuiHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    private void handlePlayersClick(Player player, int slot) {
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        ItemStack item = top.getItem(slot);
        if (item == null || !(item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skullMeta)) {
            return;
        }
        if (skullMeta.getOwningPlayer() == null) {
            return;
        }
        UUID targetId = skullMeta.getOwningPlayer().getUniqueId();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cThat player is no longer online.");
            gui.openPlayers(player);
            return;
        }
        gui.openActions(player, target);
    }

    private void handleActionsClick(Player player, VaultSyncGuiHolder holder, int slot) {
        Player target = resolveTarget(holder);
        if (target == null) {
            player.sendMessage("§cThat player is no longer online.");
            gui.openPlayers(player);
            return;
        }
        switch (slot) {
            case 11 -> {
                player.closeInventory();
                plugin.savePlayer(target, player);
            }
            case 13 -> {
                player.closeInventory();
                plugin.queryPlayerInfo(target.getName(), player);
            }
            case 15 -> gui.openConfirmRestore(player, target);
            case 22 -> gui.openPlayers(player);
            default -> {
            }
        }
    }

    private void handleConfirmClick(Player player, VaultSyncGuiHolder holder, int slot) {
        if (slot == 15) {
            Player target = resolveTarget(holder);
            if (target != null) {
                gui.openActions(player, target);
            } else {
                gui.openPlayers(player);
            }
            return;
        }
        if (slot != 11) {
            return;
        }
        Player target = resolveTarget(holder);
        player.closeInventory();
        if (target == null) {
            player.sendMessage("§cThat player is no longer online.");
            return;
        }
        plugin.restorePlayer(target, player);
    }

    private static Player resolveTarget(VaultSyncGuiHolder holder) {
        if (holder.targetUuid() == null) {
            return null;
        }
        Player target = Bukkit.getPlayer(holder.targetUuid());
        if (target == null || !target.isOnline()) {
            return null;
        }
        return target;
    }
}
