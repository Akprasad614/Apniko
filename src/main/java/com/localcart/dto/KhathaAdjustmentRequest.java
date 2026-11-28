package com.localcart.dto;
import com.localcart.entity.KhathaAdjustmentType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record KhathaAdjustmentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
 @NotNull KhathaAdjustmentType type,@NotBlank @Size(min=8,max=500) String reason) {}
