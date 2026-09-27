package com.starmaster.crystalpvpdisabler;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CrystalPvPDisabler extends JavaPlugin implements Listener {

    private static final int BSTATS_PLUGIN_ID = 32535;

    private static final long INTERACTION_COOLDOWN = 300L;
    private static final long PENDING_EXPLOSION_WINDOW = 3000L;
    private static final long CLEANUP_DELAY_TICKS = 600L;
    private static final long CLEANUP_PERIOD_TICKS = 600L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final double AFFECTED_RADIUS = 10.0;

    private final Map<UUID, Long> recentAnchorInteraction = new ConcurrentHashMap<>();
    private final Map<BlockKey, Long> pendingAnchorExplosions = new ConcurrentHashMap<>();

    private ScheduledExecutorService cleanupExecutor;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();

        if (getConfig().getBoolean("enable-metrics", true)) {
            new Metrics(this, BSTATS_PLUGIN_ID);
        }

        startCleanupTask();
    }

    @Override
    public void onDisable() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
    }

    private void startCleanupTask() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, getName() + "-cleanup");
            thread.setDaemon(true);
            return thread;
        });

        cleanupExecutor.scheduleAtFixedRate(this::pruneExpiredEntries,
                CLEANUP_DELAY_TICKS * MILLIS_PER_TICK,
                CLEANUP_PERIOD_TICKS * MILLIS_PER_TICK,
                TimeUnit.MILLISECONDS);
    }

    private void pruneExpiredEntries() {
        long currentTime = System.currentTimeMillis();

        recentAnchorInteraction.values().removeIf(timestamp -> currentTime - timestamp > INTERACTION_COOLDOWN);
        pendingAnchorExplosions.values().removeIf(timestamp -> currentTime - timestamp > PENDING_EXPLOSION_WINDOW);
    }

    private void markNearbyPlayers(Location location, long timestamp) {
        location.getNearbyPlayers(AFFECTED_RADIUS)
                .forEach(player -> recentAnchorInteraction.put(player.getUniqueId(), timestamp));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.RESPAWN_ANCHOR) {
            return;
        }

        Block anchor = event.getClickedBlock();
        long now = System.currentTimeMillis();

        pendingAnchorExplosions.put(BlockKey.of(anchor), now);

        boolean isCharged = anchor.getBlockData() instanceof RespawnAnchor respawnAnchor && respawnAnchor.getCharges() > 0;
        boolean willExplode = isCharged && event.getPlayer().getWorld().getEnvironment() != World.Environment.NETHER;
        if (willExplode) {
            markNearbyPlayers(anchor.getLocation(), now);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block exploded = event.getBlock();
        long now = System.currentTimeMillis();

        Long interactionTime = pendingAnchorExplosions.remove(BlockKey.of(exploded));

        boolean isAnchorExplosion = exploded.getType() == Material.RESPAWN_ANCHOR
                || (interactionTime != null && now - interactionTime < PENDING_EXPLOSION_WINDOW);

        if (isAnchorExplosion) {
            markNearbyPlayers(exploded.getLocation(), now);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager().getType() == EntityType.END_CRYSTAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)
                || event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        Player player = (Player) event.getEntity();
        Long lastInteraction = recentAnchorInteraction.get(player.getUniqueId());

        if (lastInteraction != null
                && System.currentTimeMillis() - lastInteraction < INTERACTION_COOLDOWN) {
            event.setCancelled(true);
        }
    }

    private record BlockKey(UUID world, int x, int y, int z) {

        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
