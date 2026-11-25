package com.localcart.entity;

import com.localcart.repository.KhathaLedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class KhathaLedgerEntryImmutabilityTest {

    @Test
    void jpaLifecycleCallbacksRejectLedgerUpdatesAndDeletes() throws Exception {
        assertLifecycleCallbackRejectsMutation("preventUpdate");
        assertLifecycleCallbackRejectsMutation("preventDelete");
    }

    @Test
    void repositoryDoesNotExposeJpaRepositoryMutationMethods() {
        assertFalse(JpaRepository.class.isAssignableFrom(KhathaLedgerEntryRepository.class));
        assertFalse(Arrays.stream(KhathaLedgerEntryRepository.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("delete")));
    }

    private void assertLifecycleCallbackRejectsMutation(String callbackName) throws Exception {
        Method callback = KhathaLedgerEntry.class.getDeclaredMethod(callbackName);
        callback.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> callback.invoke(new KhathaLedgerEntry())
        );

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("append-only"));
    }
}
