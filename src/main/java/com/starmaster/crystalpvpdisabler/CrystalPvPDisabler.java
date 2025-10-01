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
        

    }

    @Override
    public void onDisable() {

    }

    /**
     * Track when players interact with respawn anchors
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            event.getClickedBlock() != null && 
            event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) {
            
            recentAnchorInteraction.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Handle end crystal explosions
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity().getType() == EntityType.END_CRYSTAL) {
            // End crystal explosion detected
        }
    }

    /**
     * Handle respawn anchor explosions (block-based)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getType() == Material.RESPAWN_ANCHOR) {
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
        
        // Check if this player recently interacted with a respawn anchor
        UUID playerId = player.getUniqueId();
        Long lastInteraction = recentAnchorInteraction.get(playerId);
        
        if (lastInteraction != null && 
            System.currentTimeMillis() - lastInteraction < INTERACTION_COOLDOWN) {
            
            if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                
                event.setCancelled(true);
                return;
            }
        }
        
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            
            if (isNearbyRespawnAnchor(player)) {
                event.setCancelled(true);
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
