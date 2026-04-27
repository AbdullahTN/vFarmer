package com.asmus.vfarmer.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Abstraction layer for island/claim plugins.
 * Supports: SuperiorSkyblock2, BentoBox, IridiumSkyblock, FabledSkyBlock,
 *           Lands, GriefPrevention, GriefDefender, Towny, UXClaim
 */
public class IslandHook {

    public enum HookType {
        SUPERIOR_SKYBLOCK, BENTOBOX, IRIDIUM_SKYBLOCK, FABLED_SKYBLOCK,
        LANDS, GRIEF_PREVENTION, GRIEF_DEFENDER, TOWNY, UXCLAIM, NONE
    }

    private final HookType activeHook;
    private final Logger logger;

    public IslandHook(org.bukkit.plugin.Plugin plugin) {
        this.logger = plugin.getLogger();
        this.activeHook = detectHook();
        if (activeHook != HookType.NONE) {
            logger.info("Hooked into: " + activeHook.name());
        } else {
            logger.warning("No supported island/claim plugin found! Farmer will use world-based mode.");
        }
    }

    private HookType detectHook() {
        if (isPluginEnabled("SuperiorSkyblock2")) return HookType.SUPERIOR_SKYBLOCK;
        if (isPluginEnabled("BentoBox")) return HookType.BENTOBOX;
        if (isPluginEnabled("IridiumSkyblock")) return HookType.IRIDIUM_SKYBLOCK;
        if (isPluginEnabled("FabledSkyBlock")) return HookType.FABLED_SKYBLOCK;
        if (isPluginEnabled("Lands")) return HookType.LANDS;
        if (isPluginEnabled("GriefPrevention")) return HookType.GRIEF_PREVENTION;
        if (isPluginEnabled("GriefDefender")) return HookType.GRIEF_DEFENDER;
        if (isPluginEnabled("Towny")) return HookType.TOWNY;
        if (isPluginEnabled("UXClaim")) return HookType.UXCLAIM;
        return HookType.NONE;
    }

    private boolean isPluginEnabled(String name) {
        return Bukkit.getPluginManager().getPlugin(name) != null
                && Bukkit.getPluginManager().isPluginEnabled(name);
    }

    public HookType getActiveHook() {
        return activeHook;
    }

    /**
     * Gets the island/claim UUID at a given location.
     * Returns null if no island/claim exists.
     */
    public UUID getIslandId(Location location) {
        try {
            switch (activeHook) {
                case SUPERIOR_SKYBLOCK:
                    return getSuperiorIslandId(location);
                case BENTOBOX:
                    return getBentoBoxIslandId(location);
                case IRIDIUM_SKYBLOCK:
                    return getIridiumIslandId(location);
                case FABLED_SKYBLOCK:
                    return getFabledIslandId(location);
                case LANDS:
                    return getLandsClaimId(location);
                case GRIEF_PREVENTION:
                    return getGPClaimId(location);
                case GRIEF_DEFENDER:
                    return getGDClaimId(location);
                case TOWNY:
                    return getTownyId(location);
                case UXCLAIM:
                    return getUXClaimId(location);
                default:
                    // World-based: use world UUID
                    return location.getWorld() != null ? location.getWorld().getUID() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the player is a member of the island/claim at the location.
     */
    public boolean isPlayerOnOwnIsland(Player player, Location location) {
        try {
            switch (activeHook) {
                case SUPERIOR_SKYBLOCK:
                    return isSuperiorMember(player, location);
                case BENTOBOX:
                    return isBentoBoxMember(player, location);
                case IRIDIUM_SKYBLOCK:
                    return isIridiumMember(player, location);
                case FABLED_SKYBLOCK:
                    return isFabledMember(player, location);
                case LANDS:
                    return isLandsMember(player, location);
                case GRIEF_PREVENTION:
                    return isGPMember(player, location);
                case GRIEF_DEFENDER:
                    return isGDMember(player, location);
                case TOWNY:
                    return isTownyMember(player, location);
                case UXCLAIM:
                    return isUXClaimMember(player, location);
                default:
                    return true; // No hook = always allowed
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the island/claim UUID for a player (their own island).
     */
    public UUID getPlayerIslandId(Player player) {
        try {
            switch (activeHook) {
                case SUPERIOR_SKYBLOCK:
                    return getSuperiorPlayerIslandId(player);
                case BENTOBOX:
                    return getBentoBoxPlayerIslandId(player);
                case IRIDIUM_SKYBLOCK:
                    return getIridiumPlayerIslandId(player);
                case FABLED_SKYBLOCK:
                    return getFabledPlayerIslandId(player);
                case LANDS:
                    return getLandsPlayerClaimId(player);
                default:
                    return getIslandId(player.getLocation());
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if a location is within the bounds of the given island/claim.
     */
    public boolean isLocationInIsland(Location location, UUID islandId) {
        UUID locIslandId = getIslandId(location);
        return locIslandId != null && locIslandId.equals(islandId);
    }

    // ==================== SuperiorSkyblock2 ====================
    private UUID getSuperiorIslandId(Location location) {
        var island = com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getIslandAt(location);
        return island != null ? island.getUniqueId() : null;
    }

    private boolean isSuperiorMember(Player player, Location location) {
        var island = com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getIslandAt(location);
        if (island == null) return false;
        var sp = com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getPlayer(player);
        return sp != null && island.isMember(sp);
    }

    private UUID getSuperiorPlayerIslandId(Player player) {
        var sp = com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getPlayer(player);
        if (sp == null) return null;
        var island = sp.getIsland();
        return island != null ? island.getUniqueId() : null;
    }

    // ==================== BentoBox ====================
    private UUID getBentoBoxIslandId(Location location) {
        // BentoBox reflection-based hook
        try {
            var bentoBox = Bukkit.getPluginManager().getPlugin("BentoBox");
            var method = bentoBox.getClass().getMethod("getIslands");
            var islandsManager = method.invoke(bentoBox);
            var getIslandAt = islandsManager.getClass().getMethod("getIslandAt", Location.class);
            var optional = getIslandAt.invoke(islandsManager, location);
            var isPresentMethod = optional.getClass().getMethod("isPresent");
            if ((boolean) isPresentMethod.invoke(optional)) {
                var getMethod = optional.getClass().getMethod("get");
                var island = getMethod.invoke(optional);
                var getUniqueId = island.getClass().getMethod("getUniqueId");
                return UUID.fromString(getUniqueId.invoke(island).toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isBentoBoxMember(Player player, Location location) {
        UUID islandId = getBentoBoxIslandId(location);
        UUID playerIslandId = getBentoBoxPlayerIslandId(player);
        return islandId != null && islandId.equals(playerIslandId);
    }

    private UUID getBentoBoxPlayerIslandId(Player player) {
        return getBentoBoxIslandId(player.getLocation());
    }

    // ==================== IridiumSkyblock ====================
    private UUID getIridiumIslandId(Location location) {
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("IridiumSkyblock");
            var method = plugin.getClass().getMethod("getIslandManager");
            var manager = method.invoke(plugin);
            var getIslandViaLocation = manager.getClass().getMethod("getIslandViaLocation", Location.class);
            var optional = getIslandViaLocation.invoke(manager, location);
            var isPresentMethod = optional.getClass().getMethod("isPresent");
            if ((boolean) isPresentMethod.invoke(optional)) {
                var getMethod = optional.getClass().getMethod("get");
                var island = getMethod.invoke(optional);
                var getId = island.getClass().getMethod("getId");
                int id = (int) getId.invoke(island);
                return new UUID(0, id);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isIridiumMember(Player player, Location location) {
        UUID islandId = getIridiumIslandId(location);
        UUID playerIslandId = getIridiumIslandId(player.getLocation());
        return islandId != null && islandId.equals(playerIslandId);
    }

    // ==================== FabledSkyBlock ====================
    private UUID getFabledIslandId(Location location) {
        try {
            var apiClass = Class.forName("com.songoda.skyblock.api.SkyBlockAPI");
            var getIslandManager = apiClass.getMethod("getIslandManager");
            var manager = getIslandManager.invoke(null);
            var getIslandAtLocation = manager.getClass().getMethod("getIslandAtLocation", Location.class);
            var island = getIslandAtLocation.invoke(manager, location);
            if (island != null) {
                var getIslandUUID = island.getClass().getMethod("getIslandUUID");
                return (UUID) getIslandUUID.invoke(island);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isFabledMember(Player player, Location location) {
        UUID islandId = getFabledIslandId(location);
        UUID playerIslandId = getFabledIslandId(player.getLocation());
        return islandId != null && islandId.equals(playerIslandId);
    }

    // ==================== Lands ====================
    private UUID getLandsClaimId(Location location) {
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("Lands");
            var method = plugin.getClass().getMethod("getLandWorld", org.bukkit.World.class);
            var landWorld = method.invoke(plugin, location.getWorld());
            if (landWorld != null) {
                var getLandAt = landWorld.getClass().getMethod("getLandAt", int.class, int.class);
                var area = getLandAt.invoke(landWorld, location.getBlockX() >> 4, location.getBlockZ() >> 4);
                if (area != null) {
                    var getLand = area.getClass().getMethod("getLand");
                    var land = getLand.invoke(area);
                    if (land != null) {
                        var getUid = land.getClass().getMethod("getUid");
                        return (UUID) getUid.invoke(land);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isLandsMember(Player player, Location location) {
        UUID claimId = getLandsClaimId(location);
        UUID playerClaimId = getLandsPlayerClaimId(player);
        return claimId != null && claimId.equals(playerClaimId);
    }

    private UUID getLandsPlayerClaimId(Player player) {
        return getLandsClaimId(player.getLocation());
    }

    // ==================== GriefPrevention ====================
    private UUID getGPClaimId(Location location) {
        try {
            var gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            var instanceField = gpClass.getField("instance");
            var gp = instanceField.get(null);
            var dataStore = gpClass.getMethod("getDataStore").invoke(gp);
            var getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Object.class);
            var claim = getClaimAt.invoke(dataStore, location, false, null);
            if (claim != null) {
                var getID = claim.getClass().getMethod("getID");
                Long id = (Long) getID.invoke(claim);
                return new UUID(0, id);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isGPMember(Player player, Location location) {
        try {
            var gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            var instanceField = gpClass.getField("instance");
            var gp = instanceField.get(null);
            var dataStore = gpClass.getMethod("getDataStore").invoke(gp);
            var getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Object.class);
            var claim = getClaimAt.invoke(dataStore, location, false, null);
            if (claim != null) {
                var allowAccess = claim.getClass().getMethod("allowAccess", Player.class);
                return allowAccess.invoke(claim, player) == null; // null = no error = allowed
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ==================== GriefDefender ====================
    private UUID getGDClaimId(Location location) {
        try {
            var coreClass = Class.forName("com.griefdefender.api.GriefDefender");
            var getCore = coreClass.getMethod("getCore");
            var core = getCore.invoke(null);
            var getClaimAt = core.getClass().getMethod("getClaimAt", Location.class);
            var claim = getClaimAt.invoke(core, location);
            if (claim != null) {
                var getUniqueId = claim.getClass().getMethod("getUniqueId");
                return (UUID) getUniqueId.invoke(claim);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isGDMember(Player player, Location location) {
        UUID claimId = getGDClaimId(location);
        UUID playerClaimId = getGDClaimId(player.getLocation());
        return claimId != null && claimId.equals(playerClaimId);
    }

    // ==================== Towny ====================
    private UUID getTownyId(Location location) {
        try {
            var townyAPIClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            var getInstance = townyAPIClass.getMethod("getInstance");
            var api = getInstance.invoke(null);
            var getTownBlock = api.getClass().getMethod("getTownBlock", Location.class);
            var townBlock = getTownBlock.invoke(api, location);
            if (townBlock != null) {
                var getTown = townBlock.getClass().getMethod("getTown");
                var town = getTown.invoke(townBlock);
                if (town != null) {
                    var getUUID = town.getClass().getMethod("getUUID");
                    return (UUID) getUUID.invoke(town);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isTownyMember(Player player, Location location) {
        try {
            var townyAPIClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            var getInstance = townyAPIClass.getMethod("getInstance");
            var api = getInstance.invoke(null);
            var getTownBlock = api.getClass().getMethod("getTownBlock", Location.class);
            var townBlock = getTownBlock.invoke(api, location);
            if (townBlock != null) {
                var getTown = townBlock.getClass().getMethod("getTown");
                var town = getTown.invoke(townBlock);
                if (town != null) {
                    var hasResident = town.getClass().getMethod("hasResident", String.class);
                    return (boolean) hasResident.invoke(town, player.getName());
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ==================== UXClaim ====================
    private UUID getUXClaimId(Location location) {
        // UXClaim uses chunk-based claims
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("UXClaim");
            if (plugin != null) {
                var method = plugin.getClass().getMethod("getChunkOwner", org.bukkit.Chunk.class);
                var owner = method.invoke(plugin, location.getChunk());
                if (owner != null) {
                    return UUID.nameUUIDFromBytes(owner.toString().getBytes());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isUXClaimMember(Player player, Location location) {
        UUID claimId = getUXClaimId(location);
        UUID playerClaimId = getUXClaimId(player.getLocation());
        return claimId != null && claimId.equals(playerClaimId);
    }

    private UUID getIridiumPlayerIslandId(Player player) {
        return getIridiumIslandId(player.getLocation());
    }

    private UUID getFabledPlayerIslandId(Player player) {
        return getFabledIslandId(player.getLocation());
    }
}
