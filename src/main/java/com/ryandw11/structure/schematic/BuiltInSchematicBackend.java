package com.ryandw11.structure.schematic;

import com.ryandw11.structure.CustomStructures;
import com.ryandw11.structure.api.StructureSpawnEvent;
import com.ryandw11.structure.api.holder.StructureSpawnHolder;
import com.ryandw11.structure.loottables.LootTableType;
import com.ryandw11.structure.structure.Structure;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Built-in Sponge schematic paste backend. It places blocks incrementally on Folia region threads.
 */
public final class BuiltInSchematicBackend {
    private BuiltInSchematicBackend() {
    }

    public static void paste(Location pasteLocation, File schematicFile, String filename, boolean useAir,
                             Structure structure, int iteration) {
        CustomStructures plugin = CustomStructures.getInstance();
        World world = pasteLocation.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Cannot paste schematic " + filename + " because the target world is not loaded.");
            return;
        }

        int pasteX = pasteLocation.getBlockX();
        int pasteY = pasteLocation.getBlockY();
        int pasteZ = pasteLocation.getBlockZ();
        String worldName = world.getName();

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                SpongeSchematic schematic = SpongeSchematicReader.read(schematicFile, getMaxSchematicFileSizeBytes(plugin));
                Location baseLocation = new Location(world, pasteX, pasteY, pasteZ);
                double rotation = selectRotation(structure, iteration);
                List<PendingBlock> pendingBlocks = buildPendingBlocks(schematic, baseLocation, rotation, useAir, structure);
                List<PendingFillColumn> fillColumns = buildFillColumns(schematic, baseLocation, rotation, structure);

                if (plugin.isDebug()) {
                    logInvalidPaletteFallbacks(plugin, filename, schematic);
                    plugin.getLogger().info(String.format("(%s) Queued %s with %s blocks using built-in schematic backend (rotation %s).",
                            worldName, filename, pendingBlocks.size(), rotation));
                }

                Bukkit.getRegionScheduler().run(plugin, baseLocation, ignored -> startPaste(
                        schematic, baseLocation, filename, pendingBlocks, fillColumns, structure, iteration, rotation
                ));
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Unable to read schematic " + filename + " with the built-in backend.", ex);
            }
        });
    }

    private static double selectRotation(Structure structure, int iteration) {
        if (structure.getStructureProperties().isRandomRotation() && iteration == 0) {
            return Math.floor(Math.random() * 4) * 90.0;
        }
        if (iteration != 0) {
            return Math.toDegrees(structure.getSubSchemRotation());
        }
        return Math.toDegrees(structure.getBaseRotation());
    }

    private static void logInvalidPaletteFallbacks(CustomStructures plugin, String filename, SpongeSchematic schematic) {
        Set<String> logged = new HashSet<>();
        for (int y = 0; y < schematic.getHeight(); y++) {
            for (int z = 0; z < schematic.getLength(); z++) {
                for (int x = 0; x < schematic.getWidth(); x++) {
                    SchematicBlockState blockState = schematic.getBlockState(x, y, z);
                    if (blockState.isInvalid() && logged.add(blockState.getStateString())) {
                        plugin.getLogger().warning("Schematic " + filename + " contains an unsupported block state, using sanitized fallback: " + blockState.getStateString());
                    }
                }
            }
        }
    }

    private static List<PendingBlock> buildPendingBlocks(SpongeSchematic schematic, Location pasteLocation,
                                                         double rotation, boolean useAir, Structure structure) {
        List<PendingBlock> pendingBlocks = new ArrayList<>(Math.min(schematic.getBlockCount(), 4096));
        for (int y = 0; y < schematic.getHeight(); y++) {
            for (int z = 0; z < schematic.getLength(); z++) {
                for (int x = 0; x < schematic.getWidth(); x++) {
                    SchematicBlockState blockState = schematic.getBlockState(x, y, z);
                    if (!useAir && blockState.isAir()) {
                        continue;
                    }
                    if (!structure.getSourceMaskProperties().matchesSource(blockState.getStateString(), blockState.getMaterial())) {
                        continue;
                    }

                    Location worldLocation = schematic.toWorldLocation(x, y, z, pasteLocation, rotation);
                    pendingBlocks.add(new PendingBlock(
                            worldLocation.getBlockX(),
                            worldLocation.getBlockY(),
                            worldLocation.getBlockZ(),
                            blockState,
                            schematic.getBlockEntity(x, y, z)
                    ));
                }
            }
        }
        return pendingBlocks;
    }

    private static List<PendingFillColumn> buildFillColumns(SpongeSchematic schematic, Location pasteLocation,
                                                            double rotation, Structure structure) {
        if (!structure.getBottomSpaceFill().isEnabled()) {
            return Collections.emptyList();
        }

        List<PendingFillColumn> columns = new ArrayList<>();
        int bottomY = 0;
        Location minLocation = SchematicLocationUtils.getMinimumLocation(schematic, pasteLocation, rotation);
        int fillStartY = minLocation.getBlockY() - 1;

        for (int x = 0; x < schematic.getWidth(); x++) {
            for (int z = 0; z < schematic.getLength(); z++) {
                SchematicBlockState blockState = schematic.getBlockState(x, bottomY, z);
                if (!blockState.getMaterial().isSolid()) {
                    continue;
                }

                Location worldLocation = schematic.toWorldLocation(x, bottomY, z, pasteLocation, rotation);
                columns.add(new PendingFillColumn(worldLocation.getBlockX(), fillStartY, worldLocation.getBlockZ()));
            }
        }
        return columns;
    }

    private static void startPaste(SpongeSchematic schematic, Location pasteLocation, String filename,
                                   List<PendingBlock> pendingBlocks, List<PendingFillColumn> fillColumns,
                                   Structure structure, int iteration, double rotation) {
        CustomStructures plugin = CustomStructures.getInstance();
        World world = pasteLocation.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Cannot paste schematic " + filename + " because the target world is not loaded.");
            return;
        }

        List<Location> postProcessLocations = Collections.synchronizedList(new ArrayList<>());
        Set<String> postProcessKeys = Collections.synchronizedSet(new HashSet<>());
        int maxBlocks = getMaxBlocksPerTick(plugin);
        double maxMillis = getMaxMillisPerTick(plugin);

        new FoliaRegionWorkRunner<>(
                plugin,
                world,
                pendingBlocks,
                maxBlocks,
                maxMillis,
                pendingBlock -> placeBlock(world, pendingBlock, structure, rotation, postProcessLocations, postProcessKeys),
                () -> startBottomFill(schematic, pasteLocation, fillColumns, structure, iteration, rotation, postProcessLocations)
        ).start();
    }

    private static void startBottomFill(SpongeSchematic schematic, Location pasteLocation, List<PendingFillColumn> fillColumns,
                                        Structure structure, int iteration, double rotation, List<Location> postProcessLocations) {
        CustomStructures plugin = CustomStructures.getInstance();
        World world = pasteLocation.getWorld();
        if (world == null || fillColumns.isEmpty()) {
            schedulePostProcess(schematic, pasteLocation, structure, iteration, rotation, postProcessLocations);
            return;
        }

        Bukkit.getRegionScheduler().run(plugin, pasteLocation, task -> {
            Optional<Material> fillMaterial = structure.getBottomSpaceFill().getFillMaterial(pasteLocation.getBlock().getBiome());
            if (fillMaterial.isEmpty()) {
                schedulePostProcess(schematic, pasteLocation, structure, iteration, rotation, postProcessLocations);
                return;
            }

            new FoliaRegionWorkRunner<>(
                    plugin,
                    world,
                    fillColumns,
                    Math.max(1, getMaxBlocksPerTick(plugin) / 8),
                    getMaxMillisPerTick(plugin),
                    column -> fillColumn(world, column, fillMaterial.get(), structure),
                    () -> schedulePostProcess(schematic, pasteLocation, structure, iteration, rotation, postProcessLocations)
            ).start();
        });
    }

    private static void schedulePostProcess(SpongeSchematic schematic, Location pasteLocation, Structure structure,
                                            int iteration, double rotation, List<Location> postProcessLocations) {
        CustomStructures plugin = CustomStructures.getInstance();
        long delay = Math.round(structure.getStructureLimitations().getReplacementBlocksDelay() * 20L);
        if (delay <= 0) {
            delay = 1L;
        }

        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            World world = pasteLocation.getWorld();
            if (world == null) {
                return;
            }
            List<PendingPostProcessLocation> pending = new ArrayList<>();
            for (Location location : postProcessLocations) {
                pending.add(new PendingPostProcessLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            }

            new FoliaRegionWorkRunner<>(
                    plugin,
                    world,
                    pending,
                    getMaxBlocksPerTick(plugin),
                    getMaxMillisPerTick(plugin),
                    item -> processLocation(world, item, schematic, pasteLocation, structure, iteration, rotation),
                    () -> callSpawnEvent(schematic, pasteLocation, structure, iteration, rotation, postProcessLocations)
            ).start();
        }, delay * 50L, TimeUnit.MILLISECONDS);
    }

    private static void placeBlock(World world, PendingBlock pendingBlock, Structure structure, double rotation,
                                   List<Location> postProcessLocations, Set<String> postProcessKeys) {
        if (pendingBlock.y < world.getMinHeight() || pendingBlock.y >= world.getMaxHeight()) {
            return;
        }

        Block block = world.getBlockAt(pendingBlock.x, pendingBlock.y, pendingBlock.z);
        if (!structure.getTargetMaskProperties().matchesTarget(block.getType())) {
            return;
        }

        Material replacement = structure.getStructureLimitations().getBlockReplacement().get(pendingBlock.state.getMaterial());
        if (replacement != null) {
            block.setType(replacement, false);
        } else {
            block.setBlockData(pendingBlock.state.getBlockData(rotation), false);
        }

        if (pendingBlock.blockEntity != null && pendingBlock.blockEntity.isSign() && block.getState() instanceof Sign sign) {
            String[] lines = pendingBlock.blockEntity.readSignLines();
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                sign.setLine(i, lines[i]);
            }
            sign.update(true, false);
        }

        if (isPostProcessCandidate(block.getType())) {
            String key = pendingBlock.x + ";" + pendingBlock.y + ";" + pendingBlock.z;
            if (postProcessKeys.add(key)) {
                postProcessLocations.add(new Location(world, pendingBlock.x, pendingBlock.y, pendingBlock.z));
            }
        }
    }

    private static void fillColumn(World world, PendingFillColumn column, Material fillMaterial, Structure structure) {
        int y = column.y;
        for (int i = 0; i < 64 && y >= world.getMinHeight(); i++) {
            Block block = world.getBlockAt(column.x, y, column.z);
            boolean shouldFill = block.isEmpty()
                    || CustomStructures.getInstance().getBlockIgnoreManager().getBlocks().contains(block.getType())
                    || (structure.getStructureProperties().shouldIgnoreWater() && block.getType() == Material.WATER);
            if (!shouldFill) {
                break;
            }
            block.setType(fillMaterial, false);
            y--;
        }
    }

    private static void processLocation(World world, PendingPostProcessLocation item, SpongeSchematic schematic,
                                        Location pasteLocation, Structure structure, int iteration, double rotation) {
        Location location = new Location(world, item.x, item.y, item.z);
        if (location.getBlock().getState() instanceof Container) {
            LootTableReplacer.replaceContainerContent(structure, location);
        }
        if (location.getBlock().getState() instanceof Sign) {
            Location minLoc = SchematicLocationUtils.getMinimumLocation(schematic, pasteLocation, rotation);
            Location maxLoc = SchematicLocationUtils.getMaximumLocation(schematic, pasteLocation, rotation);
            SchematicSignReplacer.processAndReplaceSign(location, minLoc, maxLoc, structure, rotation);
        }
        if (location.getBlock().getState() instanceof Sign) {
            SchematicSignReplacer.replaceSignWithSchematic(location, structure, iteration);
        }
    }

    private static void callSpawnEvent(SpongeSchematic schematic, Location pasteLocation, Structure structure,
                                       int iteration, double rotation, List<Location> postProcessLocations) {
        if (iteration >= 1) {
            return;
        }

        Bukkit.getRegionScheduler().run(CustomStructures.getInstance(), pasteLocation, task -> {
            StructureSpawnHolder structureSpawnHolder = new StructureSpawnHolder(
                    SchematicLocationUtils.getMinimumLocation(schematic, pasteLocation, rotation),
                    SchematicLocationUtils.getMaximumLocation(schematic, pasteLocation, rotation),
                    postProcessLocations
            );
            StructureSpawnEvent structureSpawnEvent = new StructureSpawnEvent(structure, pasteLocation, rotation, structureSpawnHolder);
            Bukkit.getServer().getPluginManager().callEvent(structureSpawnEvent);
        });
    }

    private static boolean isPostProcessCandidate(Material material) {
        return isSign(material) || LootTableType.valueOf(material) != null;
    }

    private static boolean isSign(Material material) {
        String name = material.name();
        return name.endsWith("_SIGN") || name.endsWith("_WALL_SIGN") || name.endsWith("_HANGING_SIGN") || name.endsWith("_WALL_HANGING_SIGN");
    }

    private static int getMaxBlocksPerTick(CustomStructures plugin) {
        return Math.max(1, plugin.getConfig().getInt("SchematicPasting.MaxBlocksPerTick", 3000));
    }

    private static double getMaxMillisPerTick(CustomStructures plugin) {
        return Math.max(0.25D, plugin.getConfig().getDouble("SchematicPasting.MaxMillisPerTick", 4.0D));
    }

    private static long getMaxSchematicFileSizeBytes(CustomStructures plugin) {
        long megabytes = plugin.getConfig().getLong("SchematicPasting.MaxSchematicFileSizeMB", 128L);
        if (megabytes <= 0) {
            return 0L;
        }
        return megabytes * 1024L * 1024L;
    }

    private static final class PendingBlock implements FoliaRegionWorkRunner.RegionWorkItem {
        private final int x;
        private final int y;
        private final int z;
        private final SchematicBlockState state;
        private final SchematicBlockEntity blockEntity;

        private PendingBlock(int x, int y, int z, SchematicBlockState state, SchematicBlockEntity blockEntity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
            this.blockEntity = blockEntity;
        }

        @Override
        public int getChunkX() {
            return Math.floorDiv(x, 16);
        }

        @Override
        public int getChunkZ() {
            return Math.floorDiv(z, 16);
        }
    }

    private static final class PendingFillColumn implements FoliaRegionWorkRunner.RegionWorkItem {
        private final int x;
        private final int y;
        private final int z;

        private PendingFillColumn(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int getChunkX() {
            return Math.floorDiv(x, 16);
        }

        @Override
        public int getChunkZ() {
            return Math.floorDiv(z, 16);
        }
    }

    private static final class PendingPostProcessLocation implements FoliaRegionWorkRunner.RegionWorkItem {
        private final int x;
        private final int y;
        private final int z;

        private PendingPostProcessLocation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int getChunkX() {
            return Math.floorDiv(x, 16);
        }

        @Override
        public int getChunkZ() {
            return Math.floorDiv(z, 16);
        }
    }
}
