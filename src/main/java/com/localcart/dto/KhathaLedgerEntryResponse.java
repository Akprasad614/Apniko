package com.localcart.dto;
import com.localcart.entity.KhathaLedgerEntry;
import java.math.BigDecimal;
import java.time.*;
public record KhathaLedgerEntryResponse(Long id,String entryType,BigDecimal amount,BigDecimal outstandingAfter,
 String sourceType,String sourceId,String description,String actorName,String actorRole,LocalDate dueDate,LocalDateTime createdAt) {
 public static KhathaLedgerEntryResponse from(KhathaLedgerEntry e) {
  return new KhathaLedgerEntryResponse(e.getId(),e.getEntryType().name(),e.getAmount(),e.getOutstandingAfter(),
   e.getSourceType(),e.getSourceId(),e.getDescription(),e.getActorName(),e.getActorRole(),e.getDueDate(),e.getCreatedAt());
 }
}
