package com.localcart.dto;
import com.localcart.entity.KhathaAccountStatus;
import jakarta.validation.constraints.NotNull;
public record KhathaStatusUpdateRequest(@NotNull KhathaAccountStatus status) {}
