package archive.searchwarps.search;

import archive.searchwarps.data.WarpIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Free-text search across warp names, display names, lore, and destination IDs.
 * Uses case-insensitive substring matching with multi-term AND logic.
 * Query "Peter Mary" matches warps containing BOTH "peter" AND "mary".
 */
public class WarpSearchEngine {

    /**
     * Searches for warps matching the given query.
     * Multi-term queries (space-separated) use AND logic - all terms must match.
     * Searches across warp names, display names, all lore lines (with color codes stripped), and destination IDs.
     *
     * @param allIcons List of all warp icons to search
     * @param query Search query (case-insensitive, space-separated for multiple terms)
     * @return List of warps matching ALL search terms
     */
    public List<WarpIcon> search(List<WarpIcon> allIcons, String query) {
        // Normalize query (lowercase, trim)
        String normalizedQuery = query.toLowerCase().trim();

        // If empty query, return all warps
        if (normalizedQuery.isEmpty()) {
            return new ArrayList<>(allIcons);
        }

        // Filter warps where all query terms match
        return allIcons.stream()
            .filter(icon -> matches(icon, normalizedQuery))
            .collect(Collectors.toList());
    }

    /**
     * Checks if a warp icon matches the search query.
     * For multi-term queries, ALL terms must match (AND logic).
     * Each term can match in any searchable field:
     * - Warp name (case-insensitive)
     * - Display name (color codes stripped, case-insensitive)
     * - All lore lines (color codes stripped, case-insensitive)
     * - Destination ID (case-insensitive)
     *
     * @param icon The warp icon to check
     * @param normalizedQuery The normalized (lowercase, trimmed) query
     * @return true if ALL query terms match somewhere in the warp metadata
     */
    private boolean matches(WarpIcon icon, String normalizedQuery) {
        // Split query into individual terms (space-separated)
        String[] terms = normalizedQuery.split("\\s+");

        // All terms must match (AND logic)
        for (String term : terms) {
            if (!matchesTerm(icon, term)) {
                return false; // If any term doesn't match, exclude this icon
            }
        }

        return true; // All terms matched
    }

    /**
     * Checks if a single search term matches anywhere in the warp's metadata.
     * Searches across name, display name, lore lines, and destination ID.
     *
     * @param icon The warp icon to check
     * @param term The search term (already lowercase)
     * @return true if the term matches in any searchable field
     */
    private boolean matchesTerm(WarpIcon icon, String term) {
        // Search in name
        if (icon.name().toLowerCase().contains(term)) {
            return true;
        }

        // Search in display name (with color codes stripped)
        String cleanDisplayName = stripColorCodes(icon.displayName());
        if (cleanDisplayName.toLowerCase().contains(term)) {
            return true;
        }

        // Search in destination ID
        if (icon.destinationId().toLowerCase().contains(term)) {
            return true;
        }

        // Search in lore lines (with color codes stripped)
        for (String loreLine : icon.lore()) {
            String cleanLore = stripColorCodes(loreLine);
            if (cleanLore.toLowerCase().contains(term)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Strips Minecraft color codes from text.
     * Removes patterns like &f, &e, &0-9, &a-f, &k-o, &r.
     *
     * @param text Text with color codes
     * @return Text without color codes
     */
    private String stripColorCodes(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }
}
