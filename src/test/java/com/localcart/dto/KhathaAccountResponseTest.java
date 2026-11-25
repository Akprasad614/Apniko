package com.localcart.dto;

import com.localcart.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KhathaAccountResponseTest {
    @Test
    void availableCreditIncludesPositiveCarriedCreditBalance() {
        User customer=new User();
        setId(customer,10L);
        customer.setName("Khatha Customer");
        customer.setEmail("customer@example.test");
        customer.setRole(Role.CUSTOMER);
        customer.setActive(true);

        User owner=new User();
        setId(owner,11L);
        owner.setName("Shop Owner");
        owner.setEmail("owner@example.test");
        owner.setRole(Role.SHOPKEEPER);
        owner.setActive(true);

        Shop shop=new Shop();
        setId(shop,3L);
        shop.setName("Neighbourhood Store");
        shop.setOwner(owner);
        shop.setActive(true);

        KhathaAccount account=new KhathaAccount();
        account.setShop(shop);
        account.setCustomer(customer);
        account.setCreditLimit(new BigDecimal("1000.00"));
        account.setOutstandingBalance(new BigDecimal("1000.00"));
        account.setCreditBalance(new BigDecimal("150.00"));
        account.setStatus(KhathaAccountStatus.ACTIVE);
        account.setRepaymentDays(15);
        account.setTermsVersion(1);
        account.setAcceptedTermsVersion(1);
        account.setDueDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5));

        KhathaAccountResponse response=KhathaAccountResponse.from(account);

        assertEquals(0,new BigDecimal("150.00").compareTo(response.availableCredit()));
    }

    private static void setId(Object target,Long id) {
        try {
            var field=target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target,id);
        } catch(ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
