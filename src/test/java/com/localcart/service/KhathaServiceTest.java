package com.localcart.service;

import com.localcart.entity.*;
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

class KhathaServiceTest {
    private KhathaAccountRepository accounts;
    private KhathaLedgerEntryRepository ledger;
    private KhathaRepaymentRepository repayments;
    private KhathaAuditLogRepository audit;
    private ShopRepository shops;
    private UserRepository users;
    private OrderRepository orders;
    private KhathaService service;

    @BeforeEach void setup() {
        accounts=mock(KhathaAccountRepository.class);
        ledger=mock(KhathaLedgerEntryRepository.class);
        repayments=mock(KhathaRepaymentRepository.class);
        audit=mock(KhathaAuditLogRepository.class);
        shops=mock(ShopRepository.class);
        users=mock(UserRepository.class);
        orders=mock(OrderRepository.class);
        service=new KhathaService(accounts,ledger,repayments,audit,shops,users,orders);
    }

    @Test void orderDebitUsesAvailableCreditAndStartsRepaymentClock() {
        User customer=user(10L,Role.CUSTOMER); Shop shop=shop(3L);
        KhathaAccount account=account(shop,customer,"1000.00","100.00");
        Order order=order(77L,shop,customer,250.0);
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","77")).thenReturn(false);
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));

        service.chargeOrder(order,customer);

        assertEquals(new BigDecimal("350.00"),account.getOutstandingBalance());
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
        debit.setAmount(new BigDecimal("250.00"));
        when(accounts.findByShopAndCustomerForUpdate(3L,10L)).thenReturn(Optional.of(account));
        when(ledger.existsByAccountIdAndSourceTypeAndSourceId(1L,"ORDER_REVERSAL","77")).thenReturn(false);
        when(ledger.findByAccountIdAndSourceTypeAndSourceId(1L,"ORDER","77")).thenReturn(Optional.of(debit));
        when(accounts.save(any())).thenAnswer(call->call.getArgument(0));
        when(ledger.save(any())).thenAnswer(call->call.getArgument(0));
        Order order=order(77L,shop,customer,250.0);

        service.reverseOrderDebit(order,actor);
        service.reverseOrderDebit(order,actor);

        assertEquals(new BigDecimal("100.00"),account.getOutstandingBalance());
        verify(ledger,times(1)).save(argThat(e->e.getEntryType()==KhathaLedgerEntryType.ORDER_REVERSAL));
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
