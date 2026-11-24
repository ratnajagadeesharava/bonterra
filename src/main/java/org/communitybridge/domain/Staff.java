package org.communitybridge.domain;

/** A fixture staff identity: exactly one role in exactly one nonprofit. Inactive staff are UNAUTHENTICATED. */
public record Staff(String id, String nonprofitId, String name, Role role, boolean active) {
}
