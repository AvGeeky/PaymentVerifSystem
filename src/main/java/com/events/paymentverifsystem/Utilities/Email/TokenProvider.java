package com.events.paymentverifsystem.Utilities.Email;

public interface TokenProvider {
    /**
     * Return a valid access token (short-lived). Return null if not available.
     * Implementation must handle refreshing tokens as needed.
     */
    String getAccessToken();
}
