package com.events.paymentverifsystem.Utilities.Email;

public interface ProcessedMessageStore {
    /**
     * Returns true if the item is already processed.
     */
    boolean isProcessed(String messageId);

    /**
     * Atomically mark as processed with TTL.
     * Returns true if this call actually marked it (i.e., it did not exist before).
     */
    boolean markProcessed(String messageId);
}
