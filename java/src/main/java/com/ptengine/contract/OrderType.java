package com.ptengine.contract;

/**
 * Order type. Mirrors {@code orderType} in
 * {@code schemas/v0.1/order-intent.schema.json}. {@code LIMIT} requires a
 * positive {@link OrderIntent#limitPrice()}; {@code MARKET} forbids one.
 */
public enum OrderType {
    LIMIT,
    MARKET
}
