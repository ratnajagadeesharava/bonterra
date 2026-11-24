package org.communitybridge.service.input;

/** @param reason optional, at most 500 chars */
public record RejectRequestInput(String requestId, String reason) {
}
