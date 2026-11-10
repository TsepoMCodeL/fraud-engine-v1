package fraudengine.api.dto;

import fraudengine.domain.GeoLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Geographic coordinates of transaction origination")
public record LocationDto(
        @Schema(description = "Latitude in degrees [-90.0, 90.0]", example = "40.7128")
        @DecimalMin(value = "-90.0", message = "latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "latitude must be <= 90.0")
        double latitude,

        @Schema(description = "Longitude in degrees [-180.0, 180.0]", example = "-74.0060")
        @DecimalMin(value = "-180.0", message = "longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "longitude must be <= 180.0")
        double longitude,

        @Schema(description = "Two or three-letter ISO country code", example = "US")
        @NotBlank(message = "countryCode is required")
        @Size(min = 2, max = 3, message = "countryCode must be 2 or 3 ISO characters")
        String countryCode
) {
    public GeoLocation toDomain() {
        return new GeoLocation(latitude, longitude, countryCode);
    }
}
