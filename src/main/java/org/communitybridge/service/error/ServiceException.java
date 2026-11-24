package org.communitybridge.service.error;

import java.util.List;
import java.util.Map;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.domain.Role;

/**
 * The only way a service reports a business failure. The surrounding transaction always rolls
 * back, so state is unchanged (NFR-03). Messages never contain another nonprofit's data.
 * One factory per code fixes the details each code carries (spec: Error behavior).
 */
public class ServiceException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public ServiceException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public ErrorBody toBody() {
        return new ErrorBody(code, getMessage(), details);
    }

    public static ServiceException unauthenticated() {
        return new ServiceException(ErrorCode.UNAUTHENTICATED, "Unknown or inactive staff identity", Map.of());
    }

    public static ServiceException forbidden(Role requiredRole) {
        return new ServiceException(ErrorCode.FORBIDDEN, "This action requires role " + requiredRole,
                Map.of("requiredRole", requiredRole.name()));
    }

    public static ServiceException validation(List<FieldError> fields) {
        return new ServiceException(ErrorCode.VALIDATION_ERROR, "Invalid input",
                Map.of("fields", List.copyOf(fields)));
    }

    public static ServiceException notFound(String what) {
        return new ServiceException(ErrorCode.NOT_FOUND, what + " not found", Map.of());
    }

    public static ServiceException invalidTransition(RequestStatus current) {
        return new ServiceException(ErrorCode.INVALID_TRANSITION, "Not allowed while request is " + current,
                Map.of("currentStatus", current.name()));
    }

    public static ServiceException insufficientFunds(long availableCents, long requestedCents) {
        return new ServiceException(ErrorCode.INSUFFICIENT_FUNDS, "Fund does not have enough available",
                Map.of("availableCents", availableCents, "requestedCents", requestedCents));
    }

    public static ServiceException fundInactive() {
        return new ServiceException(ErrorCode.FUND_INACTIVE, "Fund is closed", Map.of());
    }

    public static ServiceException idempotencyConflict(String existingRequestId) {
        return new ServiceException(ErrorCode.IDEMPOTENCY_CONFLICT,
                "clientRequestId was already used with different input",
                Map.of("existingRequestId", existingRequestId));
    }

    public static ServiceException internal() {
        return new ServiceException(ErrorCode.INTERNAL, "Internal error; nothing was changed", Map.of());
    }
}
