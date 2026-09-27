# CrystalPvPDisabler

**An easy way to turn off CrystalPVP on your server!**

Tired of people using end crystals and respawn anchors in fights? This plugin disables crystal/anchor PvP while keeping everything else explosive!

## What's Blocked
- End Crystal damage to players
- Respawn Anchor damage to players

## What Still Works  
- TNT, Creepers, Minecarts - **Full damage**
- Placing & crafting crystals/anchors - **Still works**
- Block destruction from crystals/anchors - **Still works**

## Features
- **Zero config** - Drop in and go
- **Ultra lightweight** - Doesn't lag your server
- **Folia support** - Region-safe, no BukkitScheduler calls

## Requirements
- Java 21+
- Minecraft 1.21+ (api-version `1.21`)
- Works on Paper, Folia and Spigot

## Folia

The plugin is marked `folia-supported: true` and is safe to run on regionised servers:

- No `BukkitScheduler` / `BukkitRunnable` usage. On Folia those throw, so the periodic
  cache-pruning task runs on the plugin's own daemon thread instead. It only touches
  plain `ConcurrentHashMap`s, never world or entity state, so it needs no region ownership.
- Both shared maps are `ConcurrentHashMap`s. A crystal detonation, an anchor detonation and
  the resulting damage event can all be handled by *different* region threads, so the old
  `HashMap`s were a data race.
- Pending explosions are keyed by an immutable `BlockKey(worldId, x, y, z)` record instead of
  a `Location`, so a lookup never touches another region's `World` object.

Behaviour is identical to 1.3.0 on Paper; only the scheduling and shared state were changed.

## Building

```bash
mvn clean package
```

The shaded jar lands in `target/crystalpvpdisabler-1.4.0.jar`.
