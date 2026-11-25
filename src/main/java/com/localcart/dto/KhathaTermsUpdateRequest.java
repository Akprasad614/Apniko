package com.localcart.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record KhathaTermsUpdateRequest(@NotNull @DecimalMin("0.01") BigDecimal creditLimit,
 @Min(1) @Max(90) int repaymentDays,@NotBlank @Size(min=8,max=500) String reason) {}
