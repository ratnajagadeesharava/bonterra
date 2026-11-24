package org.communitybridge.mcp;

/**
 * The staff ID this server process was launched for (CB_STAFF_ID), already checked at startup. It is passed to
 * the service on every call, which re-resolves it, so a deactivation or role change applies at once.
 * Nothing a client sends can change it (MCP-06, NFR-05).
 */
public record LaunchIdentity(String staffId) {
}
