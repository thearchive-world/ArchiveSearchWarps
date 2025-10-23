package archive.searchwarps.gui;

import archive.searchwarps.data.WarpIcon;
import archive.searchwarps.sorting.DistanceSorter;
import archive.searchwarps.sorting.SortMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main warp browser GUI using the InventoryHolder pattern.
 * Displays warps in a paginated chest inventory (54 slots).
 * Layout: 45 warp items + 9 UI buttons
 */
public class WarpBrowserGUI implements InventoryHolder {
    private static final int WARPS_PER_PAGE = 45;
    private static final int INVENTORY_SIZE = 54;

    // GUI slot constants (public for use in listeners)
    public static final int SLOT_PREV_PAGE = 45;
    public static final int SLOT_FILLER_1 = 46;
    public static final int SLOT_FILLER_2 = 47;
    public static final int SLOT_FILLER_3 = 48;
    public static final int SLOT_SEARCH = 49;
    public static final int SLOT_FILLER_4 = 50;
    public static final int SLOT_SORT_TOGGLE = 51;
    public static final int SLOT_FILLER_5 = 52;
    public static final int SLOT_NEXT_PAGE = 53;

    private final Inventory inventory;
    private final Player viewer;
    private final List<WarpIcon> displayedWarps;
    private final int currentPage;
    private final SortMode sortMode;
    private final Location playerLocation;
    private final Map<String, Double> distanceMap;

    /**
     * Creates a new warp browser GUI.
     *
     * @param viewer The player viewing the GUI
     * @param warps The list of warps to display (already filtered and sorted)
     * @param page The current page number (0-based)
     * @param sortMode The current sort mode
     * @param playerLocation The player's location (for distance calculations)
     */
    public WarpBrowserGUI(Player viewer, List<WarpIcon> warps, int page, SortMode sortMode, Location playerLocation) {
        this(viewer, warps, page, sortMode, playerLocation, new HashMap<>());
    }

    /**
     * Creates a new warp browser GUI with distance information.
     *
     * @param viewer The player viewing the GUI
     * @param warps The list of warps to display (already filtered and sorted)
     * @param page The current page number (0-based)
     * @param sortMode The current sort mode
     * @param playerLocation The player's location (for distance calculations)
     * @param distanceMap Map of warp destination IDs to distances (for distance sort mode)
     */
    public WarpBrowserGUI(Player viewer, List<WarpIcon> warps, int page, SortMode sortMode, Location playerLocation, Map<String, Double> distanceMap) {
        this.viewer = viewer;
        this.displayedWarps = new ArrayList<>(warps);
        this.currentPage = page;
        this.sortMode = sortMode;
        this.playerLocation = playerLocation;
        this.distanceMap = distanceMap;

        // Create inventory
        this.inventory = Bukkit.createInventory(
            this,
            INVENTORY_SIZE,
            Component.translatable("archive.searchwarps.gui_title")
        );

        // Populate with items
        populateInventory();
    }

    /**
     * Populates the inventory with warp items and UI buttons.
     */
    private void populateInventory() {
        // Clear inventory
        inventory.clear();

        // Calculate page range
        int startIndex = currentPage * WARPS_PER_PAGE;
        int endIndex = Math.min(startIndex + WARPS_PER_PAGE, displayedWarps.size());

        // Add warp items (slots 0-44)
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot >= WARPS_PER_PAGE) break;

            WarpIcon warp = displayedWarps.get(i);
            ItemStack warpItem = createWarpItem(warp);
            inventory.setItem(slot, warpItem);
        }

        // Add UI buttons (slots 45-53)
        addUIButtons();
    }

    /**
     * Adds UI buttons to the bottom row (slots 45-53).
     */
    private void addUIButtons() {
        // Slot 45: Previous page
        if (hasPrevPage()) {
            ItemStack prevButton = ItemStack.of(Material.ARROW);
            prevButton.editMeta(meta -> {
                Component prevText = GlobalTranslator.render(
                    Component.translatable("archive.searchwarps.previous_page"),
                    Locale.US
                );
                meta.displayName(prevText.color(NamedTextColor.YELLOW));
            });
            inventory.setItem(SLOT_PREV_PAGE, prevButton);
        }

        // Slots 46-48, 50, 52: Filler (gray glass pane)
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> {
            meta.displayName(Component.text(" ")); // Empty name to hide default
        });
        inventory.setItem(SLOT_FILLER_1, filler);
        inventory.setItem(SLOT_FILLER_2, filler);
        inventory.setItem(SLOT_FILLER_3, filler);
        inventory.setItem(SLOT_FILLER_4, filler);
        inventory.setItem(SLOT_FILLER_5, filler);

        // Slot 49: Search button
        ItemStack searchButton = ItemStack.of(Material.COMPASS);
        searchButton.editMeta(meta -> {
            Component searchText = GlobalTranslator.render(
                Component.translatable("archive.searchwarps.search_button"),
                Locale.US
            );
            meta.displayName(searchText.color(NamedTextColor.AQUA));
        });
        inventory.setItem(SLOT_SEARCH, searchButton);

        // Slot 51: Sort toggle button
        ItemStack sortButton = ItemStack.of(Material.HOPPER);
        sortButton.editMeta(meta -> {
            Component sortText;
            if (sortMode == SortMode.ALPHABETICAL) {
                sortText = GlobalTranslator.render(
                    Component.translatable("archive.searchwarps.sort_alphabetical"),
                    Locale.US
                );
            } else {
                sortText = GlobalTranslator.render(
                    Component.translatable("archive.searchwarps.sort_distance"),
                    Locale.US
                );
            }
            meta.displayName(sortText.color(NamedTextColor.GREEN));
        });
        inventory.setItem(SLOT_SORT_TOGGLE, sortButton);

        // Slot 53: Next page
        if (hasNextPage()) {
            ItemStack nextButton = ItemStack.of(Material.ARROW);
            nextButton.editMeta(meta -> {
                Component nextText = GlobalTranslator.render(
                    Component.translatable("archive.searchwarps.next_page"),
                    Locale.US
                );
                meta.displayName(nextText.color(NamedTextColor.YELLOW));
            });
            inventory.setItem(SLOT_NEXT_PAGE, nextButton);
        }
    }

    /**
     * Creates an ItemStack for a warp icon.
     *
     * @param warp The warp icon data
     * @return ItemStack ready to display in GUI
     */
    private ItemStack createWarpItem(WarpIcon warp) {
        ItemStack item = ItemStack.of(warp.itemType());

        item.editMeta(meta -> {
            // Set display name with color parsing
            Component displayName = parseColorCodes(warp.displayName());
            meta.displayName(displayName);

            // Set lore with color parsing
            List<Component> parsedLore = new ArrayList<>();
            for (String loreLine : warp.lore()) {
                Component parsedLine = parseColorCodes(loreLine);
                parsedLore.add(parsedLine);
            }

            // Add distance information if in distance sort mode
            if (sortMode == SortMode.DISTANCE && distanceMap.containsKey(warp.destinationId())) {
                double distance = distanceMap.get(warp.destinationId());
                String formattedDistance = DistanceSorter.formatDistance(distance);

                // Add empty line before distance if lore exists
                if (!parsedLore.isEmpty()) {
                    parsedLore.add(Component.empty());
                }

                // Add distance line in gray color (render translation for item lore)
                Component distanceLabel = GlobalTranslator.render(
                    Component.translatable("archive.searchwarps.distance_label",
                        Component.text(formattedDistance)
                    ),
                    Locale.US
                );
                parsedLore.add(distanceLabel.color(NamedTextColor.GRAY));
            }

            meta.lore(parsedLore);

            // Apply skull texture if this is a player head with custom texture
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                if (warp.skullOwner() != null) {
                    applySkullTexture(skullMeta, warp.skullOwner());
                }
            }

            // Apply banner patterns if this is a banner
            if (meta instanceof org.bukkit.inventory.meta.BannerMeta bannerMeta) {
                if (warp.bannerPatterns() != null) {
                    applyBannerPatterns(bannerMeta, warp.bannerPatterns());
                }
            }
        });

        return item;
    }

    /**
     * Parses Minecraft legacy color codes to Adventure Components.
     * Converts &f, &e, etc. to proper color formatting.
     *
     * @param text Text with legacy color codes
     * @return Parsed Adventure Component
     */
    private Component parseColorCodes(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /**
     * Applies custom skull texture to a SkullMeta.
     * Uses Paper's PlayerProfile API with properly encoded texture data.
     *
     * @param skullMeta The skull meta to modify
     * @param textureHash The texture hash from SkullOwner field (raw hash, not base64)
     */
    private void applySkullTexture(org.bukkit.inventory.meta.SkullMeta skullMeta, String textureHash) {
        try {
            // Build the texture JSON structure that Minecraft expects
            String textureJson = String.format(
                "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}",
                textureHash
            );

            // Base64 encode the JSON - this is what ProfileProperty expects
            String encodedTexture = java.util.Base64.getEncoder().encodeToString(
                textureJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            // Create profile with Paper API (no deprecation warnings)
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(textureHash.getBytes());
            com.destroystokyo.paper.profile.PlayerProfile profile =
                org.bukkit.Bukkit.createProfile(uuid, "CustomHead");

            // Set the properly encoded texture property
            com.destroystokyo.paper.profile.ProfileProperty property =
                new com.destroystokyo.paper.profile.ProfileProperty("textures", encodedTexture);
            profile.setProperty(property);

            // Apply to skull meta
            skullMeta.setPlayerProfile(profile);
        } catch (Exception e) {
            // Silently fail - skull will use default texture
        }
    }

    /**
     * Applies banner patterns to a BannerMeta.
     *
     * @param bannerMeta The banner meta to modify
     * @param patterns The list of banner patterns to apply
     */
    private void applyBannerPatterns(org.bukkit.inventory.meta.BannerMeta bannerMeta,
                                     List<WarpIcon.BannerPatternData> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return;
        }

        for (WarpIcon.BannerPatternData patternData : patterns) {
            org.bukkit.block.banner.Pattern pattern =
                new org.bukkit.block.banner.Pattern(patternData.color(), patternData.pattern());
            bannerMeta.addPattern(pattern);
        }
    }

    /**
     * Gets the warp at the specified inventory slot.
     *
     * @param slot The inventory slot (0-53)
     * @return The warp icon at that slot, or null if it's a UI button or empty
     */
    public WarpIcon getWarpAt(int slot) {
        // Only slots 0-44 contain warps
        if (slot < 0 || slot >= WARPS_PER_PAGE) {
            return null;
        }

        // Calculate index in displayedWarps list
        int index = (currentPage * WARPS_PER_PAGE) + slot;

        if (index >= displayedWarps.size()) {
            return null;
        }

        return displayedWarps.get(index);
    }

    /**
     * Calculates the total number of pages.
     */
    public int getTotalPages() {
        return (int) Math.ceil(displayedWarps.size() / (double) WARPS_PER_PAGE);
    }

    /**
     * Checks if there's a next page available.
     */
    public boolean hasNextPage() {
        return currentPage < getTotalPages() - 1;
    }

    /**
     * Checks if there's a previous page available.
     */
    public boolean hasPrevPage() {
        return currentPage > 0;
    }

    // Getters

    public Player getViewer() {
        return viewer;
    }

    public List<WarpIcon> getDisplayedWarps() {
        return new ArrayList<>(displayedWarps);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public Location getPlayerLocation() {
        return playerLocation;
    }

    public Map<String, Double> getDistanceMap() {
        return new HashMap<>(distanceMap);
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
