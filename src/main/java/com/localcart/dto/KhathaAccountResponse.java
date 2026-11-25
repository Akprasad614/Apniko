package com.localcart.dto;
import com.localcart.entity.*;
import java.math.BigDecimal;
import java.time.*;
public record KhathaAccountResponse(Long id,Long shopId,String shopName,Long customerId,String customerName,
 BigDecimal creditLimit,BigDecimal outstandingBalance,BigDecimal availableCredit,String status,int repaymentDays,
 LocalDate dueDate,boolean overdue,int termsVersion,int acceptedTermsVersion,LocalDateTime acceptedAt,
 LocalDateTime createdAt,LocalDateTime updatedAt) {
 public static KhathaAccountResponse from(KhathaAccount a) {
  boolean overdue=a.getOutstandingBalance().signum()>0 && a.getDueDate()!=null && a.getDueDate().isBefore(LocalDate.now());
  BigDecimal available=a.getCreditLimit().subtract(a.getOutstandingBalance()).max(BigDecimal.ZERO);
  return new KhathaAccountResponse(a.getId(),a.getShop().getId(),a.getShop().getName(),
   a.getCustomer().getId(),a.getCustomer().getName(),a.getCreditLimit(),a.getOutstandingBalance(),
   available,overdue?"OVERDUE":a.getStatus().name(),a.getRepaymentDays(),a.getDueDate(),overdue,
   a.getTermsVersion(),a.getAcceptedTermsVersion(),a.getAcceptedAt(),a.getCreatedAt(),a.getUpdatedAt());
 }
}
