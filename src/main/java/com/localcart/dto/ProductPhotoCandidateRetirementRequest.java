package com.localcart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductPhotoCandidateRetirementRequest(
        @NotBlank @Size(max = 500) String reason,
        boolean confirmRetirement
) {}
