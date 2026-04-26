package com.ryandw11.structure.schematic;

import com.ryandw11.structure.CustomStructures;
import com.ryandw11.structure.structure.Structure;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Handles schematic operations.
 */
public class SchematicHandler {

    private static final String WORLDEDIT_CREATOR_CLASS = "com.ryandw11.structure.schematic.worldedit.WorldEditSchematicCreator";

    private SchematicHandler() {
    }

    /**
     * Queue a schematic paste using the built-in Sponge schematic backend.
     *
     * @param loc       The location.
     * @param filename  The file name. Ex: demo.schem
     * @param useAir    If air should be placed.
     * @param structure The structure that is getting spawned.
     * @param iteration The number of iterations in a structure.
     * @throws IOException If the schematic file does not exist.
     */
    public static void placeSchematic(Location loc, String filename, boolean useAir, Structure structure, int iteration)
            throws IOException {
        CustomStructures plugin = CustomStructures.getInstance();

        if (iteration > structure.getStructureLimitations().getIterationLimit()) {
            plugin.getLogger().severe("Critical Error: StackOverflow detected. Automatically terminating the spawning of the structure.");
            plugin.getLogger().severe("The structure '" + structure.getName() + "' has spawned too many sub structure via recursion.");
            return;
        }

        File schematicFile = new File(plugin.getDataFolder() + "/schematics/" + filename);
        if (!schematicFile.exists() && iteration == 0) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&b[&aCustomStructures&b] &cA fatal error has occurred! Please check the console for errors."));
            plugin.getLogger().warning("Error: The schematic " + filename + " does not exist!");
            plugin.getLogger().warning(
                    "If this is your first time using this plugin you need to put a schematic in the schematic folder.");
            plugin.getLogger().warning("Then add it into the config.");
            plugin.getLogger().warning(
                    "If you need help look at the wiki: https://github.com/ryandw11/CustomStructures/wiki or contact Ryandw11 on spigot!");
            plugin.getLogger().warning("The plugin will now disable to prevent damage to the server.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        } else if (!schematicFile.exists()) {
            plugin.getLogger().warning("Error: The schematic " + filename + " does not exist!");
            throw new IOException("Cannot find schematic file " + filename);
        }

        BuiltInSchematicBackend.paste(loc, schematicFile, filename, useAir, structure, iteration);
    }

    /**
     * Queue a schematic paste using the built-in Sponge schematic backend.
     *
     * @param loc       The location.
     * @param filename  The file name. Ex: demo.schem
     * @param useAir    If air should be placed.
     * @param structure The structure that is getting spawned.
     * @throws IOException If the schematic file does not exist.
     */
    public static void placeSchematic(Location loc, String filename, boolean useAir, Structure structure)
            throws IOException {
        placeSchematic(loc, filename, useAir, structure, 0);
    }

    /**
     * Create a schematic and save it to the schematics folder in the CustomStructures plugin.
     *
     * <p>This command path still requires WorldEdit because it relies on the player's WorldEdit selection.</p>
     *
     * @param name    The name of the schematic.
     * @param player  The player.
     * @param world   The world.
     * @param compile If the schematic should be compiled.
     * @return If the operation was successful.
     */
    public static boolean createSchematic(String name, Player player, World world, boolean compile) {
        return invokeWorldEditCreator("createSchematic", new Class[]{String.class, Player.class, World.class, boolean.class},
                name, player, world, compile);
    }

    /**
     * Only compile a selection into a compiled schematic.
     *
     * <p>This command path still requires WorldEdit because it relies on the player's WorldEdit selection.</p>
     *
     * @param name   The name of the schematic.
     * @param player The player.
     * @param world  The world.
     * @return If the operation was successful.
     */
    public static boolean compileOnly(String name, Player player, World world) {
        return invokeWorldEditCreator("compileOnly", new Class[]{String.class, Player.class, World.class},
                name, player, world);
    }

    public static boolean canUseWorldEditCreator() {
        return CustomStructures.getInstance().getConfig().getBoolean("WorldEdit.Enabled", false)
                && Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    private static boolean invokeWorldEditCreator(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        CustomStructures plugin = CustomStructures.getInstance();
        if (!canUseWorldEditCreator()) {
            return false;
        }

        try {
            Class<?> creatorClass = Class.forName(WORLDEDIT_CREATOR_CLASS);
            Method method = creatorClass.getMethod(methodName, parameterTypes);
            Object result = method.invoke(null, arguments);
            return result instanceof Boolean && (Boolean) result;
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("WorldEdit support is enabled, but the WorldEdit schematic creator class could not be loaded.");
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            plugin.getLogger().warning("WorldEdit support is enabled, but the schematic creator could not be invoked.");
            if (plugin.isDebug()) {
                ex.printStackTrace();
            }
        } catch (InvocationTargetException ex) {
            plugin.getLogger().warning("WorldEdit failed while creating or compiling a schematic.");
            if (plugin.isDebug()) {
                ex.getTargetException().printStackTrace();
            }
        } catch (NoClassDefFoundError ex) {
            plugin.getLogger().warning("WorldEdit support is enabled, but WorldEdit classes are not available.");
            if (plugin.isDebug()) {
                ex.printStackTrace();
            }
        }
        return false;
    }
}
