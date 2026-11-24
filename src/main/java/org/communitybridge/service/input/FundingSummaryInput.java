package org.communitybridge.service.input;

/** @param fundId optional; null means every fund of the caller's nonprofit */
public record FundingSummaryInput(String fundId) {
}
