package com.ptengine.risk;

import com.ptengine.contract.OrderIntent;
import java.util.Optional;

/**
 * Pure-domain risk rule. A rule inspects an {@link OrderIntent} and either
 * passes ({@link Optional#empty()}) or rejects with one stable
 * {@link RiskRejectReason}.
 *
 * <p>Rules must not place orders, mutate OMS state, call the network or an
 * exchange, access secrets, deploy, or alter policy. No concrete production
 * numeric risk-limit rule is implemented in this candidate; rules used in
 * tests are deterministic fakes.
 */
@FunctionalInterface
public interface RiskRule {

    Optional<RiskRejectReason> evaluate(OrderIntent intent);
}
