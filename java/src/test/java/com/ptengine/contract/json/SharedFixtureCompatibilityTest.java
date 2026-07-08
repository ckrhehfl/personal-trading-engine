package com.ptengine.contract.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptengine.contract.OrderIntent;
import com.ptengine.risk.RiskDecision;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies the Candidate 5 shared-fixture compatibility boundary: the same fixture bytes under
 * {@code tests/schemas/fixtures/**} that the Python Draft 2020-12 suite
 * ({@code tests/schemas/test_schema_contracts.py}) treats as canonical accept/reject cases are
 * consumed directly by {@link ContractJsonCodec} here. No fixture content is copied into Java
 * test resources; this test reads the repository-relative fixture files at run time.
 *
 * <p>This establishes JSON-boundary compatibility only. It does not establish full backtest
 * &lt;-&gt; Java trading-path behavioral equivalence, signal snapshot equivalence, or Java
 * strategy/paper/live runtime behavior.
 */
class SharedFixtureCompatibilityTest {

    private static final ObjectMapper ASSERTION_MAPPER = new ObjectMapper();

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("tests/schemas/fixtures"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate repo root containing tests/schemas/fixtures from "
                        + Path.of("").toAbsolutePath());
    }

    private static List<Path> listFixtures(String contract, String bucket) {
        Path dir = repoRoot().resolve("tests/schemas/fixtures").resolve(contract).resolve(bucket);
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readFixture(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestFactory
    Collection<DynamicTest> validOrderIntentFixturesRoundTrip() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Path path : listFixtures("order-intent", "valid")) {
            String raw = readFixture(path);
            tests.add(
                    DynamicTest.dynamicTest(
                            path.getFileName().toString(),
                            () -> {
                                OrderIntent intent = ContractJsonCodec.parseOrderIntent(raw);
                                String reserialized = ContractJsonCodec.toJson(intent);
                                assertSemanticJsonEquals(raw, reserialized);
                            }));
        }
        assertTrue(!tests.isEmpty(), "expected at least one valid order-intent fixture");
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> invalidOrderIntentFixturesRejected() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Path path : listFixtures("order-intent", "invalid")) {
            String raw = readFixture(path);
            tests.add(
                    DynamicTest.dynamicTest(
                            path.getFileName().toString(),
                            () -> assertThrows(
                                    ContractJsonException.class, () -> ContractJsonCodec.parseOrderIntent(raw))));
        }
        assertTrue(!tests.isEmpty(), "expected at least one invalid order-intent fixture");
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> validRiskDecisionFixturesRoundTrip() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Path path : listFixtures("risk-decision", "valid")) {
            String raw = readFixture(path);
            tests.add(
                    DynamicTest.dynamicTest(
                            path.getFileName().toString(),
                            () -> {
                                RiskDecision decision = ContractJsonCodec.parseRiskDecision(raw);
                                String reserialized = ContractJsonCodec.toJson(decision);
                                assertSemanticJsonEquals(raw, reserialized);
                            }));
        }
        assertTrue(!tests.isEmpty(), "expected at least one valid risk-decision fixture");
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> invalidRiskDecisionFixturesRejected() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Path path : listFixtures("risk-decision", "invalid")) {
            String raw = readFixture(path);
            tests.add(
                    DynamicTest.dynamicTest(
                            path.getFileName().toString(),
                            () -> assertThrows(
                                    ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(raw))));
        }
        assertTrue(!tests.isEmpty(), "expected at least one invalid risk-decision fixture");
        return tests;
    }

    @Test
    void repeatedSerializationOfFixtureDerivedValueIsByteForByteDeterministic() {
        Path fixture = repoRoot().resolve("tests/schemas/fixtures/order-intent/valid/limit.json");
        OrderIntent intent = ContractJsonCodec.parseOrderIntent(readFixture(fixture));
        String first = ContractJsonCodec.toJson(intent);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, ContractJsonCodec.toJson(intent));
        }
    }

    private static void assertSemanticJsonEquals(String expectedRaw, String actualRaw) {
        try {
            JsonNode expected = ASSERTION_MAPPER.readTree(expectedRaw);
            JsonNode actual = ASSERTION_MAPPER.readTree(actualRaw);
            assertEquals(expected, actual);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
