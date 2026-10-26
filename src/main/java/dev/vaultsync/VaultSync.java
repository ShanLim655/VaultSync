package dev.vaultsync;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class VaultSync extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor {
    private static final int SNAPSHOT_VERSION = 2;
    private HikariDataSource pool;
    private ExecutorService workers;
    private volatile boolean shuttingDown;
    private final Set<UUID> joiningPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        workers = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "VaultSync-Database");
            thread.setDaemon(true);
            return thread;
        });
        try {
            HikariConfig config = new HikariConfig();
            String host = getConfig().getString("database.host", "127.0.0.1");
            int port = getConfig().getInt("database.port", 3306);
            String database = getConfig().getString("database.name", "minecraft");
            boolean ssl = getConfig().getBoolean("database.use-ssl", false);
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl
                    + "&requireSSL=" + (ssl && getConfig().getBoolean("database.require-ssl", true))
                    + "&serverTimezone=UTC&allowPublicKeyRetrieval=" + getConfig().getBoolean("database.allow-public-key-retrieval", false);
            config.setJdbcUrl(url);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(getConfig().getString("database.username", "vaultsync"));
            config.setPassword(getConfig().getString("database.password", ""));
            config.setPoolName("VaultSync-Pool");
            config.setMaximumPoolSize(Math.max(2, getConfig().getInt("database.pool.maximum-size", 10)));
            config.setMinimumIdle(Math.max(0, Math.min(config.getMaximumPoolSize(), getConfig().getInt("database.pool.minimum-idle", 2))));
            config.setConnectionTimeout(Math.max(1000, getConfig().getLong("database.pool.connection-timeout-ms", 10000)));
            config.setMaxLifetime(Math.max(60000, getConfig().getLong("database.pool.max-lifetime-ms", 1800000)));
            config.setInitializationFailTimeout(10000);
            pool = new HikariDataSource(config);
            validateDatabaseConnection();
            migrate();
            getServer().getPluginManager().registerEvents(this, this);
            if (getCommand("vaultsync") != null) getCommand("vaultsync").setExecutor(this);
            long interval = Math.max(30, getConfig().getLong("saving.autosave-interval-seconds", 300));
            Bukkit.getScheduler().runTaskTimer(this, this::saveAll, interval * 20L, interval * 20L);
            getLogger().info("VaultSync connected to the database and initialized schema version " + SNAPSHOT_VERSION + ".");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "VaultSync is disabling itself because the database could not be validated or initialized. Check config.yml and database availability.", exception);
            if (pool != null) {
                pool.close();
                pool = null;
            }
            if (workers != null) {
                workers.shutdownNow();
                workers = null;
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void validateDatabaseConnection() throws Exception {
        try (Connection connection = pool.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new IllegalStateException("Database validation query did not return 1.");
            }
        }
    }

    private void migrate() throws Exception {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vaultsync_schema_version (version INT NOT NULL PRIMARY KEY, installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vaultsync_player_data (uuid CHAR(36) NOT NULL PRIMARY KEY, player_name VARCHAR(16) NOT NULL, payload LONGBLOB NOT NULL, saved_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), schema_version INT NOT NULL DEFAULT 1, INDEX idx_vaultsync_player_name (player_name)) ENGINE=InnoDB");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vaultsync_pending_restores (uuid CHAR(36) NOT NULL PRIMARY KEY, requested_by VARCHAR(64) NOT NULL, requested_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)) ENGINE=InnoDB");
            statement.executeUpdate("INSERT IGNORE INTO vaultsync_schema_version (version) VALUES (1)");
            statement.executeUpdate("INSERT IGNORE INTO vaultsync_schema_version (version) VALUES (2)");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        joiningPlayers.add(event.getPlayer().getUniqueId());
        load(event.getPlayer(), getConfig().getBoolean("saving.restore-on-join", true), null);
    }

    @EventHandler
    public void onJoinMove(PlayerMoveEvent event) {
        if (!joiningPlayers.contains(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) event.setTo(event.getFrom());
    }

    @EventHandler
    public void onJoinInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && joiningPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && joiningPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinDrop(PlayerDropItemEvent event) {
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && joiningPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinSwap(PlayerSwapHandItemsEvent event) {
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinInteract(PlayerInteractEvent event) {
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinBlockBreak(BlockBreakEvent event) {
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinBlockPlace(BlockPlaceEvent event) {
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoinDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && joiningPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("saving.save-on-quit", true)) save(event.getPlayer(), false, null);
    }

    private void saveAll() {
        if (shuttingDown && !getConfig().getBoolean("saving.save-on-shutdown", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) save(player, false, null);
    }

    private void save(Player player, boolean notify, org.bukkit.command.CommandSender sender) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        byte[] payload;
        try {
            payload = encode(player);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not serialize a snapshot for " + name + " (" + uuid + "). Existing database data was not changed.", exception);
            send(sender, notify, "§cVaultSync could not serialize " + name + "'s data.");
            return;
        }
        enqueue(() -> {
            try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO vaultsync_player_data (uuid, player_name, payload, schema_version) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), payload=VALUES(payload), schema_version=VALUES(schema_version), saved_at=CURRENT_TIMESTAMP(3)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setBytes(3, payload);
                statement.setInt(4, SNAPSHOT_VERSION);
                statement.executeUpdate();
                send(sender, notify, "§aVaultSync saved " + name + "'s data.");
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Database save failed for " + name + " (" + uuid + ").", exception);
                send(sender, notify, "§cVaultSync failed to save " + name + "'s data. Check the server log.");
            }
        });
    }

    private void load(Player player, boolean notify, org.bukkit.command.CommandSender sender) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        enqueue(() -> {
            byte[] payload = null;
            boolean pending = false;
            try (Connection connection = pool.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT payload FROM vaultsync_player_data WHERE uuid=?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) payload = result.getBytes(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM vaultsync_pending_restores WHERE uuid=?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        pending = result.next();
                    }
                }
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Database load failed for " + name + " (" + uuid + ").", exception);
                finishJoinLoad(uuid);
                send(sender, notify, "§cVaultSync could not load " + name + "'s data. Check the server log.");
                return;
            }
            if (payload == null) {
                finishJoinLoad(uuid);
                send(sender, notify, "§eVaultSync has no saved snapshot for " + name + ".");
                return;
            }
            if (!notify && !getConfig().getBoolean("saving.restore-on-join", true) && !pending) {
                finishJoinLoad(uuid);
                return;
            }
            byte[] snapshot = payload;
            boolean clearPending = pending;
            Bukkit.getScheduler().runTask(this, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) {
                    finishJoinLoad(uuid);
                    send(sender, notify, "§e" + name + " left before their snapshot could be restored.");
                    return;
                }
                try {
                    restore(online, snapshot);
                    if (clearPending) enqueue(() -> clearPendingRestore(uuid));
                    send(sender, notify, "§aVaultSync restored " + name + "'s saved data.");
                } catch (Exception exception) {
                    getLogger().log(Level.SEVERE, "Snapshot restore failed for " + name + " (" + uuid + ").", exception);
                    send(sender, notify, "§cVaultSync could not restore " + name + "'s data. Check the server log.");
                } finally {
                    finishJoinLoad(uuid);
                }
            });
        });
    }

    private void finishJoinLoad(UUID uuid) {
        if (Bukkit.isPrimaryThread()) joiningPlayers.remove(uuid);
        else if (!shuttingDown) Bukkit.getScheduler().runTask(this, () -> joiningPlayers.remove(uuid));
    }

    private void clearPendingRestore(UUID uuid) {
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM vaultsync_pending_restores WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Could not clear pending restore marker for " + uuid + ".", exception);
        }
    }

    private void enqueue(Runnable task) {
        if (workers == null || workers.isShutdown()) {
            getLogger().warning("A database operation was rejected because VaultSync is shutting down.");
            return;
        }
        try {
            workers.execute(task);
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not queue a database operation.", exception);
        }
    }

    private void send(org.bukkit.command.CommandSender sender, boolean notify, String message) {
        if (!notify || sender == null) return;
        if (Bukkit.isPrimaryThread()) sender.sendMessage(message);
        else if (!shuttingDown) Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(message));
    }

    private byte[] encode(Player player) throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("inventory", player.getInventory().getContents());
        values.put("armor", player.getInventory().getArmorContents());
        values.put("offhand", player.getInventory().getItemInOffHand());
        values.put("enderchest", player.getEnderChest().getContents());
        values.put("totalExperience", player.getTotalExperience());
        values.put("level", player.getLevel());
        values.put("exp", player.getExp());
        values.put("health", player.getHealth());
        values.put("food", player.getFoodLevel());
        values.put("saturation", player.getSaturation());
        values.put("exhaustion", player.getExhaustion());
        values.put("fireTicks", player.getFireTicks());
        values.put("remainingAir", player.getRemainingAir());
        values.put("gameMode", player.getGameMode().name());
        values.put("allowFlight", player.getAllowFlight());
        values.put("flying", player.isFlying());
        values.put("flySpeed", player.getFlySpeed());
        values.put("walkSpeed", player.getWalkSpeed());
        values.put("absorption", player.getAbsorptionAmount());
        values.put("healthScaled", player.isHealthScaled());
        values.put("healthScale", player.getHealthScale());
        values.put("bedSpawn", player.getBedSpawnLocation());
        values.put("compassTarget", player.getCompassTarget());

        Map<String, Double> attributes = new HashMap<>();
        for (Attribute attribute : Attribute.values()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) attributes.put(attribute.name(), instance.getBaseValue());
        }
        values.put("attributes", attributes);

        List<Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", effect.getType().getKey().toString());
            entry.put("duration", effect.getDuration());
            entry.put("amplifier", effect.getAmplifier());
            entry.put("ambient", effect.isAmbient());
            entry.put("particles", effect.hasParticles());
            entry.put("icon", effect.hasIcon());
            effects.add(entry);
        }
        values.put("effects", effects);

        Map<String, Integer> untypedStats = new HashMap<>();
        Map<String, Integer> materialStats = new HashMap<>();
        Map<String, Integer> entityStats = new HashMap<>();
        for (Statistic statistic : Statistic.values()) {
            try {
                if (statistic.getType() == Statistic.Type.UNTYPED) {
                    untypedStats.put(statistic.name(), player.getStatistic(statistic));
                } else if (statistic.getType() == Statistic.Type.ITEM || statistic.getType() == Statistic.Type.BLOCK) {
                    for (Material material : Material.values()) {
                        if (material.isItem() && (statistic.getType() != Statistic.Type.BLOCK || material.isBlock())) {
                            int value = player.getStatistic(statistic, material);
                            if (value != 0) materialStats.put(statistic.name() + "|" + material.name(), value);
                        }
                    }
                } else if (statistic.getType() == Statistic.Type.ENTITY) {
                    for (EntityType entityType : EntityType.values()) {
                        try {
                            int value = player.getStatistic(statistic, entityType);
                            if (value != 0) entityStats.put(statistic.name() + "|" + entityType.name(), value);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        values.put("statsUntyped", untypedStats);
        values.put("statsMaterial", materialStats);
        values.put("statsEntity", entityStats);

        Map<String, List<String>> advancements = new HashMap<>();
        Iterator<org.bukkit.advancement.Advancement> advancementIterator = Bukkit.advancementIterator();
        while (advancementIterator.hasNext()) {
            org.bukkit.advancement.Advancement advancement = advancementIterator.next();
            Set<String> awarded = new HashSet<>(player.getAdvancementProgress(advancement).getAwardedCriteria());
            if (!awarded.isEmpty()) advancements.put(advancement.getKey().toString(), new ArrayList<>(awarded));
        }
        values.put("advancements", advancements);
        List<String> recipes = new ArrayList<>();
        for (NamespacedKey key : player.getDiscoveredRecipes()) recipes.add(key.toString());
        values.put("recipes", recipes);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(SNAPSHOT_VERSION);
            out.writeObject(values);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private void restore(Player player, byte[] payload) throws Exception {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version == 1) {
                restoreVersionOne(player, in);
                return;
            }
            if (version != SNAPSHOT_VERSION) throw new IllegalStateException("Unsupported snapshot version: " + version);
            Map<String, Object> values = (Map<String, Object>) in.readObject();
            player.getInventory().setContents((ItemStack[]) values.get("inventory"));
            player.getInventory().setArmorContents((ItemStack[]) values.get("armor"));
            player.getInventory().setItemInOffHand((ItemStack) values.get("offhand"));
            player.getEnderChest().setContents((ItemStack[]) values.get("enderchest"));
            player.setTotalExperience(Math.max(0, (Integer) values.get("totalExperience")));
            player.setLevel(Math.max(0, (Integer) values.get("level")));
            player.setExp(Math.max(0, Math.min(1, (Float) values.get("exp"))));
            restoreAttributes(player, (Map<String, Double>) values.get("attributes"));
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.max(0.1, Math.min(maxHealth, (Double) values.get("health"))));
            player.setFoodLevel(Math.max(0, Math.min(20, (Integer) values.get("food"))));
            player.setSaturation(Math.max(0, (Float) values.get("saturation")));
            player.setExhaustion(Math.max(0, (Float) values.get("exhaustion")));
            player.setFireTicks(Math.max(0, (Integer) values.get("fireTicks")));
            player.setRemainingAir(Math.max(0, (Integer) values.get("remainingAir")));
            player.setGameMode(GameMode.valueOf((String) values.get("gameMode")));
            player.setAbsorptionAmount(Math.max(0, (Double) values.get("absorption")));
            player.setHealthScaled((Boolean) values.get("healthScaled"));
            player.setHealthScale(Math.max(1, (Double) values.get("healthScale")));
            player.setWalkSpeed(clampSpeed((Float) values.get("walkSpeed")));
            player.setFlySpeed(clampSpeed((Float) values.get("flySpeed")));
            boolean allowFlight = (Boolean) values.get("allowFlight");
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying((Boolean) values.get("flying"));
            Object bedSpawn = values.get("bedSpawn");
            if (bedSpawn instanceof org.bukkit.Location location && location.getWorld() != null) player.setBedSpawnLocation(location, true);
            Object compassTarget = values.get("compassTarget");
            if (compassTarget instanceof org.bukkit.Location location && location.getWorld() != null) player.setCompassTarget(location);

            restoreStatistics(player, (Map<String, Integer>) values.get("statsUntyped"),
                    (Map<String, Integer>) values.get("statsMaterial"), (Map<String, Integer>) values.get("statsEntity"));
            restoreEffects(player, (List<Map<String, Object>>) values.get("effects"));
            restoreAdvancements(player, (Map<String, List<String>>) values.get("advancements"));
            restoreRecipes(player, (List<String>) values.get("recipes"));
            player.updateInventory();
        }
    }

    private float clampSpeed(float speed) {
        return Math.max(-1.0f, Math.min(1.0f, speed));
    }

    private void restoreAttributes(Player player, Map<String, Double> attributes) {
        if (attributes == null) return;
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            try {
                Attribute attribute = Attribute.valueOf(entry.getKey());
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null && Double.isFinite(entry.getValue())) instance.setBaseValue(entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void restoreStatistics(Player player, Map<String, Integer> untyped, Map<String, Integer> materials, Map<String, Integer> entities) {
        if (untyped != null) {
            for (Map.Entry<String, Integer> entry : untyped.entrySet()) {
                try { player.setStatistic(Statistic.valueOf(entry.getKey()), Math.max(0, entry.getValue())); } catch (IllegalArgumentException ignored) {}
            }
        }
        if (materials != null) {
            for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                String[] parts = entry.getKey().split("\\|", 2);
                if (parts.length != 2) continue;
                try { player.setStatistic(Statistic.valueOf(parts[0]), Material.valueOf(parts[1]), Math.max(0, entry.getValue())); } catch (IllegalArgumentException ignored) {}
            }
        }
        if (entities != null) {
            for (Map.Entry<String, Integer> entry : entities.entrySet()) {
                String[] parts = entry.getKey().split("\\|", 2);
                if (parts.length != 2) continue;
                try { player.setStatistic(Statistic.valueOf(parts[0]), EntityType.valueOf(parts[1]), Math.max(0, entry.getValue())); } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void restoreEffects(Player player, List<Map<String, Object>> effects) {
        for (PotionEffect current : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(current.getType());
        if (effects == null) return;
        for (Map<String, Object> entry : effects) {
            NamespacedKey key = NamespacedKey.fromString((String) entry.get("type"));
            PotionEffectType type = key == null ? null : PotionEffectType.getByKey(key);
            if (type == null) continue;
            PotionEffect effect = new PotionEffect(type, Math.max(1, (Integer) entry.get("duration")),
                    Math.max(0, (Integer) entry.get("amplifier")), (Boolean) entry.get("ambient"),
                    (Boolean) entry.get("particles"), (Boolean) entry.get("icon"));
            player.addPotionEffect(effect, true);
        }
    }

    private void restoreAdvancements(Player player, Map<String, List<String>> advancements) {
        if (advancements == null) return;
        Iterator<org.bukkit.advancement.Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            org.bukkit.advancement.Advancement advancement = iterator.next();
            org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(advancement);
            List<String> awarded = advancements.get(advancement.getKey().toString());
            Set<String> target = awarded == null ? Set.of() : new HashSet<>(awarded);
            for (String criterion : new HashSet<>(progress.getAwardedCriteria())) {
                if (!target.contains(criterion)) progress.revokeCriteria(criterion);
            }
            for (String criterion : target) {
                if (progress.getRemainingCriteria().contains(criterion)) progress.awardCriteria(criterion);
            }
        }
    }

    private void restoreRecipes(Player player, List<String> recipes) {
        Set<NamespacedKey> target = new HashSet<>();
        if (recipes != null) {
            for (String value : recipes) {
                NamespacedKey key = NamespacedKey.fromString(value);
                if (key != null) target.add(key);
            }
        }
        for (NamespacedKey current : new HashSet<>(player.getDiscoveredRecipes())) {
            if (!target.contains(current)) player.undiscoverRecipe(current);
        }
        for (NamespacedKey key : target) {
            if (Bukkit.getRecipe(key) != null) player.discoverRecipe(key);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreVersionOne(Player player, BukkitObjectInputStream in) throws Exception {
        player.getInventory().setContents((ItemStack[]) in.readObject());
        player.getInventory().setArmorContents((ItemStack[]) in.readObject());
        player.getInventory().setItemInOffHand((ItemStack) in.readObject());
        player.getEnderChest().setContents((ItemStack[]) in.readObject());
        player.setTotalExperience(Math.max(0, in.readInt()));
        player.setLevel(Math.max(0, in.readInt()));
        player.setExp(Math.max(0, Math.min(1, in.readFloat())));
        double health = in.readDouble();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.max(0.1, Math.min(maxHealth, health)));
        player.setFoodLevel(Math.max(0, Math.min(20, in.readInt())));
        player.setSaturation(Math.max(0, in.readFloat()));
        player.setExhaustion(Math.max(0, in.readFloat()));
        player.setFireTicks(Math.max(0, in.readInt()));
        player.setRemainingAir(in.readInt());
        player.setGameMode(GameMode.valueOf((String) in.readObject()));
        boolean allowFlight = in.readBoolean();
        boolean flying = in.readBoolean();
        player.setAllowFlight(allowFlight);
        if (allowFlight) player.setFlying(flying);
        Map<String, Integer> stats = (Map<String, Integer>) in.readObject();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            try { player.setStatistic(Statistic.valueOf(entry.getKey()), Math.max(0, entry.getValue())); } catch (IllegalArgumentException ignored) {}
        }
        Map<String, Integer> effects = (Map<String, Integer>) in.readObject();
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        for (Map.Entry<String, Integer> entry : effects.entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            PotionEffectType type = key == null ? null : PotionEffectType.getByKey(key);
            if (type != null) player.addPotionEffect(new PotionEffect(type, Math.max(1, entry.getValue()), 0));
        }
        Map<String, Integer> advancements = (Map<String, Integer>) in.readObject();
        Iterator<org.bukkit.advancement.Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            org.bukkit.advancement.Advancement advancement = iterator.next();
            int remaining = advancements.getOrDefault(advancement.getKey().toString(), 0);
            for (String criterion : player.getAdvancementProgress(advancement).getRemainingCriteria()) {
                if (remaining-- > 0) player.getAdvancementProgress(advancement).awardCriteria(criterion);
            }
        }
        player.updateInventory();
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6VaultSync §7| /vaultsync <status|save|restore|info|reload>");
            return true;
        }
        String action = args[0].toLowerCase();
        if (!sender.hasPermission("vaultsync.admin") && !sender.hasPermission("vaultsync." + action)) {
            sender.sendMessage("§cYou do not have permission to do that.");
            return true;
        }
        if (action.equals("status")) {
            int onlineCount = Bukkit.getOnlinePlayers().size();
            sender.sendMessage("§7Checking database connection...");
            enqueue(() -> {
                String message;
                try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT 1")) {
                    message = result.next() ? "§6VaultSync §7| Database: §aConnected §7| Online: §f" + onlineCount : "§cDatabase check failed.";
                } catch (Exception exception) {
                    getLogger().log(Level.WARNING, "Database status check failed.", exception);
                    message = "§cDatabase disconnected. Check the server log.";
                }
                send(sender, true, message);
            });
            return true;
        }
        if (action.equals("reload")) {
            reloadConfig();
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
                    save(target, true, sender);
                    sender.sendMessage("§7VaultSync queued a snapshot for " + target.getName() + ".");
                } else {
                    sender.sendMessage("§eRestoring replaces the target's current vanilla player state.");
                    joiningPlayers.add(target.getUniqueId());
                    load(target, true, sender);
                }
                return true;
            }
            if (action.equals("save")) {
                sender.sendMessage("§cSaving requires the player to be online so their current state can be captured.");
                return true;
            }
            enqueue(() -> requestOfflineRestore(targetName, sender));
            return true;
        }
        if (action.equals("info")) {
            enqueue(() -> queryInfo(targetName, sender));
            return true;
        }
        sender.sendMessage("§cUnknown command. Use /vaultsync <status|save|restore|info|reload>.");
        return true;
    }

    private void requestOfflineRestore(String name, org.bukkit.command.CommandSender sender) {
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid FROM vaultsync_player_data WHERE LOWER(player_name)=LOWER(?) ORDER BY saved_at DESC LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    send(sender, true, "§eNo stored snapshot found for " + name + ".");
                    return;
                }
                UUID uuid = UUID.fromString(result.getString(1));
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO vaultsync_pending_restores (uuid, requested_by) VALUES (?, ?) ON DUPLICATE KEY UPDATE requested_by=VALUES(requested_by), requested_at=CURRENT_TIMESTAMP(3)")) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, sender.getName());
                    insert.executeUpdate();
                }
                send(sender, true, "§aOffline restore queued for " + name + ". Their stored snapshot will be applied on their next join.");
            }
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not queue offline restore for " + name + ".", exception);
            send(sender, true, "§cCould not queue offline restore. Check the server log.");
        }
    }

    private void queryInfo(String name, org.bukkit.command.CommandSender sender) {
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, player_name, saved_at, schema_version, OCTET_LENGTH(payload) FROM vaultsync_player_data WHERE LOWER(player_name)=LOWER(?) ORDER BY saved_at DESC LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    send(sender, true, "§eNo stored snapshot found for " + name + ".");
                    return;
                }
                UUID uuid = UUID.fromString(result.getString(1));
                String playerName = result.getString(2);
                Timestamp savedAt = result.getTimestamp(3);
                int version = result.getInt(4);
                long bytes = result.getLong(5);
                send(sender, true, "§6VaultSync §7| " + playerName + " | UUID: " + uuid + " | Saved: " + savedAt + " | Snapshot v" + version + " | " + bytes + " bytes");
            }
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not inspect snapshot for " + name + ".", exception);
            send(sender, true, "§cCould not query player data. Check the server log.");
        }
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (workers != null) {
            if (getConfig().getBoolean("saving.save-on-shutdown", true)) {
                for (Player player : Bukkit.getOnlinePlayers()) save(player, false, null);
            }
            workers.shutdown();
            try {
                if (!workers.awaitTermination(Math.max(1, getConfig().getLong("saving.shutdown-timeout-seconds", 30)), TimeUnit.SECONDS)) {
                    getLogger().severe("Database queue did not flush before timeout; remaining operations are being cancelled.");
                    workers.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                workers.shutdownNow();
            }
        }
        if (pool != null) pool.close();
    }
}
