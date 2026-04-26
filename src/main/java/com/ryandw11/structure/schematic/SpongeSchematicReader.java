package com.ryandw11.structure.schematic;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads Sponge schematic v3 files, with v2 support for existing WorldEdit exports.
 */
public final class SpongeSchematicReader {
    private SpongeSchematicReader() {
    }

    public static SpongeSchematic read(File file) throws IOException {
        CompoundBinaryTag root = BinaryTagIO.readCompressedPath(file.toPath());
        CompoundBinaryTag schematic = root.get("Schematic") instanceof CompoundBinaryTag
                ? root.getCompound("Schematic")
                : root;

        int version = schematic.getInt("Version", -1);
        if (version == 3) {
            return readVersion3(schematic);
        }
        if (version == 2) {
            return readVersion2(schematic);
        }
        throw new IOException("Unsupported Sponge schematic version: " + version);
    }

    private static SpongeSchematic readVersion3(CompoundBinaryTag schematic) throws IOException {
        int width = readUnsignedShort(schematic, "Width");
        int height = readUnsignedShort(schematic, "Height");
        int length = readUnsignedShort(schematic, "Length");
        int[] offset = readOffset(schematic);

        CompoundBinaryTag blocks = schematic.getCompound("Blocks", CompoundBinaryTag.empty());
        if (blocks.size() == 0) {
            throw new IOException("Sponge schematic v3 does not contain a Blocks compound.");
        }

        SchematicBlockState[] palette = readPalette(blocks.getCompound("Palette", CompoundBinaryTag.empty()));
        int[] blockData = decodeVarInts(blocks.getByteArray("Data"), width * height * length);
        List<SchematicBlockEntity> blockEntities = readBlockEntities(blocks.getList("BlockEntities", ListBinaryTag.empty()));

        return new SpongeSchematic(3, width, height, length, offset[0], offset[1], offset[2], palette, blockData, blockEntities);
    }

    private static SpongeSchematic readVersion2(CompoundBinaryTag schematic) throws IOException {
        int width = readUnsignedShort(schematic, "Width");
        int height = readUnsignedShort(schematic, "Height");
        int length = readUnsignedShort(schematic, "Length");
        int[] offset = readOffset(schematic);

        SchematicBlockState[] palette = readPalette(schematic.getCompound("Palette", CompoundBinaryTag.empty()));
        int[] blockData = decodeVarInts(schematic.getByteArray("BlockData"), width * height * length);
        List<SchematicBlockEntity> blockEntities = readBlockEntities(schematic.getList("BlockEntities", ListBinaryTag.empty()));

        return new SpongeSchematic(2, width, height, length, offset[0], offset[1], offset[2], palette, blockData, blockEntities);
    }

    private static int readUnsignedShort(CompoundBinaryTag tag, String key) {
        return tag.getShort(key) & 0xFFFF;
    }

    private static int[] readOffset(CompoundBinaryTag tag) {
        int[] offset = tag.getIntArray("Offset", new int[]{0, 0, 0});
        if (offset.length != 3) {
            return new int[]{0, 0, 0};
        }
        return offset;
    }

    private static SchematicBlockState[] readPalette(CompoundBinaryTag paletteTag) throws IOException {
        if (paletteTag.size() == 0) {
            throw new IOException("Sponge schematic does not contain a block palette.");
        }

        int maxIndex = 0;
        for (Map.Entry<String, ? extends BinaryTag> entry : paletteTag) {
            maxIndex = Math.max(maxIndex, paletteTag.getInt(entry.getKey()));
        }

        SchematicBlockState[] palette = new SchematicBlockState[maxIndex + 1];
        for (Map.Entry<String, ? extends BinaryTag> entry : paletteTag) {
            int paletteIndex = paletteTag.getInt(entry.getKey());
            if (paletteIndex >= 0 && paletteIndex < palette.length) {
                palette[paletteIndex] = SchematicBlockState.parse(entry.getKey());
            }
        }

        SchematicBlockState air = SchematicBlockState.parse("minecraft:air");
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                palette[i] = air;
            }
        }
        return palette;
    }

    private static List<SchematicBlockEntity> readBlockEntities(ListBinaryTag blockEntitiesTag) {
        List<SchematicBlockEntity> blockEntities = new ArrayList<>();
        for (BinaryTag rawTag : blockEntitiesTag) {
            if (!(rawTag instanceof CompoundBinaryTag blockEntityTag)) {
                continue;
            }

            int[] pos = blockEntityTag.getIntArray("Pos", new int[0]);
            if (pos.length != 3) {
                continue;
            }

            String id = blockEntityTag.getString("Id", blockEntityTag.getString("id", ""));
            CompoundBinaryTag data = blockEntityTag.getCompound("Data", blockEntityTag);
            blockEntities.add(new SchematicBlockEntity(pos[0], pos[1], pos[2], id, blockEntityTag, data));
        }
        return blockEntities;
    }

    private static int[] decodeVarInts(byte[] bytes, int expectedValues) throws IOException {
        int[] values = new int[expectedValues];
        int value = 0;
        int position = 0;
        int valueCount = 0;

        for (byte b : bytes) {
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) {
                if (valueCount >= expectedValues) {
                    throw new IOException("Schematic block data contains more values than expected.");
                }
                values[valueCount++] = value;
                value = 0;
                position = 0;
                continue;
            }

            position += 7;
            if (position > 28) {
                throw new IOException("Invalid varint in schematic block data.");
            }
        }

        if (position != 0) {
            throw new IOException("Truncated varint in schematic block data.");
        }
        if (valueCount != expectedValues) {
            throw new IOException("Schematic block data contains " + valueCount + " values, expected " + expectedValues + ".");
        }
        return values;
    }
}
