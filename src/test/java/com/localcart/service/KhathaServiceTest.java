package com.localcart.service;

import com.localcart.entity.*;
import com.localcart.dto.KhathaAdjustmentRequest;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class KhathaServiceTest {
    private KhathaAccountRepository accounts;
    private KhathaLedgerEntryRepository ledger;
    private KhathaBalanceAllocationRepository allocations;
    private KhathaRepaymentRepository repayments;
    private KhathaAuditLogRepository audit;
    private NotificationRepository notifications;
    private ShopRepository shops;
    private UserRepository users;
    private OrderRepository orders;
    private KhathaService service;

    @BeforeEach void setup() {
        accounts=mock(KhathaAccountRepository.class);
        ledger=mock(KhathaLedgerEntryRepository.class);
        allocations=mock(KhathaBalanceAllocationRepository.class);
        repayments=mock(KhathaRepaymentRepository.class);
        audit=mock(KhathaAuditLogRepository.class);
        notifications=mock(NotificationRepository.class);
        shops=mock(ShopRepository.class);
        users=mock(UserRepository.class);
        orders=mock(OrderRepository.class);
        service=new KhathaService(accounts,ledger,allocations,repayments,audit,notifications,shops,users,orders);
    }

    @Test void orderDebitUsesAvailableCreditAndStartsRepaymentClock() {
        User customer=user(10L,Role.CUSTOMER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"1000.00","0.00");
        Order order=order(77L,shop,customer,250.0);
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","77")).thenReturn(false);
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));

        service.chargeOrder(order,customer);

        assertEquals(new BigDecimal("250.00"),account.getOutstandingBalance());
        assertNotNull(account.getDueDate());
        verify(ledger).save(argThat(e->e.getEntryType()==KhathaLedgerEntryType.PURCHASE_DEBIT
                && e.getAmount().compareTo(new BigDecimal("250.00"))==0 && "77".equals(e.getSourceId())));
    }

    @Test void orderDebitRejectsPurchaseAboveAvailableCredit() {
        User customer=user(10L,Role.CUSTOMER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"300.00","250.00");
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","78")).thenReturn(false);

        assertThrows(BadRequestException.class,()->service.chargeOrder(order(78L,shop,customer,80.0),customer));
        assertEquals(new BigDecimal("250.00"),account.getOutstandingBalance());
        verify(ledger,never()).save(any());
    }

    @Test void retryingSameOrderDoesNotCreateDuplicateDebit() {
        User customer=user(10L,Role.CUSTOMER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"1000.00","100.00");
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","79")).thenReturn(true);

        service.chargeOrder(order(79L,shop,customer,250.0),customer);

        assertEquals(new BigDecimal("100.00"),account.getOutstandingBalance());
        verify(ledger,never()).save(any());
    }

    @Test void orderReversalRestoresOnlyTheOriginalDebitOnce() {
        User customer=user(10L,Role.CUSTOMER); User actor=user(11L,Role.SHOPKEEPER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"1000.00","350.00");
        KhathaLedgerEntry debit=new KhathaLedgerEntry();
        setId(debit,770L);
        debit.setAmount(new BigDecimal("250.00"));
        debit.setOutstandingChange(new BigDecimal("250.00"));
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER_REVERSAL","77")).thenReturn(false,true);
        when(ledger.findByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","77")).thenReturn(Optional.of(debit));
        when(allocations.findByDebitEntryIdOrderByCreatedAtAscIdAsc(770L)).thenReturn(java.util.List.of());
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));
        Order order=order(77L,shop,customer,250.0);

        service.reverseOrderDebit(order,actor);
        service.reverseOrderDebit(order,actor);

        assertEquals(new BigDecimal("100.00"),account.getOutstandingBalance());
        verify(ledger,times(1)).save(argThat(e->e.getEntryType()==KhathaLedgerEntryType.ORDER_REVERSAL));
    }

    @Test void priorCustomerCreditOffsetsFuturePurchaseBeforeNewDebtIsAdded() {
        User customer=user(10L,Role.CUSTOMER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"1000.00","50.00");
        account.setCreditBalance(new BigDecimal("100.00"));
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","88")).thenReturn(false);
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));

        service.chargeOrder(order(88L,shop,customer,150.0),customer);

        assertEquals(new BigDecimal("100.00"),account.getOutstandingBalance());
        assertEquals(new BigDecimal("0.00"),account.getCreditBalance());
        verify(ledger).save(argThat(e->e.getCreditBalanceChange().compareTo(new BigDecimal("-100.00"))==0
                && e.getOutstandingChange().compareTo(new BigDecimal("50.00"))==0));
    }

    @Test void cancellationAfterRepaymentCarriesOverpaidPartAsCustomerCredit() {
        User customer=user(10L,Role.CUSTOMER); User actor=user(11L,Role.SHOPKEEPER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"1000.00","100.00");
        KhathaLedgerEntry debit=new KhathaLedgerEntry();
        setId(debit,89L);
        debit.setAmount(new BigDecimal("300.00"));
        debit.setOutstandingChange(new BigDecimal("300.00"));
        debit.setCreditBalanceChange(BigDecimal.ZERO);
        KhathaLedgerEntry repaymentCredit=new KhathaLedgerEntry();
        setId(repaymentCredit,501L);
        KhathaBalanceAllocation allocation=new KhathaBalanceAllocation();
        allocation.setAccount(account); allocation.setDebitEntry(debit); allocation.setCreditEntry(repaymentCredit);
        allocation.setAmount(new BigDecimal("200.00")); allocation.setAllocationType(KhathaAllocationType.ALLOCATED);
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER_REVERSAL","89")).thenReturn(false);
        when(ledger.findByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","89")).thenReturn(Optional.of(debit));
        when(allocations.findByDebitEntryIdOrderByCreatedAtAscIdAsc(89L)).thenReturn(java.util.List.of(allocation));
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));
        when(allocations.save(any())).thenAnswer(call->call.getArgument(0));

        service.reverseOrderDebit(order(89L,shop,customer,300.0),actor);

        assertEquals(new BigDecimal("0.00"),account.getOutstandingBalance());
        assertEquals(new BigDecimal("200.00"),account.getCreditBalance());
        verify(allocations).save(argThat(saved->saved.getAllocationType()==KhathaAllocationType.RELEASED
                && saved.getAmount().compareTo(new BigDecimal("200.00"))==0));
        verify(ledger).save(argThat(e->e.getEntryType()==KhathaLedgerEntryType.ORDER_REVERSAL
                && e.getCreditBalanceAfter().compareTo(new BigDecimal("200.00"))==0));
    }

    @Test void cancellingOneOrderDoesNotEraseAnotherOrdersUnpaidBalance() {
        User customer=user(10L,Role.CUSTOMER); User actor=user(11L,Role.SHOPKEEPER); Shop shop=shop(3L);
        // Order A created 200 of debt, order B created another 100. A repayment of 150
        // was allocated FIFO to A, leaving A=50 and B=100 outstanding.
        KhathaAccount account=account(shop,customer,"1000.00","150.00");
        KhathaLedgerEntry orderDebit=new KhathaLedgerEntry();
        setId(orderDebit,90L); orderDebit.setAmount(new BigDecimal("200.00"));
        orderDebit.setOutstandingChange(new BigDecimal("200.00"));
        KhathaLedgerEntry repaymentCredit=new KhathaLedgerEntry(); setId(repaymentCredit,901L);
        KhathaBalanceAllocation allocation=new KhathaBalanceAllocation();
        allocation.setAccount(account); allocation.setDebitEntry(orderDebit); allocation.setCreditEntry(repaymentCredit);
        allocation.setAmount(new BigDecimal("150.00")); allocation.setAllocationType(KhathaAllocationType.ALLOCATED);
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER_REVERSAL","90")).thenReturn(false);
        when(ledger.findByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","90")).thenReturn(Optional.of(orderDebit));
        when(allocations.findByDebitEntryIdOrderByCreatedAtAscIdAsc(90L)).thenReturn(java.util.List.of(allocation));
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));
        when(allocations.save(any())).thenAnswer(call->call.getArgument(0));

        service.reverseOrderDebit(order(90L,shop,customer,200.0),actor);

        assertEquals(new BigDecimal("100.00"),account.getOutstandingBalance());
        assertEquals(new BigDecimal("150.00"),account.getCreditBalance());
    }

    @Test void confirmedRepaymentAllocatesFifoAcrossOriginalOrderDebits() {
        User shopkeeper=user(11L,Role.SHOPKEEPER);
        User customer=user(10L,Role.CUSTOMER);
        Shop shop=shop(3L); shop.setOwner(shopkeeper);
        KhathaAccount account=account(shop,customer,"1000.00","150.00");
        KhathaLedgerEntry firstDebit=new KhathaLedgerEntry();
        setId(firstDebit,601L); firstDebit.setEntryType(KhathaLedgerEntryType.PURCHASE_DEBIT);
        firstDebit.setOutstandingChange(new BigDecimal("50.00"));
        firstDebit.setSourceId("101");
        KhathaLedgerEntry secondDebit=new KhathaLedgerEntry();
        setId(secondDebit,602L); secondDebit.setEntryType(KhathaLedgerEntryType.PURCHASE_DEBIT);
        secondDebit.setOutstandingChange(new BigDecimal("100.00"));
        secondDebit.setSourceId("102");

        KhathaRepayment repayment=new KhathaRepayment();
        setId(repayment,44L); repayment.setAccount(account); repayment.setAmount(new BigDecimal("70.00"));
        repayment.setChannel(KhathaRepaymentChannel.CASH_AT_SHOP);
        repayment.setStatus(KhathaRepaymentStatus.PENDING);

        when(users.findByEmail(shopkeeper.getEmail())).thenReturn(Optional.of(shopkeeper));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(repayments.findByIdForUpdate(44L)).thenReturn(Optional.of(repayment));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"REPAYMENT","44")).thenReturn(false);
        when(ledger.findByAccountIdAndEntryTypeInOrderByCreatedAtAscIdAsc(
                1L,java.util.List.of(KhathaLedgerEntryType.PURCHASE_DEBIT,KhathaLedgerEntryType.ADJUSTMENT_DEBIT)))
                .thenReturn(java.util.List.of(firstDebit,secondDebit));
        when(allocations.findByDebitEntryIdOrderByCreatedAtAscIdAsc(601L)).thenReturn(java.util.List.of());
        when(allocations.findByDebitEntryIdOrderByCreatedAtAscIdAsc(602L)).thenReturn(java.util.List.of());
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(repayments.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->{KhathaLedgerEntry entry=call.getArgument(0);setId(entry,700L);return entry;});
        when(allocations.save(any())).thenAnswer(call->call.getArgument(0));

        service.confirmRepayment(1L,44L,shopkeeper.getEmail());

        assertEquals(new BigDecimal("80.00"),account.getOutstandingBalance());
        ArgumentCaptor<KhathaBalanceAllocation> captor=ArgumentCaptor.forClass(KhathaBalanceAllocation.class);
        verify(allocations,times(2)).save(captor.capture());
        assertEquals(new BigDecimal("50.00"),captor.getAllValues().get(0).getAmount());
        assertEquals(601L,captor.getAllValues().get(0).getDebitEntry().getId());
        assertEquals(new BigDecimal("20.00"),captor.getAllValues().get(1).getAmount());
        assertEquals(602L,captor.getAllValues().get(1).getDebitEntry().getId());
    }


    @Test void closedAccountCannotBeReopened() {
        User shopkeeper=user(11L,Role.SHOPKEEPER);
        User customer=user(10L,Role.CUSTOMER);
        Shop shop=shop(3L); shop.setOwner(shopkeeper);
        KhathaAccount account=account(shop,customer,"1000.00","0.00");
        account.setStatus(KhathaAccountStatus.CLOSED);
        when(users.findByEmail(shopkeeper.getEmail())).thenReturn(Optional.of(shopkeeper));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThrows(BadRequestException.class,
                ()->service.updateStatus(1L,KhathaAccountStatus.PAUSED,shopkeeper.getEmail()));
        verify(accounts,never()).save(any());
    }

    @Test void unacceptedOfferCannotBePausedBeforeCustomerAcceptance() {
        User shopkeeper=user(11L,Role.SHOPKEEPER);
        User customer=user(10L,Role.CUSTOMER);
        Shop shop=shop(3L); shop.setOwner(shopkeeper);
        KhathaAccount account=account(shop,customer,"1000.00","0.00");
        account.setStatus(KhathaAccountStatus.OFFERED);
        account.setAcceptedAt(null);
        account.setAcceptedTermsVersion(0);
        when(users.findByEmail(shopkeeper.getEmail())).thenReturn(Optional.of(shopkeeper));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThrows(BadRequestException.class,
                ()->service.updateStatus(1L,KhathaAccountStatus.PAUSED,shopkeeper.getEmail()));
        verify(accounts,never()).save(any());
    }

    @Test void debitAdjustmentCannotCreateDebtOnPausedAccount() {
        User shopkeeper=user(11L,Role.SHOPKEEPER);
        User customer=user(10L,Role.CUSTOMER);
        Shop shop=shop(3L); shop.setOwner(shopkeeper);
        KhathaAccount account=account(shop,customer,"1000.00","0.00");
        account.setStatus(KhathaAccountStatus.PAUSED);
        when(users.findByEmail(shopkeeper.getEmail())).thenReturn(Optional.of(shopkeeper));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        KhathaAdjustmentRequest request=new KhathaAdjustmentRequest(
                new BigDecimal("25.00"),KhathaAdjustmentType.DEBIT,"Agreed adjustment for returned goods");
        assertThrows(BadRequestException.class,
                ()->service.adjustBalance(1L,request,shopkeeper.getEmail()));
        verify(accounts,never()).save(any());
        verify(ledger,never()).save(any());
    }

    private User user(Long id,Role role) {
        User value=new User(); setId(value,id); value.setName("Test User"); value.setEmail("test@example.com");
        value.setRole(role); value.setActive(true); return value;
    }
    private Shop shop(Long id) { Shop value=new Shop(); setId(value,id); value.setActive(true); return value; }
    private KhathaAccount account(Shop shop,User customer,String limit,String balance) {
        KhathaAccount value=new KhathaAccount(); setId(value,1L); value.setShop(shop); value.setCustomer(customer);
        value.setCreditLimit(new BigDecimal(limit)); value.setOutstandingBalance(new BigDecimal(balance));
        value.setStatus(KhathaAccountStatus.ACTIVE); value.setRepaymentDays(15); value.setTermsVersion(1);
        value.setAcceptedTermsVersion(1); value.setAcceptedAt(LocalDateTime.now()); return value;
    }
    private Order order(Long id,Shop shop,User customer,double total) {
        Order value=new Order(); setId(value,id); value.setShop(shop); value.setCustomer(customer); value.setTotalAmount(total); return value;
    }
    private void setId(Object target,Long id) {
        try { var field=target.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(target,id); }
        catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
}
