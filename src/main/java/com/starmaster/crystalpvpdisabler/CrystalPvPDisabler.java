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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bstats.bukkit.Metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrystalPvPDisabler extends JavaPlugin implements Listener {

    private static final int BSTATS_PLUGIN_ID = 32535;

    private final Map<UUID, Long> recentAnchorInteraction = new HashMap<>();
    private final long INTERACTION_COOLDOWN = 300;

    private final Map<Location, Long> pendingAnchorExplosions = new HashMap<>();
    private final long PENDING_EXPLOSION_WINDOW = 3000;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();

        if (getConfig().getBoolean("enable-metrics", true)) {
            new Metrics(this, BSTATS_PLUGIN_ID);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                recentAnchorInteraction.entrySet().removeIf(entry -> 
                    currentTime - entry.getValue() > INTERACTION_COOLDOWN);
                pendingAnchorExplosions.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > PENDING_EXPLOSION_WINDOW);
            }
        }.runTaskTimer(this, 600L, 600L);
        

    }

    @Override
    public void onDisable() {

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            event.getClickedBlock() != null && 
            event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) {

            Block anchor = event.getClickedBlock();

            pendingAnchorExplosions.put(anchor.getLocation(), System.currentTimeMillis());
            boolean isCharged = anchor.getBlockData() instanceof RespawnAnchor respawnAnchor && respawnAnchor.getCharges() > 0;
            boolean willExplode = isCharged && event.getPlayer().getWorld().getEnvironment() != World.Environment.NETHER;
            if (willExplode) {
                anchor.getLocation().getNearbyPlayers(10.0).forEach(nearby ->
                    recentAnchorInteraction.put(nearby.getUniqueId(), System.currentTimeMillis()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity().getType() == EntityType.END_CRYSTAL) {
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        Location explodedLocation = event.getBlock().getLocation();
        Long interactionTime = pendingAnchorExplosions.remove(explodedLocation);

        boolean isAnchorExplosion = event.getBlock().getType() == Material.RESPAWN_ANCHOR ||
            (interactionTime != null && System.currentTimeMillis() - interactionTime < PENDING_EXPLOSION_WINDOW);

        if (isAnchorExplosion) {
            explodedLocation.getNearbyPlayers(10.0).forEach(player -> {
                recentAnchorInteraction.put(player.getUniqueId(), System.currentTimeMillis());
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getDamager().getType() == EntityType.END_CRYSTAL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        EntityDamageEvent.DamageCause cause = event.getCause();

        UUID playerId = player.getUniqueId();
        Long lastInteraction = recentAnchorInteraction.get(playerId);
        
        if (lastInteraction != null && 
            System.currentTimeMillis() - lastInteraction < INTERACTION_COOLDOWN &&
            cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {

            event.setCancelled(true);
        }
    }
}
