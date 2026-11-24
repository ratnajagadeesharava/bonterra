package org.communitybridge.service.result;

import org.communitybridge.domain.Outcome;

public record RejectRequestResult(Outcome outcome, RequestView request) {
}
