package com.ryandw11.structure.schematic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            BlockData alias = parseLegacyAliasBlockData(stateString);
            if (alias != null) {
                return new SchematicBlockState(stateString, alias, alias.getMaterial(), false);
            }

            BlockData sanitized = parseSanitizedBlockData(stateString);
            if (sanitized != null) {
                return new SchematicBlockState(stateString, sanitized, sanitized.getMaterial(), true);
            }

            Material material = parseMaterial(stateString);
            if (material != null && material.isBlock()) {
                return new SchematicBlockState(stateString, material.createBlockData(), material, true);
            }

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

    private static BlockData parseLegacyAliasBlockData(String stateString) {
        String aliasBlockId = getLegacyBlockIdAlias(getBlockId(stateString));
        if (aliasBlockId == null) {
            return null;
        }

        return tryCreateBlockData(aliasBlockId + getPropertySuffix(stateString));
    }

    private static BlockData parseSanitizedBlockData(String stateString) {
        String blockId = getBlockId(stateString);
        List<String> properties = getProperties(stateString);
        if (properties.isEmpty()) {
            return null;
        }

        BlockData best = parseBlockDataWithoutProperties(blockId);
        if (best == null) {
            return null;
        }

        List<String> acceptedProperties = new ArrayList<>();
        for (String property : properties) {
            List<String> candidateProperties = new ArrayList<>(acceptedProperties);
            candidateProperties.add(property);

            BlockData candidate = tryCreateBlockData(blockId + "[" + String.join(",", candidateProperties) + "]");
            if (candidate != null) {
                acceptedProperties.add(property);
                best = candidate;
            }
        }
        return best;
    }

    private static BlockData parseBlockDataWithoutProperties(String blockId) {
        BlockData blockData = tryCreateBlockData(blockId);
        if (blockData != null) {
            return blockData;
        }

        Material material = parseMaterial(blockId);
        if (material != null && material.isBlock()) {
            return material.createBlockData();
        }
        return null;
    }

    private static BlockData tryCreateBlockData(String blockDataString) {
        try {
            return Bukkit.createBlockData(blockDataString);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Material parseMaterial(String stateString) {
        String blockId = getBlockId(stateString);
        String aliasBlockId = getLegacyBlockIdAlias(blockId);
        if (aliasBlockId != null) {
            Material aliasMaterial = parseMaterial(aliasBlockId);
            if (aliasMaterial != null) {
                return aliasMaterial;
            }
        }

        Material material = Material.matchMaterial(blockId);
        if (material != null) {
            return material;
        }

        int namespaceIndex = blockId.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex + 1 < blockId.length()) {
            String withoutNamespace = blockId.substring(namespaceIndex + 1);
            material = Material.matchMaterial(withoutNamespace);
            if (material != null) {
                return material;
            }
            material = Material.matchMaterial(withoutNamespace.toUpperCase());
            if (material != null) {
                return material;
            }
        }

        return Material.matchMaterial(blockId.toUpperCase().replace(':', '_'));
    }

    private static String getBlockId(String stateString) {
        String trimmed = stateString.trim();
        int propertyIndex = stateString.indexOf('[');
        if (propertyIndex >= 0) {
            return trimmed.substring(0, trimmed.indexOf('[')).trim();
        }
        return trimmed;
    }

    private static String getLegacyBlockIdAlias(String blockId) {
        return switch (blockId.trim().toLowerCase(Locale.ROOT)) {
            case "minecraft:chain", "chain" -> "minecraft:iron_chain";
            case "minecraft:grass", "grass" -> "minecraft:short_grass";
            default -> null;
        };
    }

    private static String getPropertySuffix(String stateString) {
        int start = stateString.indexOf('[');
        int end = stateString.lastIndexOf(']');
        if (start < 0 || end < start) {
            return "";
        }
        return stateString.substring(start, end + 1).trim();
    }

    private static List<String> getProperties(String stateString) {
        int start = stateString.indexOf('[');
        int end = stateString.lastIndexOf(']');
        if (start < 0 || end <= start + 1) {
            return List.of();
        }

        String[] split = stateString.substring(start + 1, end).split(",");
        List<String> properties = new ArrayList<>();
        for (String property : split) {
            String trimmed = property.trim();
            if (!trimmed.isEmpty()) {
                properties.add(trimmed);
            }
        }
        return properties;
    }
}
