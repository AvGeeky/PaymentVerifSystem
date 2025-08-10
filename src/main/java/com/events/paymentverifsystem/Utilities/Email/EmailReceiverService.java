package com.events.paymentverifsystem.Utilities.Email;
import com.events.paymentverifsystem.Utilities.Payment.PaymentInfo;
import com.events.paymentverifsystem.Utilities.Redis.RedisPaymentStore;
import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.events.paymentverifsystem.Utilities.Email.EmailParser.safeGetMessageId;

@Service
public class EmailReceiverService {
    private static final Logger log = LoggerFactory.getLogger(EmailReceiverService.class);

    private final EmailProperties props;
    private final TokenProvider tokenProvider;
    private final RedisPaymentStore redisPaymentStore;
    private final EmailProcessedStoreProperties processedProps;
    private final Session session;
    private final RedisTemplate<String, Object> redisTemplate; // used for heartbeat

    private volatile Store store;
    private volatile IMAPFolder inbox;

    private final ScheduledExecutorService keepAliveScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "imap-keepalive"));
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "email-heartbeat"));
    private final ScheduledExecutorService sweepScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "email-sweep"));
    private final ExecutorService workerPool = Executors.newFixedThreadPool(6, r -> new Thread(r, "email-worker-"));
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledFuture<?> keepAliveFuture;
    private ScheduledFuture<?> heartbeatFuture;

    // single listener instance so we can remove it on close
    private final MessageCountAdapter messageListener = new MessageCountAdapter() {
        @Override
        public void messagesAdded(MessageCountEvent event) {
            Message[] msgs = event.getMessages();
            log.info("IDLE: {} new messages", msgs.length);
            for (Message m : msgs) workerPool.submit(() -> safeHandle(m));
        }
    };




    // business TTL and processed TTL (configurable)
    private final Duration businessKeyTtl = Duration.ofMinutes(20);
    private final int processedKeyTtlSeconds;

    public EmailReceiverService(EmailProperties props,
                                TokenProvider tokenProvider,
                                RedisPaymentStore redisPaymentStore,
                                EmailProcessedStoreProperties processedProps,
                                Session session,
                                RedisTemplate<String, Object> redisTemplate) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.redisPaymentStore = redisPaymentStore;
        this.processedProps = processedProps;
        this.session = session;
        this.redisTemplate = redisTemplate;
        this.processedKeyTtlSeconds = (int) processedProps.getProcessedMessageTtlSeconds();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("EmailReceiverService already running");
            return;
        }
        scheduleHeartbeat();
        scheduleSweep();
        Thread t = new Thread(this::mainLoop, "EmailReceiverService-Loop"); //handles incoming emails
        t.setDaemon(true);
        t.start();
    }

    private void mainLoop() {
        int backoff = props.getIdleReconnectBackoffSeconds();
        while (running.get()) {
            try {
                connectAndOpenInbox();
                // ensure listener not added multiple times
                try { inbox.removeMessageCountListener(messageListener); } catch (Exception ignored) {}
                inbox.addMessageCountListener(messageListener);

                scheduleKeepAlive();

                while (running.get() && store != null && store.isConnected() && inbox != null && inbox.isOpen()) {
                    try {
                        log.debug("Entering IMAP IDLE");
                        inbox.idle();
                        log.debug("IDLE returned");
                        backoff = props.getIdleReconnectBackoffSeconds();
                    } catch (FolderClosedException | StoreClosedException fce) {
                        log.warn("Folder/Store closed, reconnecting", fce);
                        break;
                    } catch (MessagingException me) {
                        log.warn("Messaging exception during IDLE, reconnecting", me);
                        break;
                    }
                }
            } catch (Exception ex) {
                log.error("Unexpected error in IMAP main loop", ex);
            } finally {
                safeCloseFolder();
                safeCloseStore();
                try {
                    int wait = Math.min(props.getIdleReconnectMaxBackoffSeconds(), backoff);
                    log.info("Reconnect backoff {}s", wait);
                    TimeUnit.SECONDS.sleep(wait);
                    backoff = Math.min(props.getIdleReconnectMaxBackoffSeconds(), backoff * 2);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        shutdownExecutors();
        log.info("EmailReceiverService stopped.");
    }

    private void connectAndOpenInbox() throws MessagingException {
        if (session == null) throw new IllegalStateException("Session bean not injected");

        if (store != null && store.isConnected() && inbox != null && inbox.isOpen()) return;

        log.info("Connecting to IMAP {}:{}", props.getHost(), props.getPort());
        store = session.getStore(props.getProtocol());

        if (props.isUseOauth2()) {
            String token = tokenProvider.getAccessToken();
            if (token == null) {
                log.warn("OAuth2 requested but token not available; falling back to password auth");
                store.connect(props.getHost(), props.getPort(), props.getUsername(), props.getPassword());
            } else {
                session.getProperties().put("mail.imaps.sasl.enable", "true");
                session.getProperties().put("mail.imaps.sasl.mechanisms", "XOAUTH2");
                store.connect(props.getHost(), props.getPort(), props.getUsername(), token);
            }
        } else {
            store.connect(props.getHost(), props.getPort(), props.getUsername(), props.getPassword());
        }

        Folder f = store.getFolder("INBOX");
        if (f == null || !f.exists()) throw new MessagingException("INBOX not found");
        inbox = (IMAPFolder) f;
        inbox.open(Folder.READ_WRITE);
        log.info("INBOX opened");
    }

    private void scheduleKeepAlive() {
        if (keepAliveFuture != null && !keepAliveFuture.isDone()) keepAliveFuture.cancel(true);
        keepAliveFuture = keepAliveScheduler.scheduleAtFixedRate(() -> {
            try {
                if (inbox != null && inbox.isOpen()) {
                    log.debug("Keepalive NOOP");
                    inbox.doCommand(protocol -> { protocol.simpleCommand("NOOP", null); return null; });
                }
            } catch (Exception e) {
                log.warn("Keepalive failed", e);
            }
        }, props.getKeepAliveFreqMillis(), props.getKeepAliveFreqMillis(), TimeUnit.MILLISECONDS);
    }

    private void scheduleHeartbeat() {
        if (heartbeatFuture != null && !heartbeatFuture.isDone()) heartbeatFuture.cancel(true);
        long ttlSeconds = Math.max(60, props.getKeepAliveFreqMillis() / 1000 * 2L);
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                StringBuilder issues = new StringBuilder();

                if (keepAliveScheduler.isShutdown()) issues.append("keepAliveScheduler, ");
                if (heartbeatScheduler.isShutdown()) issues.append("heartbeatScheduler, ");
                if (sweepScheduler.isShutdown()) issues.append("sweepScheduler, ");
                if (workerPool.isShutdown()) issues.append("workerPool, ");
                if (inbox == null) issues.append("inbox=null, ");
                else if (!inbox.isOpen()) issues.append("inbox not open, ");
                if (store == null) issues.append("store=null, ");
                else if (!store.isConnected()) issues.append("store not connected, ");
                if (!running.get()) issues.append("service not running, ");

                if (issues.length() == 0) {
                    String hbKey = "email-listener:heartbeat";
                    String value = Instant.now().toString();
                    redisTemplate.opsForValue().set(hbKey, value, Duration.ofSeconds(ttlSeconds));
                    log.debug("Heartbeat written to Redis");
                } else {
                    log.warn("Heartbeat not written: service not fully running. Issues: {}", issues.toString());
                }
            } catch (Exception e) {
                log.warn("Failed to write heartbeat to Redis", e);
            }
        }, 5, props.getKeepAliveFreqMillis() / 1000, TimeUnit.SECONDS);
    }



    private void safeHandle(Message msg) {
        try { handleIncoming(msg); } catch (Exception e) { log.error("Failed to handle message", e); }
    }

    private void handleIncoming(Message message) {
        String messageId = null;
        try {
            // --- get message id early (header-only cheap op if inbox fetched headers) ---
            messageId = safeGetMessageId(message);
            if (messageId == null) {
                // fallback synthetic id
                messageId = "synth-" + Math.abs(message.hashCode()) + "-" + System.currentTimeMillis();
            }

            // --- Fast path: check Redis processed flag and skip if present ---
            try {
                if (redisPaymentStore.isProcessed(messageId)) {
                    log.debug("Message already processed in Redis (m-id={}), skipping.", messageId);
                    // optional: still move to Processed for mailbox cleanliness
                    moveToFolder(message, "Processed");
                    // best-effort: mark SEEN but do not rely on it
                    try { message.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
                    return;
                }
            } catch (Exception e) {
                // If Redis is unavailable, we continue and try to process (fail-open).
                log.warn("Redis check failed for mid={}, continuing to attempt parse", messageId, e);
            }

            // parse full message (this is the heavy work) ---
            PaymentInfo info = EmailParser.parse(message);
            if (info == null) {
                log.info("Could not parse payment info; moving to Unprocessed (mid={})", messageId);
                // Mark processed in Redis to avoid repeated parsing churn (best-effort)
                try {
                    redisPaymentStore.savePaymentAtomic(
                            new PaymentInfo("unknown", "", Instant.now(), "", "", "", "", "", messageId),
                            businessKeyTtl(),
                            processedKeyTtlSeconds
                    );
                } catch (Exception e) {
                    log.warn("Failed to mark synthetic processed key for unparsed message mid={}", messageId, e);
                }
                moveToFolder(message, "Unprocessed");
                try { message.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
                return;
            }

            // Ensure the PaymentInfo contains the messageId we used (parser might give it)
            if (info.getMessageId() == null || info.getMessageId().isBlank()) {
                info.setMessageId(messageId);
            } else {
                // normalize parser-provided id to our canonical form (avoid CRLF issues)
                messageId = info.getMessageId();
            }

            // timestamp check: skip if older than 1 day (business rule)
            Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
            if (info.getPaidOn().isBefore(cutoff)) {
                log.info("Payment {} older than 1 day ({}). mid={}", info.getPaymentId(), info.getPaidOn(), messageId);
                try {
                    // mark processed so we don't re-parse
                    redisPaymentStore.savePaymentAtomic(info, businessKeyTtl(), processedKeyTtlSeconds);
                } catch (Exception e) {
                    log.warn("Failed to mark processed for old payment mid={}", messageId, e);
                }
                moveToFolder(message, "Processed");
                try { message.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
                return;
            }

            // Attempt atomic claim+write using RedisPaymentStore (it returns true if we claimed)
            boolean claimed;
            try {
                claimed = redisPaymentStore.savePaymentAtomic(info, businessKeyTtl(), processedKeyTtlSeconds);
            } catch (Exception e) {
                // fallback behaviour: log and attempt to mark processed non-atomically (fail-open)
                log.error("Error while saving payment to Redis for mid={}", messageId, e);
                claimed = false;
            }

            if (claimed) {
                log.info("Claimed and saved payment {} (mid={})", info.getPaymentId(), messageId);
                moveToFolder(message, "Processed");
                try { message.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
                // TODO: notify downstream (webhook, business queue) if needed
            } else {
                log.info("Payment {} already claimed by another instance (mid={}), moving to Processed", info.getPaymentId(), messageId);
                moveToFolder(message, "Processed");
                try { message.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
            }

        } catch (Exception ex) {
            log.error("Error processing incoming message mid=" + messageId, ex);
            // On unexpected errors, try to move message to Unprocessed so it won't keep being retried forever
            try { moveToFolder(message, "Unprocessed"); } catch (Exception ignore) {}
        }
    }


    private Duration businessKeyTtl() { return businessKeyTtl; }

    private void markSeen(Message msg) {
        try { msg.setFlag(Flags.Flag.SEEN, true); } catch (MessagingException e) { log.warn("Failed to mark SEEN", e); }
    }

    //run a scheduler event to sweep unseen messages every one hour
    private void scheduleSweep() {
        sweepScheduler.scheduleAtFixedRate(() -> {
            try {
                if (inbox != null && inbox.isOpen()) {
                    log.debug("Running Redis-based unseen sweep (headers only)...");

                    Instant cutoff = Instant.now().minus(60, ChronoUnit.MINUTES);
                    Message[] messages = inbox.getMessages();

                    for (Message m : messages) {
                        try {
                            // Fetch only the headers we care about
                            m.getFolder().fetch(new Message[]{m}, new FetchProfile() {{
                                add(FetchProfile.Item.ENVELOPE);
                                add("Message-ID");
                            }});

                            Instant received = m.getReceivedDate() == null
                                    ? Instant.now()
                                    : m.getReceivedDate().toInstant();

                            if (received.isBefore(cutoff)) continue;

                            String[] idHeader = m.getHeader("Message-ID");
                            if (idHeader == null || idHeader.length == 0) continue;

                            String messageId = idHeader[0];
                            if (!redisPaymentStore.isProcessed(messageId)) {
                                log.info("Sweep found unprocessed message mid={}, fetching full message", messageId);

                                // Fetch full message only now
                                Message fullMessage = inbox.getMessage(m.getMessageNumber());
                                workerPool.submit(() -> safeHandle(fullMessage));
                            }
                        } catch (Exception innerEx) {
                            log.warn("Error while checking message in sweep", innerEx);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error during Redis-based unseen sweep", e);
            }
        }, 1, 60, TimeUnit.MINUTES); // 60M sweep interval
    }



    private void moveToFolder(Message msg, String folderName) {
        try {
            Folder dest = store.getFolder(folderName);
            if (!dest.exists()) dest.create(Folder.HOLDS_MESSAGES);
            if (!dest.isOpen()) dest.open(Folder.READ_WRITE);
            inbox.copyMessages(new Message[]{msg}, dest);
            msg.setFlag(Flags.Flag.DELETED, true);
            log.info("Moved message to folder {}", folderName);
        } catch (Exception e) {
            log.error("Failed to move message to {}: {}", folderName, e.getMessage());
        }
    }

    private void safeCloseFolder() {
        try {
            if (inbox != null) {
                try { inbox.removeMessageCountListener(messageListener); } catch (Exception ignored) {}
                if (inbox.isOpen()) inbox.close(true);
            }
        } catch (Exception e) { log.warn("Error closing inbox", e); } finally { inbox = null; }
    }

    private void safeCloseStore() {
        try { if (store != null && store.isConnected()) store.close(); } catch (Exception e) { log.warn("Error closing store", e); } finally { store = null; }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        safeCloseFolder();
        safeCloseStore();
        try { if (keepAliveFuture != null) keepAliveFuture.cancel(true); } catch (Exception ignored) {}
        try { if (heartbeatFuture != null) heartbeatFuture.cancel(true); } catch (Exception ignored) {}
        shutdownExecutors();
    }

    private void shutdownExecutors() {
        try { keepAliveScheduler.shutdownNow(); } catch (Exception ignored) {}
        try { heartbeatScheduler.shutdownNow(); } catch (Exception ignored) {}
        try { sweepScheduler.shutdownNow(); } catch (Exception ignored) {}
        try {
            workerPool.shutdown();
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) workerPool.shutdownNow();
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("keepAliveScheduler", !keepAliveScheduler.isShutdown());
        status.put("heartbeatScheduler", !heartbeatScheduler.isShutdown());
        status.put("sweepScheduler", !sweepScheduler.isShutdown());
        status.put("workerPool", !workerPool.isShutdown());
        status.put("inbox", inbox != null && inbox.isOpen());
        status.put("store", store != null && store.isConnected());
        status.put("running", running.get());
        return status;
    }
}
