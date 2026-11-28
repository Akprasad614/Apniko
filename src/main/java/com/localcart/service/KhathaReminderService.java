package com.localcart.service;

import com.localcart.entity.KhathaAccount;
import com.localcart.entity.Notification;
import com.localcart.entity.NotificationType;
import com.localcart.repository.KhathaAccountRepository;
import com.localcart.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class KhathaReminderService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final KhathaAccountRepository accounts;
    private final NotificationRepository notifications;

    public KhathaReminderService(
            KhathaAccountRepository accounts,
            NotificationRepository notifications
    ) {
        this.accounts = accounts;
        this.notifications = notifications;
    }

    /**
     * Sends one in-app reminder for each account and due date.
     * Re-running the job is safe because notification source keys are unique.
     * No payment is initiated or collected by this job.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendDueDateReminders() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        sendUpcomingReminders(today);
        sendOverdueReminders(today);
    }

    private void sendUpcomingReminders(LocalDate today) {
        List<KhathaAccount> upcoming = accounts
                .findByOutstandingBalanceGreaterThanAndDueDateBetween(
                        ZERO, today, today.plusDays(3)
                );
        for (KhathaAccount account : upcoming) {
            if (account.getCustomer() == null || account.getShop() == null) {
                continue;
            }
            String sourceKey = "khatha:due:" + account.getId() + ":" + account.getDueDate();
            if (notifications.existsBySourceKey(sourceKey)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setRecipientUserId(account.getCustomer().getId());
            notification.setTitle("Khatha payment due soon");
            notification.setMessage(
                    "Your " + account.getOutstandingBalance() + " INR Khatha balance at "
                            + account.getShop().getName() + " is due on " + account.getDueDate()
                            + ". Review the account in Apniko."
            );
            notification.setType(NotificationType.PAYMENT);
            notification.setSourceKey(sourceKey);
            notifications.save(notification);
        }
    }

    private void sendOverdueReminders(LocalDate today) {
        List<KhathaAccount> overdue = accounts
                .findByOutstandingBalanceGreaterThanAndDueDateBefore(ZERO, today);
        for (KhathaAccount account : overdue) {
            if (account.getCustomer() == null || account.getShop() == null) {
                continue;
            }
            String sourceKey = "khatha:overdue:" + account.getId() + ":" + account.getDueDate();
            if (notifications.existsBySourceKey(sourceKey)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setRecipientUserId(account.getCustomer().getId());
            notification.setTitle("Khatha payment overdue");
            notification.setMessage(
                    "Your " + account.getOutstandingBalance() + " INR Khatha balance at "
                            + account.getShop().getName() + " was due on " + account.getDueDate()
                            + ". Please review the account and contact the shop if you need help."
            );
            notification.setType(NotificationType.PAYMENT);
            notification.setSourceKey(sourceKey);
            notifications.save(notification);
        }
    }
}
