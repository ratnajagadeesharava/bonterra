package org.communitybridge.service.result;

import java.util.List;

/** total counts every match, ignoring limit and offset. Ordered by createdAt, then id. */
public record ListRequestsResult(List<RequestView> requests, int total) {
}
