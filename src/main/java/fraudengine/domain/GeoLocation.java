package fraudengine.domain;

import java.util.Objects;

/**
 * Immutable geolocation coordinates with distance computation via Haversine formula.
 */
public record GeoLocation(double latitude, double longitude, String countryCode) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public GeoLocation {
        Objects.requireNonNull(countryCode, "countryCode cannot be null");
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude out of range [-90, 90]: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude out of range [-180, 180]: " + longitude);
        }
    }

    /**
     * Calculates great-circle distance in kilometers to another location.
     */
    public double distanceKmTo(GeoLocation other) {
        if (other == null) return 0.0;

        double lat1Rad = Math.toRadians(this.latitude);
        double lon1Rad = Math.toRadians(this.longitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double lon2Rad = Math.toRadians(other.longitude);

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}

