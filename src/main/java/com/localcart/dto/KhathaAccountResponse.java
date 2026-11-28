package com.localcart.dto;
import com.localcart.entity.*;
import java.math.BigDecimal;
import java.time.*;
public record KhathaAccountResponse(Long id,Long shopId,String shopName,Long customerId,String customerName,
 BigDecimal creditLimit,BigDecimal outstandingBalance,BigDecimal availableCredit,BigDecimal creditBalance,String status,int repaymentDays,
 LocalDate dueDate,boolean overdue,int termsVersion,int acceptedTermsVersion,LocalDateTime acceptedAt,
 LocalDateTime createdAt,LocalDateTime updatedAt) {
 public static KhathaAccountResponse from(KhathaAccount a) {
  boolean overdue=a.getOutstandingBalance().signum()>0 && a.getDueDate()!=null && a.getDueDate().isBefore(LocalDate.now(ZoneId.of("Asia/Kolkata")));
  BigDecimal available=a.getCreditLimit().subtract(a.getOutstandingBalance()).max(BigDecimal.ZERO)
   .add(a.getCreditBalance()==null?BigDecimal.ZERO:a.getCreditBalance().max(BigDecimal.ZERO));
  String displayStatus=overdue && a.getStatus()==KhathaAccountStatus.ACTIVE?"OVERDUE":a.getStatus().name();
  return new KhathaAccountResponse(a.getId(),a.getShop().getId(),a.getShop().getName(),
   a.getCustomer().getId(),a.getCustomer().getName(),a.getCreditLimit(),a.getOutstandingBalance(),
   available,a.getCreditBalance()==null?BigDecimal.ZERO:a.getCreditBalance(),displayStatus,a.getRepaymentDays(),a.getDueDate(),overdue,
   a.getTermsVersion(),a.getAcceptedTermsVersion(),a.getAcceptedAt(),a.getCreatedAt(),a.getUpdatedAt());
 }
}
