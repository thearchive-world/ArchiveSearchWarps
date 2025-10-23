package archive.searchwarps.data;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;

import java.util.List;

/**
 * Represents a single warp icon from ActionIcons.yml.
 * Contains all data needed for display and teleportation.
 * Immutable record loaded once at startup and read many times during runtime.
 */
public record WarpIcon(
    String name,
    Material itemType,
    String displayName,
    List<String> lore,
    String destinationId,
    int performed,
    String page,
    String skullOwner,
    List<BannerPatternData> bannerPatterns
) {
    /**
     * Compact constructor ensures defensive copying for unmodifiable lists.
     * Called automatically for all record construction.
     */
    public WarpIcon {
        // Use List.copyOf() for true immutability - no need for defensive copies in getters
        lore = List.copyOf(lore);
        if (bannerPatterns != null) {
            bannerPatterns = List.copyOf(bannerPatterns);
        }
    }

    /**
     * Represents a single banner pattern layer (color + pattern type).
     */
    public record BannerPatternData(DyeColor color, PatternType pattern) {}

    @Override
    public String toString() {
        return "WarpIcon{name='" + name + "', destinationId='" + destinationId +
               "', performed=" + performed + "}";
    }
}
