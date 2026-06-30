package com.ledgerone.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TickerSymbolValidatorTest {
    private final TickerSymbolValidator validator = new TickerSymbolValidator();

    @Test
    void acceptsCommonTickerFormatsUsedByUsEquities() {
        assertThat(validator.isValid("AAPL", null)).isTrue();
        assertThat(validator.isValid("BRK.B", null)).isTrue();
        assertThat(validator.isValid("BF-B", null)).isTrue();
    }

    @Test
    void rejectsBlankOrMalformedTickerSymbols() {
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid("123A", null)).isFalse();
        assertThat(validator.isValid("BRK/B", null)).isFalse();
    }
}
