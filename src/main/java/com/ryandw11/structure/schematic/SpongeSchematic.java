package com.ryandw11.structure.schematic;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory representation of the Sponge schematic data needed for block placement.
 */
public final class SpongeSchematic {
    private final int version;
    private final int width;
    private final int height;
    private final int length;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final SchematicBlockState[] palette;
    private final int[] blocks;
    private final Map<Integer, SchematicBlockEntity> blockEntities;

    public SpongeSchematic(int version, int width, int height, int length, int offsetX, int offsetY, int offsetZ,
                           SchematicBlockState[] palette, int[] blocks, Collection<SchematicBlockEntity> blockEntities) {
        this.version = version;
        this.width = width;
        this.height = height;
        this.length = length;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.palette = palette;
        this.blocks = blocks;
        this.blockEntities = new HashMap<>();
        for (SchematicBlockEntity blockEntity : blockEntities) {
            this.blockEntities.put(index(blockEntity.getX(), blockEntity.getY(), blockEntity.getZ()), blockEntity);
        }
    }

    public int getVersion() {
        return version;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public int getBlockCount() {
        return width * height * length;
    }

    public SchematicBlockState getBlockState(int x, int y, int z) {
        int paletteIndex = blocks[index(x, y, z)];
        if (paletteIndex < 0 || paletteIndex >= palette.length || palette[paletteIndex] == null) {
            return palette[0];
        }
        return palette[paletteIndex];
    }

    public SchematicBlockEntity getBlockEntity(int x, int y, int z) {
        return blockEntities.get(index(x, y, z));
    }

    public Collection<SchematicBlockEntity> getBlockEntities() {
        return Collections.unmodifiableCollection(blockEntities.values());
    }

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    public Location toWorldLocation(int x, int y, int z, Location pasteLocation, double rotationDegrees) {
        int[] rotated = rotateRelative(x + offsetX, z + offsetZ, rotationDegrees);
        return new Location(
                pasteLocation.getWorld(),
                pasteLocation.getBlockX() + rotated[0],
                pasteLocation.getBlockY() + y + offsetY,
                pasteLocation.getBlockZ() + rotated[1]
        );
    }

    public static int[] rotateRelative(int x, int z, double rotationDegrees) {
        int normalized = (int) Math.round(rotationDegrees / 90.0) % 4;
        if (normalized < 0) {
            normalized += 4;
        }

        return switch (normalized) {
            case 1 -> new int[]{z, -x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }
}
