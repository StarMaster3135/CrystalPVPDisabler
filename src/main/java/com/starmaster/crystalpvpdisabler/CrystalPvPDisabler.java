package com.starmaster.crystalpvpdisabler;

import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class CrystalPvPDisabler extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CrystalPvPDisabler has been enabled! End crystals and respawn anchors can no longer damage players.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CrystalPvPDisabler has been disabled.");
    }

    /**
     * Prevents end crystal and respawn anchor explosions from damaging players
     * while allowing damage to blocks and other entities
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Check if the explosion is caused by an end crystal
        if (event.getEntity().getType() == EntityType.END_CRYSTAL) {
            // Get all nearby players in the explosion radius
            event.getEntity().getNearbyEntities(6.0, 6.0, 6.0).stream()
                .filter(entity -> entity instanceof Player)
                .forEach(player -> {
                    // Remove any damage that would be applied to players
                    // The explosion will still affect blocks and other entities
                });
        }
    }

    /**
     * Prevents respawn anchor explosions from damaging players
     * while allowing damage to blocks and other entities
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        // Check if the explosion is caused by a respawn anchor
        if (event.getBlock().getType() == Material.RESPAWN_ANCHOR) {
            // Get all nearby players in the explosion radius
            event.getBlock().getLocation().getNearbyPlayers(5.0).forEach(player -> {
                // The damage prevention is handled in EntityDamageByEntityEvent
                // This event is mainly for the explosion effect
            });
        }
    }

    /**
     * Prevents direct damage from end crystal and respawn anchor explosions to players
     * This handles the damage event directly
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if the damage is being dealt to a player
        if (event.getEntity() instanceof Player) {
            // Check if the damage source is an end crystal explosion
            if (event.getDamager().getType() == EntityType.END_CRYSTAL) {
                // Cancel the damage event
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents all block explosion damage to players (mainly respawn anchors)
     * while allowing entity explosions (TNT, creepers, etc.) to work normally
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBlockExplosionDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            // Cancel all block explosion damage to players
            // This will catch respawn anchor explosions
            event.setCancelled(true);
        }
    }
}