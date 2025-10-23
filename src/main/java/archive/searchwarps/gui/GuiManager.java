package archive.searchwarps.gui;

import archive.searchwarps.data.WarpDataLoader;
import archive.searchwarps.data.WarpIcon;
import archive.searchwarps.search.WarpSearchEngine;
import archive.searchwarps.sorting.DistanceSorter;
import archive.searchwarps.sorting.SortMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centralized GUI creation and management.
 * Orchestrates all GUI operations including opening browsers, search results, and pagination.
 */
public class GuiManager {
    private final Plugin plugin;
    private final WarpDataLoader dataLoader;
    private final WarpSearchEngine searchEngine;

    public GuiManager(Plugin plugin, WarpDataLoader dataLoader, WarpSearchEngine searchEngine) {
        this.plugin = plugin;
        this.dataLoader = dataLoader;
        this.searchEngine = searchEngine;
    }

    /**
     * Opens the main warp browser for a player.
     * Shows all warps sorted alphabetically by default.
     *
     * @param player The player to show the browser to
     */
    public void openMainBrowser(Player player) {
        // Capture player location for distance calculations
        Location playerLocation = player.getLocation();

        // Get all warps
        List<WarpIcon> warps = new ArrayList<>(dataLoader.getWarpIcons());

        // Sort alphabetically (default mode)
        warps.sort(Comparator.comparing(WarpIcon::name, String.CASE_INSENSITIVE_ORDER));

        // Create and open GUI with default alphabetical sort
        WarpBrowserGUI gui = new WarpBrowserGUI(player, warps, 0, SortMode.ALPHABETICAL, playerLocation);
        player.openInventory(gui.getInventory());
    }

    /**
     * Opens search results for a query.
     * Searches all warps and displays matching results.
     *
     * @param player The player to show results to
     * @param query The search query
     */
    public void openSearchResults(Player player, String query) {
        // Capture player location for distance calculations
        Location playerLocation = player.getLocation();

        // Search
        List<WarpIcon> results = searchEngine.search(
            dataLoader.getWarpIcons(),
            query
        );

        // Sort alphabetically (default mode for search)
        results.sort(Comparator.comparing(WarpIcon::name, String.CASE_INSENSITIVE_ORDER));

        // Create and open GUI with alphabetical sort
        WarpBrowserGUI gui = new WarpBrowserGUI(player, results, 0, SortMode.ALPHABETICAL, playerLocation);
        player.openInventory(gui.getInventory());

        // Log search
        plugin.getLogger().info(
            player.getName() + " searched for: \"" + query + "\" " +
            "(found " + results.size() + " results)"
        );
    }

    /**
     * Opens a specific page of warps.
     * Used for pagination navigation.
     *
     * @param player The player to show the page to
     * @param warps The list of warps to display
     * @param page The page number (0-based)
     * @param sortMode The current sort mode to maintain
     * @param playerLocation The player's location for distance calculations
     * @param distanceMap Map of warp destination IDs to distances (for distance sort mode)
     */
    public void openPage(Player player, List<WarpIcon> warps, int page, SortMode sortMode, Location playerLocation, java.util.Map<String, Double> distanceMap) {
        WarpBrowserGUI gui = new WarpBrowserGUI(player, warps, page, sortMode, playerLocation, distanceMap);
        player.openInventory(gui.getInventory());
    }

    /**
     * Toggles the sort mode for the current warp list and reopens the GUI.
     * Switches between alphabetical and distance sorting.
     *
     * @param player The player viewing the GUI
     * @param currentWarps The current list of displayed warps
     * @param currentMode The current sort mode
     * @param playerLocation The player's location for distance calculations
     */
    public void toggleSortMode(Player player, List<WarpIcon> currentWarps, SortMode currentMode, Location playerLocation) {
        // Determine new sort mode
        SortMode newMode = (currentMode == SortMode.ALPHABETICAL) ? SortMode.DISTANCE : SortMode.ALPHABETICAL;

        // Create a copy of the warp list to sort
        List<WarpIcon> warps = new ArrayList<>(currentWarps);

        // Sort based on new mode
        WarpBrowserGUI gui;
        if (newMode == SortMode.ALPHABETICAL) {
            warps.sort(Comparator.comparing(WarpIcon::name, String.CASE_INSENSITIVE_ORDER));
            gui = new WarpBrowserGUI(player, warps, 0, newMode, playerLocation);
        } else {
            // Sort with distance information
            var warpsWithDistance = DistanceSorter.sortWithDistance(warps, playerLocation);

            // Extract sorted warps and create distance map
            List<WarpIcon> sortedWarps = new ArrayList<>();
            java.util.Map<String, Double> distanceMap = new java.util.HashMap<>();
            for (var wwd : warpsWithDistance) {
                sortedWarps.add(wwd.warp());
                distanceMap.put(wwd.warp().destinationId(), wwd.distance());
            }

            gui = new WarpBrowserGUI(player, sortedWarps, 0, newMode, playerLocation, distanceMap);
        }

        // Reopen GUI with new sort order
        player.openInventory(gui.getInventory());

        // Log sort mode change
        plugin.getLogger().info(
            player.getName() + " changed sort mode to: " + newMode.name()
        );
    }
}
