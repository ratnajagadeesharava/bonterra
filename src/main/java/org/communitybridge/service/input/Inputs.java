package org.communitybridge.service.input;

/**
 * Validation limits shared by the service and, in step 6, the published MCP input schemas,
 * so the two can never disagree.
 */
public final class Inputs {

    public static final int PURPOSE_MAX = 500;
    public static final int REASON_MAX = 500;
    public static final int SEARCH_MAX = 100;
    public static final int CLIENT_REQUEST_ID_MAX = 64;
    public static final String CLIENT_REQUEST_ID_PATTERN = "^[A-Za-z0-9_-]{1,64}$";
    public static final int LIMIT_MIN = 1;
    public static final int LIMIT_MAX = 100;
    public static final int LIMIT_DEFAULT = 50;

    private Inputs() {
    }
}
