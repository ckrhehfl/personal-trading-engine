package com.ptengine.contract.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderIntentJsonCodecTest {

    private static final ObjectMapper ASSERTION_MAPPER = new ObjectMapper();

    private static final String VALID_MARKET_JSON =
            """
            {
              "schemaVersion": "0.1.0",
              "intentId": "intent-100",
              "strategyId": "strategy-smoke",
              "instrument": "BTC-USDT-PERP",
              "intentType": "ENTER",
              "direction": "LONG",
              "orderType": "MARKET",
              "requestedNotional": "25",
              "createdAtEpochMs": 1783396900000
            }
            """;

    private static final String VALID_LIMIT_JSON =
            """
            {
              "schemaVersion": "0.1.0",
              "intentId": "intent-101",
              "strategyId": "strategy-smoke",
              "instrument": "BTC-USDT-PERP",
              "intentType": "ENTER",
              "direction": "LONG",
              "orderType": "LIMIT",
              "requestedNotional": "100.00",
              "limitPrice": "60000.50",
              "createdAtEpochMs": 1783396900001
            }
            """;

    @Test
    void validMarketIntentParses() {
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(VALID_MARKET_JSON);
        assertEquals(OrderType.MARKET, intent.orderType());
        assertEquals(IntentType.ENTER, intent.intentType());
        assertEquals(Direction.LONG, intent.direction());
        assertEquals(new BigDecimal("25"), intent.requestedNotional());
        assertEquals(null, intent.limitPrice());
    }

    @Test
    void validLimitIntentParses() {
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(VALID_LIMIT_JSON);
        assertEquals(OrderType.LIMIT, intent.orderType());
        assertEquals(new BigDecimal("60000.50"), intent.limitPrice());
    }

    @Test
    void trailingContentAfterObjectRejected() {
        String withTrailingGarbage = VALID_MARKET_JSON.strip() + " {}";
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent(withTrailingGarbage));
    }

    @Test
    void nonObjectTopLevelRejected() {
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent("[1, 2, 3]"));
    }

    @Test
    void lowercaseEnumRejectedCaseSensitively() {
        String lowercased = VALID_MARKET_JSON.replace("\"MARKET\"", "\"market\"");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent(lowercased));
    }

    @Test
    void plusSignPrefixedDecimalRejected() {
        String withPlusSign = VALID_MARKET_JSON.replace("\"25\"", "\"+25\"");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent(withPlusSign));
    }

    @Test
    void timestampAsFloatRejected() {
        String withFloatTimestamp = VALID_MARKET_JSON.replace("1783396900000", "1783396900000.0");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent(withFloatTimestamp));
    }

    @Test
    void signedLongMaxTimestampAccepted() {
        String withMaxTimestamp = VALID_MARKET_JSON.replace("1783396900000", "9223372036854775807");
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(withMaxTimestamp);
        assertEquals(Long.MAX_VALUE, intent.createdAtEpochMs());
    }

    @Test
    void marketWithExplicitNullLimitPriceKeyRejected() {
        String withNullLimitPriceKey =
                VALID_MARKET_JSON.strip().replaceFirst("\\}\\s*$", ",\n  \"limitPrice\": null\n}");
        assertThrows(
                ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent(withNullLimitPriceKey));
    }

    @Test
    void repeatedSerializationIsByteForByteDeterministic() {
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(VALID_LIMIT_JSON);
        String first = ContractJsonCodec.toJson(intent);
        String second = ContractJsonCodec.toJson(intent);
        String third = ContractJsonCodec.toJson(intent);
        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void marketSerializationOmitsLimitPrice() throws Exception {
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(VALID_MARKET_JSON);
        String json = ContractJsonCodec.toJson(intent);
        JsonNode node = ASSERTION_MAPPER.readTree(json);
        assertFalse(node.has("limitPrice"));
    }

    @Test
    void decimalFieldsSerializeAsJsonStringsNotNumbers() throws Exception {
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(VALID_LIMIT_JSON);
        String json = ContractJsonCodec.toJson(intent);
        JsonNode node = ASSERTION_MAPPER.readTree(json);
        assertTrue(node.get("requestedNotional").isTextual());
        assertTrue(node.get("limitPrice").isTextual());
    }

    @Test
    void serializationFailsClosedWhenDecimalExceedsCanonicalLength() {
        OrderIntent intent = new OrderIntent(
                OrderIntent.SCHEMA_VERSION,
                "intent-1",
                "strategy-1",
                "BTCUSDT",
                IntentType.ENTER,
                Direction.LONG,
                OrderType.MARKET,
                new BigDecimal("1" + "0".repeat(70)),
                null,
                1_000L);
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.toJson(intent));
    }
}
