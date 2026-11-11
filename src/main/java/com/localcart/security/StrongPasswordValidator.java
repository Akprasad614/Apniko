package com.localcart.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/** Enforces a registration policy without allowing BCrypt's 72-byte truncation. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MINIMUM_UTF8_BYTES = 12;
    private static final int BCRYPT_MAXIMUM_UTF8_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MINIMUM_UTF8_BYTES || byteLength > BCRYPT_MAXIMUM_UTF8_BYTES) {
            return false;
        }

        boolean hasUpperCase = password.codePoints().anyMatch(Character::isUpperCase);
        boolean hasLowerCase = password.codePoints().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        boolean hasSymbol = password.codePoints().anyMatch(codePoint ->
                !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint));

        return hasUpperCase && hasLowerCase && hasDigit && hasSymbol;
    }
}
