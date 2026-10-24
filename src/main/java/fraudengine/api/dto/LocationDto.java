package fraudengine.api.dto;

import fraudengine.domain.GeoLocation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationDto(
        @DecimalMin(value = "-90.0", message = "latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "latitude must be <= 90.0")
        double latitude,

        @DecimalMin(value = "-180.0", message = "longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "longitude must be <= 180.0")
        double longitude,

        @NotBlank(message = "countryCode is required")
        @Size(min = 2, max = 3, message = "countryCode must be 2 or 3 ISO characters")
        String countryCode
) {
    public GeoLocation toDomain() {
        return new GeoLocation(latitude, longitude, countryCode);
    }
}

