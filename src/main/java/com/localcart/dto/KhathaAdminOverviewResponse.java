package com.localcart.dto;
import java.math.BigDecimal;
public record KhathaAdminOverviewResponse(long totalAccounts,long activeAccounts,long pausedAccounts,
 long pendingRepayments,long overdueAccounts,BigDecimal totalOutstanding) {}
