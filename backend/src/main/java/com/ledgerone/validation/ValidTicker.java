package com.ledgerone.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = TickerSymbolValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTicker {
    String message() default "Ticker must be 1 to 10 letters, numbers, dots, or hyphens";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
