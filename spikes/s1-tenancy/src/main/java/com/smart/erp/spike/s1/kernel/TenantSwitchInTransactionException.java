package com.smart.erp.spike.s1.kernel;

/**
 * A tenant scope opened while a transaction is active (doc §4.4 rule 7): the transaction's connection already belongs
 * to the previous tenant, or the transaction started without one.
 */
public class TenantSwitchInTransactionException extends IllegalStateException {

    public TenantSwitchInTransactionException(TenantKey current, TenantKey requested) {
        super(
                current == null
                        ? "Tenant " + requested + " must be bound before the transaction starts (doc §4.4 rule 7)"
                        : "Cannot switch tenant from " + current + " to " + requested
                                + " inside a transaction (doc §4.4 rule 7)");
    }
}
