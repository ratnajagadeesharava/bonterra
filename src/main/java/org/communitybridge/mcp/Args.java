package org.communitybridge.mcp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.communitybridge.service.error.FieldError;
import org.communitybridge.service.error.ServiceException;

/**
 * Tool arguments after the JSON-shape check. The SDK's own schema validation is turned off (spike S2) because it
 * answers with text only and before the role check; this check runs after the role check and reports every
 * problem as a VALIDATION_ERROR naming the field. A JSON null counts as "not given".
 */
final class Args {

    private final Map<String, Object> values;

    private Args(Map<String, Object> values) {
        this.values = values;
    }

    /** @throws ServiceException VALIDATION_ERROR listing every unknown field and every wrong JSON type */
    static Args check(Map<String, Object> raw, List<Param> params) {
        Map<String, Param> byName = new HashMap<>();
        params.forEach(p -> byName.put(p.name(), p));
        Map<String, Object> values = new HashMap<>();
        List<FieldError> errors = new ArrayList<>();

        for (Map.Entry<String, Object> e : (raw == null ? Map.<String, Object>of() : raw).entrySet()) {
            Param p = byName.get(e.getKey());
            Object v = e.getValue();
            if (p == null) {
                errors.add(new FieldError(e.getKey(), "is not an allowed field"));
            } else if (v == null) {
                continue; // null means not given
            } else if (p.type() == Param.Type.STRING) {
                if (v instanceof String s) {
                    values.put(p.name(), s);
                } else {
                    errors.add(new FieldError(p.name(), "must be a JSON string"));
                }
            } else {
                Integer i = asInt(v);
                if (i == null) {
                    errors.add(new FieldError(p.name(), "must be a JSON integer"));
                } else {
                    values.put(p.name(), i);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw ServiceException.validation(errors);
        }
        return new Args(values);
    }

    String str(String name) {
        return (String) values.get(name);
    }

    Integer integer(String name) {
        return (Integer) values.get(name);
    }

    private static Integer asInt(Object v) {
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Long || v instanceof Short || v instanceof BigInteger) {
            long l = ((Number) v).longValue();
            if (v instanceof BigInteger b && b.bitLength() > 63) {
                return null;
            }
            // Out-of-int values are clamped so the service reports the range rule, not a type error.
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, l));
        }
        return null; // decimals, strings, booleans, objects
    }
}
