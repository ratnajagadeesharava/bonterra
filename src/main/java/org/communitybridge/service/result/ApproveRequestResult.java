package org.communitybridge.service.result;

import org.communitybridge.domain.Outcome;

/** fund shows the balances after this call. */
public record ApproveRequestResult(Outcome outcome, RequestView request, FundSummary fund) {
}
