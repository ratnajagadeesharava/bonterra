package org.communitybridge.domain;

/**
 * An exact USD amount as a whole number of cents (NFR-01). No floating point anywhere.
 *
 * <p>Limits: a request amount is 1 to {@link #MAX_REQUEST_CENTS}; a fund allocation is at most
 * {@link #MAX_FUND_CENTS}. These keep every total far below Long.MAX_VALUE.
 *
 * <p>Parsing and formatting are implemented in step 3 (service layer); until then they throw,
 * so the step 2 tests compile and fail.
 */
public record Money(long cents) {

    public static final String CURRENCY = "USD";

    /** $1,000,000.00 */
    public static final long MAX_REQUEST_CENTS = 100_000_000L;

    /** $10,000,000.00 */
    public static final long MAX_FUND_CENTS = 1_000_000_000L;

    /** Format rule for an input amount string: no sign, no exponent, no leading zeros, at most 2 decimals. */
    public static final String AMOUNT_PATTERN = "^(0|[1-9]\\d{0,6})(\\.\\d{1,2})?$";

    public Money {
        if (cents < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + cents);
        }
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /**
     * Parses a request amount such as "300.50" into cents, by string parsing only.
     * Format must match {@link #AMOUNT_PATTERN}; value must be 1..{@link #MAX_REQUEST_CENTS} cents.
     *
     * @throws org.communitybridge.service.error.ServiceException VALIDATION_ERROR on field "amount"
     */
    public static Money parseRequestAmount(String amount) {
        throw new UnsupportedOperationException("Implemented in step 3");
    }

    /** "300.50" style text for people; always two decimals, no grouping, no currency sign. */
    public String toPlainString() {
        throw new UnsupportedOperationException("Implemented in step 3");
    }
}
