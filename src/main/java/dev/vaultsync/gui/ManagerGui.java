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
    public static final int PAGE_SIZE = 45;

    public void openPlayers(Player viewer) {
        openPlayers(viewer, 0);
    }

    public void openPlayers(Player viewer, int requestedPage) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int pageCount = pageCount(online.size());
        int page = clampPage(requestedPage, online.size());
        VaultSyncGuiHolder holder = new VaultSyncGuiHolder(GuiView.PLAYERS, null, null, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, TITLE_PLAYERS);
        holder.setInventory(inventory);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, online.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, playerHead(online.get(index)));
        }

        if (page > 0) {
            inventory.setItem(45, namedItem(Material.ARROW, "§ePrevious Page"));
        }
        inventory.setItem(49, namedItem(Material.PAPER, "§fPage " + (page + 1) + " / " + pageCount));
        inventory.setItem(50, namedItem(Material.BARRIER, "§cClose"));
        if (page + 1 < pageCount) {
            inventory.setItem(53, namedItem(Material.ARROW, "§eNext Page"));
        }
        viewer.openInventory(inventory);
    }

    static int pageCount(int playerCount) {
        return Math.max(1, (Math.max(0, playerCount) + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clampPage(int requestedPage, int playerCount) {
        return Math.max(0, Math.min(requestedPage, pageCount(playerCount) - 1));
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
