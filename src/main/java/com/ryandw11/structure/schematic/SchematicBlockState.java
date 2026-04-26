package com.ryandw11.structure.schematic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * A Bukkit-ready block state parsed from a Sponge schematic palette entry.
 */
public final class SchematicBlockState {
    private final String stateString;
    private final BlockData blockData;
    private final Material material;
    private final boolean invalid;

    private SchematicBlockState(String stateString, BlockData blockData, Material material, boolean invalid) {
        this.stateString = stateString;
        this.blockData = blockData;
        this.material = material;
        this.invalid = invalid;
    }

    public static SchematicBlockState parse(String stateString) {
        try {
            BlockData blockData = Bukkit.createBlockData(stateString);
            return new SchematicBlockState(stateString, blockData, blockData.getMaterial(), false);
        } catch (IllegalArgumentException ex) {
            BlockData air = Bukkit.createBlockData(Material.AIR);
            return new SchematicBlockState(stateString, air, Material.AIR, true);
        }
    }

    public String getStateString() {
        return stateString;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public BlockData getBlockData(double rotationDegrees) {
        int normalized = SpongeSchematic.normalizeRotation(rotationDegrees);
        if (normalized == 0) {
            return blockData;
        }

        BlockData rotated = blockData.clone();
        switch (normalized) {
            case 1 -> rotated.rotate(StructureRotation.COUNTERCLOCKWISE_90);
            case 2 -> rotated.rotate(StructureRotation.CLOCKWISE_180);
            case 3 -> rotated.rotate(StructureRotation.CLOCKWISE_90);
            default -> {
            }
        }
        return rotated;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isAir() {
        return material.isAir();
    }

    public boolean isInvalid() {
        return invalid;
    }
}
