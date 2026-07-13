package com.ptengine.bingx.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.bingx.market.BingxPublicMarketDataClient.RawResponse;
import com.ptengine.bingx.market.BingxPublicMarketDataClient.Transport;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fully offline, deterministic test suite for {@link
 * BingxPublicMarketDataClient#fetchRecentBtcUsdt15mCandles()}. No test in this class opens a
 * socket, sleeps, or reads the real clock. HTTP interaction is intercepted at the package-private
 * {@link Transport} seam via {@link RecordingTransport}, the same seam Candidate 18's {@code
 * BingxPublicMarketDataClientTest} uses for {@code fetchRecentBtcUsdtTrades()}.
 */
class BingxPublicMarketDataClientCandleTest {

    private static final URI VALID_BASE_URI = URI.create("https://open-api.bingx.com");
    private static final String EXPECTED_PATH = "/openApi/swap/v3/quote/klines";
    private static final String EXPECTED_QUERY = "symbol=BTC-USDT&interval=15m&limit=1000";

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    // ------------------------------------------------------------------
    // A: request contract
    // ------------------------------------------------------------------

    @Test
    void request_isExactGetToExactPathAndQueryOnce() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_CANDLE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdt15mCandles();

        assertEquals(1, transport.invocationCount());
        HttpRequest sent = transport.lastRequest();
        assertEquals("GET", sent.method());
        assertEquals(EXPECTED_PATH, sent.uri().getRawPath());
        assertEquals(EXPECTED_QUERY, sent.uri().getRawQuery());
    }

    @Test
    void request_setsAcceptHeaderAndNoAuthHeaders() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_CANDLE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdt15mCandles();

        HttpRequest sent = transport.lastRequest();
        assertEquals(List.of("application/json"), sent.headers().allValues("Accept"));
        assertTrue(sent.headers().firstValue("Authorization").isEmpty());
        assertTrue(sent.headers().firstValue("X-BX-APIKEY").isEmpty());
        assertTrue(sent.headers().firstValue("signature").isEmpty());
        assertTrue(sent.headers().firstValue("timestamp").isEmpty());
    }

    @Test
    void request_hasNoBody() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_CANDLE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdt15mCandles();

        assertTrue(transport.lastRequest().bodyPublisher().isEmpty());
    }

    @Test
    void request_transportInvokedExactlyOnce() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_CANDLE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdt15mCandles();
        assertEquals(1, transport.invocationCount());
    }

    // ------------------------------------------------------------------
    // B: successful parsing
    // ------------------------------------------------------------------

    @Test
    void fetch_parsesSingleElementBatch() {
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(ONE_CANDLE)));
        List<BingxPerpetualCandle> candles = client.fetchRecentBtcUsdt15mCandles();

        assertEquals(1, candles.size());
        BingxPerpetualCandle candle = candles.get(0);
        assertEquals("BTC-USDT", candle.symbol());
        assertEquals("15m", candle.interval());
        assertEquals(1700000000000L, candle.openTimeEpochMs());
        assertEquals(new BigDecimal("62800.0"), candle.open());
        assertEquals(new BigDecimal("62900.0"), candle.high());
        assertEquals(new BigDecimal("62700.0"), candle.low());
        assertEquals(new BigDecimal("62850.0"), candle.close());
        assertEquals(new BigDecimal("100.0"), candle.volume());
    }

    @Test
    void fetch_parsesMultiElementBatch() {
        String second = candleJson(1700000900000L, "62850.0", "62950.0", "62800.0", "62900.0", "50.0");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(ONE_CANDLE, second)));
        List<BingxPerpetualCandle> candles = client.fetchRecentBtcUsdt15mCandles();

        assertEquals(2, candles.size());
        assertEquals(1700000000000L, candles.get(0).openTimeEpochMs());
        assertEquals(1700000900000L, candles.get(1).openTimeEpochMs());
    }

    @Test
    void fetch_parses1000ElementBatch() {
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJsonOfSize(1000)));
        List<BingxPerpetualCandle> candles = client.fetchRecentBtcUsdt15mCandles();
        assertEquals(1000, candles.size());
    }

    @Test
    void fetch_preservesWireOrderWithoutSortingAndWithoutDeduplication() {
        // Deliberately non-monotonic timestamps, including an exact duplicate timestamp, to prove
        // the client neither sorts nor deduplicates - it only preserves array order. Real BingX
        // v3 responses are observed strictly descending, but this class must not assume that.
        String first = candleJson(500L, "1.0", "2.0", "0.5", "1.5", "1.0");
        String second = candleJson(100L, "2.0", "3.0", "1.0", "2.5", "1.0");
        String third = candleJson(100L, "3.0", "4.0", "2.0", "3.5", "1.0");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(first, second, third)));

        List<BingxPerpetualCandle> candles = client.fetchRecentBtcUsdt15mCandles();

        assertEquals(3, candles.size());
        assertEquals(500L, candles.get(0).openTimeEpochMs());
        assertEquals(100L, candles.get(1).openTimeEpochMs());
        assertEquals(100L, candles.get(2).openTimeEpochMs());
        assertEquals(new BigDecimal("2.0"), candles.get(1).open());
        assertEquals(new BigDecimal("3.0"), candles.get(2).open());
    }

    @Test
    void fetch_returnsImmutableList() {
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(ONE_CANDLE)));
        List<BingxPerpetualCandle> candles = client.fetchRecentBtcUsdt15mCandles();
        assertThrows(UnsupportedOperationException.class, () -> candles.add(candles.get(0)));
    }

    @Test
    void fetch_preservesDecimalScaleExactly() {
        String candle = candleJson(1L, "62800.10", "62900.100", "62700.0", "62850.50", "0.00010");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(candle)));
        BingxPerpetualCandle parsed = client.fetchRecentBtcUsdt15mCandles().get(0);

        assertEquals("62800.10", parsed.open().toPlainString());
        assertEquals("62900.100", parsed.high().toPlainString());
        assertEquals("62700.0", parsed.low().toPlainString());
        assertEquals("62850.50", parsed.close().toPlainString());
        assertEquals("0.00010", parsed.volume().toPlainString());
    }

    @Test
    void fetch_acceptsZeroVolume() {
        String candle = candleJson(1L, "62800.0", "62900.0", "62700.0", "62850.0", "0");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(candle)));
        BingxPerpetualCandle parsed = client.fetchRecentBtcUsdt15mCandles().get(0);
        assertEquals(new BigDecimal("0"), parsed.volume());
    }

    @Test
    void fetch_ignoresAdditiveUnknownFields() {
        String candleWithExtras =
                "{\"time\":1700000000000,\"open\":\"62800.0\",\"high\":\"62900.0\",\"low\":\"62700.0\","
                        + "\"close\":\"62850.0\",\"volume\":\"100.0\",\"closeTime\":1700000899999,"
                        + "\"quoteVolume\":\"6280000.0\"}";
        String responseWithExtraTopLevel =
                "{\"code\":0,\"msg\":\"\",\"data\":[" + candleWithExtras + "],\"extraTopLevelField\":true}";
        BingxPublicMarketDataClient client = clientWithResponse(ok(responseWithExtraTopLevel));

        List<BingxPerpetualCandle> candles = client.fetchRecentBtcUsdt15mCandles();
        assertEquals(1, candles.size());
        assertEquals(new BigDecimal("62800.0"), candles.get(0).open());
    }

    @Test
    void fetch_isDeterministicAcrossFreshClientInstancesGivenEqualResponse() {
        String responseBody = batchJson(ONE_CANDLE);
        BingxPublicMarketDataClient clientA =
                new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(ok(responseBody)));
        BingxPublicMarketDataClient clientB =
                new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(ok(responseBody)));

        assertEquals(clientA.fetchRecentBtcUsdt15mCandles(), clientB.fetchRecentBtcUsdt15mCandles());
    }

    @Test
    void fetch_doesNotFilterTheLeadingPossiblyOpenCandle() {
        // No isClosed field exists on the wire (confirmed by live discovery); this class must not
        // invent open/incomplete-candle filtering that the verified contract does not support.
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJsonOfSize(3)));
        assertEquals(3, client.fetchRecentBtcUsdt15mCandles().size());
    }

    // ------------------------------------------------------------------
    // C: envelope / body failures
    // ------------------------------------------------------------------

    @Test
    void fetch_rejects400() {
        assertRejectsStatus(400);
    }

    @Test
    void fetch_rejects500() {
        assertRejectsStatus(500);
    }

    @Test
    void fetch_rejects3xxWithoutFollowingOrRetrying() {
        RecordingTransport transport = RecordingTransport.ofResponse(status(302));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void fetch_wrapsIOException() {
        RecordingTransport transport = RecordingTransport.ofIoFailure(new IOException("connection reset"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        BingxPublicMarketDataException thrown =
                assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
        assertTrue(thrown.getCause() instanceof IOException);
    }

    @Test
    void fetch_wrapsInterruptedExceptionAndRestoresInterruptFlag() {
        RecordingTransport transport = RecordingTransport.ofInterruptedFailure(new InterruptedException("stopped"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);

        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void fetch_rejectsBodyOver1MiB() {
        StringBuilder oversized = new StringBuilder("{\"code\":0,\"msg\":\"");
        oversized.append("x".repeat(BingxPublicMarketDataClient.MAX_RESPONSE_BYTES));
        oversized.append("\",\"data\":[]}");
        BingxPublicMarketDataClient client = clientWithResponse(ok(oversized.toString()));
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
    }

    @Test
    void fetch_rejectsMalformedUtf8() {
        byte[] malformed = new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
        RecordingTransport transport = RecordingTransport.ofResponse(new RawResponse(200, malformed));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
    }

    @Test
    void fetch_rejectsMalformedJson() {
        assertRejectsBody("{not valid json");
    }

    @Test
    void fetch_rejectsTrailingJson() {
        assertRejectsBody(batchJson(ONE_CANDLE) + "{}");
    }

    @Test
    void fetch_rejectsNonObjectTopLevel() {
        assertRejectsBody("[]");
    }

    @Test
    void fetch_rejectsMissingWrongTypeOrNonzeroCode() {
        assertRejectsBody("{\"msg\":\"\",\"data\":[" + ONE_CANDLE + "]}");
        assertRejectsBody("{\"code\":\"0\",\"msg\":\"\",\"data\":[" + ONE_CANDLE + "]}");
        assertRejectsBody("{\"code\":109400,\"msg\":\"limit: This field must be less than or equal to 1440. \","
                + "\"data\":{}}");
    }

    @Test
    void fetch_rejectsMissingOrWrongTypeMsg() {
        assertRejectsBody("{\"code\":0,\"data\":[" + ONE_CANDLE + "]}");
        assertRejectsBody("{\"code\":0,\"msg\":123,\"data\":[" + ONE_CANDLE + "]}");
    }

    @Test
    void fetch_rejectsMissingOrWrongTypeData() {
        assertRejectsBody("{\"code\":0,\"msg\":\"\"}");
        assertRejectsBody("{\"code\":0,\"msg\":\"\",\"data\":{}}");
    }

    @Test
    void fetch_rejectsEmptyData() {
        assertRejectsBody("{\"code\":0,\"msg\":\"\",\"data\":[]}");
    }

    @Test
    void fetch_rejects1001Elements() {
        assertRejectsBody(batchJsonOfSize(1001));
    }

    @Test
    void fetch_rejectsNonObjectCandleElement() {
        assertRejectsBody("{\"code\":0,\"msg\":\"\",\"data\":[\"not-an-object\"]}");
    }

    // ------------------------------------------------------------------
    // D: per-candle field validation
    // ------------------------------------------------------------------

    @Test
    void fetch_rejectsEachRequiredFieldMissing() {
        assertRejectsBody(
                batchJson("{\"open\":\"1.0\",\"high\":\"2.0\",\"low\":\"0.5\",\"close\":\"1.5\",\"volume\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"high\":\"2.0\",\"low\":\"0.5\",\"close\":\"1.5\",\"volume\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"open\":\"1.0\",\"low\":\"0.5\",\"close\":\"1.5\",\"volume\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"open\":\"1.0\",\"high\":\"2.0\",\"close\":\"1.5\",\"volume\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"open\":\"1.0\",\"high\":\"2.0\",\"low\":\"0.5\",\"volume\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"open\":\"1.0\",\"high\":\"2.0\",\"low\":\"0.5\",\"close\":\"1.5\"}"));
    }

    @Test
    void fetch_rejectsEachRequiredFieldNull() {
        assertRejectsBody(batchJson(candleJsonWithField("time", "null")));
        assertRejectsBody(batchJson(candleJsonWithField("open", "null")));
        assertRejectsBody(batchJson(candleJsonWithField("high", "null")));
        assertRejectsBody(batchJson(candleJsonWithField("low", "null")));
        assertRejectsBody(batchJson(candleJsonWithField("close", "null")));
        assertRejectsBody(batchJson(candleJsonWithField("volume", "null")));
    }

    @Test
    void fetch_rejectsEachRequiredFieldWrongType() {
        assertRejectsBody(batchJson(candleJsonWithField("time", "\"1\"")));
        assertRejectsBody(batchJson(candleJsonWithField("open", "1.0")));
        assertRejectsBody(batchJson(candleJsonWithField("high", "1.0")));
        assertRejectsBody(batchJson(candleJsonWithField("low", "1.0")));
        assertRejectsBody(batchJson(candleJsonWithField("close", "1.0")));
        assertRejectsBody(batchJson(candleJsonWithField("volume", "1.0")));
    }

    @Test
    void fetch_rejectsNegativeOrOutOfLongRangeTime() {
        assertRejectsBody(batchJson(candleJson(-1L, "1.0", "2.0", "0.5", "1.5", "1.0")));
        assertRejectsBody(batchJson(candleJsonWithField("time", "99999999999999999999999999")));
    }

    @Test
    void fetch_rejectsWhitespaceSignOrExponentDecimal() {
        for (String bad : List.of(" 1.0", "1.0 ", "+1.0", "-1.0", "1e10", "1E10", "NaN", "Infinity", "")) {
            assertRejectsBody(batchJson(candleJsonWithField("open", "\"" + bad + "\"")), "open=" + bad);
        }
    }

    @Test
    void fetch_rejectsNonPositiveOhlc() {
        assertRejectsBody(batchJson(candleJsonWithField("open", "\"0\"")));
        assertRejectsBody(batchJson(candleJsonWithField("open", "\"-1.0\"")));
        assertRejectsBody(batchJson(candleJsonWithField("high", "\"0\"")));
        assertRejectsBody(batchJson(candleJsonWithField("low", "\"0\"")));
        assertRejectsBody(batchJson(candleJsonWithField("close", "\"0\"")));
    }

    @Test
    void fetch_rejectsNegativeVolume() {
        assertRejectsBody(batchJson(candleJsonWithField("volume", "\"-1.0\"")));
        assertRejectsBody(batchJson(candleJsonWithField("volume", "\"-0.0001\"")));
    }

    @Test
    void fetch_rejectsWhitespaceSignOrExponentVolume() {
        for (String bad : List.of(" 1.0", "1.0 ", "+1.0", "1e10", "1E10", "NaN", "Infinity", "")) {
            assertRejectsBody(batchJson(candleJsonWithField("volume", "\"" + bad + "\"")), "volume=" + bad);
        }
    }

    @Test
    void fetch_rejectsOverlongDecimalWithoutEchoingRawValueInMessage() {
        String tooLong = "1" + "0".repeat(70);
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(candleJsonWithField("open", "\"" + tooLong + "\""))));
        BingxPublicMarketDataException thrown =
                assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
        assertFalse(thrown.getMessage().contains(tooLong));
    }

    @Test
    void fetch_rejectsOverlongVolumeWithoutEchoingRawValueInMessage() {
        String tooLong = "1" + "0".repeat(70);
        BingxPublicMarketDataClient client =
                clientWithResponse(ok(batchJson(candleJsonWithField("volume", "\"" + tooLong + "\""))));
        BingxPublicMarketDataException thrown =
                assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
        assertFalse(thrown.getMessage().contains(tooLong));
    }

    // ------------------------------------------------------------------
    // direct BingxPerpetualCandle construction (compact-constructor branches)
    // ------------------------------------------------------------------

    private static final BigDecimal ONE = new BigDecimal("1.0");

    @Test
    void candle_rejectsNullOrWrongSymbol() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle(null, "15m", 1L, ONE, ONE, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("ETH-USDT", "15m", 1L, ONE, ONE, ONE, ONE, ONE));
    }

    @Test
    void candle_rejectsNullOrWrongInterval() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", null, 1L, ONE, ONE, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "1h", 1L, ONE, ONE, ONE, ONE, ONE));
    }

    @Test
    void candle_rejectsNegativeOpenTime() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", -1L, ONE, ONE, ONE, ONE, ONE));
    }

    @Test
    void candle_rejectsNullOrNonPositiveOhlc() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, null, ONE, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, null, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, ONE, null, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, ONE, ONE, null, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, BigDecimal.ZERO, ONE, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualCandle(
                                "BTC-USDT", "15m", 1L, new BigDecimal("-1.0"), ONE, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, BigDecimal.ZERO, ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualCandle(
                                "BTC-USDT", "15m", 1L, ONE, new BigDecimal("-1.0"), ONE, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, ONE, BigDecimal.ZERO, ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualCandle(
                                "BTC-USDT", "15m", 1L, ONE, ONE, new BigDecimal("-1.0"), ONE, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, ONE, ONE, BigDecimal.ZERO, ONE));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualCandle(
                                "BTC-USDT", "15m", 1L, ONE, ONE, ONE, new BigDecimal("-1.0"), ONE));
    }

    @Test
    void candle_rejectsNullOrNegativeVolume() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, ONE, ONE, ONE, null));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualCandle(
                                "BTC-USDT", "15m", 1L, ONE, ONE, ONE, ONE, new BigDecimal("-1.0")));
    }

    @Test
    void candle_acceptsZeroVolume() {
        BingxPerpetualCandle candle = new BingxPerpetualCandle("BTC-USDT", "15m", 1L, ONE, ONE, ONE, ONE, BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, candle.volume());
    }

    // ------------------------------------------------------------------
    // shared test fixtures / helpers
    // ------------------------------------------------------------------

    private static final String ONE_CANDLE =
            candleJson(1700000000000L, "62800.0", "62900.0", "62700.0", "62850.0", "100.0");

    private static String candleJson(
            long time, String open, String high, String low, String close, String volume) {
        return "{\"time\":"
                + time
                + ",\"open\":\""
                + open
                + "\",\"high\":\""
                + high
                + "\",\"low\":\""
                + low
                + "\",\"close\":\""
                + close
                + "\",\"volume\":\""
                + volume
                + "\"}";
    }

    /** Builds a valid candle JSON object with a single named field replaced by a raw (unquoted-safe) token. */
    private static String candleJsonWithField(String fieldToReplace, String rawValue) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("time", "1");
        fields.put("open", "\"1.0\"");
        fields.put("high", "\"2.0\"");
        fields.put("low", "\"0.5\"");
        fields.put("close", "\"1.5\"");
        fields.put("volume", "\"1.0\"");
        fields.put(fieldToReplace, rawValue);

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String batchJson(String... candles) {
        StringBuilder sb = new StringBuilder("{\"code\":0,\"msg\":\"\",\"data\":[");
        for (int i = 0; i < candles.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(candles[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String batchJsonOfSize(int size) {
        String[] candles = new String[size];
        for (int i = 0; i < size; i++) {
            candles[i] =
                    candleJson(1_700_000_000_000L + (900_000L * i), "1.0", "2.0", "0.5", "1.5", "1.0");
        }
        return batchJson(candles);
    }

    private static RawResponse ok(String json) {
        return new RawResponse(200, json.getBytes(StandardCharsets.UTF_8));
    }

    private static RawResponse status(int code) {
        return new RawResponse(code, "{}".getBytes(StandardCharsets.UTF_8));
    }

    private static BingxPublicMarketDataClient clientWithResponse(RawResponse response) {
        return new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(response));
    }

    private static void assertRejectsStatus(int statusCode) {
        RecordingTransport transport = RecordingTransport.ofResponse(status(statusCode));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
    }

    private static void assertRejectsBody(String body) {
        assertRejectsBody(body, null);
    }

    private static void assertRejectsBody(String body, String message) {
        BingxPublicMarketDataClient client = clientWithResponse(ok(body));
        if (message != null) {
            assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles, message);
        } else {
            assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
        }
    }

    /** Fake {@link Transport}: captures every {@link HttpRequest} it receives; never touches the network. */
    private static final class RecordingTransport implements Transport {

        private final List<HttpRequest> requests = new ArrayList<>();
        private final RawResponse response;
        private final IOException ioFailure;
        private final InterruptedException interruptedFailure;

        private RecordingTransport(RawResponse response, IOException ioFailure, InterruptedException interruptedFailure) {
            this.response = response;
            this.ioFailure = ioFailure;
            this.interruptedFailure = interruptedFailure;
        }

        static RecordingTransport ofResponse(RawResponse response) {
            return new RecordingTransport(response, null, null);
        }

        static RecordingTransport ofIoFailure(IOException e) {
            return new RecordingTransport(null, e, null);
        }

        static RecordingTransport ofInterruptedFailure(InterruptedException e) {
            return new RecordingTransport(null, null, e);
        }

        @Override
        public RawResponse send(HttpRequest request) throws IOException, InterruptedException {
            requests.add(request);
            if (ioFailure != null) {
                throw ioFailure;
            }
            if (interruptedFailure != null) {
                throw interruptedFailure;
            }
            return response;
        }

        int invocationCount() {
            return requests.size();
        }

        HttpRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }
    }
}
