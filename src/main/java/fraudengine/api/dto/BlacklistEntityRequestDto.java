package fraudengine.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BlacklistEntityRequestDto(
        @NotBlank(message = "value cannot be blank")
        @Size(max = 255, message = "value must be at most 255 characters")
        String value,

        @NotNull(message = "type must be ACCOUNT, IP, or DEVICE")
        BlacklistType type,

        @Size(max = 512, message = "reason must be at most 512 characters")
        String reason
) {
    public enum BlacklistType {
        ACCOUNT,
        IP,
        DEVICE
    }
}
