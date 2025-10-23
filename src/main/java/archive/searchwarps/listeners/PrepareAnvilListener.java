package archive.searchwarps.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for PrepareAnvilEvent to capture the renamed text from SearchGUI.
 * Stores the text in a map for later retrieval when player clicks the result.
 * Cleans up via InventoryCloseListener and PlayerQuitEvent to prevent memory leaks.
 */
public class PrepareAnvilListener implements Listener {
    private final Plugin plugin;
    private final Map<UUID, String> pendingSearches = new HashMap<>();

    public PrepareAnvilListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        // Check if this is a search anvil
        // We identify it by MenuType and the paper item in slot 0
        if (event.getView().getMenuType() != org.bukkit.inventory.MenuType.ANVIL) {
            return;
        }

        // Check if slot 0 has a paper item (our search item marker)
        ItemStack firstItem = event.getInventory().getItem(0);
        if (firstItem == null || firstItem.getType() != Material.PAPER) {
            return;
        }

        // Get the player (viewer)
        if (event.getView().getPlayer() instanceof Player player) {
            // Get the rename text from the anvil view (modern API, not deprecated)
            String renameText = event.getView().getRenameText();

            // Store the text for this player
            if (renameText != null && !renameText.isEmpty()) {
                pendingSearches.put(player.getUniqueId(), renameText);

                // CRITICAL: Set a result item so the anvil allows clicking
                // Without this, clicking the result slot does NOTHING (as shown in logs)
                ItemStack resultItem = ItemStack.of(Material.PAPER);
                resultItem.editMeta(meta -> {
                    meta.displayName(Component.text(renameText));
                });
                event.setResult(resultItem);

                // Note: setRepairCost is deprecated in modern Paper
                // The anvil should work without XP cost when using MenuType API
            } else {
                // No valid search text - don't set a result
                event.setResult(null);
            }
        }
    }

    /**
     * Gets the pending search query for a player and removes it from the map.
     *
     * @param playerId The player's UUID
     * @return The pending search query, or null if none exists
     */
    public String consumePendingSearch(UUID playerId) {
        return pendingSearches.remove(playerId);
    }

    /**
     * Clears a pending search without consuming it.
     *
     * @param playerId The player's UUID
     */
    public void clearPendingSearch(UUID playerId) {
        pendingSearches.remove(playerId);
    }

    /**
     * Cleans up pending searches when a player disconnects.
     * Prevents memory leaks from abandoned search sessions.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingSearches.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Listens for anvil inventory closes to clean up abandoned search queries.
     * Prevents memory leaks when players close the search GUI without clicking.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if it's an anvil inventory
        if (event.getView().getMenuType() != org.bukkit.inventory.MenuType.ANVIL) {
            return;
        }

        // Check if slot 0 has a paper item (our search item marker)
        ItemStack firstItem = event.getInventory().getItem(0);
        if (firstItem == null || firstItem.getType() != Material.PAPER) {
            return;
        }

        // Clean up any pending search for this player
        if (event.getPlayer() instanceof Player player) {
            clearPendingSearch(player.getUniqueId());
        }
    }
}
