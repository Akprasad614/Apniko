package com.localcart.dto;

public record ProductPhotoCandidateRetirementResponse(
        int retiredCandidates,
        int preservedPreviouslyApprovedPhotos,
        String message
) {}
