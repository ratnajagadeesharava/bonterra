package org.communitybridge.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One tool argument. The same definition produces the published input schema and the runtime JSON-shape check,
 * so they cannot disagree. Value rules (length, pattern, ranges) are published for clients and enforced by the
 * service's InputValidator; the JSON check here only enforces "known field" and "right JSON type".
 *
 * @param extra additional JSON Schema keywords for clients (enum, pattern, maxLength, minimum, ...)
 */
record Param(String name, Type type, boolean required, String description, Map<String, Object> extra) {

    enum Type {
        STRING("string"),
        INTEGER("integer");

        final String json;

        Type(String json) {
            this.json = json;
        }
    }

    static Param required(String name, String description, Object... extraPairs) {
        return new Param(name, Type.STRING, true, description, pairs(extraPairs));
    }

    static Param optional(String name, String description, Object... extraPairs) {
        return new Param(name, Type.STRING, false, description, pairs(extraPairs));
    }

    static Param optionalInt(String name, String description, Object... extraPairs) {
        return new Param(name, Type.INTEGER, false, description, pairs(extraPairs));
    }

    Map<String, Object> schema() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", type.json);
        s.put("description", description);
        s.putAll(extra);
        return s;
    }

    private static Map<String, Object> pairs(Object[] kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
