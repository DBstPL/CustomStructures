package com.ryandw11.structure.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

/**
 * Block entity data from a Sponge schematic.
 */
public final class SchematicBlockEntity {
    private final int x;
    private final int y;
    private final int z;
    private final String id;
    private final CompoundBinaryTag tag;
    private final CompoundBinaryTag data;

    public SchematicBlockEntity(int x, int y, int z, String id, CompoundBinaryTag tag, CompoundBinaryTag data) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.id = id;
        this.tag = tag;
        this.data = data;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getId() {
        return id;
    }

    public boolean isSign() {
        return id != null && (id.equalsIgnoreCase("minecraft:sign") || id.toLowerCase().endsWith("_sign"));
    }

    public String[] readSignLines() {
        String[] lines = readModernSignLines(data.getCompound("front_text", tag.getCompound("front_text", CompoundBinaryTag.empty())));
        if (lines != null) {
            return lines;
        }

        String[] legacy = new String[4];
        boolean found = false;
        for (int i = 0; i < legacy.length; i++) {
            String value = firstString("Text" + (i + 1));
            if (!value.isEmpty()) {
                found = true;
            }
            legacy[i] = toPlainText(value);
        }
        return found ? legacy : new String[]{"", "", "", ""};
    }

    private String firstString(String key) {
        String fromData = data.getString(key, "");
        if (!fromData.isEmpty()) {
            return fromData;
        }
        return tag.getString(key, "");
    }

    private static String[] readModernSignLines(CompoundBinaryTag textTag) {
        if (textTag == null || textTag.size() == 0) {
            return null;
        }

        ListBinaryTag messages = textTag.getList("messages", ListBinaryTag.empty());
        if (messages.size() == 0) {
            return null;
        }

        String[] lines = new String[]{"", "", "", ""};
        for (int i = 0; i < Math.min(4, messages.size()); i++) {
            lines[i] = toPlainText(messages.getString(i, ""));
        }
        return lines;
    }

    private static String toPlainText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
            return raw;
        }

        try {
            JsonElement element = JsonParser.parseString(trimmed);
            StringBuilder builder = new StringBuilder();
            appendJsonText(element, builder);
            return builder.toString();
        } catch (RuntimeException ex) {
            return raw;
        }
    }

    private static void appendJsonText(JsonElement element, StringBuilder builder) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            builder.append(element.getAsString());
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                appendJsonText(child, builder);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("text")) {
            appendJsonText(object.get("text"), builder);
        }
        if (object.has("extra")) {
            appendJsonText(object.get("extra"), builder);
        }
        if (object.has("translate") && builder.length() == 0) {
            appendJsonText(object.get("translate"), builder);
        }
    }
}
