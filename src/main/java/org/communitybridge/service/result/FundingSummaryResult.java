package org.communitybridge.service.result;

import java.util.List;

public record FundingSummaryResult(List<FundSummary> funds, FundingTotals totals) {
}
