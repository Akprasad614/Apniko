package com.localcart.dto;
import com.localcart.entity.KhathaRepayment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record KhathaRepaymentResponse(Long id,Long accountId,BigDecimal amount,String channel,String status,
 String reference,String note,String actedByName,String decisionReason,LocalDateTime createdAt,LocalDateTime confirmedAt) {
 public static KhathaRepaymentResponse from(KhathaRepayment r) {
  return new KhathaRepaymentResponse(r.getId(),r.getAccount().getId(),r.getAmount(),r.getChannel().name(),
   r.getStatus().name(),r.getReference(),r.getNote(),r.getActedByName(),r.getDecisionReason(),r.getCreatedAt(),r.getConfirmedAt());
 }
}
