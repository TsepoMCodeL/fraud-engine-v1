package fraudengine.api.dto;

import fraudengine.domain.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of account transactions")
public record TransactionPageDto(
        @Schema(description = "Page content containing transaction records")
        List<Transaction> content,

        @Schema(description = "Current page index (zero-based)", example = "0")
        int page,

        @Schema(description = "Page size requested", example = "20")
        int size,

        @Schema(description = "Total number of matching transactions across all pages", example = "1")
        long totalElements,

        @Schema(description = "Total page count", example = "1")
        int totalPages
) {
    public TransactionPageDto {
        content = List.copyOf(content);
    }
}
