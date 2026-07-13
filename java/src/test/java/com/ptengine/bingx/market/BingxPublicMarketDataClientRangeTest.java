package com.ptengine.bingx.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Fully offline, deterministic test suite for {@link
 * BingxPublicMarketDataClient#fetchBtcUsdt15mCandlesInRange(long, long)} (Candidate 20). No test in
 * this class opens a socket, sleeps, reads the real clock, or uses a mocking framework. HTTP
 * interaction is intercepted at the package-private {@link Transport} seam via {@link
 * RecordingTransport}, the same seam Candidate 18/19's sibling test classes use.
 */
class BingxPublicMarketDataClientRangeTest {

    private static final URI VALID_BASE_URI = URI.create("https://open-api.bingx.com");
    private static final String EXPECTED_PATH = "/openApi/swap/v3/quote/klines";

    /** 15 minutes in milliseconds &mdash; the candle grid confirmed by live discovery (D015). */
    private static final long INTERVAL = 900_000L;

    /** An arbitrary but exactly-grid-aligned base time, built by construction rather than a magic literal. */
    private static final long ALIGNED_START = 2_000_000L * INTERVAL;

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    // ------------------------------------------------------------------
    // A: argument validation (zero transport invocations on rejection)
    // ------------------------------------------------------------------

    @Test
    void range_rejectsNegativeStartTime() {
        // -INTERVAL (not -1) is deliberately grid-aligned and otherwise produces a valid,
        // in-bound range: if the non-negativity guard were ever removed, every other check
        // (alignment/ordering/width) would still pass and the exploding transport below would
        // fire. A non-aligned negative value like -1 would be caught by the alignment check too,
        // masking a regression in the non-negativity guard specifically.
        assertRejectsArgumentsWithoutTransport(-INTERVAL, 0L);
    }

    @Test
    void range_rejectsNegativeEndTime() {
        assertRejectsArgumentsWithoutTransport(ALIGNED_START, -1L);
    }

    @Test
    void range_acceptsZeroStartTime() {
        // The lowest legal boundary value for the non-negativity guard, exercised as a positive
        // (accepted) case rather than only ever appearing inside a rejected input.
        long end = INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(0L)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(0L, end);
        assertEquals(1, candles.size());
        assertEquals(0L, candles.get(0).openTimeEpochMs());
    }

    @Test
    void range_rejectsMisalignedStartTime() {
        assertRejectsArgumentsWithoutTransport(ALIGNED_START + 1, ALIGNED_START + INTERVAL);
    }

    @Test
    void range_rejectsMisalignedEndTime() {
        assertRejectsArgumentsWithoutTransport(ALIGNED_START, ALIGNED_START + INTERVAL + 1);
    }

    @Test
    void range_rejectsEqualBounds() {
        assertRejectsArgumentsWithoutTransport(ALIGNED_START, ALIGNED_START);
    }

    @Test
    void range_rejectsReversedBounds() {
        assertRejectsArgumentsWithoutTransport(ALIGNED_START + INTERVAL, ALIGNED_START);
    }

    @Test
    void range_rejectsEndTimeAboveExchangeDocumentedMaximum() {
        // 17,514,115,200,000 is the exchange's own live-confirmed absolute endTime ceiling
        // (D015); one grid step above it must be rejected before any transport call rather than
        // relying on the server's code=109400 rejection after a round trip.
        long aboveCeiling = 17_514_115_200_000L + INTERVAL;
        assertRejectsArgumentsWithoutTransport(aboveCeiling - INTERVAL, aboveCeiling);
    }

    @Test
    void range_rejectsRangeExceedingSafeBound() {
        // 1001 candles' worth: one interval beyond the verified 1000-candle safe bound.
        assertRejectsArgumentsWithoutTransport(ALIGNED_START, ALIGNED_START + 1001 * INTERVAL);
    }

    @Test
    void range_acceptsExactMinimumValidRange() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(ALIGNED_START)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertEquals(1, candles.size());
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void range_acceptsExactMaximumSafeRange() {
        long end = ALIGNED_START + 1000 * INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJsonOfSize(ALIGNED_START, 1000)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertEquals(1000, candles.size());
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void range_acceptsLargestValidValuesAtTheDocumentedCeilingWithoutOverflow() {
        // The largest values now reachable through valid input at all, since anything above the
        // exchange's documented endTime ceiling is rejected first: proves the width check
        // (subtraction-based) behaves correctly right at that realistic boundary.
        long largestValidStart = 17_514_115_200_000L - INTERVAL;
        long largestValidEnd = 17_514_115_200_000L;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(largestValidStart)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        List<BingxPerpetualCandle> candles =
                client.fetchBtcUsdt15mCandlesInRange(largestValidStart, largestValidEnd);
        assertEquals(1, candles.size());
    }

    @Test
    void range_rejectsNearLongMaxValueInputsCleanlyWithoutArithmeticOverflow() {
        // Values this large are now caught by the endTime-ceiling check well before the
        // width/alignment arithmetic runs, but this proves that arithmetic still degrades cleanly
        // (a single BingxPublicMarketDataException, never an ArithmeticException or wraparound
        // acceptance) for genuinely extreme inputs, rather than relying on the ceiling check alone.
        long hugeAlignedStart = (Long.MAX_VALUE / INTERVAL - 2) * INTERVAL;
        long hugeAlignedEnd = hugeAlignedStart + INTERVAL;
        assertRejectsArgumentsWithoutTransport(hugeAlignedStart, hugeAlignedEnd);
    }

    @Test
    void range_invalidArgumentsNeverInvokeTransport() {
        Transport explodingTransport =
                request -> {
                    throw new AssertionError("transport must not be invoked for invalid arguments");
                };
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, explodingTransport);
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> client.fetchBtcUsdt15mCandlesInRange(-1L, ALIGNED_START));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, ALIGNED_START));
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, ALIGNED_START + 1001 * INTERVAL));
    }

    // ------------------------------------------------------------------
    // B: exact request shape
    // ------------------------------------------------------------------

    @Test
    void request_isExactGetToExactPathAndQueryOnce() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(ALIGNED_START)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);

        assertEquals(1, transport.invocationCount());
        HttpRequest sent = transport.lastRequest();
        assertEquals("GET", sent.method());
        assertEquals(EXPECTED_PATH, sent.uri().getRawPath());
        assertEquals(
                "symbol=BTC-USDT&interval=15m&startTime=" + ALIGNED_START + "&endTime=" + end + "&limit=1000",
                sent.uri().getRawQuery());
    }

    @Test
    void request_setsAcceptHeaderAndNoAuthHeaders() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(ALIGNED_START)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);

        HttpRequest sent = transport.lastRequest();
        assertEquals(List.of("application/json"), sent.headers().allValues("Accept"));
        assertTrue(sent.headers().firstValue("Authorization").isEmpty());
        assertTrue(sent.headers().firstValue("X-BX-APIKEY").isEmpty());
        assertTrue(sent.headers().firstValue("signature").isEmpty());
    }

    @Test
    void request_hasNoBody() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(ALIGNED_START)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertTrue(transport.lastRequest().bodyPublisher().isEmpty());
    }

    @Test
    void request_transportInvokedExactlyOnce() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(ALIGNED_START)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertEquals(1, transport.invocationCount());
    }

    // ------------------------------------------------------------------
    // C: successful parsing
    // ------------------------------------------------------------------

    @Test
    void fetch_parsesMultiElementBatch() {
        long end = ALIGNED_START + 2 * INTERVAL;
        String body = rangeBatchJson(candleJson(ALIGNED_START + INTERVAL), candleJson(ALIGNED_START));
        BingxPublicMarketDataClient client = clientWithResponse(ok(body));
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);

        assertEquals(2, candles.size());
        assertEquals(ALIGNED_START + INTERVAL, candles.get(0).openTimeEpochMs());
        assertEquals(ALIGNED_START, candles.get(1).openTimeEpochMs());
    }

    @Test
    void fetch_preservesWireOrderWithoutSortingOrDeduplication() {
        // Two rows sharing an open time (allowed by a range wide enough for 2 candles' worth of
        // capacity), deliberately out of any chronological order: proves the client only preserves
        // wire order and does not sort or deduplicate.
        long end = ALIGNED_START + 2 * INTERVAL;
        String first = candleJson(ALIGNED_START, "0.8");
        String second = candleJson(ALIGNED_START, "1.2");
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(first, second)));

        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);

        assertEquals(2, candles.size());
        assertEquals(new BigDecimal("0.8"), candles.get(0).open());
        assertEquals(new BigDecimal("1.2"), candles.get(1).open());
    }

    @Test
    void fetch_returnsImmutableList() {
        long end = ALIGNED_START + INTERVAL;
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(ALIGNED_START)));
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertThrows(UnsupportedOperationException.class, () -> candles.add(candles.get(0)));
    }

    @Test
    void fetch_preservesDecimalScaleExactly() {
        long end = ALIGNED_START + INTERVAL;
        String candle = candleJsonFull(ALIGNED_START, "62800.10", "62900.100", "62700.0", "62850.50", "0.00010");
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(candle)));
        BingxPerpetualCandle parsed = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end).get(0);
        assertEquals("62800.10", parsed.open().toPlainString());
        assertEquals("0.00010", parsed.volume().toPlainString());
    }

    @Test
    void fetch_isDeterministicAcrossFreshClientInstancesGivenEqualResponse() {
        long end = ALIGNED_START + INTERVAL;
        String responseBody = rangeBatchJson(ALIGNED_START);
        BingxPublicMarketDataClient clientA =
                new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(ok(responseBody)));
        BingxPublicMarketDataClient clientB =
                new BingxPublicMarketDataClient(VALID_BASE_URI, RecordingTransport.ofResponse(ok(responseBody)));

        assertEquals(
                clientA.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end),
                clientB.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_ignoresAdditiveUnknownFields() {
        long end = ALIGNED_START + INTERVAL;
        String candleWithExtras =
                "{\"time\":" + ALIGNED_START + ",\"open\":\"1.0\",\"high\":\"2.0\",\"low\":\"0.5\","
                        + "\"close\":\"1.5\",\"volume\":\"1.0\",\"closeTime\":" + (ALIGNED_START + INTERVAL - 1) + "}";
        String response = "{\"code\":0,\"msg\":\"\",\"data\":[" + candleWithExtras + "],\"extraTopLevelField\":true}";
        BingxPublicMarketDataClient client = clientWithResponse(ok(response));
        assertEquals(1, client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end).size());
    }

    @Test
    void fetch_allowsLegitimateEmptySuccessUnlikeRecentMethods() {
        // Locked empty-result policy (D015): a valid, non-degenerate range may legitimately return
        // zero rows (e.g. a future range, or a range before market data existed) and this must not
        // throw, unlike fetchRecentBtcUsdt15mCandles()/fetchRecentBtcUsdtTrades().
        long end = ALIGNED_START + INTERVAL;
        BingxPublicMarketDataClient client = clientWithResponse(ok("{\"code\":0,\"msg\":\"\",\"data\":[]}"));
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertEquals(0, candles.size());
    }

    // ------------------------------------------------------------------
    // D: range enforcement (fail closed, not silently filtered)
    // ------------------------------------------------------------------

    @Test
    void fetch_rejectsRowBelowLowerBound() {
        long end = ALIGNED_START + INTERVAL;
        String belowBound = candleJson(ALIGNED_START - INTERVAL);
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(belowBound)));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_rejectsRowExactlyAtExclusiveUpperBound() {
        long end = ALIGNED_START + INTERVAL;
        // The row's own open time equals endTime exactly: excluded per the locked [start, end)
        // contract, so a server/fake that returns it must be rejected, not silently kept.
        String atUpperBound = candleJson(end);
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(atUpperBound)));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_rejectsRowStrictlyAboveUpperBound() {
        long end = ALIGNED_START + INTERVAL;
        String aboveUpperBound = candleJson(end + INTERVAL);
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(aboveUpperBound)));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_acceptsRowExactlyAtInclusiveLowerBound() {
        long end = ALIGNED_START + INTERVAL;
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(ALIGNED_START)));
        List<BingxPerpetualCandle> candles = client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertEquals(1, candles.size());
        assertEquals(ALIGNED_START, candles.get(0).openTimeEpochMs());
    }

    @Test
    void fetch_rejectsImpossibleResponseCountForNarrowRange() {
        // Range width allows at most 1 candle, but the fake response returns two rows that are
        // each individually within [start, end): the per-row bound check alone would not catch
        // this, so the count-vs-range-capacity check must.
        long end = ALIGNED_START + INTERVAL;
        String duplicate = candleJson(ALIGNED_START);
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(duplicate, duplicate)));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_rejectsMoreThan1000Elements() {
        long end = ALIGNED_START + 1000 * INTERVAL;
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJsonOfSize(ALIGNED_START, 1001)));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    // ------------------------------------------------------------------
    // E: HTTP / envelope / body failure modes
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
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(status(302));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
        assertEquals(1, transport.invocationCount());
    }

    @Test
    void fetch_wrapsIOException() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofIoFailure(new IOException("connection reset"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        BingxPublicMarketDataException thrown =
                assertThrows(
                        BingxPublicMarketDataException.class,
                        () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
        assertTrue(thrown.getCause() instanceof IOException);
    }

    @Test
    void fetch_wrapsInterruptedExceptionAndRestoresInterruptFlag() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofInterruptedFailure(new InterruptedException("stopped"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);

        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void fetch_rejectsBodyOver1MiB() {
        long end = ALIGNED_START + INTERVAL;
        StringBuilder oversized = new StringBuilder("{\"code\":0,\"msg\":\"");
        oversized.append("x".repeat(BingxPublicMarketDataClient.MAX_RESPONSE_BYTES));
        oversized.append("\",\"data\":[]}");
        BingxPublicMarketDataClient client = clientWithResponse(ok(oversized.toString()));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_rejectsMalformedUtf8() {
        long end = ALIGNED_START + INTERVAL;
        byte[] malformed = new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
        RecordingTransport transport = RecordingTransport.ofResponse(new RawResponse(200, malformed));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    @Test
    void fetch_rejectsMalformedJson() {
        assertRejectsBody("{not valid json");
    }

    @Test
    void fetch_rejectsTrailingJson() {
        assertRejectsBody(rangeBatchJson(ALIGNED_START) + "{}");
    }

    @Test
    void fetch_rejectsNonObjectTopLevel() {
        assertRejectsBody("[]");
    }

    @Test
    void fetch_rejectsMissingWrongTypeOrNonzeroCode() {
        assertRejectsBody("{\"msg\":\"\",\"data\":[]}");
        assertRejectsBody("{\"code\":\"0\",\"msg\":\"\",\"data\":[]}");
        assertRejectsBody("{\"code\":109400,\"msg\":\"startTime is later than endTime.\",\"data\":{}}");
    }

    @Test
    void fetch_rejectsMissingOrWrongTypeMsg() {
        assertRejectsBody("{\"code\":0,\"data\":[]}");
        assertRejectsBody("{\"code\":0,\"msg\":123,\"data\":[]}");
    }

    @Test
    void fetch_rejectsMissingOrWrongTypeData() {
        assertRejectsBody("{\"code\":0,\"msg\":\"\"}");
        assertRejectsBody("{\"code\":0,\"msg\":\"\",\"data\":{}}");
    }

    @Test
    void fetch_rejectsNonObjectCandleElement() {
        assertRejectsBody("{\"code\":0,\"msg\":\"\",\"data\":[\"not-an-object\"]}");
    }

    @Test
    void fetch_rejectsMalformedCandleMissingField() {
        long end = ALIGNED_START + INTERVAL;
        String missingClose =
                "{\"time\":" + ALIGNED_START + ",\"open\":\"1.0\",\"high\":\"2.0\",\"low\":\"0.5\",\"volume\":\"1.0\"}";
        BingxPublicMarketDataClient client = clientWithResponse(ok(rangeBatchJson(missingClose)));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    // ------------------------------------------------------------------
    // F: regression - existing methods and one-GET invariant unchanged
    // ------------------------------------------------------------------

    @Test
    void regression_existingRecentCandlesMethodStillRejectsEmptyData() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok("{\"code\":0,\"msg\":\"\",\"data\":[]}"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdt15mCandles);
    }

    @Test
    void regression_existingRecentTradesMethodStillRejectsEmptyData() {
        RecordingTransport transport = RecordingTransport.ofResponse(ok("{\"code\":0,\"msg\":\"\",\"data\":[]}"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(BingxPublicMarketDataException.class, client::fetchRecentBtcUsdtTrades);
    }

    @Test
    void regression_existingRecentCandlesRequestShapeUnchanged() {
        String oneTrade = "{\"time\":1,\"open\":\"1.0\",\"high\":\"2.0\",\"low\":\"0.5\",\"close\":\"1.5\",\"volume\":\"1.0\"}";
        RecordingTransport transport =
                RecordingTransport.ofResponse(ok("{\"code\":0,\"msg\":\"\",\"data\":[" + oneTrade + "]}"));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchRecentBtcUsdt15mCandles();

        assertEquals(1, transport.invocationCount());
        HttpRequest sent = transport.lastRequest();
        assertEquals(EXPECTED_PATH, sent.uri().getRawPath());
        assertEquals("symbol=BTC-USDT&interval=15m&limit=1000", sent.uri().getRawQuery());
    }

    @Test
    void regression_rangeMethodNeverIssuesMoreThanOneGet() {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(ok(rangeBatchJson(ALIGNED_START)));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end);
        assertEquals(1, transport.invocationCount());
    }

    // ------------------------------------------------------------------
    // shared test fixtures / helpers
    // ------------------------------------------------------------------

    private static String candleJson(long time) {
        return candleJson(time, "1.0");
    }

    private static String candleJson(long time, String open) {
        return "{\"time\":" + time + ",\"open\":\"" + open + "\",\"high\":\"2.0\",\"low\":\"0.5\","
                + "\"close\":\"1.5\",\"volume\":\"1.0\"}";
    }

    private static String candleJsonFull(
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

    private static String rangeBatchJson(long onlyCandleTime) {
        return rangeBatchJson(candleJson(onlyCandleTime));
    }

    private static String rangeBatchJson(String... candles) {
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

    private static String rangeBatchJsonOfSize(long start, int size) {
        String[] candles = new String[size];
        for (int i = 0; i < size; i++) {
            candles[i] = candleJson(start + (long) i * INTERVAL);
        }
        return rangeBatchJson(candles);
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

    private static void assertRejectsArgumentsWithoutTransport(long startTimeEpochMs, long endTimeEpochMs) {
        Transport explodingTransport =
                request -> {
                    throw new AssertionError("transport must not be invoked for invalid arguments");
                };
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, explodingTransport);
        assertThrows(
                BingxPublicMarketDataException.class,
                () -> client.fetchBtcUsdt15mCandlesInRange(startTimeEpochMs, endTimeEpochMs));
    }

    private static void assertRejectsStatus(int statusCode) {
        long end = ALIGNED_START + INTERVAL;
        RecordingTransport transport = RecordingTransport.ofResponse(status(statusCode));
        BingxPublicMarketDataClient client = new BingxPublicMarketDataClient(VALID_BASE_URI, transport);
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
    }

    private static void assertRejectsBody(String body) {
        long end = ALIGNED_START + INTERVAL;
        BingxPublicMarketDataClient client = clientWithResponse(ok(body));
        assertThrows(
                BingxPublicMarketDataException.class, () -> client.fetchBtcUsdt15mCandlesInRange(ALIGNED_START, end));
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
