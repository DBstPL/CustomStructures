package com.ryandw11.structure.schematic.worldedit;

import com.ryandw11.structure.CustomStructures;
import com.ryandw11.structure.io.BlockTag;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import me.ryandw11.ods.ObjectDataStructure;
import me.ryandw11.ods.tags.IntTag;
import me.ryandw11.ods.tags.ListTag;
import me.ryandw11.ods.tags.ObjectTag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * WorldEdit-backed schematic creation commands. This class is loaded only when WorldEdit support is enabled.
 */
public final class WorldEditSchematicCreator {
    private WorldEditSchematicCreator() {
    }

    public static boolean createSchematic(String name, Player player, World world, boolean compile) {
        CustomStructures plugin = CustomStructures.getInstance();

        try {
            WorldEditPlugin worldEditPlugin = (WorldEditPlugin) plugin.getServer().getPluginManager().getPlugin("WorldEdit");
            if (worldEditPlugin == null) {
                return false;
            }

            Region selection = worldEditPlugin.getSession(player).getSelection(BukkitAdapter.adapt(world));
            CuboidRegion region = new CuboidRegion(selection.getWorld(), selection.getMinimumPoint(), selection.getMaximumPoint());
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(BlockVector3.at(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()));

            try (var editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(selection.getWorld(), -1)) {
                ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
                Operations.complete(forwardExtentCopy);
            } catch (WorldEditException e) {
                e.printStackTrace();
            }

            File file = new File(plugin.getDataFolder() + File.separator + "schematics" + File.separator + name + ".schem");
            try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(file))) {
                writer.write(clipboard);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (compile) {
                compileSchem(player.getLocation(), selection, name);
            }
            return true;
        } catch (IncompleteRegionException ex) {
            return false;
        }
    }

    public static boolean compileOnly(String name, Player player, World world) {
        try {
            WorldEditPlugin worldEditPlugin = (WorldEditPlugin) CustomStructures.getInstance().getServer().getPluginManager().getPlugin("WorldEdit");
            if (worldEditPlugin == null) {
                return false;
            }
            Region selection = worldEditPlugin.getSession(player).getSelection(BukkitAdapter.adapt(world));
            compileSchem(player.getLocation(), selection, name);
            return true;
        } catch (IncompleteRegionException ex) {
            return false;
        }
    }

    private static void compileSchem(Location loc, Region reg, String name) {
        CustomStructures plugin = CustomStructures.getInstance();

        IntTag intTag = new IntTag("ver", CustomStructures.COMPILED_STRUCT_VER);
        ListTag<BlockTag> containers = new ListTag<>("containers", new ArrayList<>());
        ListTag<BlockTag> signs = new ListTag<>("signs", new ArrayList<>());
        List<Location> locations = new ArrayList<>();
        Location minLoc = new Location(loc.getWorld(), reg.getMinimumPoint().getX(), reg.getMinimumPoint().getY(), reg.getMinimumPoint().getZ());

        for (int x = reg.getMinimumPoint().getX(); x <= reg.getMaximumPoint().getX(); x++) {
            for (int y = reg.getMinimumPoint().getY(); y <= reg.getMaximumPoint().getY(); y++) {
                for (int z = reg.getMinimumPoint().getZ(); z <= reg.getMaximumPoint().getZ(); z++) {
                    Location location = new Location(loc.getWorld(), x, y, z);
                    Block block = location.getBlock();
                    BlockState blockState = location.getBlock().getState();

                    if (blockState instanceof Container) {
                        if (blockState instanceof Chest chestBlockState) {
                            InventoryHolder holder = chestBlockState.getInventory().getHolder();
                            if (holder instanceof DoubleChest doubleChest) {
                                Location leftSideLocation = ((Chest) doubleChest.getLeftSide()).getLocation();
                                Location rightSideLocation = ((Chest) doubleChest.getRightSide()).getLocation();
                                Location roundedLocation = new Location(location.getWorld(), Math.floor(location.getX()), Math.floor(location.getY()), Math.floor(location.getZ()));

                                if (leftSideLocation.distance(roundedLocation) < 1) {
                                    if (isNotAlreadyIn(locations, rightSideLocation)) {
                                        locations.add(roundedLocation);
                                        containers.addTag(new BlockTag(Material.CHEST, location.subtract(minLoc)));
                                    }
                                } else if (rightSideLocation.distance(roundedLocation) < 1) {
                                    if (isNotAlreadyIn(locations, leftSideLocation)) {
                                        locations.add(roundedLocation);
                                        containers.addTag(new BlockTag(Material.CHEST, location.subtract(minLoc)));
                                    }
                                }
                            } else if (holder instanceof Chest) {
                                locations.add(location);
                                containers.addTag(new BlockTag(Material.CHEST, location.subtract(minLoc)));
                            }
                        } else {
                            locations.add(location);
                            containers.addTag(new BlockTag(block.getType(), location.subtract(minLoc)));
                        }
                    } else if (blockState instanceof Sign) {
                        locations.add(location);
                        signs.addTag(new BlockTag(block.getType(), location.subtract(minLoc)));
                    }
                }
            }
        }
        ObjectDataStructure ods = new ObjectDataStructure(new File(plugin.getDataFolder() + File.separator + "schematics" + File.separator + name + ".cschem"));
        ods.save(Arrays.asList(intTag, containers, signs));
        if (plugin.isDebug()) {
            plugin.getLogger().info("Successfully compiled the schematic: " + name);
        }
    }

    private static boolean isNotAlreadyIn(List<Location> locations, Location location) {
        for (Location auxLocation : locations) {
            if (location.distance(auxLocation) < 1) {
                return false;
            }
        }
        return true;
    }
}
