package fraudengine.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BlacklistEntityRequestDto(
        @NotBlank(message = "value cannot be blank")
        String value,

        @NotNull(message = "type must be ACCOUNT, IP, or DEVICE")
        BlacklistType type,

        String reason
) {
    public enum BlacklistType {
        ACCOUNT,
        IP,
        DEVICE
    }
}
