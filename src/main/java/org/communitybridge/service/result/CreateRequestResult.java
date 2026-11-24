package org.communitybridge.service.result;

import org.communitybridge.domain.Outcome;

/** ALREADY_APPLIED only when the same clientRequestId and identical input were sent before (FR-12). */
public record CreateRequestResult(Outcome outcome, RequestView request) {
}
