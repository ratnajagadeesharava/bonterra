package org.communitybridge.service.error;

import java.util.Map;
import org.communitybridge.domain.ErrorCode;

/** Sent as structuredContent { error: ErrorBody } with isError: true. details may be empty, never null. */
public record ErrorBody(ErrorCode code, String message, Map<String, Object> details) {
}
