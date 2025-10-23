package archive.searchwarps.listeners;

import archive.searchwarps.data.WarpIcon;
import archive.searchwarps.gui.GuiManager;
import archive.searchwarps.gui.SearchGUI;
import archive.searchwarps.gui.WarpBrowserGUI;
import de.codingair.warpsystem.api.ITeleportManager;
import de.codingair.warpsystem.api.Options;
import de.codingair.warpsystem.api.TeleportService;
import de.codingair.warpsystem.api.destinations.utils.IDestination;
import de.codingair.warpsystem.api.destinations.utils.Result;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

/**
 * Handles clicks in WarpBrowserGUI and SearchGUI.
 * Processes warp teleportation, pagination, and search actions.
 */
public class InventoryClickListener implements Listener {
    private final Plugin plugin;
    private final GuiManager guiManager;
    private final PrepareAnvilListener prepareAnvilListener;

    public InventoryClickListener(Plugin plugin, GuiManager guiManager, PrepareAnvilListener prepareAnvilListener) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.prepareAnvilListener = prepareAnvilListener;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if it's SearchGUI (anvil) - use MenuType check instead of InventoryHolder
        if (event.getView().getMenuType() == org.bukkit.inventory.MenuType.ANVIL) {
            handleSearchGUIClick(event);
            return;
        }

        // Check if it's our WarpBrowserGUI
        if (!(event.getInventory().getHolder(false) instanceof WarpBrowserGUI gui)) {
            return;
        }

        // Cancel event to prevent item pickup
        event.setCancelled(true);

        // Must be a player
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getSlot();

        // Handle different slot types
        if (slot >= 0 && slot < 45) {
            // Slots 0-44: Warp items
            handleWarpClick(player, gui, slot);
        } else if (slot == WarpBrowserGUI.SLOT_PREV_PAGE) {
            // Slot 45: Previous page button
            handlePreviousPage(player, gui);
        } else if (slot == WarpBrowserGUI.SLOT_SEARCH) {
            // Slot 49: Search button
            handleSearchButton(player);
        } else if (slot == WarpBrowserGUI.SLOT_SORT_TOGGLE) {
            // Slot 51: Sort toggle button
            handleSortButton(player, gui);
        } else if (slot == WarpBrowserGUI.SLOT_NEXT_PAGE) {
            // Slot 53: Next page button
            handleNextPage(player, gui);
        }
        // Slots 46-48, 50, 52 are filler glass panes - do nothing
    }

    /**
     * Handles clicking a warp item (slots 0-44).
     * Teleports the player to the selected warp.
     */
    private void handleWarpClick(Player player, WarpBrowserGUI gui, int slot) {
        WarpIcon warp = gui.getWarpAt(slot);
        if (warp == null) {
            // Empty slot or invalid
            return;
        }

        // Close inventory
        player.closeInventory();

        // Teleport to warp
        teleportToWarp(player, warp);
    }

    /**
     * Teleports a player to a warp using WarpSystem-API.
     */
    private void teleportToWarp(Player player, WarpIcon warp) {
        ITeleportManager manager = TeleportService.get();

        if (manager == null) {
            // WarpSystem not loaded
            player.sendMessage(
                Component.translatable("archive.searchwarps.warpsystem_not_found")
                    .color(NamedTextColor.RED)
            );
            plugin.getLogger().warning("WarpSystem not loaded - cannot teleport " +
                player.getName() + " to " + warp.name());
            return;
        }

        // Build destination using SimpleWarp ID
        IDestination destination = manager.destinationBuilder()
            .simpleWarpDestination(warp.destinationId());

        // Create options (use defaults from WarpSystem)
        Options options = manager.options()
            .setDestination(destination)
            .setDisplayName(warp.name());

        // Execute teleport (async, returns CompletableFuture)
        CompletableFuture<Result> future = manager.teleport(player, options);

        // Log teleportation attempt
        plugin.getLogger().info(
            player.getName() + " teleporting to warp '" + warp.name() +
            "' (destination: " + warp.destinationId() + ")"
        );

        // Handle result for additional logging and error handling
        future.thenAccept(result -> {
            if (result == Result.SUCCESS) {
                plugin.getLogger().info(
                    player.getName() + " successfully teleported to " + warp.name()
                );
            } else {
                plugin.getLogger().info(
                    player.getName() + " teleport to " + warp.name() +
                    " failed with result: " + result
                );
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning(
                "Teleport exception for " + player.getName() + " to " + warp.name() +
                ": " + ex.getMessage()
            );
            return null;
        });
    }

    /**
     * Handles clicking the previous page button (slot 45).
     */
    private void handlePreviousPage(Player player, WarpBrowserGUI gui) {
        if (!gui.hasPrevPage()) {
            return;
        }

        int newPage = gui.getCurrentPage() - 1;
        guiManager.openPage(player, gui.getDisplayedWarps(), newPage, gui.getSortMode(), gui.getPlayerLocation(), gui.getDistanceMap());
    }

    /**
     * Handles clicking the search button (slot 49).
     */
    private void handleSearchButton(Player player) {
        player.closeInventory();

        // Try to open search GUI - if it fails, player will see an error message
        if (!SearchGUI.open(player)) {
            plugin.getLogger().warning("Failed to open search GUI for " + player.getName());
        }
    }

    /**
     * Handles clicking the sort toggle button (slot 51).
     * Toggles between alphabetical and distance sorting.
     */
    private void handleSortButton(Player player, WarpBrowserGUI gui) {
        guiManager.toggleSortMode(
            player,
            gui.getDisplayedWarps(),
            gui.getSortMode(),
            gui.getPlayerLocation()
        );
    }

    /**
     * Handles clicking the next page button (slot 53).
     */
    private void handleNextPage(Player player, WarpBrowserGUI gui) {
        if (!gui.hasNextPage()) {
            return;
        }

        int newPage = gui.getCurrentPage() + 1;
        guiManager.openPage(player, gui.getDisplayedWarps(), newPage, gui.getSortMode(), gui.getPlayerLocation(), gui.getDistanceMap());
    }

    /**
     * Handles clicks in the SearchGUI (anvil).
     * When player clicks the result slot (slot 2), execute the search.
     */
    private void handleSearchGUIClick(InventoryClickEvent event) {
        // Must be a player
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        // Only handle clicks on the result slot (slot 2 in anvil)
        if (slot != 2) {
            // Allow other clicks (input slots, inventory, etc.)
            return;
        }

        // Check if there's actually an item in the result slot
        // For anvils, also check the inventory directly
        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType().isAir()) {
            // Try checking the inventory directly (with null safety)
            if (event.getInventory() != null) {
                resultItem = event.getInventory().getItem(2);
            }
        }

        if (resultItem == null || resultItem.getType().isAir()) {
            return;
        }

        // Get the pending search query from PrepareAnvilListener
        String query = prepareAnvilListener.consumePendingSearch(player.getUniqueId());

        // Ignore null or empty queries
        if (query == null || query.isEmpty()) {
            // Cancel to prevent taking the item, but don't execute search
            event.setCancelled(true);
            return;
        }

        // Cancel the event to prevent taking the item
        event.setCancelled(true);

        // Close inventory
        player.closeInventory();

        final String finalQuery = query;

        // Open search results on next tick
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            guiManager.openSearchResults(player, finalQuery);
        });
    }
}
