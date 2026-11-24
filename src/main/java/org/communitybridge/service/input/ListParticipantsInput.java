package org.communitybridge.service.input;

/**
 * @param search optional case-insensitive name filter, at most 100 chars
 * @param limit  optional, 1..100, default 50
 */
public record ListParticipantsInput(String search, Integer limit) {
}
