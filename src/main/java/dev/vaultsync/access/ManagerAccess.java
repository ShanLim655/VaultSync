package dev.vaultsync.access;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public final class ManagerAccess {
    private final Supplier<FileConfiguration> configSupplier;

    public ManagerAccess(Supplier<FileConfiguration> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public boolean canUseGui(Player player) {
        if (player == null) {
            return false;
        }
        if (!player.hasPermission("vaultsync.admin") && !player.hasPermission("vaultsync.gui")) {
            return false;
        }
        return isAllowlisted(player.getUniqueId(), configSupplier.get().getStringList("allowmanager"));
    }

    public static boolean isAllowlisted(UUID uuid, List<String> entries) {
        if (uuid == null || entries == null || entries.isEmpty()) {
            return false;
        }
        String expected = uuid.toString().toLowerCase(Locale.ROOT);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            try {
                if (UUID.fromString(trimmed).toString().toLowerCase(Locale.ROOT).equals(expected)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    public static List<UUID> parseAllowmanager(List<String> entries) {
        List<UUID> parsed = new ArrayList<>();
        if (entries == null) {
            return parsed;
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                parsed.add(UUID.fromString(entry.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parsed;
    }
}
