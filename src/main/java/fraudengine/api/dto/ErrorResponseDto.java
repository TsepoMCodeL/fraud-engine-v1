package fraudengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Standard API error response payload")
public record ErrorResponseDto(
        @Schema(description = "Timestamp when error occurred", example = "2026-11-05T08:15:30Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "HTTP reason phrase", example = "Bad Request")
        String error,

        @Schema(description = "Error summary message", example = "Validation failed for request payload")
        String message,

        @Schema(description = "Specific validation or error detail messages", example = "[\"amount must be strictly positive\"]")
        List<String> details,

        @Schema(description = "Request URI path", example = "/api/v1/fraud/evaluate")
        String path
) {
    public ErrorResponseDto {
        details = List.copyOf(details);
    }

    public static ErrorResponseDto of(int status, String error, String message, List<String> details, String path) {
        return new ErrorResponseDto(Instant.now(), status, error, message, details, path);
    }
}
