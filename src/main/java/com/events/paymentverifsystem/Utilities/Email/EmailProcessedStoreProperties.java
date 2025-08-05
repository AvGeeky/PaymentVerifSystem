package com.events.paymentverifsystem.Utilities.Email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "email.processed-store")
public class EmailProcessedStoreProperties {

    private long processedMessageTtlSeconds = 24 * 3600;

    public long getProcessedMessageTtlSeconds() {
        return processedMessageTtlSeconds;
    }

    public void setProcessedMessageTtlSeconds(long processedMessageTtlSeconds) {
        this.processedMessageTtlSeconds = processedMessageTtlSeconds;
    }
}
