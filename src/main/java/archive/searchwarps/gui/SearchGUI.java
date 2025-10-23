package archive.searchwarps.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import java.util.Locale;

/**
 * Anvil-based search interface for free-text warp search.
 * Player renames an item to input their search query.
 * Uses Paper's MenuType API for proper anvil functionality.
 */
public class SearchGUI {

    /**
     * Opens an anvil GUI for the player to input a search query.
     * Uses Paper's MenuType API which properly supports PrepareAnvilEvent.
     *
     * @param player The player to show the search GUI to
     * @return true if the GUI was opened successfully, false otherwise
     */
    public static boolean open(Player player) {
        try {
            // Use Paper's MenuType API to create a proper anvil
            InventoryView view = MenuType.ANVIL.builder()
                .title(Component.translatable("archive.searchwarps.search_title"))
                .build(player);

            // Put a paper item in the first slot with placeholder text (render for item display)
            ItemStack searchItem = ItemStack.of(Material.PAPER);
            searchItem.editMeta(meta -> {
                Component placeholderText = GlobalTranslator.render(
                    Component.translatable("archive.searchwarps.search_placeholder"),
                    Locale.US
                );
                meta.displayName(placeholderText);
            });
            view.getTopInventory().setItem(0, searchItem);

            // Open the view
            view.open();

            // Note: Input is captured via PrepareAnvilEvent and InventoryClickEvent
            return true;
        } catch (Exception e) {
            // MenuType API is experimental and may fail
            player.sendMessage(
                Component.translatable("archive.searchwarps.search_gui_failed")
                    .color(NamedTextColor.RED)
            );
            player.sendMessage(
                Component.translatable("archive.searchwarps.search_gui_error",
                    Component.text(e.getMessage())
                ).color(NamedTextColor.GRAY)
            );
            return false;
        }
    }
}
