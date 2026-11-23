package com.localcart.dto;

import java.util.List;

public record ProductImportBatchResponse(
        int received,
        int inserted,
        int updated,
        int unchanged,
        int rejected,
        List<String> errors
) {
}
