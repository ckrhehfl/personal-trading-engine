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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fully offline test suite for {@link BingxPublicMarketDataClient}. No test in this class
 * performs real network I/O; all HTTP interaction is intercepted by {@link RecordingTransport}, a
 * package-private fake implementing the same {@link Transport} seam the production {@code
 * JdkHttpTransport} implements.
 */
class BingxPublicMarketDataClientTest {

    private static final URI VALID_BASE_URI = URI.create("https://open-api.bingx.com");
    private static final String EXPECTED_PATH = "/openApi/swap/v2/quote/trades";
    private static final String EXPECTED_QUERY = "symbol=BTC-USDT&limit=1000";

    @AfterEach
    void clearInterruptFlag() {
        // Test 37 deliberately interrupts the current thread; make sure that never leaks into a
        // later test in this (or any other) class regardless of pass/fail.
        Thread.interrupted();
    }

    // ------------------------------------------------------------------
    // 1-9: base URI validation
    // ------------------------------------------------------------------

    @Test
    void constructor_rejectsNullBaseUri() {
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(null));
    }

    @Test
    void constructor_rejectsRelativeUri() {
        URI relative = URI.create("/openApi/swap/v2/quote/trades");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(relative));
    }

    @Test
    void constructor_rejectsHttpScheme() {
        URI http = URI.create("http://open-api.bingx.com");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(http));
    }

    @Test
    void constructor_rejectsMissingHost() {
        URI noHost = URI.create("https:///path");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(noHost));
    }

    @Test
    void constructor_rejectsUserInfo() {
        URI withUserInfo = URI.create("https://user:pass@open-api.bingx.com");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(withUserInfo));
    }

    @Test
    void constructor_rejectsQuery() {
        URI withQuery = URI.create("https://open-api.bingx.com?a=b");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(withQuery));
    }

    @Test
    void constructor_rejectsFragment() {
        URI withFragment = URI.create("https://open-api.bingx.com#frag");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(withFragment));
    }

    @Test
    void constructor_rejectsNonRootSubPath() {
        URI subPath = URI.create("https://open-api.bingx.com/v1");
        assertThrows(BingxPublicMarketDataException.class, () -> new BingxPublicMarketDataClient(subPath));
    }

    @Test
    void constructor_acceptsHttpsRootBaseUri() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_TRADE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();
        assertEquals(1, trades.size());
    }

    // ------------------------------------------------------------------
    // 10-18: exact request shape
    // ------------------------------------------------------------------

    @Test
    void request_isExactGetToExactPathAndQueryOnce() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_TRADE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdtTrades();

        assertEquals(1, transport.invocationCount());
        HttpRequest sent = transport.lastRequest();
        assertEquals("GET", sent.method());
        assertEquals(EXPECTED_PATH, sent.uri().getRawPath());
        assertEquals(EXPECTED_QUERY, sent.uri().getRawQuery());
    }

    @Test
    void request_setsAcceptHeaderAndNoAuthHeaders() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_TRADE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdtTrades();

        HttpRequest sent = transport.lastRequest();
        assertEquals(List.of("application/json"), sent.headers().allValues("Accept"));
        assertTrue(sent.headers().firstValue("Authorization").isEmpty());
        assertTrue(sent.headers().firstValue("X-BX-APIKEY").isEmpty());
        assertTrue(sent.headers().firstValue("signature").isEmpty());
    }

    @Test
    void request_hasNoBody() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_TRADE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdtTrades();

        HttpRequest sent = transport.lastRequest();
        assertTrue(sent.bodyPublisher().isEmpty());
    }

    @Test
    void request_transportInvokedExactlyOnce() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok(batchJson(ONE_TRADE)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdtTrades();
        assertEquals(1, transport.invocationCount());
    }

    // ------------------------------------------------------------------
    // 19-29: successful batch parsing
    // ------------------------------------------------------------------

    @Test
    void fetch_parsesSingleElementBatch() {
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(ONE_TRADE)));
        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();

        assertEquals(1, trades.size());
        BingxPerpetualTrade trade = trades.get(0);
        assertEquals("BTC-USDT", trade.symbol());
        assertEquals(1700000000000L, trade.tradedAtEpochMs());
        assertTrue(trade.buyerMaker());
        assertEquals(new BigDecimal("62735.9"), trade.price());
        assertEquals(new BigDecimal("0.0001"), trade.quantity());
        assertEquals(new BigDecimal("6.27"), trade.quoteQuantity());
    }

    @Test
    void fetch_parsesMultiElementBatch() {
        String second = tradeJson(1700000001000L, false, "62740.0", "0.0002", "12.55");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(ONE_TRADE, second)));
        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();

        assertEquals(2, trades.size());
        assertTrue(trades.get(0).buyerMaker());
        assertFalse(trades.get(1).buyerMaker());
    }

    @Test
    void fetch_parses1000ElementBatch() {
        String batch = batchJsonOfSize(1000);
        BingxPublicMarketDataClient client = clientWithResponse(ok(batch));
        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();
        assertEquals(1000, trades.size());
    }

    @Test
    void fetch_preservesWireOrderWithoutSortingAndWithoutDeduplication() {
        // Deliberately non-monotonic timestamps, including an exact duplicate timestamp, to prove
        // the client neither sorts nor deduplicates - it only preserves array order.
        String first = tradeJson(500L, true, "1.0", "1.0", "1.0");
        String second = tradeJson(100L, false, "2.0", "1.0", "2.0");
        String third = tradeJson(100L, true, "3.0", "1.0", "3.0");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(first, second, third)));

        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();

        assertEquals(3, trades.size());
        assertEquals(500L, trades.get(0).tradedAtEpochMs());
        assertEquals(100L, trades.get(1).tradedAtEpochMs());
        assertEquals(100L, trades.get(2).tradedAtEpochMs());
        assertEquals(new BigDecimal("2.0"), trades.get(1).price());
        assertEquals(new BigDecimal("3.0"), trades.get(2).price());
    }

    @Test
    void fetch_returnsImmutableList() {
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(ONE_TRADE)));
        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();
        assertThrows(UnsupportedOperationException.class, () -> trades.add(trades.get(0)));
    }

    @Test
    void fetch_preservesDecimalScaleExactly() {
        String traded = tradeJson(1L, true, "62735.90", "0.00010", "6.270");
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(traded)));
        BingxPerpetualTrade trade = client.fetchRecentBtcUsdtTrades().get(0);

        assertEquals("62735.90", trade.price().toPlainString());
        assertEquals("0.00010", trade.quantity().toPlainString());
        assertEquals("6.270", trade.quoteQuantity().toPlainString());
    }

    @Test
    void fetch_ignoresAdditiveUnknownFields() {
        String tradeWithExtras =
                "{\"time\":1700000000000,\"isBuyerMaker\":true,\"price\":\"62735.9\",\"qty\":\"0.0001\","
                        + "\"quoteQty\":\"6.27\",\"fillId\":\"692822024\",\"ts\":1700000000000}";
        String responseWithExtraTopLevel =
                "{\"code\":0,\"msg\":\"\",\"data\":[" + tradeWithExtras + "],\"extraTopLevelField\":true}";
        BingxPublicMarketDataClient client = clientWithResponse(ok(responseWithExtraTopLevel));

        List<BingxPerpetualTrade> trades = client.fetchRecentBtcUsdtTrades();
        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("62735.9"), trades.get(0).price());
    }

    @Test
    void fetch_isDeterministicAcrossFreshClientInstancesGivenEqualResponse() {
        String responseBody = batchJson(ONE_TRADE);
        BingxPublicMarketDataClient clientA =
                new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(ok(responseBody)));
        BingxPublicMarketDataClient clientB =
                new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(ok(responseBody)));

        assertEquals(clientA.fetchRecentBtcUsdtTrades(), clientB.fetchRecentBtcUsdtTrades());
    }

    // ------------------------------------------------------------------
    // 30-38: HTTP status / transport failure handling
    // ------------------------------------------------------------------

    @Test
    void fetch_rejects204() {
        assertRejectsStatus(204);
    }

    @Test
    void fetch_rejects3xxWithoutFollowingOrRetrying() {
        RecordingTransport transport = RecordingTransport.ofResponse(status(302));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void fetch_rejects400() {
        assertRejectsStatus(400);
    }

    @Test
    void fetch_rejects418WithoutRetry() {
        RecordingTransport transport = RecordingTransport.ofResponse(status(418));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void fetch_rejects429WithoutRetry() {
        RecordingTransport transport = RecordingTransport.ofResponse(status(429));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void fetch_rejects500() {
        assertRejectsStatus(500);
    }

    @Test
    void fetch_wrapsIOException() {
        RecordingTransport transport = RecordingTransport.ofIoFailure(new IOException("connection reset"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        BingxPublicMarketDataException thrown =
                assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
        assertTrue(thrown.getCause() instanceof IOException);
    }

    @Test
    void fetch_wrapsInterruptedExceptionAndRestoresInterruptFlag() {
        RecordingTransport transport = RecordingTransport.ofInterruptedFailure(new InterruptedException("stopped"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);

        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
        assertTrue(Thread.currentThread().isInterrupted());
        // Explicit local cleanup in addition to the @AfterEach safety net.
        Thread.interrupted();
    }

    // ------------------------------------------------------------------
    // 39-49: body size / JSON envelope strictness
    // ------------------------------------------------------------------

    @Test
    void fetch_rejectsBodyOver1MiB() {
        StringBuilder oversized = new StringBuilder("{\"code\":0,\"msg\":\"");
        oversized.append("x".repeat(1_048_576));
        oversized.append("\",\"data\":[]}");
        BingxPublicMarketDataClient client = clientWithResponse(ok(oversized.toString()));
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
    }

    @Test
    void fetch_rejectsMalformedUtf8() {
        byte[] malformed = new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
        RecordingTransport transport = RecordingTransport.ofResponse(new RawResponse(200, malformed));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
    }

    @Test
    void fetch_rejectsMalformedJson() {
        assertRejectsBody("{not valid json");
    }

    @Test
    void fetch_rejectsTrailingJson() {
        assertRejectsBody(batchJson(ONE_TRADE) + "{}");
    }

    @Test
    void fetch_rejectsNonObjectTopLevel() {
        assertRejectsBody("[]");
    }

    @Test
    void fetch_rejectsMissingWrongTypeOrNonzeroCode() {
        assertRejectsBody("{\"msg\":\"\",\"data\":[" + ONE_TRADE + "]}");
        assertRejectsBody("{\"code\":\"0\",\"msg\":\"\",\"data\":[" + ONE_TRADE + "]}");
        assertRejectsBody("{\"code\":1,\"msg\":\"\",\"data\":[" + ONE_TRADE + "]}");
    }

    @Test
    void fetch_rejectsMissingOrWrongTypeMsg() {
        assertRejectsBody("{\"code\":0,\"data\":[" + ONE_TRADE + "]}");
        assertRejectsBody("{\"code\":0,\"msg\":123,\"data\":[" + ONE_TRADE + "]}");
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
    void fetch_rejectsNonObjectTradeElement() {
        assertRejectsBody("{\"code\":0,\"msg\":\"\",\"data\":[\"not-an-object\"]}");
    }

    // ------------------------------------------------------------------
    // 50-57: per-trade field validation
    // ------------------------------------------------------------------

    @Test
    void fetch_rejectsEachRequiredFieldMissing() {
        assertRejectsBody(batchJson("{\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(batchJson("{\"time\":1,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(batchJson("{\"time\":1,\"isBuyerMaker\":true,\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\"}"));
    }

    @Test
    void fetch_rejectsEachRequiredFieldNull() {
        assertRejectsBody(
                batchJson("{\"time\":null,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":null,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":null,\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":null,\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":null}"));
    }

    @Test
    void fetch_rejectsEachRequiredFieldWrongType() {
        assertRejectsBody(
                batchJson("{\"time\":\"1\",\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":\"true\",\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":1.0,\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":1.0,\"quoteQty\":\"1.0\"}"));
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\",\"quoteQty\":1.0}"));
    }

    @Test
    void fetch_rejectsNegativeOrOutOfLongRangeTime() {
        assertRejectsBody(batchJson(tradeJson(-1L, true, "1.0", "1.0", "1.0")));
        assertRejectsBody(
                batchJson(
                        "{\"time\":99999999999999999999999999,\"isBuyerMaker\":true,\"price\":\"1.0\",\"qty\":\"1.0\","
                                + "\"quoteQty\":\"1.0\"}"));
    }

    @Test
    void fetch_rejectsDecimalAsNumberToken() {
        assertRejectsBody(
                batchJson("{\"time\":1,\"isBuyerMaker\":true,\"price\":1,\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}"));
    }

    @Test
    void fetch_rejectsWhitespaceSignOrExponentDecimal() {
        for (String bad : List.of(" 1.0", "1.0 ", "+1.0", "-1.0", "1e10", "1E10", "NaN", "Infinity", "")) {
            assertRejectsBody(batchJson(tradeJsonRawPrice(bad)), "expected rejection for price=" + bad);
        }
    }

    @Test
    void fetch_rejectsZeroOrNegativeDecimal() {
        assertRejectsBody(batchJson(tradeJsonRawPrice("0")));
        assertRejectsBody(batchJson(tradeJsonRawPrice("0.0")));
        assertRejectsBody(batchJson(tradeJsonRawPrice("-1.0")));
    }

    @Test
    void fetch_rejectsOverlongDecimalWithoutEchoingRawValueInMessage() {
        String tooLong = "1" + "0".repeat(70);
        BingxPublicMarketDataClient client = clientWithResponse(ok(batchJson(tradeJsonRawPrice(tooLong))));
        BingxPublicMarketDataException thrown =
                assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
        assertFalse(thrown.getMessage().contains(tooLong));
    }

    // ------------------------------------------------------------------
    // 58-60: direct BingxPerpetualTrade construction (all 9 compact-constructor branches)
    // ------------------------------------------------------------------

    @Test
    void trade_rejectsNullSymbol() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                null, 1L, true, new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("1.0")));
    }

    @Test
    void trade_rejectsWrongSymbol() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "ETH-USDT", 1L, true, new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("1.0")));
    }

    @Test
    void trade_rejectsNegativeTime() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT", -1L, true, new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("1.0")));
    }

    @Test
    void trade_rejectsNullOrNonPositivePrice() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualTrade("BTC-USDT", 1L, true, null, new BigDecimal("1.0"), new BigDecimal("1.0")));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT", 1L, true, BigDecimal.ZERO, new BigDecimal("1.0"), new BigDecimal("1.0")));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT",
                                1L,
                                true,
                                new BigDecimal("-1.0"),
                                new BigDecimal("1.0"),
                                new BigDecimal("1.0")));
    }

    @Test
    void trade_rejectsNullOrNonPositiveQuantity() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualTrade("BTC-USDT", 1L, true, new BigDecimal("1.0"), null, new BigDecimal("1.0")));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT", 1L, true, new BigDecimal("1.0"), BigDecimal.ZERO, new BigDecimal("1.0")));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT",
                                1L,
                                true,
                                new BigDecimal("1.0"),
                                new BigDecimal("-1.0"),
                                new BigDecimal("1.0")));
    }

    @Test
    void trade_rejectsNullOrNonPositiveQuoteQuantity() {
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> new BingxPerpetualTrade("BTC-USDT", 1L, true, new BigDecimal("1.0"), new BigDecimal("1.0"), null));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT", 1L, true, new BigDecimal("1.0"), new BigDecimal("1.0"), BigDecimal.ZERO));
        assertThrows(
                BingxPublicMarketDataException.class,
                () ->
                        new BingxPerpetualTrade(
                                "BTC-USDT",
                                1L,
                                true,
                                new BigDecimal("1.0"),
                                new BigDecimal("1.0"),
                                new BigDecimal("-1.0")));
    }

    // ------------------------------------------------------------------
    // shared test fixtures / helpers
    // ------------------------------------------------------------------

    private static final String ONE_TRADE = tradeJson(1700000000000L, true, "62735.9", "0.0001", "6.27");

    private static String tradeJson(long time, boolean isBuyerMaker, String price, String qty, String quoteQty) {
        return "{\"time\":"
                + time
                + ",\"isBuyerMaker\":"
                + isBuyerMaker
                + ",\"price\":\""
                + price
                + "\",\"qty\":\""
                + qty
                + "\",\"quoteQty\":\""
                + quoteQty
                + "\"}";
    }

    private static String tradeJsonRawPrice(String rawPrice) {
        return "{\"time\":1,\"isBuyerMaker\":true,\"price\":\"" + rawPrice + "\",\"qty\":\"1.0\",\"quoteQty\":\"1.0\"}";
    }

    private static String batchJson(String... trades) {
        StringBuilder sb = new StringBuilder("{\"code\":0,\"msg\":\"\",\"data\":[");
        for (int i = 0; i < trades.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(trades[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String batchJsonOfSize(int size) {
        String[] trades = new String[size];
        for (int i = 0; i < size; i++) {
            trades[i] = tradeJson(1_700_000_000_000L + i, i % 2 == 0, "1.0", "1.0", "1.0");
        }
        return batchJson(trades);
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
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
    }

    private static void assertRejectsBody(String body) {
        assertRejectsBody(body, null);
    }

    private static void assertRejectsBody(String body, String message) {
        BingxPublicMarketDataClient client = clientWithResponse(ok(body));
        if (message != null) {
            assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades, message);
        } else {
            assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
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
