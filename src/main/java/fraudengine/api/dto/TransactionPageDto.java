package fraudengine.api.dto;

import fraudengine.domain.Transaction;

import java.util.List;

public record TransactionPageDto(
        List<Transaction> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
