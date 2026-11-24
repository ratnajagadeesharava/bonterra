package org.communitybridge.service.result;

import org.communitybridge.domain.Role;

public record WhoAmIResult(String staffId, String name, Role role, String nonprofitId, String nonprofitName) {
}
