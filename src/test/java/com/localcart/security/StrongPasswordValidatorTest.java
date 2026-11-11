package com.localcart.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void acceptsAComplexPasswordWithinBcryptsByteLimit() {
        assertTrue(validator.isValid("CorrectHorseBattery1!", null));
    }

    @Test
    void rejectsPasswordsMissingARequiredCharacterClass() {
        assertFalse(validator.isValid("alllowercase123!", null));
        assertFalse(validator.isValid("ALLUPPERCASE123!", null));
        assertFalse(validator.isValid("NoDigitsOrSymbol!", null));
        assertFalse(validator.isValid("NoSymbols1234", null));
    }

    @Test
    void rejectsPasswordsThatWouldBeTruncatedByBcrypt() {
        assertFalse(validator.isValid("A1!" + "é".repeat(35), null));
    }
}
