package org.communitybridge.service.input;

/**
 * All filters optional and combined with AND.
 *
 * @param status one of PENDING, APPROVED, REJECTED, PAID
 * @param limit  1..100, default 50
 * @param offset 0 or more, default 0
 */
public record ListRequestsInput(String status, String fundId, String participantId, Integer limit, Integer offset) {
}
