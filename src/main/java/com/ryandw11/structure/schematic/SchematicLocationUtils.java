package com.ryandw11.structure.schematic;

import org.bukkit.Location;

import java.util.List;

/**
 * Location utilities for schematic placement.
 */
public class SchematicLocationUtils {
    private SchematicLocationUtils() {}

    /**
     * Checks to see if a location is not already inside a list of locations.
     *
     * @param locations The list of locations.
     * @param location  The location to check
     * @return If it is not already in.
     */
    protected static boolean isNotAlreadyIn(List<Location> locations, Location location) {
        for (Location auxLocation : locations) {
            if (location.distance(auxLocation) < 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rotate the point around the center.
     *
     * @param point  The point
     * @param center The center
     * @param angle  The angle to rotate by.
     * @return The final position.
     */
    public static Location rotateAround(Location point, Location center, double angle) {
        angle = Math.toRadians(angle * -1);
        double rotatedX = Math.cos(angle) * (point.getBlockX() - center.getBlockX()) - Math.sin(angle) * (point.getBlockZ() - center.getBlockZ()) + center.getBlockX();
        double rotatedZ = Math.sin(angle) * (point.getBlockX() - center.getBlockX()) + Math.cos(angle) * (point.getBlockZ() - center.getBlockZ()) + center.getBlockZ();

        return new Location(point.getWorld(), Math.floor(rotatedX), point.getY(), Math.floor(rotatedZ));
    }

    /**
     * Get the minimum location of a structure.
     *
     * @param schematic     The loaded schematic.
     * @param pasteLocation The paste location.
     * @param rotation      The rotation of the structure.
     * @return The minimum location.
     */
    public static Location getMinimumLocation(SpongeSchematic schematic, Location pasteLocation, double rotation) {
        return schematic.toWorldLocation(0, 0, 0, pasteLocation, rotation);
    }

    /**
     * Get the maximum location of a structure.
     *
     * @param schematic     The loaded schematic.
     * @param pasteLocation The paste location.
     * @param rotation      The rotation of the structure.
     * @return The maximum location.
     */
    public static Location getMaximumLocation(SpongeSchematic schematic, Location pasteLocation, double rotation) {
        return schematic.toWorldLocation(schematic.getWidth() - 1, schematic.getHeight() - 1, schematic.getLength() - 1, pasteLocation, rotation);
    }
}
