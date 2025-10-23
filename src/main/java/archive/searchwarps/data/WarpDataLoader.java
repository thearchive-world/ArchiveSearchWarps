package archive.searchwarps.data;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads and parses ActionIcons.yml from WarpSystem plugin.
 * Provides thread-safe access to warp icon data.
 */
public class WarpDataLoader {
    private final Plugin plugin;
    private final Logger logger;
    private final String warpsystemDataFolder;
    private final String actionIconsFile;

    private List<WarpIcon> warpIcons = new ArrayList<>();
    private final Object lock = new Object();

    // Mapping from legacy pattern identifiers to modern NamespacedKey values
    private static final Map<String, String> PATTERN_ID_MAP = createPatternIdMap();

    private static Map<String, String> createPatternIdMap() {
        Map<String, String> map = new HashMap<>();
        // Legacy identifier -> Modern namespaced key
        map.put("b", "base");
        map.put("bs", "stripe_bottom");
        map.put("ts", "stripe_top");
        map.put("ls", "stripe_left");
        map.put("rs", "stripe_right");
        map.put("cs", "stripe_center");
        map.put("ms", "stripe_middle");
        map.put("drs", "stripe_downright");
        map.put("dls", "stripe_downleft");
        map.put("ss", "small_stripes");
        map.put("cr", "cross");
        map.put("sc", "square_bottom_left");
        map.put("ld", "diagonal_left");
        map.put("rud", "diagonal_up_right");
        map.put("lud", "diagonal_up_left");
        map.put("rd", "diagonal_right");
        map.put("vh", "half_vertical");
        map.put("vhr", "half_vertical_right");
        map.put("hh", "half_horizontal");
        map.put("hhb", "half_horizontal_bottom");
        map.put("bl", "square_bottom_left");
        map.put("br", "square_bottom_right");
        map.put("tl", "square_top_left");
        map.put("tr", "square_top_right");
        map.put("bt", "triangle_bottom");
        map.put("tt", "triangle_top");
        map.put("bts", "triangles_bottom");
        map.put("tts", "triangles_top");
        map.put("mc", "circle");
        map.put("mr", "rhombus");
        map.put("bo", "border");
        map.put("cbo", "curly_border");
        map.put("bri", "bricks");
        map.put("gra", "gradient");
        map.put("gru", "gradient_up");
        map.put("cre", "creeper");
        map.put("sku", "skull");
        map.put("flo", "flower");
        map.put("moj", "mojang");
        map.put("glb", "globe");
        map.put("pig", "piglin");
        return map;
    }

    public WarpDataLoader(Plugin plugin, String warpsystemDataFolder, String actionIconsFile) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.warpsystemDataFolder = warpsystemDataFolder;
        this.actionIconsFile = actionIconsFile;
    }

    /**
     * Loads warp data from ActionIcons.yml.
     * Should be called asynchronously to avoid blocking main thread.
     */
    public void load() {
        logger.info("Loading warp data from ActionIcons.yml...");

        File actionIconsPath = new File(warpsystemDataFolder, actionIconsFile);

        if (!actionIconsPath.exists()) {
            logger.severe("ActionIcons.yml not found at: " + actionIconsPath.getAbsolutePath());
            logger.severe("Plugin will not function. Check config.yml settings.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(actionIconsPath);

        List<WarpIcon> loadedIcons = new ArrayList<>();
        int skippedCount = 0;

        // Get Icons list from YAML
        List<?> iconsList = yaml.getList("Icons");

        if (iconsList == null || iconsList.isEmpty()) {
            logger.severe("No 'Icons' list found in ActionIcons.yml!");
            return;
        }

        // Parse each icon entry
        for (int index = 0; index < iconsList.size(); index++) {
            Object iconObj = iconsList.get(index);

            // Each icon should be a Map (configuration section)
            if (!(iconObj instanceof Map)) {
                logger.warning("Icon at index " + index + " is not a map, skipping");
                skippedCount++;
                continue;
            }

            try {
                // Convert map to ConfigurationSection for easier parsing
                @SuppressWarnings("unchecked")
                Map<String, Object> iconMap = (Map<String, Object>) iconObj;

                MemoryConfiguration iconSection = new MemoryConfiguration();
                convertMapToSection(iconMap, iconSection);

                WarpIcon warpIcon = parseIcon(iconSection);
                if (warpIcon != null) {
                    loadedIcons.add(warpIcon);
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                logger.warning("Failed to parse warp icon at index " + index + ": " + e.getMessage());
                skippedCount++;
            }
        }

        // Thread-safe update
        synchronized (lock) {
            this.warpIcons = loadedIcons;
        }

        logger.info("Loaded " + loadedIcons.size() + " warps from ActionIcons.yml" +
                    (skippedCount > 0 ? " (skipped " + skippedCount + " invalid entries)" : ""));
    }

    /**
     * Recursively converts a Map to a ConfigurationSection.
     * This is needed because nested Maps from YAML need to be converted to ConfigurationSections
     * for getConfigurationSection() to work properly.
     */
    @SuppressWarnings("unchecked")
    private void convertMapToSection(Map<String, Object> map, ConfigurationSection section) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                // Recursively convert nested maps
                ConfigurationSection subSection = section.createSection(key);
                convertMapToSection((Map<String, Object>) value, subSection);
            } else {
                // Set primitive values directly
                section.set(key, value);
            }
        }
    }

    /**
     * Parses a single icon section from YAML.
     * Returns null if the icon is invalid or missing required fields.
     */
    private WarpIcon parseIcon(ConfigurationSection iconSection) {
        // Extract name (required)
        String name = iconSection.getString("name");
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Extract item section (contains Type, Name, Lore, SkullOwner, Banner, etc.)
        ConfigurationSection itemSection = iconSection.getConfigurationSection("item");

        // Parse material type from Type field, default to ENDER_PEARL if missing/invalid
        Material itemType = Material.ENDER_PEARL;
        if (itemSection != null) {
            String typeString = itemSection.getString("Type");
            if (typeString != null && !typeString.isEmpty()) {
                Material parsedMaterial = Material.matchMaterial(typeString);
                if (parsedMaterial != null) {
                    itemType = parsedMaterial;
                } else {
                    logger.warning("Warp '" + name + "' has invalid material type: " + typeString + ", defaulting to ENDER_PEARL");
                }
            }
        }

        // Extract display name (use icon name if not specified)
        String displayName = name;
        List<String> lore = new ArrayList<>();

        if (itemSection != null) {
            displayName = itemSection.getString("Name", name);

            // Extract lore (optional - needed for search)
            lore = itemSection.getStringList("Lore");
            if (lore == null) {
                lore = new ArrayList<>();
            }
        }

        // Extract skull owner texture hash (for PLAYER_HEAD items)
        String skullOwner = null;
        if (itemSection != null && itemSection.contains("SkullOwner")) {
            skullOwner = itemSection.getString("SkullOwner");
        }

        // Extract banner patterns (for banner items)
        List<WarpIcon.BannerPatternData> bannerPatterns = null;
        if (itemSection != null && itemSection.contains("Banner")) {
            List<?> bannerList = itemSection.getList("Banner");
            if (bannerList != null && !bannerList.isEmpty()) {
                bannerPatterns = new ArrayList<>();
                for (Object patternObj : bannerList) {
                    if (patternObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> patternMap = (Map<String, Object>) patternObj;

                        String colorStr = (String) patternMap.get("color");
                        String patternStr = (String) patternMap.get("pattern");

                        if (colorStr != null && patternStr != null) {
                            try {
                                org.bukkit.DyeColor color = org.bukkit.DyeColor.valueOf(colorStr.toUpperCase());

                                // Convert legacy identifier to modern namespaced key
                                String modernKey = PATTERN_ID_MAP.get(patternStr.toLowerCase());
                                PatternType pattern = null;

                                if (modernKey != null) {
                                    // Use modern RegistryAccess API to get PatternType
                                    NamespacedKey key = NamespacedKey.minecraft(modernKey);
                                    pattern = RegistryAccess.registryAccess()
                                        .getRegistry(RegistryKey.BANNER_PATTERN)
                                        .get(key);
                                }

                                if (pattern != null) {
                                    bannerPatterns.add(new WarpIcon.BannerPatternData(color, pattern));
                                } else {
                                    logger.warning("Warp '" + name + "' has invalid banner pattern: " + patternStr);
                                }
                            } catch (IllegalArgumentException e) {
                                logger.warning("Warp '" + name + "' has invalid banner color: " + colorStr);
                            }
                        }
                    }
                }

                // If no valid patterns were parsed, set to null
                if (bannerPatterns.isEmpty()) {
                    bannerPatterns = null;
                }
            }
        }

        // Extract destination ID from actions list
        String destinationId = null;
        List<?> actionsList = iconSection.getList("actions");
        if (actionsList != null && !actionsList.isEmpty()) {
            Object firstAction = actionsList.get(0);

            // Actions list contains maps, convert first action to ConfigurationSection
            if (firstAction instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionMap = (Map<String, Object>) firstAction;

                // Navigate: action -> value -> destination -> id
                Object valueObj = actionMap.get("value");
                if (valueObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> valueMap = (Map<String, Object>) valueObj;

                    Object destObj = valueMap.get("destination");
                    if (destObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> destMap = (Map<String, Object>) destObj;

                        Object idObj = destMap.get("id");
                        if (idObj instanceof String) {
                            destinationId = (String) idObj;
                        }
                    }
                }
            }
        }

        // All ActionIcons should have destination IDs - if missing, it's a parsing error
        if (destinationId == null || destinationId.isEmpty()) {
            logger.warning("Warp '" + name + "' has no destination ID - parsing failed, skipping");
            return null;
        }

        // Extract performed count (default 0)
        int performed = iconSection.getInt("performed", 0);

        // Extract page/category (optional)
        String page = iconSection.getString("page", "");

        return new WarpIcon(name, itemType, displayName, lore, destinationId, performed, page, skullOwner, bannerPatterns);
    }

    /**
     * Returns an unmodifiable list of all loaded warp icons.
     * Thread-safe.
     */
    public List<WarpIcon> getWarpIcons() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(warpIcons));
        }
    }

    /**
     * Reloads warp data from disk.
     * Should be called asynchronously.
     */
    public void reload() {
        load();
    }

    /**
     * Returns the number of loaded warps.
     * Thread-safe.
     */
    public int getWarpCount() {
        synchronized (lock) {
            return warpIcons.size();
        }
    }
}
