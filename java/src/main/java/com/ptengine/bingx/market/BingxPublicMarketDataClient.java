package com.ptengine.bingx.market;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One-shot, read-only client for public, unauthenticated BingX Swap market-data endpoints for the
 * {@code BTC-USDT} USDT-M perpetual instrument: recent trades ({@code GET
 * /openApi/swap/v2/quote/trades}), recent 15-minute klines, and one caller-supplied, bounded
 * historical 15-minute kline range (both {@code GET /openApi/swap/v3/quote/klines}).
 *
 * <p>Each fetch method performs exactly one HTTP GET per call: no retry, backoff, polling,
 * caching, or redirect following. The trades request always sends {@code
 * symbol=BTC-USDT&limit=1000}, but {@code limit} is a request-side upper-bound intent only &mdash;
 * this class does not trust or claim the server honors it as a count guarantee there. The recent
 * klines request always sends {@code symbol=BTC-USDT&interval=15m&limit=1000}; live observation
 * confirmed {@code limit} is honored there for requested values up to 1000, but this class still
 * validates the actual batch size rather than trusting the request parameter. Response array wire
 * order is preserved exactly as received; no sort, reverse, deduplication, aggregation, or "latest"
 * selection is performed anywhere in this class. No fetch method claims that any returned candle is
 * closed rather than still forming.
 *
 * <p>The bounded-range kline fetch ({@link #fetchBtcUsdt15mCandlesInRange(long, long)}) adds
 * {@code startTime}/{@code endTime} query parameters over the same endpoint. Live discovery
 * (Candidate 20 / Issue #46, decision {@code docs/11_DECISION_LOG.md} D015) confirmed that, when
 * both are supplied together, BingX applies a half-open {@code [startTime, endTime)} filter on
 * candle open time and silently truncates &mdash; keeping only the newest rows, with no error
 * signal &mdash; once the implied row count would exceed the verified 1000-row cap. This method
 * therefore requires both bounds to be 15-minute-grid-aligned and the span to cover at most 1000
 * candles, so that silent truncation is structurally unreachable rather than merely detected after
 * the fact. This is intentionally narrower than what the exchange itself accepts.
 *
 * <p>This class carries no credentials, no signing, no account/order authority, no scheduler or
 * runtime loop, and no persistence. It is not a market-data collection service and does not claim
 * to be one.
 */
public final class BingxPublicMarketDataClient {

    private static final String REQUIRED_SCHEME = "https";
    private static final String TRADES_REQUEST_PATH = "/openApi/swap/v2/quote/trades";
    private static final String TRADES_REQUEST_QUERY = "symbol=BTC-USDT&limit=1000";
    private static final String CANDLES_REQUEST_PATH = "/openApi/swap/v3/quote/klines";
    private static final String CANDLES_REQUEST_QUERY = "symbol=BTC-USDT&interval=15m&limit=1000";
    private static final String REQUIRED_SYMBOL = "BTC-USDT";
    private static final String REQUIRED_INTERVAL = "15m";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_DECIMAL_LENGTH = 64;
    private static final int MAX_BATCH_SIZE = 1000;

    /** The 15-minute candle grid width in milliseconds; every observed candle open time is a multiple of this. */
    static final long CANDLE_INTERVAL_MILLIS = 900_000L;

    /**
     * The largest {@code endTimeEpochMs - startTimeEpochMs} span guaranteed not to require more
     * than {@link #MAX_BATCH_SIZE} candles, and therefore guaranteed not to trigger the silent
     * newest-anchored truncation confirmed by live discovery (Candidate 20 / D015).
     */
    static final long MAX_RANGE_MILLIS = (long) MAX_BATCH_SIZE * CANDLE_INTERVAL_MILLIS;

    /**
     * The live-observed exchange ceiling on {@code endTime} (D015): a request above this was
     * rejected server-side with {@code code=109400} during Candidate 20 live validation. This is
     * not drawn from an official documented contract &mdash; the interactive API docs were
     * inaccessible as a JS-rendered SPA at the time &mdash; so it is checked client-side as a
     * conservative guard, failing before any transport call, consistent with every other {@code
     * validateRange} check.
     */
    static final long MAX_END_TIME_EPOCH_MILLIS = 17_514_115_200_000L;

    /** Mirrors {@code com.ptengine.contract.json.ContractJsonCodec}'s positive-decimal pattern exactly. */
    private static final Pattern POSITIVE_DECIMAL_PATTERN =
            Pattern.compile("^(?:0\\.(?:0*[1-9]\\d*)|[1-9]\\d*(?:\\.\\d+)?)$");

    /** Same canonical decimal shape as {@link #POSITIVE_DECIMAL_PATTERN}, but zero is also allowed. */
    private static final Pattern NON_NEGATIVE_DECIMAL_PATTERN =
            Pattern.compile("^(?:0(?:\\.\\d+)?|[1-9]\\d*(?:\\.\\d+)?)$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI validatedBaseUri;
    private final URI tradesTarget;
    private final URI candlesTarget;
    private final Transport transport;

    public BingxPublicMarketDataClient(URI baseUri) {
        this(baseUri, new JdkHttpTransport());
    }

    BingxPublicMarketDataClient(URI baseUri, Transport transport) {
        this.validatedBaseUri = validateBaseUri(baseUri);
        this.tradesTarget = buildTargetUri(validatedBaseUri, TRADES_REQUEST_PATH, TRADES_REQUEST_QUERY);
        this.candlesTarget = buildTargetUri(validatedBaseUri, CANDLES_REQUEST_PATH, CANDLES_REQUEST_QUERY);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /**
     * Issues exactly one {@code GET} to {@code /openApi/swap/v2/quote/trades?symbol=BTC-USDT&limit=1000}
     * and returns the response batch as an immutable, wire-order-preserving list.
     *
     * @throws BingxPublicMarketDataException on any transport failure, non-200 status, oversized
     *     body, malformed/invalid JSON, envelope/business-code rejection, out-of-bounds batch
     *     size, or per-trade field validation failure
     */
    public List<BingxPerpetualTrade> fetchRecentBtcUsdtTrades() {
        byte[] body = fetch(tradesTarget);
        return parseTradeBatch(decodeStrictUtf8(body));
    }

    /**
     * Issues exactly one {@code GET} to {@code
     * /openApi/swap/v3/quote/klines?symbol=BTC-USDT&interval=15m&limit=1000} and returns the
     * response batch as an immutable, wire-order-preserving list.
     *
     * @throws BingxPublicMarketDataException on any transport failure, non-200 status, oversized
     *     body, malformed/invalid JSON, envelope/business-code rejection, out-of-bounds batch
     *     size, or per-candle field validation failure
     */
    public List<BingxPerpetualCandle> fetchRecentBtcUsdt15mCandles() {
        byte[] body = fetch(candlesTarget);
        return parseCandleBatch(decodeStrictUtf8(body));
    }

    /**
     * Issues exactly one {@code GET} to {@code
     * /openApi/swap/v3/quote/klines?symbol=BTC-USDT&interval=15m&startTime=<startTimeEpochMs>&endTime=<endTimeEpochMs>&limit=1000}
     * for one caller-supplied, client-validated 15-minute-aligned time range, and returns the
     * response batch as an immutable, wire-order-preserving list.
     *
     * <p>Both {@code startTimeEpochMs} and {@code endTimeEpochMs} must be non-negative exact
     * multiples of {@value #CANDLE_INTERVAL_MILLIS} (15 minutes in milliseconds &mdash; the same
     * grid every candle open time is observed on), with {@code endTimeEpochMs} strictly greater
     * than {@code startTimeEpochMs} and the span not exceeding 1000 candles
     * ({@value #MAX_RANGE_MILLIS} milliseconds). This bound is intentionally narrower than what the
     * exchange itself accepts: live discovery (Candidate 20 / Issue #46, D015) confirmed the
     * exchange applies a half-open {@code [startTime, endTime)} filter on candle open time when
     * both parameters are supplied, and silently keeps only the newest candles &mdash; dropping the
     * oldest ones with no error signal &mdash; once the implied count would exceed 1000. This
     * method's validation exists to make that silent-truncation case unreachable, not merely to
     * detect it afterward.
     *
     * <p>Unlike {@link #fetchRecentBtcUsdt15mCandles()}, an empty result is a legitimate outcome of
     * this method (both a fully-future range and a range before market data existed were confirmed
     * live to return a successful empty batch) and does not throw.
     *
     * @throws BingxPublicMarketDataException on invalid arguments (before any transport call is
     *     made), or on any transport failure, non-200 status, oversized body, malformed/invalid
     *     JSON, envelope/business-code rejection, out-of-bounds batch size, a returned candle whose
     *     open time falls outside {@code [startTimeEpochMs, endTimeEpochMs)}, a returned candle whose
     *     open time is not aligned to the 15-minute candle grid, or per-candle field validation failure
     */
    public List<BingxPerpetualCandle> fetchBtcUsdt15mCandlesInRange(long startTimeEpochMs, long endTimeEpochMs) {
        validateRange(startTimeEpochMs, endTimeEpochMs);
        URI target =
                buildTargetUri(validatedBaseUri, CANDLES_REQUEST_PATH, rangeQuery(startTimeEpochMs, endTimeEpochMs));
        byte[] body = fetch(target);
        return parseCandleRangeBatch(decodeStrictUtf8(body), startTimeEpochMs, endTimeEpochMs);
    }

    private static String rangeQuery(long startTimeEpochMs, long endTimeEpochMs) {
        return "symbol=BTC-USDT&interval=15m&startTime="
                + startTimeEpochMs
                + "&endTime="
                + endTimeEpochMs
                + "&limit=1000";
    }

    private static void validateRange(long startTimeEpochMs, long endTimeEpochMs) {
        if (startTimeEpochMs < 0) {
            throw new BingxPublicMarketDataException(
                    "startTimeEpochMs must be non-negative, was: " + startTimeEpochMs);
        }
        if (endTimeEpochMs < 0) {
            throw new BingxPublicMarketDataException("endTimeEpochMs must be non-negative, was: " + endTimeEpochMs);
        }
        requireGridAligned(startTimeEpochMs, "startTimeEpochMs");
        requireGridAligned(endTimeEpochMs, "endTimeEpochMs");
        if (endTimeEpochMs > MAX_END_TIME_EPOCH_MILLIS) {
            throw new BingxPublicMarketDataException(
                    "endTimeEpochMs exceeds the live-observed exchange maximum of "
                            + MAX_END_TIME_EPOCH_MILLIS + ", was: " + endTimeEpochMs);
        }
        if (endTimeEpochMs <= startTimeEpochMs) {
            throw new BingxPublicMarketDataException(
                    "endTimeEpochMs must be strictly greater than startTimeEpochMs, was startTimeEpochMs="
                            + startTimeEpochMs + ", endTimeEpochMs=" + endTimeEpochMs);
        }
        // Safe because startTimeEpochMs and endTimeEpochMs are already both validated non-negative
        // and endTimeEpochMs > startTimeEpochMs above: this subtraction cannot overflow for any
        // representable long inputs, unlike an addition-based bound check would risk.
        long rangeMillis = endTimeEpochMs - startTimeEpochMs;
        if (rangeMillis > MAX_RANGE_MILLIS) {
            throw new BingxPublicMarketDataException(
                    "requested range of " + rangeMillis + " milliseconds exceeds the safe single-request bound of "
                            + MAX_BATCH_SIZE + " candles (" + MAX_RANGE_MILLIS + " milliseconds)");
        }
    }

    private static void requireGridAligned(long value, String fieldName) {
        if (value % CANDLE_INTERVAL_MILLIS != 0) {
            throw new BingxPublicMarketDataException(
                    fieldName
                            + " must be aligned to the 15-minute candle grid (a multiple of "
                            + CANDLE_INTERVAL_MILLIS
                            + "), was: "
                            + value);
        }
    }

    private byte[] fetch(URI target) {
        HttpRequest request =
                HttpRequest.newBuilder(target)
                        .GET()
                        .header("Accept", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .build();

        RawResponse response;
        try {
            response = transport.send(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BingxPublicMarketDataException(
                    "interrupted while calling the BingX public market data endpoint", e);
        } catch (IOException e) {
            throw new BingxPublicMarketDataException(
                    "transport failure calling the BingX public market data endpoint", e);
        }

        if (response.statusCode() != 200) {
            throw new BingxPublicMarketDataException("unexpected HTTP status: " + response.statusCode());
        }

        byte[] body = response.body();
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new BingxPublicMarketDataException(
                    "response body exceeds maximum size of " + MAX_RESPONSE_BYTES + " bytes");
        }
        return body;
    }

    // ------------------------------------------------------------------
    // base URI validation and target construction
    // ------------------------------------------------------------------

    private static URI validateBaseUri(URI baseUri) {
        if (baseUri == null) {
            throw new BingxPublicMarketDataException("baseUri must not be null");
        }
        if (!baseUri.isAbsolute()) {
            throw new BingxPublicMarketDataException("baseUri must be an absolute URI");
        }
        if (!REQUIRED_SCHEME.equals(baseUri.getScheme())) {
            throw new BingxPublicMarketDataException(
                    "baseUri scheme must be exactly \"" + REQUIRED_SCHEME + "\", was: " + baseUri.getScheme());
        }
        if (baseUri.getHost() == null || baseUri.getHost().isEmpty()) {
            throw new BingxPublicMarketDataException("baseUri must have a host");
        }
        if (baseUri.getRawUserInfo() != null) {
            throw new BingxPublicMarketDataException("baseUri must not contain user-info");
        }
        if (baseUri.getRawQuery() != null) {
            throw new BingxPublicMarketDataException("baseUri must not contain a query");
        }
        if (baseUri.getRawFragment() != null) {
            throw new BingxPublicMarketDataException("baseUri must not contain a fragment");
        }
        String path = baseUri.getRawPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new BingxPublicMarketDataException("baseUri path must be empty or \"/\", was: " + path);
        }
        return baseUri;
    }

    private static URI buildTargetUri(URI baseUri, String path, String query) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), path, query, null);
        } catch (URISyntaxException e) {
            throw new BingxPublicMarketDataException("failed to construct the BingX request URI", e);
        }
    }

    // ------------------------------------------------------------------
    // response decoding and parsing
    // ------------------------------------------------------------------

    private static String decodeStrictUtf8(byte[] body) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException e) {
            throw new BingxPublicMarketDataException("response body is not valid UTF-8", e);
        }
    }

    private static JsonNode parseEnvelope(String json) {
        return parseEnvelope(json, false);
    }

    private static JsonNode parseEnvelope(String json, boolean allowEmptyData) {
        JsonNode root;
        try (JsonParser parser = MAPPER.getFactory().createParser(json)) {
            root = MAPPER.readTree(parser);
            if (root == null || root.isMissingNode()) {
                throw new BingxPublicMarketDataException("response body must not be empty");
            }
            if (parser.nextToken() != null) {
                throw new BingxPublicMarketDataException("trailing content after top-level JSON value");
            }
        } catch (IOException e) {
            throw new BingxPublicMarketDataException("malformed JSON response body: " + e.getMessage(), e);
        }

        if (!root.isObject()) {
            throw new BingxPublicMarketDataException("top-level JSON value must be an object");
        }

        JsonNode codeNode = root.get("code");
        if (codeNode == null || !codeNode.isIntegralNumber()) {
            throw new BingxPublicMarketDataException("code must be a JSON integer");
        }
        if (codeNode.asLong() != 0L) {
            throw new BingxPublicMarketDataException("non-zero business code: " + codeNode.asLong());
        }

        JsonNode msgNode = root.get("msg");
        if (msgNode == null || !msgNode.isTextual()) {
            throw new BingxPublicMarketDataException("msg must be a JSON string");
        }

        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            throw new BingxPublicMarketDataException("data must be a JSON array");
        }
        if (!allowEmptyData && dataNode.isEmpty()) {
            throw new BingxPublicMarketDataException("data must not be empty");
        }
        if (dataNode.size() > MAX_BATCH_SIZE) {
            throw new BingxPublicMarketDataException(
                    "data must not exceed " + MAX_BATCH_SIZE + " elements, had: " + dataNode.size());
        }
        return dataNode;
    }

    private static List<BingxPerpetualTrade> parseTradeBatch(String json) {
        JsonNode dataNode = parseEnvelope(json);
        List<BingxPerpetualTrade> trades = new ArrayList<>(dataNode.size());
        for (JsonNode tradeNode : dataNode) {
            trades.add(parseTrade(tradeNode));
        }
        return List.copyOf(trades);
    }

    private static BingxPerpetualTrade parseTrade(JsonNode tradeNode) {
        if (!tradeNode.isObject()) {
            throw new BingxPublicMarketDataException("each trade element must be a JSON object");
        }
        long tradedAtEpochMs = requireEpochMillis(tradeNode, "time");
        boolean buyerMaker = requireBoolean(tradeNode, "isBuyerMaker");
        BigDecimal price = requirePositiveDecimal(tradeNode, "price");
        BigDecimal quantity = requirePositiveDecimal(tradeNode, "qty");
        BigDecimal quoteQuantity = requirePositiveDecimal(tradeNode, "quoteQty");
        return new BingxPerpetualTrade(REQUIRED_SYMBOL, tradedAtEpochMs, buyerMaker, price, quantity, quoteQuantity);
    }

    private static List<BingxPerpetualCandle> parseCandleBatch(String json) {
        JsonNode dataNode = parseEnvelope(json);
        List<BingxPerpetualCandle> candles = new ArrayList<>(dataNode.size());
        for (JsonNode candleNode : dataNode) {
            candles.add(parseCandle(candleNode));
        }
        return List.copyOf(candles);
    }

    /**
     * Same as {@link #parseCandleBatch(String)}, except empty {@code data} is a legitimate success
     * (per the locked range empty-result policy) and every parsed candle's {@code openTimeEpochMs}
     * is independently checked against the 15-minute candle grid and the requested {@code
     * [startTimeEpochMs, endTimeEpochMs)} bound, failing closed &mdash; rather than silently
     * filtering &mdash; on any row that is misaligned, outside that bound, or on a batch larger
     * than the range could possibly contain.
     */
    private static List<BingxPerpetualCandle> parseCandleRangeBatch(
            String json, long startTimeEpochMs, long endTimeEpochMs) {
        JsonNode dataNode = parseEnvelope(json, true);
        long maxPossibleCandles = (endTimeEpochMs - startTimeEpochMs) / CANDLE_INTERVAL_MILLIS;
        if (dataNode.size() > maxPossibleCandles) {
            throw new BingxPublicMarketDataException(
                    "data element count " + dataNode.size() + " exceeds the maximum of " + maxPossibleCandles
                            + " candles possible for the requested range");
        }
        List<BingxPerpetualCandle> candles = new ArrayList<>(dataNode.size());
        for (JsonNode candleNode : dataNode) {
            BingxPerpetualCandle candle = parseCandle(candleNode);
            requireGridAligned(candle.openTimeEpochMs(), "returned candle openTimeEpochMs");
            if (candle.openTimeEpochMs() < startTimeEpochMs || candle.openTimeEpochMs() >= endTimeEpochMs) {
                throw new BingxPublicMarketDataException(
                        "returned candle openTimeEpochMs " + candle.openTimeEpochMs()
                                + " falls outside the requested range [" + startTimeEpochMs + ", " + endTimeEpochMs
                                + ")");
            }
            candles.add(candle);
        }
        return List.copyOf(candles);
    }

    private static BingxPerpetualCandle parseCandle(JsonNode candleNode) {
        if (!candleNode.isObject()) {
            throw new BingxPublicMarketDataException("each candle element must be a JSON object");
        }
        long openTimeEpochMs = requireEpochMillis(candleNode, "time");
        BigDecimal open = requirePositiveDecimal(candleNode, "open");
        BigDecimal high = requirePositiveDecimal(candleNode, "high");
        BigDecimal low = requirePositiveDecimal(candleNode, "low");
        BigDecimal close = requirePositiveDecimal(candleNode, "close");
        BigDecimal volume = requireNonNegativeDecimal(candleNode, "volume");
        return new BingxPerpetualCandle(
                REQUIRED_SYMBOL, REQUIRED_INTERVAL, openTimeEpochMs, open, high, low, close, volume);
    }

    private static long requireEpochMillis(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new BingxPublicMarketDataException(field + " must be a JSON integer");
        }
        if (!value.canConvertToLong()) {
            throw new BingxPublicMarketDataException(field + " exceeds signed 64-bit range");
        }
        long millis = value.longValue();
        if (millis < 0) {
            throw new BingxPublicMarketDataException(field + " must be non-negative");
        }
        return millis;
    }

    private static boolean requireBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new BingxPublicMarketDataException(field + " must be a JSON boolean");
        }
        return value.booleanValue();
    }

    private static BigDecimal requirePositiveDecimal(JsonNode node, String field) {
        return requireDecimal(node, field, POSITIVE_DECIMAL_PATTERN, "positive-decimal");
    }

    private static BigDecimal requireNonNegativeDecimal(JsonNode node, String field) {
        return requireDecimal(node, field, NON_NEGATIVE_DECIMAL_PATTERN, "non-negative-decimal");
    }

    private static BigDecimal requireDecimal(
            JsonNode node, String field, Pattern pattern, String representationName) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new BingxPublicMarketDataException(field + " must be a JSON string, not a number");
        }
        String text = value.textValue();
        if (text.length() > MAX_DECIMAL_LENGTH) {
            // Never echo the raw value here: an oversized field is exactly the case where it must
            // not be interpolated verbatim into the exception message (only its length is safe).
            throw new BingxPublicMarketDataException(
                    field + " exceeds maximum decimal length of " + MAX_DECIMAL_LENGTH + " characters, had: "
                            + text.length());
        }
        if (!pattern.matcher(text).matches()) {
            throw new BingxPublicMarketDataException(
                    field + " does not match the canonical " + representationName + " representation: " + text);
        }
        return new BigDecimal(text);
    }

    // ------------------------------------------------------------------
    // transport seam (package-private; production path always uses JdkHttpTransport)
    // ------------------------------------------------------------------

    interface Transport {

        RawResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    record RawResponse(int statusCode, byte[] body) {}

    /**
     * Seam between {@link JdkHttpTransport} and the underlying HTTP mechanism. Production always
     * uses the JDK {@link HttpClient} with {@link HttpResponse.BodyHandlers#ofInputStream()};
     * tests may inject a process-local fake that never opens a socket.
     */
    interface StreamSender {

        StreamResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    record StreamResponse(int statusCode, InputStream body) {}

    /**
     * Reads {@code in} into a byte array, always closing it. Stops consuming input as soon as a
     * read crosses {@code maxBytes}: at most one fixed read buffer beyond the bound is ever
     * consumed, and the remainder of an oversized stream is never drained. Response accumulation
     * is therefore bounded as a function of {@code maxBytes}, not of the underlying stream's
     * length &mdash; this does not claim an exact total heap-allocation figure (the accumulator's
     * own buffer growth/copy overhead is implementation detail) or that OOM is impossible.
     */
    static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream accumulated = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(
                            "response body exceeds maximum size of " + maxBytes + " bytes during streaming read");
                }
                accumulated.write(buffer, 0, read);
            }
            return accumulated.toByteArray();
        }
    }

    static final class JdkHttpTransport implements Transport {

        private final StreamSender sender;

        JdkHttpTransport() {
            this(defaultSender());
        }

        JdkHttpTransport(StreamSender sender) {
            this.sender = Objects.requireNonNull(sender, "sender");
        }

        private static StreamSender defaultSender() {
            HttpClient httpClient =
                    HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
            return request -> {
                HttpResponse<InputStream> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                return new StreamResponse(response.statusCode(), response.body());
            };
        }

        @Override
        public RawResponse send(HttpRequest request) throws IOException, InterruptedException {
            StreamResponse response = sender.send(request);
            byte[] body = readBounded(response.body(), MAX_RESPONSE_BYTES);
            return new RawResponse(response.statusCode(), body);
        }
    }
}
