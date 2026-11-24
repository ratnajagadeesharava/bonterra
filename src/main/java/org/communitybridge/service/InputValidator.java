package org.communitybridge.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.communitybridge.domain.Money;
import org.communitybridge.service.error.FieldError;
import org.communitybridge.service.error.ServiceException;

/**
 * Collects every bad field of one input, then throws a single VALIDATION_ERROR listing them all.
 * Each method returns the normalized value (trimmed text, parsed enum, cents), or null when invalid.
 */
final class InputValidator {

    private static final int ID_MAX = 100;

    private final List<FieldError> errors = new ArrayList<>();

    String requiredId(String field, String value) {
        if (value == null || value.isBlank()) {
            return fail(field, "is required");
        }
        if (value.length() > ID_MAX) {
            return fail(field, "must be at most " + ID_MAX + " characters");
        }
        return value;
    }

    /** Absent is fine; present must be a non-blank ID. */
    String optionalId(String field, String value) {
        return value == null ? null : requiredId(field, value);
    }

    /** Exact, case-sensitive enum name. */
    <E extends Enum<E>> E requiredEnum(String field, String value, Class<E> type) {
        if (value == null) {
            return fail(field, "is required");
        }
        return parseEnum(field, value, type);
    }

    <E extends Enum<E>> E optionalEnum(String field, String value, Class<E> type) {
        return value == null ? null : parseEnum(field, value, type);
    }

    /** Trimmed; must be 1..max characters after trimming. */
    String requiredText(String field, String value, int max) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            return fail(field, "is required");
        }
        if (trimmed.length() > max) {
            return fail(field, "must be at most " + max + " characters");
        }
        return trimmed;
    }

    /** Trimmed; blank becomes null; must be at most max characters. */
    String optionalText(String field, String value, int max) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            return fail(field, "must be at most " + max + " characters");
        }
        return trimmed;
    }

    /** Absent is fine; present must match the pattern exactly. */
    String optionalPattern(String field, String value, Pattern pattern, String rule) {
        if (value == null) {
            return null;
        }
        return pattern.matcher(value).matches() ? value : fail(field, rule);
    }

    /** Request amount in cents (see Money.parseRequestAmount); 0 when invalid. */
    long amount(String value) {
        try {
            return Money.parseRequestAmount(value).cents();
        } catch (ServiceException e) {
            errors.addAll(fieldsOf(e));
            return 0;
        }
    }

    /** Absent gives the default; present must be within min..max. */
    int intInRange(String field, Integer value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        if (value < min || value > max) {
            fail(field, max == Integer.MAX_VALUE ? "must be at least " + min : "must be between " + min + " and " + max);
            return defaultValue;
        }
        return value;
    }

    void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw ServiceException.validation(errors);
        }
    }

    private <E extends Enum<E>> E parseEnum(String field, String value, Class<E> type) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        return fail(field, "must be one of " + Arrays.toString(type.getEnumConstants()));
    }

    private <T> T fail(String field, String reason) {
        errors.add(new FieldError(field, reason));
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<FieldError> fieldsOf(ServiceException e) {
        return (List<FieldError>) e.details().get("fields");
    }
}
