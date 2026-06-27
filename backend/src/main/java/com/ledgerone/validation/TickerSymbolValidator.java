package com.ledgerone.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TickerSymbolValidator implements ConstraintValidator<ValidTicker, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && value.matches("[A-Za-z]{1,10}");
    }
}
