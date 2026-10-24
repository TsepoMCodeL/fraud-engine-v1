package fraudengine.api.dto;

import java.util.List;

public record TransactionEvaluationPageDto(
        List<TransactionEvaluationResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
