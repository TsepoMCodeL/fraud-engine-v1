package fraudengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request payload to add an account, IP, or device to the fraud blacklist")
public record BlacklistEntityRequestDto(
        @Schema(description = "Target entity value to blacklist (account ID, IP address, or device ID)", example = "ACC-MALICIOUS-99")
        @NotBlank(message = "value cannot be blank")
        @Size(max = 255, message = "value must be at most 255 characters")
        String value,

        @Schema(description = "Entity classification type", example = "ACCOUNT")
        @NotNull(message = "type must be ACCOUNT, IP, or DEVICE")
        BlacklistType type,

        @Schema(description = "Optional administrative justification for blacklist entry", example = "Reported stolen card activity")
        @Size(max = 512, message = "reason must be at most 512 characters")
        @Nullable String reason
) {
    public enum BlacklistType {
        ACCOUNT,
        IP,
        DEVICE
    }
}
