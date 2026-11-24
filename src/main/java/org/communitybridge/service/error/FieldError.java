package org.communitybridge.service.error;

/**
 * One bad input field, listed in VALIDATION_ERROR details.fields.
 *
 * @param field input name, e.g. "amount"
 */
public record FieldError(String field, String reason) {
}
