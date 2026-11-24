package org.communitybridge.domain;

/** A person receiving assistance. Read-only through tools; created only by the seed. */
public record Participant(String id, String nonprofitId, String name, int householdSize, String city) {
}
