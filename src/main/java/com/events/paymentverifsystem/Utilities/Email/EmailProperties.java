package com.events.paymentverifsystem.Utilities.Email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private String host = "imap.gmail.com";

    private int port = 993;

    private String protocol = "imaps";

    private String username;

    /**
     * Either an app password, or use OAuth2 token flow via TokenProvider
     */
    private String password;

    /**
     * Use OAuth2? If true, EmailReceiverService will ask TokenProvider for access tokens.
     */
    private boolean useOauth2 = false;

    // connection timeout
    /*
    connectionTimeout: Timeout in milliseconds for establishing a connection (default 10,000 ms).
    timeout: General timeout in milliseconds for operations (default 10,000 ms).
    keepAliveFreqMillis: Frequency in milliseconds to send keep-alive messages (default 300,000 ms or 5 minutes).
    idleReconnectBackoffSeconds: Initial backoff in seconds before reconnecting after idle disconnect (default 2 seconds).
    idleReconnectMaxBackoffSeconds: Maximum backoff in seconds for idle reconnect attempts (default 300 seconds).
     */
    private int connectionTimeout = 10_000;
    private int timeout = 10_000;
    private int keepAliveFreqMillis = 240000; // 4 min ish thanks to gmail
    private int idleReconnectBackoffSeconds = 2;
    private int idleReconnectMaxBackoffSeconds = 100;


    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isUseOauth2() { return useOauth2; }
    public void setUseOauth2(boolean useOauth2) { this.useOauth2 = useOauth2; }

    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getKeepAliveFreqMillis() { return keepAliveFreqMillis; }
    public void setKeepAliveFreqMillis(int keepAliveFreqMillis) { this.keepAliveFreqMillis = keepAliveFreqMillis; }

    public int getIdleReconnectBackoffSeconds() { return idleReconnectBackoffSeconds; }
    public void setIdleReconnectBackoffSeconds(int idleReconnectBackoffSeconds) { this.idleReconnectBackoffSeconds = idleReconnectBackoffSeconds; }

    public int getIdleReconnectMaxBackoffSeconds() { return idleReconnectMaxBackoffSeconds; }
    public void setIdleReconnectMaxBackoffSeconds(int idleReconnectMaxBackoffSeconds) { this.idleReconnectMaxBackoffSeconds = idleReconnectMaxBackoffSeconds; }
}