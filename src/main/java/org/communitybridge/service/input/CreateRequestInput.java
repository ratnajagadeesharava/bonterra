package org.communitybridge.service.input;

/**
 * @param category        one of RENT, UTILITIES, TRANSPORTATION, OTHER
 * @param purpose         trimmed, 1..500 chars, stored verbatim and never interpreted
 * @param amount          decimal string such as "300.50"; see Money.AMOUNT_PATTERN
 * @param clientRequestId optional, 1..64 chars of [A-Za-z0-9_-]
 */
public record CreateRequestInput(
        String participantId,
        String fundId,
        String category,
        String purpose,
        String amount,
        String clientRequestId) {
}
