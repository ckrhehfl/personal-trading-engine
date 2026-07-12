package com.ptengine.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskOutcome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Candidate 13: proves the existing Python deterministic backtest engine
 * ({@code run_backtest}) and the existing Java {@link PaperBroker#execute} agree on economic side
 * and exact execution price for one fixed, repository-shared MARKET-fill fixture (see
 * {@code tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json}, consumed
 * independently by
 * {@code python/tests/backtest/test_python_java_market_fill_equivalence.py}). No fixture content
 * is copied into Java test resources; this test reads the repository-relative fixture file at run
 * time, following the {@code SharedFixtureCompatibilityTest} convention.
 *
 * <p>This is a narrower claim than full fill/no-fill parity. Python's current non-fill causes (no
 * next candle, below minimum order quantity) and Java's {@link PaperExecutionStatus#NO_FILL} (a
 * LIMIT order that does not cross best bid/ask) are not the same abstraction, so LIMIT and
 * NO_FILL equivalence are explicitly out of scope here.
 *
 * <p>Only the real public {@link PaperBroker#execute} entry point is exercised, with a matching
 * {@code PASS} {@link RiskDecision} as required by that method's contract. No private method is
 * invoked by reflection and no execution logic is reimplemented in a test-only broker.
 */
class PythonJavaMarketFillEquivalenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE_VERSION = "python-java-market-fill-equivalence.v0.1";
    private static final String FIXTURE_RELATIVE_PATH =
            "tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json";

    private static final Set<String> REQUIRED_CASE_IDS = Set.of(
            "enter-long-market-buy",
            "enter-short-market-sell",
            "exit-long-market-sell",
            "exit-short-market-buy");

    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "caseId",
            "intentType",
            "direction",
            "orderType",
            "referencePrice",
            "slippageBps",
            "bestBid",
            "bestAsk",
            "expectedStatus",
            "expectedSide",
            "expectedExecutionPrice");

    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    private final PaperBroker broker = new PaperBroker();

    /** Deterministically validated, parsed fixture case. Not shared code with the Python suite. */
    private record MarketFillCase(
            String caseId,
            IntentType intentType,
            Direction direction,
            OrderType orderType,
            BigDecimal referencePrice,
            BigDecimal slippageBps,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            String expectedStatus,
            PaperExecutionSide expectedSide,
            BigDecimal expectedExecutionPrice) {}

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("tests/execution/fixtures"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate repo root containing tests/execution/fixtures from "
                        + Path.of("").toAbsolutePath());
    }

    private static JsonNode loadFixtureRoot() {
        Path fixture = repoRoot().resolve(FIXTURE_RELATIVE_PATH);
        try {
            return MAPPER.readTree(Files.readString(fixture));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BigDecimal decimalField(JsonNode caseNode, String field, String caseId) {
        JsonNode value = caseNode.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException(
                    "case " + caseId + ": " + field + " must be a JSON string, was: " + value);
        }
        return new BigDecimal(value.asText());
    }

    /**
     * Economic execution direction only, independently re-derived here (not shared code with
     * {@link PaperBroker#execute}'s own {@code sideFor}), so this test does not simply assert its
     * own production logic back at itself.
     */
    private static PaperExecutionSide expectedSideFor(IntentType intentType, Direction direction) {
        boolean enter = intentType == IntentType.ENTER;
        boolean isLong = direction == Direction.LONG;
        if (enter) {
            return isLong ? PaperExecutionSide.BUY : PaperExecutionSide.SELL;
        }
        return isLong ? PaperExecutionSide.SELL : PaperExecutionSide.BUY;
    }

    /**
     * Parses and fails closed on any structural or arithmetic inconsistency in the shared
     * fixture: wrong fixtureVersion, missing/extra/duplicate case IDs, unsupported orderType or
     * expectedStatus, an expectedSide inconsistent with the closed intentType/direction mapping,
     * expectedExecutionPrice arithmetic that does not match referencePrice/slippageBps, or a
     * best bid/ask that is not aligned with the expected side's execution price.
     */
    private static List<MarketFillCase> loadAndValidateCases() {
        JsonNode root = loadFixtureRoot();

        JsonNode versionNode = root.get("fixtureVersion");
        if (versionNode == null || !FIXTURE_VERSION.equals(versionNode.asText())) {
            throw new IllegalStateException(
                    "unexpected fixtureVersion: " + (versionNode == null ? null : versionNode.asText()));
        }

        JsonNode casesNode = root.get("cases");
        if (casesNode == null || !casesNode.isArray()) {
            throw new IllegalStateException("fixture 'cases' must be a JSON array");
        }

        List<MarketFillCase> cases = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();

        for (JsonNode caseNode : casesNode) {
            Set<String> actualFields = new LinkedHashSet<>();
            caseNode.fieldNames().forEachRemaining(actualFields::add);
            if (!actualFields.equals(REQUIRED_FIELDS)) {
                throw new IllegalStateException(
                        "case has unexpected field set: " + actualFields + " expected: " + REQUIRED_FIELDS);
            }

            String caseId = caseNode.get("caseId").asText();
            if (!seenIds.add(caseId)) {
                throw new IllegalStateException("duplicate caseId: " + caseId);
            }

            String orderTypeRaw = caseNode.get("orderType").asText();
            if (!"MARKET".equals(orderTypeRaw)) {
                throw new IllegalStateException("unsupported orderType in case " + caseId + ": " + orderTypeRaw);
            }
            String expectedStatus = caseNode.get("expectedStatus").asText();
            if (!"FILLED".equals(expectedStatus)) {
                throw new IllegalStateException(
                        "unsupported expectedStatus in case " + caseId + ": " + expectedStatus);
            }

            IntentType intentType = IntentType.valueOf(caseNode.get("intentType").asText());
            Direction direction = Direction.valueOf(caseNode.get("direction").asText());
            PaperExecutionSide expectedSideFromMapping = expectedSideFor(intentType, direction);
            String expectedSideRaw = caseNode.get("expectedSide").asText();
            if (!expectedSideFromMapping.name().equals(expectedSideRaw)) {
                throw new IllegalStateException(
                        "case " + caseId + ": expectedSide " + expectedSideRaw
                                + " does not match closed intentType/direction mapping "
                                + expectedSideFromMapping);
            }

            BigDecimal referencePrice = decimalField(caseNode, "referencePrice", caseId);
            BigDecimal slippageBps = decimalField(caseNode, "slippageBps", caseId);
            BigDecimal bestBid = decimalField(caseNode, "bestBid", caseId);
            BigDecimal bestAsk = decimalField(caseNode, "bestAsk", caseId);
            BigDecimal expectedExecutionPrice = decimalField(caseNode, "expectedExecutionPrice", caseId);

            boolean isBuy = expectedSideFromMapping == PaperExecutionSide.BUY;
            BigDecimal adjustment = referencePrice.multiply(slippageBps).divide(BPS_DIVISOR);
            BigDecimal arithmeticPrice =
                    isBuy ? referencePrice.add(adjustment) : referencePrice.subtract(adjustment);
            if (arithmeticPrice.compareTo(expectedExecutionPrice) != 0) {
                throw new IllegalStateException(
                        "case " + caseId + ": expectedExecutionPrice " + expectedExecutionPrice
                                + " does not equal referencePrice/slippageBps arithmetic " + arithmeticPrice);
            }

            BigDecimal alignedPrice = isBuy ? bestAsk : bestBid;
            if (alignedPrice.compareTo(expectedExecutionPrice) != 0) {
                throw new IllegalStateException(
                        "case " + caseId + ": " + (isBuy ? "bestAsk" : "bestBid") + " " + alignedPrice
                                + " does not equal expectedExecutionPrice " + expectedExecutionPrice);
            }

            cases.add(
                    new MarketFillCase(
                            caseId,
                            intentType,
                            direction,
                            OrderType.valueOf(orderTypeRaw),
                            referencePrice,
                            slippageBps,
                            bestBid,
                            bestAsk,
                            expectedStatus,
                            expectedSideFromMapping,
                            expectedExecutionPrice));
        }

        if (!seenIds.equals(REQUIRED_CASE_IDS)) {
            throw new IllegalStateException(
                    "fixture case IDs " + seenIds + " do not exactly match required " + REQUIRED_CASE_IDS);
        }

        return cases;
    }

    private static OrderIntent intentFor(MarketFillCase c) {
        return new OrderIntent(
                OrderIntent.SCHEMA_VERSION,
                "intent-" + c.caseId(),
                "strategy-equivalence",
                "BTCUSDT",
                c.intentType(),
                c.direction(),
                OrderType.MARKET,
                new BigDecimal("100"),
                null,
                1_000L);
    }

    private static RiskDecision passDecisionFor(OrderIntent intent) {
        return new RiskDecision(
                RiskDecision.SCHEMA_VERSION,
                "decision-" + intent.intentId(),
                intent.intentId(),
                RiskOutcome.PASS,
                List.of(),
                2_000L);
    }

    private static PaperMarketSnapshot snapshotFor(MarketFillCase c) {
        return new PaperMarketSnapshot("BTCUSDT", c.bestBid(), c.bestAsk(), 3_000L);
    }

    private static PaperExecutionMetadata metadataFor(MarketFillCase c) {
        return new PaperExecutionMetadata("exec-" + c.caseId(), 4_000L);
    }

    private PaperExecutionResult executeCase(MarketFillCase c) {
        OrderIntent intent = intentFor(c);
        return broker.execute(intent, passDecisionFor(intent), snapshotFor(c), metadataFor(c));
    }

    @Test
    void fixtureHasExpectedVersionCasesAndArithmetic() {
        List<MarketFillCase> cases = loadAndValidateCases();

        assertEquals(REQUIRED_CASE_IDS.size(), cases.size());
        Set<String> ids = new LinkedHashSet<>();
        for (MarketFillCase c : cases) {
            ids.add(c.caseId());
            assertEquals(OrderType.MARKET, c.orderType());
            assertEquals("FILLED", c.expectedStatus());
        }
        assertEquals(REQUIRED_CASE_IDS, ids);
    }

    @TestFactory
    Collection<DynamicTest> javaPaperBrokerMatchesFixtureForEveryCase() {
        List<MarketFillCase> cases = loadAndValidateCases();
        List<DynamicTest> tests = new ArrayList<>();
        for (MarketFillCase c : cases) {
            tests.add(
                    DynamicTest.dynamicTest(
                            c.caseId(),
                            () -> {
                                PaperExecutionResult result = executeCase(c);
                                assertEquals(PaperExecutionStatus.FILLED, result.status());
                                assertEquals(c.expectedSide(), result.side());
                                assertEquals(
                                        0,
                                        result.executionPrice().compareTo(c.expectedExecutionPrice()),
                                        "executionPrice " + result.executionPrice()
                                                + " must numerically equal expectedExecutionPrice "
                                                + c.expectedExecutionPrice());
                            }));
        }
        assertEquals(REQUIRED_CASE_IDS.size(), tests.size());
        return tests;
    }

    @Test
    void repeatedExecutionOfEveryFixtureCaseIsDeterministic() {
        List<MarketFillCase> cases = loadAndValidateCases();
        for (MarketFillCase c : cases) {
            PaperExecutionResult first = executeCase(c);
            PaperExecutionResult second = executeCase(c);
            assertEquals(first, second);
        }
    }
}
