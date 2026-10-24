package fraudengine.api.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponseDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details,
        String path
) {
    public static ErrorResponseDto of(int status, String error, String message, List<String> details, String path) {
        return new ErrorResponseDto(Instant.now(), status, error, message, details, path);
    }
}
