package org.communitybridge.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.communitybridge.domain.Category;
import org.communitybridge.domain.FundStatus;
import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.domain.Role;

/**
 * JSON Schema fragments for tool outputs. Each one mirrors a result record field by field, with
 * additionalProperties false and every field required, so the MCP client's output validation (spike S3b)
 * catches any drift between a record and its schema.
 */
final class Schemas {

    private Schemas() {
    }

    // ------------------------------------------------------------ primitives

    static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }

    static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    static Map<String, Object> money(String description) {
        return Map.of("type", "string", "description", description + ", USD with two decimals, e.g. \"300.50\"");
    }

    static Map<String, Object> cents(String description) {
        return Map.of("type", "integer", "minimum", 0, "description", description + " in cents");
    }

    static Map<String, Object> enumOf(Enum<?>[] values) {
        return Map.of("type", "string", "enum", Arrays.stream(values).map(Enum::name).toList());
    }

    static Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    /** A closed object: every listed property required, nothing else allowed. Pairs of name, schema. */
    static Map<String, Object> object(Object... nameSchemaPairs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < nameSchemaPairs.length; i += 2) {
            properties.put((String) nameSchemaPairs[i], nameSchemaPairs[i + 1]);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    // ------------------------------------------------------------ result records

    static final Map<String, Object> OUTCOME = Map.of("type", "string", "enum",
            Arrays.stream(Outcome.values()).map(Enum::name).toList(),
            "description", "APPLIED: this call made the change. ALREADY_APPLIED: it had already happened; nothing changed.");

    static final Map<String, Object> PARTICIPANT = object(
            "id", string(), "name", string(), "householdSize", integer(), "city", string());

    static final Map<String, Object> REQUEST = object(
            "id", string(),
            "participantId", string(),
            "fundId", string(),
            "category", enumOf(Category.values()),
            "purpose", string(),
            "amountCents", cents("Requested amount"),
            "amount", money("Requested amount"),
            "currency", string(),
            "status", enumOf(RequestStatus.values()),
            "clientRequestId", nullableString(),
            "createdBy", string(),
            "createdAt", string("UTC ISO-8601"),
            "decidedBy", nullableString(),
            "decidedAt", nullableString(),
            "rejectionReason", nullableString());

    static final Map<String, Object> FUND = object(
            "id", string(),
            "name", string(),
            "status", enumOf(FundStatus.values()),
            "currency", string(),
            "allocationCents", cents("Fixed allocation"),
            "committedCents", cents("Reserved by APPROVED requests"),
            "paidCents", cents("Paid out"),
            "availableCents", cents("allocation - committed - paid"),
            "pendingCents", cents("PENDING requests, information only"),
            "pendingCount", integer(),
            "allocation", money("Fixed allocation"),
            "committed", money("Reserved by APPROVED requests"),
            "paid", money("Paid out"),
            "available", money("allocation - committed - paid"),
            "pending", money("PENDING requests, information only"));

    static final Map<String, Object> TOTALS = object(
            "currency", string(),
            "allocationCents", cents("Total allocation"),
            "committedCents", cents("Total committed"),
            "paidCents", cents("Total paid"),
            "availableCents", cents("Total available"),
            "pendingCents", cents("Total pending"),
            "allocation", money("Total allocation"),
            "committed", money("Total committed"),
            "paid", money("Total paid"),
            "available", money("Total available"),
            "pending", money("Total pending"));

    static final Map<String, Object> PAYMENT = object(
            "id", string(),
            "requestId", string(),
            "amountCents", cents("Paid amount"),
            "amount", money("Paid amount"),
            "currency", string(),
            "recordedBy", string(),
            "recordedAt", string("UTC ISO-8601"));

    static final Map<String, Object> WHOAMI = object(
            "staffId", string(), "name", string(), "role", enumOf(Role.values()),
            "nonprofitId", string(), "nonprofitName", string());
}
