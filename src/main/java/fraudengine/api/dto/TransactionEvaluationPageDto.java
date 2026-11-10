package fraudengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of fraud evaluations")
public record TransactionEvaluationPageDto(
        @Schema(description = "Page content containing transaction and evaluation pairs")
        List<TransactionEvaluationResponseDto> content,

        @Schema(description = "Current page index (zero-based)", example = "0")
        int page,

        @Schema(description = "Page size requested", example = "20")
        int size,

        @Schema(description = "Total number of matching evaluations across all pages", example = "1")
        long totalElements,

        @Schema(description = "Total page count", example = "1")
        int totalPages
) {
    public TransactionEvaluationPageDto {
        content = List.copyOf(content);
    }
}
