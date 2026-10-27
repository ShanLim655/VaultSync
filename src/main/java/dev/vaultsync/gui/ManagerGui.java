package dev.vaultsync.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ManagerGui {
    public static final String TITLE_PLAYERS = "VaultSync Players";
    public static final String TITLE_ACTIONS = "VaultSync Actions";
    public static final String TITLE_CONFIRM = "VaultSync Confirm Restore";

    public void openPlayers(Player viewer) {
        VaultSyncGuiHolder holder = new VaultSyncGuiHolder(GuiView.PLAYERS, null, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, TITLE_PLAYERS);
        holder.setInventory(inventory);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(player -> player.getName().toLowerCase()));
        int slot = 0;
        for (Player target : online) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, playerHead(target));
        }

        inventory.setItem(49, namedItem(Material.BARRIER, "§cClose"));
        viewer.openInventory(inventory);
    }

    public void openActions(Player viewer, Player target) {
        VaultSyncGuiHolder holder = new VaultSyncGuiHolder(GuiView.ACTIONS, target.getUniqueId(), target.getName());
        Inventory inventory = Bukkit.createInventory(holder, 27, TITLE_ACTIONS);
        holder.setInventory(inventory);

        inventory.setItem(4, playerHead(target));
        inventory.setItem(11, namedItem(Material.CHEST, "§aSave", "§7Save " + target.getName() + "'s current state."));
        inventory.setItem(13, namedItem(Material.BOOK, "§eInfo", "§7Inspect the stored snapshot."));
        inventory.setItem(15, namedItem(Material.ENDER_CHEST, "§cRestore", "§7Restore the stored snapshot.", "§eRequires confirmation."));
        inventory.setItem(22, namedItem(Material.ARROW, "§7Back"));
        viewer.openInventory(inventory);
    }

    public void openConfirmRestore(Player viewer, Player target) {
        VaultSyncGuiHolder holder = new VaultSyncGuiHolder(GuiView.CONFIRM_RESTORE, target.getUniqueId(), target.getName());
        Inventory inventory = Bukkit.createInventory(holder, 27, TITLE_CONFIRM);
        holder.setInventory(inventory);

        inventory.setItem(4, playerHead(target));
        inventory.setItem(11, namedItem(Material.LIME_CONCRETE, "§aConfirm Restore",
                "§7Restore " + target.getName() + "'s saved data.",
                "§cThis replaces their current vanilla state."));
        inventory.setItem(15, namedItem(Material.RED_CONCRETE, "§cCancel"));
        viewer.openInventory(inventory);
    }

    private static ItemStack playerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName("§f" + player.getName());
            skullMeta.setLore(List.of("§7Click to manage", "§8" + player.getUniqueId()));
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private static ItemStack namedItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(line);
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
