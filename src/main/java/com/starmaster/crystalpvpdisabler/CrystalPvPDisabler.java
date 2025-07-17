package com.starmaster.crystalpvpdisabler;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrystalPvPDisabler extends JavaPlugin implements Listener {

    // Track players who recently interacted with respawn anchors
    private final Map<UUID, Long> recentAnchorInteraction = new HashMap<>();
    private final long INTERACTION_COOLDOWN = 5000; // 5 seconds

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        
        // Clean up old interactions every 30 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                recentAnchorInteraction.entrySet().removeIf(entry -> 
                    currentTime - entry.getValue() > INTERACTION_COOLDOWN);
            }
        }.runTaskTimer(this, 600L, 600L); // 30 seconds
        
        getLogger().info("CrystalPvPDisabler has been enabled! End crystals and respawn anchors can no longer damage players.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CrystalPvPDisabler has been disabled.");
    }

    /**
     * Track when players interact with respawn anchors
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            event.getClickedBlock() != null && 
            event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) {
            
            // Track this interaction
            recentAnchorInteraction.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
            getLogger().info("Player " + event.getPlayer().getName() + " interacted with respawn anchor");
        }
    }

    /**
     * Handle end crystal explosions
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity().getType() == EntityType.END_CRYSTAL) {
            getLogger().info("End crystal explosion detected");
        }
    }

    /**
     * Handle respawn anchor explosions (block-based)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getType() == Material.RESPAWN_ANCHOR) {
            getLogger().info("Respawn anchor block explosion detected");
            
            // Get all nearby players and mark them for protection
            event.getBlock().getLocation().getNearbyPlayers(10.0).forEach(player -> {
                recentAnchorInteraction.put(player.getUniqueId(), System.currentTimeMillis());
            });
        }
    }

    /**
     * Handle end crystal damage
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getDamager().getType() == EntityType.END_CRYSTAL) {
                event.setCancelled(true);
                getLogger().info("Blocked end crystal damage to player: " + event.getEntity().getName());
            }
        }
    }

    /**
     * Comprehensive damage handler - catches ALL damage types
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        EntityDamageEvent.DamageCause cause = event.getCause();
        
        // Always log damage for debugging
        getLogger().info("Player " + player.getName() + " took " + event.getFinalDamage() + " damage from: " + cause);
        
        // Check if this player recently interacted with a respawn anchor
        UUID playerId = player.getUniqueId();
        Long lastInteraction = recentAnchorInteraction.get(playerId);
        
        if (lastInteraction != null && 
            System.currentTimeMillis() - lastInteraction < INTERACTION_COOLDOWN) {
            
            // Block any explosion damage if they recently interacted with an anchor
            if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                
                event.setCancelled(true);
                getLogger().info("Blocked potential respawn anchor damage to player: " + player.getName());
                return;
            }
        }
        
        // Alternative approach: Check for nearby respawn anchors for any explosion damage
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            
            if (isNearbyRespawnAnchor(player)) {
                event.setCancelled(true);
                getLogger().info("Blocked explosion damage near respawn anchor to player: " + player.getName());
            }
        }
    }

    /**
     * Check for nearby respawn anchors
     */
    private boolean isNearbyRespawnAnchor(Player player) {
        int radius = 10; // Larger radius to be safe
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + y*y + z*z);
                    if (distance > radius) continue;
                    
                    try {
                        Material blockType = player.getLocation().clone().add(x, y, z).getBlock().getType();
                        if (blockType == Material.RESPAWN_ANCHOR) {
                            return true;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
        return false;
    }
}
