package org.communitybridge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.communitybridge.service.error.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exact money, without the service: string parsing to cents and formatting back. NFR-01. */
@DisplayName("9a · Money (parse and format)")
class MoneyTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} cents")
    @CsvSource({
        "0.01, 1",
        "0.10, 10",
        "0.1, 10",
        "1, 100",
        "10.5, 1050",
        "300.50, 30050",
        "999999.99, 99999999",
    })
    @DisplayName("NFR-01 · a well-formed amount string parses to exact cents by string arithmetic, never through floating point")
    void parses(String text, long cents) {
        assertThat(Money.parseRequestAmount(text).cents()).isEqualTo(cents);
    }

    @Test
    @DisplayName("NFR-01 · the request range is 1 cent to $1,000,000.00 inclusive")
    void requestRange() {
        assertThat(Money.parseRequestAmount("1000000.00").cents()).isEqualTo(Money.MAX_REQUEST_CENTS);
        assertThat(Money.parseRequestAmount("0.01").cents()).isEqualTo(1);
        assertThatThrownBy(() -> Money.parseRequestAmount("1000000.01")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> Money.parseRequestAmount("0.00")).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @NullSource
    @ValueSource(strings = {"0", "-5", "10.005", "007", "00.50", "abc", "1e3", "1E3", "", " 1", "1 ", "1.",
        ".5", "1,000", "$1", "+1", "1.2.3", "NaN", "Infinity", "0x10", "１２", "12345678"})
    @DisplayName("NFR-01 · malformed or out-of-range strings are VALIDATION_ERROR on 'amount' (no sign, exponent, leading zeros, separators, padding or non-ASCII digits)")
    void rejects(String text) {
        assertThatThrownBy(() -> Money.parseRequestAmount(text))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @ParameterizedTest(name = "{0} cents -> \"{1}\"")
    @CsvSource({
        "0, 0.00",
        "5, 0.05",
        "10, 0.10",
        "30050, 300.50",
        "970000, 9700.00",
        "100000000, 1000000.00",
        "1000000000, 10000000.00",
    })
    @DisplayName("NFR-01 · cents format as plain text with exactly two decimals, no grouping or currency sign")
    void formats(long cents, String text) {
        assertThat(Money.ofCents(cents).toPlainString()).isEqualTo(text);
    }

    @Test
    @DisplayName("NFR-01 · Money cannot be negative")
    void notNegative() {
        assertThatThrownBy(() -> Money.ofCents(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
