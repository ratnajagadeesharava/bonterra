package org.communitybridge.service;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Server-made IDs: a type prefix plus 20 random hex characters, e.g. req_3f9a0c1d2b4e5f607182. */
final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    static String next(String prefix) {
        byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);
        return prefix + "_" + HexFormat.of().formatHex(bytes);
    }
}
