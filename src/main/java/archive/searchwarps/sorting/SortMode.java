package archive.searchwarps.sorting;

/**
 * Represents the sorting mode for displaying warps in the browser GUI.
 */
public enum SortMode {
    /**
     * Sort warps alphabetically by name (A-Z, case-insensitive).
     */
    ALPHABETICAL,

    /**
     * Sort warps by Euclidean distance from the player's current location.
     * Closer warps appear first.
     */
    DISTANCE
}
