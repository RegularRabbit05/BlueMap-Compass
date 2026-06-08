package fun.mntale.blueMapCompass;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.BlueMapWorld;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import com.flowpowered.math.vector.Vector3d;
import java.util.Optional;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

public class BannerMarkerListener implements Listener {

    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof Banner banner)) return;
        ItemStack item = event.getItemInHand();
        if (!(item.hasItemMeta() && item.getItemMeta().hasDisplayName())) {
            if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                org.bukkit.Bukkit.getLogger().info("[BlueMapCompass][DEBUG] Banner placed with NO custom name at " + block.getLocation());
            }
            return; // Ignore banners with no custom name
        }
        if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
            org.bukkit.Bukkit.getLogger().info("[BlueMapCompass][DEBUG] Banner placed with custom name at " + block.getLocation());
        }
        String name = org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        DyeColor color = banner.getBaseColor();
        Location loc = block.getLocation();
        String worldName = loc.getWorld().getName();
        Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        String markerId = "banner-" + worldName + "-" + loc.getBlockX() + "-" + loc.getBlockY() + "-" + loc.getBlockZ() + "-" + color;
        String placerUuid = event.getPlayer().getUniqueId().toString();
        String placerName = event.getPlayer().getName();
        // Save to storage
        BlueMapCompass.instance.bannerMarkerStorage.addMarker(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), name, color.name(), markerId, placerUuid, placerName);
        // Create marker in BlueMap
        BlueMapAPI.getInstance().ifPresent(api -> {
            Optional<BlueMapWorld> blueMapWorld = api.getWorld(loc.getWorld().getUID());
            blueMapWorld.ifPresent(world -> {
                world.getMaps().forEach(map -> {
                    MarkerSet markerSet = map.getMarkerSets().computeIfAbsent("banner-markers", k -> MarkerSet.builder().label("Banner Markers").build());
                    String description = "<b>" + name + "</b><br>" +
                                       "<b>Position:</b> " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                                       "<br><b>Owner:</b> " + placerName;
                    String iconUrl = "assets/bluemap/web/images/banners/" + color.name().toLowerCase() + ".png";
                    POIMarker marker = POIMarker.builder()
                            .label(name)
                            .position(pos)
                            .detail(description)
                            .icon(iconUrl, 0, 0)
                            .maxDistance(100000)
                            .build();
                    markerSet.getMarkers().put(markerId, marker);
                    if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                        org.bukkit.Bukkit.getLogger().info("[BlueMapCompass][DEBUG] Banner marker added: " + markerId);
                    }
                });
            });
        });
    }

    // Utility to handle banner removal logic
    private void handleBannerRemove(Block block, String eventType) {
        if (!(block.getState() instanceof Banner banner)) return;
        Location loc = block.getLocation();
        String worldName = loc.getWorld().getName();
        DyeColor color = banner.getBaseColor();
        String markerId = "banner-" + worldName + "-" + loc.getBlockX() + "-" + loc.getBlockY() + "-" + loc.getBlockZ() + "-" + color;
        if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
            org.bukkit.Bukkit.getLogger().info("[BlueMapCompass][DEBUG] Banner marker removed by " + eventType + " at " + loc + " markerId=" + markerId);
        }
        // Remove from storage
        BlueMapCompass.instance.bannerMarkerStorage.removeMarkerByLocation(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        // Remove marker from BlueMap by markerId (for live update)
        BlueMapCompass.instance.bannerMarkerStorage.removeMarker(markerId);
        // Remove marker from BlueMap
        BlueMapAPI.getInstance().ifPresent(api -> {
            Optional<BlueMapWorld> blueMapWorld = api.getWorld(loc.getWorld().getUID());
            blueMapWorld.ifPresent(world -> {
                world.getMaps().forEach(map -> {
                    MarkerSet markerSet = map.getMarkerSets().get("banner-markers");
                    if (markerSet != null) {
                        markerSet.getMarkers().remove(markerId);
                    }
                });
            });
        });
        // Untrack for all online players and remove displays
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            java.util.Set<String> tracked = fun.mntale.blueMapCompass.WaypointManager.getTrackedMarkers(player);
            if (tracked.contains(markerId)) {
                tracked.remove(markerId);
                fun.mntale.blueMapCompass.WaypointManager.setTrackedMarkers(player, tracked);
                fun.mntale.blueMapCompass.WaypointManager.removeWaypointDisplayOnly(player, markerId);
            }
        }
    }

    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        handleBannerRemove(event.getBlock(), "BlockBreakEvent");
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleBannerRemove(block, "BlockExplodeEvent");
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleBannerRemove(block, "EntityExplodeEvent");
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        handleBannerRemove(event.getBlock(), "BlockBurnEvent");
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        handleBannerRemove(event.getToBlock(), "BlockFromToEvent");
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        handleBannerRemove(event.getBlock(), "BlockFadeEvent");
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            handleBannerRemove(block, "BlockPistonExtendEvent");
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            handleBannerRemove(block, "BlockPistonRetractEvent");
        }
    }

    // Call this on plugin enable to restore all markers
    public void restoreAllMarkers() {
        BlueMapAPI.onEnable(api -> {
            for (BannerMarkerStorage.BannerMarkerData data : BlueMapCompass.instance.bannerMarkerStorage.getAllMarkers()) {
                Location loc = data.getLocation();
                if (loc == null) continue;
                Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
                Optional<BlueMapWorld> blueMapWorld = api.getWorld(loc.getWorld().getUID());
                blueMapWorld.ifPresent(world -> {
                    world.getMaps().forEach(map -> {
                        MarkerSet markerSet = map.getMarkerSets().computeIfAbsent("banner-markers", k -> MarkerSet.builder().label("Banner Markers").build());
                        String description = "<b>" + data.name + "</b><br>" +
                                           "<b>Position:</b> " + data.x + ", " + data.y + ", " + data.z +
                                           "<br><b>Owner:</b> " + (data.placerName != null && !data.placerName.isEmpty() ? data.placerName : data.placerUuid);
                        String iconUrl = "assets/bluemap/web/images/banners/" + data.color.toLowerCase() + ".png";
                        POIMarker marker = POIMarker.builder()
                                .label(data.name)
                                .position(pos)
                                .detail(description)
                                .icon(iconUrl, 32, 48)
                                .maxDistance(100000)
                                .build();
                        markerSet.getMarkers().put(data.markerId, marker);
                    });
                });
            }
        });
    }
} 