package com.localcart.service;

import com.localcart.entity.*;
import com.localcart.repository.KhathaAccountRepository;
import com.localcart.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KhathaReminderServiceTest {
    @Test
    void sendsUpcomingAndOverdueNotificationsOnlyOncePerAccountAndDueDate() {
        KhathaAccountRepository accounts=mock(KhathaAccountRepository.class);
        NotificationRepository notifications=mock(NotificationRepository.class);
        KhathaReminderService service=new KhathaReminderService(accounts,notifications);

        LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));
        KhathaAccount upcoming=account(1L,today.plusDays(2));
        KhathaAccount overdue=account(2L,today.minusDays(1));

        when(accounts.findByOutstandingBalanceGreaterThanAndDueDateBetween(
                new BigDecimal("0.00"),today,today.plusDays(3))).thenReturn(List.of(upcoming));
        when(accounts.findByOutstandingBalanceGreaterThanAndDueDateBefore(
                new BigDecimal("0.00"),today)).thenReturn(List.of(overdue));
        when(notifications.existsBySourceKey(anyString())).thenReturn(false);

        service.sendDueDateReminders();

        ArgumentCaptor<Notification> captor=ArgumentCaptor.forClass(Notification.class);
        verify(notifications,times(2)).save(captor.capture());
        List<Notification> saved=captor.getAllValues();
        assertEquals("Khatha payment due soon",saved.get(0).getTitle());
        assertEquals("khatha:due:1:"+upcoming.getDueDate(),saved.get(0).getSourceKey());
        assertEquals("Khatha payment overdue",saved.get(1).getTitle());
        assertEquals("khatha:overdue:2:"+overdue.getDueDate(),saved.get(1).getSourceKey());
        assertEquals(NotificationType.PAYMENT,saved.get(0).getType());
    }

    @Test
    void doesNotCreateDuplicateReminderForAnExistingSourceKey() {
        KhathaAccountRepository accounts=mock(KhathaAccountRepository.class);
        NotificationRepository notifications=mock(NotificationRepository.class);
        KhathaReminderService service=new KhathaReminderService(accounts,notifications);

        LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));
        KhathaAccount upcoming=account(3L,today.plusDays(1));
        when(accounts.findByOutstandingBalanceGreaterThanAndDueDateBetween(
                new BigDecimal("0.00"),today,today.plusDays(3))).thenReturn(List.of(upcoming));
        when(accounts.findByOutstandingBalanceGreaterThanAndDueDateBefore(
                new BigDecimal("0.00"),today)).thenReturn(List.of());
        when(notifications.existsBySourceKey("khatha:due:3:"+upcoming.getDueDate())).thenReturn(true);

        service.sendDueDateReminders();

        verify(notifications,never()).save(any(Notification.class));
    }

    private KhathaAccount account(Long id,LocalDate dueDate) {
        User customer=new User();
        setId(customer,id+100);
        customer.setName("Khatha Customer");
        customer.setEmail("customer"+id+"@example.test");
        customer.setRole(Role.CUSTOMER);
        customer.setActive(true);
        Shop shop=new Shop();
        setId(shop,id+200);
        shop.setName("Neighbourhood Store");
        shop.setActive(true);
        KhathaAccount account=new KhathaAccount();
        setId(account,id);
        account.setCustomer(customer);
        account.setShop(shop);
        account.setOutstandingBalance(new BigDecimal("125.00"));
        account.setCreditLimit(new BigDecimal("1000.00"));
        account.setDueDate(dueDate);
        return account;
    }

    private void setId(Object target,Long id) {
        try {
            var field=target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target,id);
        } catch(ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
