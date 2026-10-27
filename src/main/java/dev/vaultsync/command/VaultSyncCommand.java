package dev.vaultsync.command;

import dev.vaultsync.VaultSync;
import dev.vaultsync.gui.ManagerGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class VaultSyncCommand implements CommandExecutor {
    private final VaultSync plugin;
    private final ManagerGui managerGui;

    public VaultSyncCommand(VaultSync plugin, ManagerGui managerGui) {
        this.plugin = plugin;
        this.managerGui = managerGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6VaultSync §7| /vaultsync <status|save|restore|info|reload|gui>");
            return true;
        }

        String action = args[0].toLowerCase();
        if (action.equals("gui")) {
            return handleGui(sender);
        }

        if (!sender.hasPermission("vaultsync.admin") && !sender.hasPermission("vaultsync." + action)) {
            sender.sendMessage("§cYou do not have permission to do that.");
            return true;
        }

        if (action.equals("status")) {
            plugin.status(sender);
            return true;
        }
        if (action.equals("reload")) {
            plugin.reloadVaultSyncConfig();
            sender.sendMessage("§aVaultSync configuration reloaded. Database connection settings require a restart.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /vaultsync " + action + " <player>");
            return true;
        }

        String targetName = args[1];
        if (action.equals("save") || action.equals("restore")) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                if (action.equals("save")) {
                    plugin.savePlayer(target, sender);
                } else {
                    sender.sendMessage("§eRestoring replaces the target's current vanilla player state.");
                    plugin.restorePlayer(target, sender);
                }
                return true;
            }
            if (action.equals("save")) {
                sender.sendMessage("§cSaving requires the player to be online so their current state can be captured.");
                return true;
            }
            plugin.queueOfflineRestore(targetName, sender);
            return true;
        }
        if (action.equals("info")) {
            plugin.queryPlayerInfo(targetName, sender);
            return true;
        }

        sender.sendMessage("§cUnknown command. Use /vaultsync <status|save|restore|info|reload|gui>.");
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThe VaultSync GUI can only be opened by players.");
            return true;
        }
        if (!plugin.managerAccess().canUseGui(player)) {
            player.sendMessage("§cYou do not have permission to use the VaultSync manager GUI.");
            return true;
        }
        managerGui.openPlayers(player);
        return true;
    }
}
