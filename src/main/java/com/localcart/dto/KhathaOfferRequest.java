package com.localcart.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record KhathaOfferRequest(@NotNull Long shopId,@NotNull Long customerId,
 @NotNull @DecimalMin("0.01") BigDecimal creditLimit,@Min(1) @Max(90) int repaymentDays) {}
