package org.communitybridge.domain;

/** A tenant. Owns every other record. Seeded: np_cb (Community Bridge), np_hr (Harbor Relief). */
public record Nonprofit(String id, String name) {
}
