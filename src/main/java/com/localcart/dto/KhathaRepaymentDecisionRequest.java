package com.localcart.dto;
import jakarta.validation.constraints.Size;
public record KhathaRepaymentDecisionRequest(@Size(max=500) String reason) {}
