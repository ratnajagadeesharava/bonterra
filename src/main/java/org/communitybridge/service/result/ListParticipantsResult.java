package org.communitybridge.service.result;

import java.util.List;

/** Ordered by name, then id. */
public record ListParticipantsResult(List<ParticipantView> participants) {
}
