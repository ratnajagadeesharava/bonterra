package org.communitybridge.service.result;

import java.util.List;

/** Ordered by id. */
public record ListFundsResult(List<FundSummary> funds) {
}
