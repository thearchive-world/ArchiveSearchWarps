package archive.searchwarps;

import archive.searchwarps.data.WarpDataLoader;
import archive.searchwarps.gui.GuiManager;
import archive.searchwarps.listeners.InventoryClickListener;
import archive.searchwarps.listeners.PrepareAnvilListener;
import archive.searchwarps.search.WarpSearchEngine;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.codingair.warpsystem.api.TeleportService;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Main plugin class for ArchiveSearchWarps.
 * Provides GUI-based warp search and browsing using WarpSystem-API.
 */
public final class ArchiveSearchWarps extends JavaPlugin {

    // Core components
    private WarpDataLoader dataLoader;
    private WarpSearchEngine searchEngine;
    private GuiManager guiManager;

    // Configuration
    private String warpsystemDataFolder;
    private String actionIconsFile;

    @Override
    public void onEnable() {
        // Register translations
        registerTranslations();

        // Load and validate config
        saveDefaultConfig();
        loadConfig();

        // Initialize core components
        dataLoader = new WarpDataLoader(this, warpsystemDataFolder, actionIconsFile);
        searchEngine = new WarpSearchEngine();
        guiManager = new GuiManager(this, dataLoader, searchEngine);

        // Register event listeners
        PrepareAnvilListener prepareAnvilListener = new PrepareAnvilListener(this);
        getServer().getPluginManager().registerEvents(prepareAnvilListener, this);

        getServer().getPluginManager().registerEvents(
            new InventoryClickListener(this, guiManager, prepareAnvilListener),
            this
        );

        // Load warp data asynchronously
        getLogger().info("Loading warp data from ActionIcons.yml...");
        getServer().getAsyncScheduler().runNow(this, task -> {
            dataLoader.load();

            // Log completion on main thread for visibility
            getServer().getGlobalRegionScheduler().run(this, schedTask -> {
                getLogger().info("Loaded " + dataLoader.getWarpCount() + " warps from ActionIcons.yml");
            });
        });

        // Register /searchwarps command using Brigadier
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                Commands.literal("searchwarps")
                    .requires(ctx -> ctx.getSender().hasPermission("warpsystem.use.simplewarps"))
                    .executes(ctx -> {
                        // Only players can use this command
                        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                            ctx.getSource().getSender().sendMessage(
                                Component.translatable("archive.searchwarps.players_only")
                            );
                            return Command.SINGLE_SUCCESS;
                        }

                        // Check if WarpSystem is loaded
                        if (TeleportService.get() == null) {
                            player.sendMessage(
                                Component.translatable("archive.searchwarps.warpsystem_not_found")
                                    .color(NamedTextColor.RED)
                            );
                            return Command.SINGLE_SUCCESS;
                        }

                        // Check if data is loaded
                        if (dataLoader.getWarpCount() == 0) {
                            player.sendMessage(
                                Component.translatable("archive.searchwarps.data_load_failed")
                                    .color(NamedTextColor.RED)
                            );
                            return Command.SINGLE_SUCCESS;
                        }

                        // Log command usage
                        getLogger().info(player.getName() + " opened warp browser");

                        // Open main browser (sorted alphabetically)
                        guiManager.openMainBrowser(player);

                        return Command.SINGLE_SUCCESS;
                    })
                    .then(
                        Commands.argument("query", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                // Only players can use this command
                                if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                                    ctx.getSource().getSender().sendMessage(
                                        Component.translatable("archive.searchwarps.players_only")
                                    );
                                    return Command.SINGLE_SUCCESS;
                                }

                                // Check if WarpSystem is loaded
                                if (TeleportService.get() == null) {
                                    player.sendMessage(
                                        Component.translatable("archive.searchwarps.warpsystem_not_found")
                                            .color(NamedTextColor.RED)
                                    );
                                    return Command.SINGLE_SUCCESS;
                                }

                                // Check if data is loaded
                                if (dataLoader.getWarpCount() == 0) {
                                    player.sendMessage(
                                        Component.translatable("archive.searchwarps.data_load_failed")
                                            .color(NamedTextColor.RED)
                                    );
                                    return Command.SINGLE_SUCCESS;
                                }

                                // Get search query (greedyString captures all remaining text, including spaces)
                                String query = ctx.getArgument("query", String.class);

                                // Open search results
                                guiManager.openSearchResults(player, query);

                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .then(
                        Commands.literal("reload")
                            .requires(ctx -> ctx.getSender().hasPermission("warpsystem.admin"))
                            .executes(ctx -> {
                                var sender = ctx.getSource().getSender();

                                // Send feedback
                                sender.sendMessage(
                                    Component.translatable("archive.searchwarps.reloading")
                                        .color(NamedTextColor.YELLOW)
                                );

                                // Reload config
                                reloadConfig();
                                loadConfig();

                                // Reload warp data asynchronously
                                getServer().getAsyncScheduler().runNow(this, task -> {
                                    dataLoader.reload();

                                    // Send completion message on main thread
                                    getServer().getGlobalRegionScheduler().run(this, schedTask -> {
                                        sender.sendMessage(
                                            Component.translatable("archive.searchwarps.reload_complete",
                                                Component.text(dataLoader.getWarpCount())
                                            ).color(NamedTextColor.GREEN)
                                        );
                                        getLogger().info(sender.getName() + " reloaded warp data (" + dataLoader.getWarpCount() + " warps)");
                                    });
                                });

                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .build(),
                "Open the warp browser GUI",
                List.of("sw")
            );
        });

        getLogger().info("ArchiveSearchWarps enabled successfully");
    }

    /**
     * Registers translation bundles for internationalization.
     */
    private void registerTranslations() {
        var translationRegistry = TranslationStore.messageFormat(Key.key("archive.searchwarps"));
        ResourceBundle bundle = ResourceBundle.getBundle(
            "archive.searchwarps.Bundle",
            Locale.US,
            UTF8ResourceBundleControl.utf8ResourceBundleControl()
        );
        translationRegistry.registerAll(Locale.US, bundle, true);
        GlobalTranslator.translator().addSource(translationRegistry);
    }

    /**
     * Loads configuration values and validates them.
     */
    private void loadConfig() {
        warpsystemDataFolder = getConfig().getString("warpsystem_data_folder", "plugins/WarpSystem");
        actionIconsFile = getConfig().getString("actionicons_file", "ActionIcons.yml");

        // Validate config values
        if (warpsystemDataFolder == null || warpsystemDataFolder.isEmpty()) {
            getLogger().warning("warpsystem_data_folder is empty. Using default: plugins/WarpSystem");
            warpsystemDataFolder = "plugins/WarpSystem";
        }

        if (actionIconsFile == null || actionIconsFile.isEmpty()) {
            getLogger().warning("actionicons_file is empty. Using default: ActionIcons.yml");
            actionIconsFile = "ActionIcons.yml";
        }

        getLogger().info("Loaded config: warpsystem_data_folder=" + warpsystemDataFolder +
                        ", actionicons_file=" + actionIconsFile);
    }

    @Override
    public void onDisable() {
        getLogger().info("ArchiveSearchWarps disabled successfully");
    }

    /**
     * Gets the GUI manager instance.
     * @return The GUI manager
     */
    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Gets the warp data loader instance.
     * @return The data loader
     */
    public WarpDataLoader getDataLoader() {
        return dataLoader;
    }
}
