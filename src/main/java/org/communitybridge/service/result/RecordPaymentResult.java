package org.communitybridge.service.result;

import org.communitybridge.domain.Outcome;

/** On ALREADY_APPLIED, payment is the original payment (same id). */
public record RecordPaymentResult(Outcome outcome, PaymentView payment, RequestView request, FundSummary fund) {
}
