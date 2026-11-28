package com.localcart.dto;
import com.localcart.entity.KhathaRepaymentChannel;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record KhathaRepaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
 @NotNull KhathaRepaymentChannel channel,@Size(max=120) String reference,@Size(max=500) String note,
 @NotBlank @Size(max=80) String idempotencyKey) {}
