package com.ptengine.contract;

/**
 * Position lifecycle intent. Mirrors {@code intentType} in
 * {@code schemas/v0.1/order-intent.schema.json}. Does not decide hedge vs
 * one-way position mode.
 */
public enum IntentType {
    ENTER,
    EXIT
}
