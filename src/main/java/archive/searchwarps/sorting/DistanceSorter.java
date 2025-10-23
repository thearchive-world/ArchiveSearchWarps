package archive.searchwarps.sorting;

import archive.searchwarps.data.WarpIcon;
import de.codingair.warpsystem.api.TeleportService;
import org.bukkit.Location;

import java.util.*;

/**
 * Sorts warps by Euclidean distance from a reference location.
 * Warps with unavailable locations are pushed to the end of the list.
 */
public class DistanceSorter {
    /**
     * Comparator for alphabetical sorting of WarpWithDistance objects (case-insensitive).
     */
    private static final Comparator<WarpWithDistance> WARP_WITH_DISTANCE_ALPHABETICAL_COMPARATOR =
        Comparator.comparing(w -> w.warp().name(), String.CASE_INSENSITIVE_ORDER);

    /**
     * Wrapper record to hold a WarpIcon with its calculated distance.
     */
    public record WarpWithDistance(WarpIcon warp, double distance) {}

    /**
     * Sorts a list of WarpIcons by distance and returns them with distance information.
     *
     * @param warps the list of warps to sort
     * @param playerLocation the player's current location for distance calculations
     * @return list of WarpWithDistance objects, sorted by distance
     */
    public static List<WarpWithDistance> sortWithDistance(List<WarpIcon> warps, Location playerLocation) {
        if (playerLocation == null) {
            // Fallback: return with MAX_VALUE distance
            List<WarpWithDistance> result = new ArrayList<>();
            for (WarpIcon warp : warps) {
                result.add(new WarpWithDistance(warp, Double.MAX_VALUE));
            }
            result.sort(WARP_WITH_DISTANCE_ALPHABETICAL_COMPARATOR);
            return result;
        }

        var teleportService = TeleportService.get();
        if (teleportService == null) {
            // Fallback: return with MAX_VALUE distance
            List<WarpWithDistance> result = new ArrayList<>();
            for (WarpIcon warp : warps) {
                result.add(new WarpWithDistance(warp, Double.MAX_VALUE));
            }
            result.sort(WARP_WITH_DISTANCE_ALPHABETICAL_COMPARATOR);
            return result;
        }

        // Calculate distances for all warps
        List<WarpWithDistance> warpsWithDistance = new ArrayList<>();
        for (WarpIcon warp : warps) {
            Location warpLocation = teleportService.simpleWarp(warp.destinationId());

            double distance;
            if (warpLocation == null) {
                distance = Double.MAX_VALUE;
            } else {
                distance = calculateDistance(playerLocation, warpLocation);
            }

            warpsWithDistance.add(new WarpWithDistance(warp, distance));
        }

        // Sort by distance
        warpsWithDistance.sort(Comparator.comparingDouble(WarpWithDistance::distance));

        return warpsWithDistance;
    }

    /**
     * Formats a distance value with appropriate units (blocks, K, M)
     * - Under 1K: whole number (e.g., "456 blocks")
     * - 1K-999K: rounded to thousands (e.g., "123K blocks")
     * - 1M+: with decimal only if not .0 (e.g., "30M blocks" or "2.6M blocks")
     *
     * @param distance the distance in blocks
     * @return formatted string like "23 blocks", "1K blocks", "30M blocks", or "2.6M blocks"
     */
    public static String formatDistance(double distance) {
        // Handle invalid distances
        if (distance == Double.MAX_VALUE || Double.isNaN(distance) || Double.isInfinite(distance)) {
            return "Unknown";
        }

        long rounded = Math.round(distance);

        if (rounded < 1000) {
            return rounded + " blocks";
        } else if (rounded < 1_000_000) {
            long thousands = Math.round(rounded / 1000.0);
            return thousands + "K blocks";
        } else {
            double millions = rounded / 1_000_000.0;
            // Only show decimal if not a whole number
            if (millions == Math.floor(millions)) {
                return (long) millions + "M blocks";
            } else {
                return String.format("%.1fM blocks", millions);
            }
        }
    }

    /**
     * Calculates 2D Euclidean distance between two locations, even if they're in different worlds.
     * This ignores Y-level (height) and only considers horizontal distance.
     * This is safe for cross-world distance calculation where coordinates are preserved.
     *
     * @param from the starting location
     * @param to the target location
     * @return the horizontal distance in blocks
     */
    private static double calculateDistance(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
