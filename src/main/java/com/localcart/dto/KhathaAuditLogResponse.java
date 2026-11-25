package com.localcart.dto;
import com.localcart.entity.KhathaAuditLog;
import java.time.LocalDateTime;
public record KhathaAuditLogResponse(Long id,Long accountId,String action,String reason,
 Long actorUserId,String actorName,String actorEmail,String actorRole,LocalDateTime createdAt) {
 public static KhathaAuditLogResponse from(KhathaAuditLog a) {
  return new KhathaAuditLogResponse(a.getId(),a.getAccountId(),a.getAction(),a.getReason(),
   a.getActorUserId(),a.getActorName(),a.getActorEmail(),a.getActorRole(),a.getCreatedAt());
 }
}
