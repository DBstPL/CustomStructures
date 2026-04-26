package com.ryandw11.structure.structure.properties;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A mask configuration property.
 */
public class MaskProperty {
    private final Set<String> blockTypeMask;
    private final Set<String> negatedBlockMask;
    private MaskUnion unionType;
    private boolean enabled;

    /**
     * Create the MaskProperty from a configuration file.
     *
     * @param name          The name of the mask configuration section.
     * @param configuration The configuration file.
     */
    public MaskProperty(String name, FileConfiguration configuration) {
        blockTypeMask = new HashSet<>();
        negatedBlockMask = new HashSet<>();
        unionType = MaskUnion.AND;
        enabled = false;

        if (!configuration.contains(name))
            return;

        ConfigurationSection cs = configuration.getConfigurationSection(name);
        assert cs != null;

        if (!cs.contains("Enabled") || !cs.getBoolean("Enabled"))
            return;
        enabled = true;
        if (cs.contains("UnionType")) {
            unionType = MaskUnion.valueOf(Objects.requireNonNull(cs.getString("UnionType")).toUpperCase());
        }

        processBlockTypeMask(cs);
        processNegateBlockTypeMask(cs);
    }

    /**
     * Construct MaskProperty
     *
     * @param masks     The list of Bukkit material names or namespaced block ids.
     * @param unionType The mask union type.
     */
    public MaskProperty(List<String> masks, MaskUnion unionType) {
        this.blockTypeMask = new HashSet<>();
        this.negatedBlockMask = new HashSet<>();
        for (String mask : masks) {
            this.blockTypeMask.add(normalizeBlockId(mask));
        }
        this.unionType = unionType;
        this.enabled = !masks.isEmpty();
    }

    /**
     * Get the union type.
     *
     * @return The union type.
     */
    public MaskUnion getUnionType() {
        return unionType;
    }

    /**
     * Set the union type.
     *
     * @param type The union type.
     */
    public void setUnionType(MaskUnion type) {
        this.unionType = type;
    }

    /**
     * Add a block type mask.
     *
     * @param mask The block material or namespaced id to add.
     */
    public void addMask(String mask) {
        enabled = true;
        blockTypeMask.add(normalizeBlockId(mask));
    }

    /**
     * Get the list of positive block type masks.
     *
     * @return The list of masks.
     */
    public List<String> getMasks() {
        return new ArrayList<>(blockTypeMask);
    }

    /**
     * Check whether this mask should be applied.
     *
     * @return If the mask is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Test a schematic source block against this mask.
     *
     * @param blockStateString The schematic block state string.
     * @param material         The Bukkit material parsed from the block state.
     * @return If the block should be pasted.
     */
    public boolean matchesSource(String blockStateString, Material material) {
        return matches(normalizeBlockStateId(blockStateString), normalizeMaterial(material));
    }

    /**
     * Test an existing target block against this mask.
     *
     * @param material The target block material.
     * @return If the target can be replaced.
     */
    public boolean matchesTarget(Material material) {
        return matches(normalizeMaterial(material), normalizeMaterial(material));
    }

    private boolean matches(String primaryBlockId, String materialBlockId) {
        if (!enabled) {
            return true;
        }

        List<Boolean> results = new ArrayList<>();
        if (!blockTypeMask.isEmpty()) {
            results.add(blockTypeMask.contains(primaryBlockId) || blockTypeMask.contains(materialBlockId));
        }
        if (!negatedBlockMask.isEmpty()) {
            results.add(!negatedBlockMask.contains(primaryBlockId) && !negatedBlockMask.contains(materialBlockId));
        }
        if (results.isEmpty()) {
            return true;
        }

        if (unionType == MaskUnion.OR) {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
        return results.stream().allMatch(Boolean::booleanValue);
    }

    private void processBlockTypeMask(ConfigurationSection cs) {
        if (!cs.contains("BlockTypeMask")) return;
        List<String> blockTypeStrings = cs.getStringList("BlockTypeMask");
        for (String s : blockTypeStrings) {
            blockTypeMask.add(normalizeBlockId(s));
        }
    }

    private void processNegateBlockTypeMask(ConfigurationSection cs) {
        if (!cs.contains("NegatedBlockMask")) return;
        List<String> blockTypeStrings = cs.getStringList("NegatedBlockMask");
        for (String s : blockTypeStrings) {
            negatedBlockMask.add(normalizeBlockId(s));
        }
    }

    private static String normalizeBlockStateId(String blockStateString) {
        int propertyIndex = blockStateString.indexOf('[');
        if (propertyIndex >= 0) {
            return normalizeBlockId(blockStateString.substring(0, propertyIndex));
        }
        return normalizeBlockId(blockStateString);
    }

    private static String normalizeMaterial(Material material) {
        if (material == null) {
            return "minecraft:air";
        }
        return material.getKey().toString().toLowerCase();
    }

    private static String normalizeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "minecraft:air";
        }

        String trimmed = blockId.trim();
        Material material = Material.matchMaterial(trimmed);
        if (material != null) {
            return material.getKey().toString().toLowerCase();
        }

        String lower = trimmed.toLowerCase();
        if (lower.contains(":")) {
            return lower;
        }
        return "minecraft:" + lower;
    }

    /**
     * Contains the two options for masks.
     * AND operates as a logical AND
     * while OR operates as a logical OR.
     * <p>See the world edit documentation for more information.</p>
     */
    public enum MaskUnion {
        AND, OR
    }
}
