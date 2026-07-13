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
 * One-shot, read-only client for the public, unauthenticated BingX Swap V2 recent-trades
 * endpoint ({@code GET /openApi/swap/v2/quote/trades}) for the {@code BTC-USDT} USDT-M perpetual
 * instrument.
 *
 * <p>{@link #fetchRecentBtcUsdtTrades()} performs exactly one HTTP GET per call: no retry,
 * backoff, polling, caching, or redirect following. The request always sends {@code
 * symbol=BTC-USDT&limit=1000}, but {@code limit} is a request-side upper-bound intent only &mdash;
 * this class does not trust or claim that the server honors it as a count guarantee. The response
 * array's wire order is preserved exactly as received; no sort, reverse, deduplication,
 * aggregation, or "latest trade" selection is performed anywhere in this class.
 *
 * <p>This class carries no credentials, no signing, no account/order authority, no scheduler or
 * runtime loop, and no persistence. It is not a market-data collection service and does not claim
 * to be one.
 */
public final class BingxPublicMarketDataClient {

    private static final String REQUIRED_SCHEME = "https";
    private static final String REQUEST_PATH = "/openApi/swap/v2/quote/trades";
    private static final String REQUEST_QUERY = "symbol=BTC-USDT&limit=1000";
    private static final String REQUIRED_SYMBOL = "BTC-USDT";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_DECIMAL_LENGTH = 64;
    private static final int MAX_BATCH_SIZE = 1000;

    /** Mirrors {@code com.ptengine.contract.json.ContractJsonCodec}'s positive-decimal pattern exactly. */
    private static final Pattern POSITIVE_DECIMAL_PATTERN =
            Pattern.compile("^(?:0\\.(?:0*[1-9]\\d*)|[1-9]\\d*(?:\\.\\d+)?)$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI target;
    private final Transport transport;

    public BingxPublicMarketDataClient(URI baseUri) {
        this(baseUri, new JdkHttpTransport());
    }

    BingxPublicMarketDataClient(URI baseUri, Transport transport) {
        this.target = buildTargetUri(validateBaseUri(baseUri));
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

        return parseBatch(decodeStrictUtf8(body));
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

    private static URI buildTargetUri(URI baseUri) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), REQUEST_PATH, REQUEST_QUERY, null);
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

    private static List<BingxPerpetualTrade> parseBatch(String json) {
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
        if (dataNode.isEmpty()) {
            throw new BingxPublicMarketDataException("data must not be empty");
        }
        if (dataNode.size() > MAX_BATCH_SIZE) {
            throw new BingxPublicMarketDataException(
                    "data must not exceed " + MAX_BATCH_SIZE + " elements, had: " + dataNode.size());
        }

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
        if (!POSITIVE_DECIMAL_PATTERN.matcher(text).matches()) {
            throw new BingxPublicMarketDataException(
                    field + " does not match the canonical positive-decimal representation: " + text);
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
