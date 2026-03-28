package it.onlynelchilling.ahubpvp.config;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@SuppressWarnings("all")
public class ConfigUtils {
    protected final Plugin plugin;
    private File file;
    private YamlConfiguration config;
    private String fileName;

    public ConfigUtils(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName + ".yml";
        file = new File(plugin.getDataFolder(), this.fileName);
        if (!file.exists()) {
            plugin.saveResource(this.fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        if (config.getKeys(false).isEmpty()) {
            plugin.saveResource(this.fileName, true);
            config = YamlConfiguration.loadConfiguration(file);
        }
        mergeDefaults();
    }

    public File getFile() { return file; }
    public YamlConfiguration getConfig() { return config; }
    public String getFileName() { return fileName; }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        if (config.getKeys(false).isEmpty()) {
            plugin.saveResource(this.fileName, true);
            config = YamlConfiguration.loadConfiguration(file);
        }
        mergeDefaults();
    }

    private void mergeDefaults() {
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream == null) return;

        List<String> defaultLines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(defaultStream, StandardCharsets.UTF_8))) {
            defaultLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                defaultLines.add(line);
            }
        } catch (IOException e) {
            return;
        }

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new StringReader(String.join("\n", defaultLines)));

        boolean hasMissing = false;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !config.contains(key)) {
                hasMissing = true;
                break;
            }
            if (defaults.isConfigurationSection(key) && !config.isConfigurationSection(key) && !config.contains(key)) {
                hasMissing = true;
                break;
            }
        }

        if (!hasMissing) return;

        byte[] backup;
        try {
            backup = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }

        Set<String> userKeys = config.getKeys(true);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < defaultLines.size(); i++) {
            String dLine = defaultLines.get(i);

            if (dLine.isBlank() || dLine.stripLeading().startsWith("#")) {
                result.add(dLine);
                continue;
            }

            String yamlPath = resolvePathAtLine(defaultLines, i);
            if (yamlPath == null) {
                result.add(dLine);
                continue;
            }

            if (userKeys.contains(yamlPath)) {
                if (defaults.isConfigurationSection(yamlPath)) {
                    result.add(dLine);
                } else {
                    Object userValue = config.get(yamlPath);
                    int indent = countIndent(dLine);
                    String dTrimmed = dLine.stripLeading();
                    int dColon = dTrimmed.indexOf(':');
                    String keyName = dTrimmed.substring(0, dColon).strip();
                    if ((keyName.startsWith("'") && keyName.endsWith("'")) ||
                            (keyName.startsWith("\"") && keyName.endsWith("\""))) {
                        keyName = keyName.substring(1, keyName.length() - 1);
                    }
                    List<String> serialized = serializeValue(keyName, userValue, indent);
                    result.addAll(serialized);
                    i = skipValueBlock(defaultLines, i);
                }
            } else {
                result.add(dLine);
                if (defaults.isConfigurationSection(yamlPath) && !config.contains(yamlPath)) {
                    int blockEnd = skipValueBlock(defaultLines, i);
                    for (int j = i + 1; j <= blockEnd; j++) {
                        result.add(defaultLines.get(j));
                    }
                    i = blockEnd;
                }
            }
        }

        try {
            Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            YamlConfiguration test = YamlConfiguration.loadConfiguration(file);
            if (test.getKeys(false).isEmpty()) {
                Files.write(file.toPath(), backup);
                config = YamlConfiguration.loadConfiguration(file);
                return;
            }
            config = test;
        } catch (Exception e) {
            try {
                Files.write(file.toPath(), backup);
                config = YamlConfiguration.loadConfiguration(file);
            } catch (IOException ignored) {}
        }
    }

    private String resolvePathAtLine(List<String> lines, int lineIdx) {
        String line = lines.get(lineIdx);
        if (line.isBlank() || line.stripLeading().startsWith("#")) return null;

        String trimmed = line.stripLeading();
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) return null;

        char afterColon = colonIdx + 1 < trimmed.length() ? trimmed.charAt(colonIdx + 1) : ' ';
        if (afterColon != ' ' && afterColon != '\0' && colonIdx + 1 < trimmed.length() && afterColon != '\n') {
            if (afterColon != ' ' && afterColon != '\r') return null;
        }

        String key = trimmed.substring(0, colonIdx).strip();
        if (key.startsWith("'") || key.startsWith("\"")) {
            key = key.substring(1, key.length() - 1);
        }
        if (key.startsWith("-")) return null;

        int myIndent = countIndent(line);
        if (myIndent == 0) return key;

        Deque<String> pathParts = new ArrayDeque<>();
        pathParts.addFirst(key);

        int expectedParentIndent = myIndent - 2;
        for (int i = lineIdx - 1; i >= 0 && expectedParentIndent >= 0; i--) {
            String pLine = lines.get(i);
            if (pLine.isBlank() || pLine.stripLeading().startsWith("#")) continue;

            int pIndent = countIndent(pLine);
            if (pIndent == expectedParentIndent) {
                String pTrimmed = pLine.stripLeading();
                int pColon = pTrimmed.indexOf(':');
                if (pColon <= 0) return null;

                String pKey = pTrimmed.substring(0, pColon).strip();
                if (pKey.startsWith("'") || pKey.startsWith("\"")) {
                    pKey = pKey.substring(1, pKey.length() - 1);
                }
                if (pKey.startsWith("-")) return null;

                pathParts.addFirst(pKey);
                expectedParentIndent -= 2;
            }
        }

        return String.join(".", pathParts);
    }

    private int skipValueBlock(List<String> lines, int startIdx) {
        int indent = countIndent(lines.get(startIdx));
        int i = startIdx + 1;
        while (i < lines.size()) {
            String l = lines.get(i);
            if (l.isBlank() || l.stripLeading().startsWith("#")) {
                int peek = i;
                while (peek < lines.size() && (lines.get(peek).isBlank() || lines.get(peek).stripLeading().startsWith("#"))) {
                    peek++;
                }
                if (peek >= lines.size() || countIndent(lines.get(peek)) <= indent) {
                    return i - 1;
                }
                i++;
                continue;
            }
            if (countIndent(l) <= indent) {
                return i - 1;
            }
            i++;
        }
        return i - 1;
    }

    private List<String> serializeValue(String key, Object value, int indent) {
        String pad = " ".repeat(indent);
        List<String> lines = new ArrayList<>();

        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                lines.add(pad + key + ": []");
            } else {
                lines.add(pad + key + ":");
                for (Object item : list) {
                    if (item instanceof Map) {
                        lines.addAll(serializeMapInList((Map<?, ?>) item, indent + 2));
                    } else {
                        String str = item == null ? "null" : item.toString();
                        if (needsQuoting(str)) {
                            lines.add(pad + "  - '" + str.replace("'", "''") + "'");
                        } else {
                            lines.add(pad + "  - " + str);
                        }
                    }
                }
            }
        } else if (value instanceof String str) {
            if (str.contains("\n")) {
                lines.add(pad + key + ": |");
                for (String part : str.split("\n", -1)) {
                    lines.add(pad + "  " + part);
                }
            } else if (needsQuoting(str)) {
                lines.add(pad + key + ": '" + str.replace("'", "''") + "'");
            } else {
                lines.add(pad + key + ": " + str);
            }
        } else if (value == null) {
            lines.add(pad + key + ": null");
        } else {
            lines.add(pad + key + ": " + value);
        }

        return lines;
    }

    private List<String> serializeMapInList(Map<?, ?> map, int indent) {
        String pad = " ".repeat(indent);
        List<String> lines = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String k = entry.getKey().toString();
            Object v = entry.getValue();
            if (first) {
                lines.add(pad + "- " + k + ": " + (v == null ? "null" : v));
                first = false;
            } else {
                lines.add(pad + "  " + k + ": " + (v == null ? "null" : v));
            }
        }
        return lines;
    }

    private boolean needsQuoting(String str) {
        if (str.isEmpty()) return true;
        if (str.contains("#") || str.contains(":") || str.contains("{") || str.contains("}")
                || str.contains("[") || str.contains("]") || str.contains(",")
                || str.contains("&") || str.contains("*") || str.contains("?")
                || str.contains("|") || str.contains(">") || str.contains("!")
                || str.contains("%") || str.contains("@") || str.contains("`")) return true;
        if (str.startsWith(" ") || str.endsWith(" ")) return true;
        if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")
                || str.equalsIgnoreCase("null") || str.equalsIgnoreCase("yes")
                || str.equalsIgnoreCase("no")) return true;
        try { Double.parseDouble(str); return true; } catch (NumberFormatException ignored) {}
        return false;
    }

    private int countIndent(String line) {
        int c = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') c++;
            else break;
        }
        return c;
    }

    public void save() {
        try {
            config.save(file);
            config = YamlConfiguration.loadConfiguration(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + fileName);
        }
    }

    public Object get(String path) { return config.get(path); }
    public String getString(String path, String def) { return config.getString(path, def); }
    public String getString(String path) { return config.getString(path); }
    public int getInt(String path, int def) { return config.getInt(path, def); }
    public int getInt(String path) { return config.getInt(path); }
    public boolean getBoolean(String path, boolean def) { return config.getBoolean(path, def); }
    public boolean getBoolean(String path) { return config.getBoolean(path); }
    public double getDouble(String path, double def) { return config.getDouble(path, def); }
    public double getDouble(String path) { return config.getDouble(path); }
    public long getLong(String path, long def) { return config.getLong(path, def); }
    public long getLong(String path) { return config.getLong(path); }
    public List<String> getStringList(String path) { return config.getStringList(path); }
    public List<Integer> getIntegerList(String path) { return config.getIntegerList(path); }
    public List<Map<?, ?>> getMapList(String path) { return config.getMapList(path); }

    public Map<String, Object> getMap(String path, Map<String, Object> def) {
        return config.getConfigurationSection(path) != null
                ? config.getConfigurationSection(path).getValues(false) : def;
    }

    public Map<String, Object> getMap(String path) {
        return config.getConfigurationSection(path) != null
                ? config.getConfigurationSection(path).getValues(false) : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, Class<T> clazz, T def) {
        return config.get(path, clazz) != null ? (T) config.get(path, clazz) : def;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, Class<T> clazz) { return (T) config.get(path, clazz); }

    public ConfigurationSection getConfigurationSection(String path) { return config.getConfigurationSection(path); }
    public Location getLocation(String path, Location def) { return config.getLocation(path, def); }
    public Location getLocation(String path) { return config.getLocation(path); }
    public void set(String path, Object o) { config.set(path, o); }
    public Set<String> getKeys(boolean deep) { return config.getKeys(deep); }
}
