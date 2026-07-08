package com.ptengine.contract.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskOutcome;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic local JSON codec boundary for the typed {@link OrderIntent} / {@link RiskDecision}
 * domain values, strictly aligned with {@code schemas/v0.1/order-intent.schema.json},
 * {@code schemas/v0.1/risk-decision.schema.json}, and {@code schemas/v0.1/common.schema.json}.
 *
 * <p>This is local JSON parsing/serialization only &mdash; no network access, no schema registry,
 * no generated models. It enforces the canonical v0.1 wire representation strictly (unknown
 * fields, wrong types, invalid enums, non-canonical decimal/timestamp encodings, and structural
 * violations are all rejected) and serializes deterministically (stable field order, decimals as
 * plain base-10 strings, no hidden clock/randomness/locale/timezone dependency).
 *
 * <p>This codec establishes JSON-boundary compatibility with the shared fixtures under
 * {@code tests/schemas/fixtures/**}; it does not establish full backtest &lt;-&gt; Java
 * trading-path behavioral equivalence.
 */
public final class ContractJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_DECIMAL_LENGTH = 64;

    /** Mirrors {@code common.schema.json}'s {@code positiveDecimal} pattern exactly. */
    private static final Pattern POSITIVE_DECIMAL_PATTERN =
            Pattern.compile("^(?:0\\.(?:0*[1-9]\\d*)|[1-9]\\d*(?:\\.\\d+)?)$");

    private static final Set<String> ORDER_INTENT_ALLOWED_FIELDS =
            Set.of(
                    "schemaVersion",
                    "intentId",
                    "strategyId",
                    "instrument",
                    "intentType",
                    "direction",
                    "orderType",
                    "requestedNotional",
                    "limitPrice",
                    "createdAtEpochMs");

    private static final Set<String> ORDER_INTENT_REQUIRED_FIELDS =
            Set.of(
                    "schemaVersion",
                    "intentId",
                    "strategyId",
                    "instrument",
                    "intentType",
                    "direction",
                    "orderType",
                    "requestedNotional",
                    "createdAtEpochMs");

    private static final Set<String> RISK_DECISION_ALLOWED_FIELDS =
            Set.of(
                    "schemaVersion",
                    "decisionId",
                    "intentId",
                    "outcome",
                    "reasonCodes",
                    "evaluatedAtEpochMs");

    private static final Set<String> RISK_DECISION_REQUIRED_FIELDS = RISK_DECISION_ALLOWED_FIELDS;

    private ContractJsonCodec() {}

    // ------------------------------------------------------------------
    // OrderIntent
    // ------------------------------------------------------------------

    public static OrderIntent parseOrderIntent(String json) {
        JsonNode node = parseSingleTopLevelObject(json);
        requireExactFields(node, ORDER_INTENT_ALLOWED_FIELDS, ORDER_INTENT_REQUIRED_FIELDS);

        String schemaVersion = requireSchemaVersion(node);
        String intentId = requireIdentifier(node, "intentId");
        String strategyId = requireIdentifier(node, "strategyId");
        String instrument = requireIdentifier(node, "instrument");
        IntentType intentType = requireEnum(node, "intentType", IntentType.class, IntentType.values());
        Direction direction = requireEnum(node, "direction", Direction.class, Direction.values());
        OrderType orderType = requireEnum(node, "orderType", OrderType.class, OrderType.values());
        BigDecimal requestedNotional = requireDecimalString(node, "requestedNotional");
        long createdAtEpochMs = requireEpochMillis(node, "createdAtEpochMs");

        BigDecimal limitPrice;
        if (orderType == OrderType.LIMIT) {
            if (!node.has("limitPrice")) {
                throw new ContractJsonException("orderType LIMIT requires limitPrice");
            }
            limitPrice = requireDecimalString(node, "limitPrice");
        } else {
            if (node.has("limitPrice")) {
                throw new ContractJsonException("orderType MARKET forbids limitPrice");
            }
            limitPrice = null;
        }

        try {
            return new OrderIntent(
                    schemaVersion,
                    intentId,
                    strategyId,
                    instrument,
                    intentType,
                    direction,
                    orderType,
                    requestedNotional,
                    limitPrice,
                    createdAtEpochMs);
        } catch (RuntimeException e) {
            throw new ContractJsonException("rejected by OrderIntent domain validation: " + e.getMessage(), e);
        }
    }

    public static String toJson(OrderIntent intent) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("schemaVersion", intent.schemaVersion());
        node.put("intentId", intent.intentId());
        node.put("strategyId", intent.strategyId());
        node.put("instrument", intent.instrument());
        node.put("intentType", intent.intentType().name());
        node.put("direction", intent.direction().name());
        node.put("orderType", intent.orderType().name());
        node.put("requestedNotional", formatDecimal(intent.requestedNotional(), "requestedNotional"));
        if (intent.orderType() == OrderType.LIMIT) {
            node.put("limitPrice", formatDecimal(intent.limitPrice(), "limitPrice"));
        }
        node.put("createdAtEpochMs", intent.createdAtEpochMs());
        return writeDeterministic(node);
    }

    // ------------------------------------------------------------------
    // RiskDecision
    // ------------------------------------------------------------------

    public static RiskDecision parseRiskDecision(String json) {
        JsonNode node = parseSingleTopLevelObject(json);
        requireExactFields(node, RISK_DECISION_ALLOWED_FIELDS, RISK_DECISION_REQUIRED_FIELDS);

        String schemaVersion = requireSchemaVersion(node);
        String decisionId = requireIdentifier(node, "decisionId");
        String intentId = requireIdentifier(node, "intentId");
        RiskOutcome outcome = requireEnum(node, "outcome", RiskOutcome.class, RiskOutcome.values());
        List<String> reasonCodes = requireReasonCodes(node);
        long evaluatedAtEpochMs = requireEpochMillis(node, "evaluatedAtEpochMs");

        try {
            return new RiskDecision(schemaVersion, decisionId, intentId, outcome, reasonCodes, evaluatedAtEpochMs);
        } catch (RuntimeException e) {
            throw new ContractJsonException("rejected by RiskDecision domain validation: " + e.getMessage(), e);
        }
    }

    public static String toJson(RiskDecision decision) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("schemaVersion", decision.schemaVersion());
        node.put("decisionId", decision.decisionId());
        node.put("intentId", decision.intentId());
        node.put("outcome", decision.outcome().name());
        ArrayNode reasonCodes = node.putArray("reasonCodes");
        for (String code : decision.reasonCodes()) {
            reasonCodes.add(code);
        }
        node.put("evaluatedAtEpochMs", decision.evaluatedAtEpochMs());
        return writeDeterministic(node);
    }

    private static List<String> requireReasonCodes(JsonNode node) {
        JsonNode reasonCodesNode = node.get("reasonCodes");
        if (reasonCodesNode == null || !reasonCodesNode.isArray()) {
            throw new ContractJsonException("reasonCodes must be a JSON array");
        }
        List<String> reasonCodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode element : reasonCodesNode) {
            if (!element.isTextual()) {
                throw new ContractJsonException("reasonCodes element must be a JSON string");
            }
            String text = element.textValue();
            validateIdentifierText(text, "reasonCodes element");
            if (!seen.add(text)) {
                throw new ContractJsonException("reasonCodes must not contain a duplicate: " + text);
            }
            reasonCodes.add(text);
        }
        return reasonCodes;
    }

    // ------------------------------------------------------------------
    // shared parsing helpers
    // ------------------------------------------------------------------

    private static JsonNode parseSingleTopLevelObject(String json) {
        if (json == null) {
            throw new ContractJsonException("JSON input must not be null");
        }
        try (JsonParser parser = MAPPER.getFactory().createParser(json)) {
            JsonNode node = MAPPER.readTree(parser);
            if (node == null || node.isMissingNode()) {
                throw new ContractJsonException("JSON input must not be empty");
            }
            if (parser.nextToken() != null) {
                throw new ContractJsonException("trailing content after top-level JSON value");
            }
            if (!node.isObject()) {
                throw new ContractJsonException("top-level JSON value must be an object");
            }
            return node;
        } catch (IOException e) {
            throw new ContractJsonException("malformed JSON input: " + e.getMessage(), e);
        }
    }

    private static void requireExactFields(JsonNode node, Set<String> allowed, Set<String> required) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            if (!allowed.contains(name)) {
                throw new ContractJsonException("unknown field: " + name);
            }
        }
        for (String field : required) {
            if (!node.has(field)) {
                throw new ContractJsonException("missing required field: " + field);
            }
        }
    }

    private static String requireSchemaVersion(JsonNode node) {
        JsonNode value = node.get("schemaVersion");
        if (value == null || !value.isTextual() || !"0.1.0".equals(value.textValue())) {
            throw new ContractJsonException("schemaVersion must be exactly \"0.1.0\"");
        }
        return value.textValue();
    }

    private static String requireIdentifier(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new ContractJsonException(field + " must be a JSON string");
        }
        String text = value.textValue();
        validateIdentifierText(text, field);
        return text;
    }

    private static void validateIdentifierText(String text, String field) {
        if (text.isEmpty() || text.length() > MAX_IDENTIFIER_LENGTH) {
            throw new ContractJsonException(
                    field + " must be 1-" + MAX_IDENTIFIER_LENGTH + " characters, was " + text.length());
        }
        if (text.chars().allMatch(Character::isWhitespace)) {
            throw new ContractJsonException(field + " must contain at least one non-whitespace character");
        }
    }

    private static <E extends Enum<E>> E requireEnum(JsonNode node, String field, Class<E> type, E[] values) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new ContractJsonException(field + " must be a JSON string");
        }
        String text = value.textValue();
        for (E candidate : values) {
            if (candidate.name().equals(text)) {
                return candidate;
            }
        }
        throw new ContractJsonException("unknown " + field + ": " + text);
    }

    private static BigDecimal requireDecimalString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new ContractJsonException(field + " must be a JSON string, not a number");
        }
        String text = value.textValue();
        if (text.length() > MAX_DECIMAL_LENGTH || !POSITIVE_DECIMAL_PATTERN.matcher(text).matches()) {
            throw new ContractJsonException(
                    field + " does not match the canonical positive-decimal representation: " + text);
        }
        return new BigDecimal(text);
    }

    private static long requireEpochMillis(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new ContractJsonException(field + " must be a JSON integer");
        }
        if (!value.canConvertToLong()) {
            throw new ContractJsonException(field + " exceeds signed 64-bit epoch-millis bounds");
        }
        long millis = value.longValue();
        if (millis < 0) {
            throw new ContractJsonException(field + " must be non-negative");
        }
        return millis;
    }

    // ------------------------------------------------------------------
    // shared serialization helpers
    // ------------------------------------------------------------------

    private static String formatDecimal(BigDecimal value, String field) {
        String text = value.toPlainString();
        if (text.length() > MAX_DECIMAL_LENGTH || !POSITIVE_DECIMAL_PATTERN.matcher(text).matches()) {
            throw new ContractJsonException(
                    "cannot serialize " + field + " into the canonical positive-decimal representation: " + text);
        }
        return text;
    }

    private static String writeDeterministic(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ContractJsonException("failed to serialize contract value", e);
        }
    }
}
