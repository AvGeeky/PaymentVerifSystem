package com.events.paymentverifsystem.Controller;
import com.events.paymentverifsystem.Utilities.Email.EmailReceiverService;
import com.events.paymentverifsystem.Utilities.Redis.RedisPaymentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Admin endpoints for email-listener health and inspection of processed Redis entries.
 *
 * - GET /api/admin/email-listener/health
 * - GET /api/admin/email-listener/processed
 *
 * The processed endpoint returns:
 *  - processed key
 *  - processed key value (messageId)
 *  - type
 *  - attached payment data (if a matching attendance:payments:* hash contains messageId)
 */
@RestController
@RequestMapping("/api/admin")
public class EmailListenerAdminController {

    private final EmailReceiverService emailReceiverService;
    private final RedisPaymentStore redisPaymentStore;

    @Autowired
    public EmailListenerAdminController(RedisTemplate<String, Object> redisTemplate,
                                        EmailReceiverService emailReceiverService,
                                        RedisPaymentStore redisPaymentStore) {
        this.redisTemplate = redisTemplate;
        this.emailReceiverService = emailReceiverService;
        this.redisPaymentStore = redisPaymentStore;
    }

    private static final Logger log = LoggerFactory.getLogger(EmailListenerAdminController.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisSerializer stringSerializer = new StringRedisSerializer();

    private static final String HEARTBEAT_KEY = "email-listener:heartbeat";
    private static final Duration DEFAULT_MAX_HEARTBEAT_AGE = Duration.ofSeconds(90);

    // Safety limit for scanning business keys (attendance:payments:*). Increase only if you know your dataset size.
    private static final int BUSINESS_SCAN_LIMIT = 5_000;



    // ---------------- Health endpoint ----------------

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(@RequestParam(value = "maxAgeSeconds", required = false) Long maxAgeSeconds) {
        Duration maxAge = (maxAgeSeconds == null) ? DEFAULT_MAX_HEARTBEAT_AGE : Duration.ofSeconds(maxAgeSeconds);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", HEARTBEAT_KEY);

        // Add detailed health info
        Map<String, Object> dependencyHealth = emailReceiverService.getHealthStatus();
        resp.put("dependencies", dependencyHealth);

        try {
            Object rawObj = redisTemplate.opsForValue().get(HEARTBEAT_KEY);
            String raw = rawObj == null ? null : rawObj.toString();
            resp.put("lastHeartbeat", raw);

            if (raw == null) {
                resp.put("status", "DOWN");
                resp.put("reason", "No heartbeat key found in Redis");
                return ResponseEntity.status(200).body(resp);
            }

            Instant last;
            try {
                last = Instant.parse(raw);
            } catch (DateTimeParseException e) {
                resp.put("status", "DOWN");
                resp.put("reason", "Invalid timestamp stored in heartbeat");
                return ResponseEntity.status(200).body(resp);
            }

            Duration age = Duration.between(last, Instant.now());
            resp.put("ageSeconds", age.getSeconds());
            if (age.compareTo(maxAge) <= 0) {
                resp.put("status", "UP");
                return ResponseEntity.ok(resp);
            } else {
                resp.put("status", "STALE");
                resp.put("reason", "Heartbeat older than allowed maxAgeSeconds=" + maxAge.getSeconds());
                return ResponseEntity.status(200).body(resp);
            }

        } catch (Exception e) {
            log.error("Failed to read heartbeat from Redis", e);
            resp.put("status", "DOWN");
            resp.put("reason", "Redis read error: " + e.getMessage());
            return ResponseEntity.status(200).body(resp);
        }
    }

    // src/main/java/com/events/paymentverifsystem/Controller/PaymentVerificationController.java

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> listActivePayments(
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("limit", limit);

        try {
            // Scan attendance:payments:* keys
            List<String> keys = redisPaymentStore.scanKeys("attendance:payments:*", limit);
            List<Map<String, Object>> active = new ArrayList<>();
            for (String k : keys) {
                Map<Object, Object> hash = redisTemplate.opsForHash().entries(k);
                if (hash != null && "received".equals(hash.get("status"))) {
                    Map<String, Object> payment = new LinkedHashMap<>();
                    hash.forEach((kk, vv) -> payment.put(kk.toString(), vv));
                    payment.put("_redisKey", k);
                    active.add(payment);
                }
            }
            resp.put("found", active.size());
            resp.put("payments", active);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    // ---------------- Processed listing with attached payment data ----------------

    /**
     * List processed keys and attach payment data (if available).
     *
     * Query params:
     *   pattern (default "processed:message:*")
     *   limit   (default 100)
     *
     * Returns:
     *  {
     *    "pattern": "...",
     *    "limit": 100,
     *    "found": N,
     *    "entries": [
     *       {
     *         "key":"processed:message:...","type":"string","value":"<messageId>",
     *         "payment": { "paymentId": "...", "amount":"...", "paymentTs":"...", ... } OR null
     *       }, ...
     *    ]
     *  }
     */
    @GetMapping("/processed")
    public ResponseEntity<Map<String, Object>> listProcessedWithPayments(
            @RequestParam(value = "pattern", defaultValue = "processed:message:*") String pattern,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("pattern", pattern);
        resp.put("limit", limit);

        try {
            // 1) scan processed keys
            List<String> processedKeys = scanKeys(pattern, limit);
            resp.put("found", processedKeys.size());

            // 2) build a map messageId -> paymentData by scanning attendance:payments:*
            Map<String, Map<String, Object>> messageIdToPayment = buildMessageIdToPaymentMap(BUSINESS_SCAN_LIMIT);

            List<Object> entries = new ArrayList<>(processedKeys.size());
            for (String k : processedKeys) {
                Map<String, Object> kv = new LinkedHashMap<>();
                kv.put("key", k);

                DataType type = redisTemplate.type(k);
                kv.put("type", type == null ? "none" : type.code());

                // get primary value (for processed keys it's usually the messageId string)
                Object value = null;
                if (type == DataType.STRING || type == DataType.NONE) {
                    value = redisTemplate.opsForValue().get(k);
                    kv.put("value", value);
                } else if (type == DataType.HASH) {
                    Map<Object, Object> hash = redisTemplate.opsForHash().entries(k);
                    kv.put("value", hash);
                    // try to extract messageId from the hash if present
                    Object mid = hash.get("messageId");
                    if (mid != null) value = mid.toString();
                } else {
                    // for lists/sets/zsets return a brief description
                    kv.put("info", fetchOverviewForComplexType(k, type));
                }

                String messageId = value == null ? null : value.toString();

                // attach payment info (if we found one in the business scan)
                Map<String, Object> payment = null;
                if (messageId != null) {
                    payment = messageIdToPayment.get(messageId);
                }
                kv.put("payment", payment); // may be null

                entries.add(kv);
            }

            resp.put("entries", entries);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Error scanning processed keys and attaching payments", e);
            resp.put("status", "error");
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    // ---------------- Helper methods ----------------

    /**
     * Scan Redis keys using SCAN and return up to 'limit' keys matching pattern.
     */
    private List<String> scanKeys(String pattern, int limit) {
        if (limit <= 0) return Collections.emptyList();

        return redisTemplate.execute((RedisCallback<List<String>>) connection -> {
            List<String> out = new ArrayList<>();
            Cursor<byte[]> cursor = null;
            try {
                ScanOptions scanOptions = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(Math.min(1000, Math.max(10, limit)))
                        .build();
                cursor = connection.scan(scanOptions);
                while (cursor.hasNext() && out.size() < limit) {
                    byte[] bs = cursor.next();
                    String key = stringSerializer.deserialize(bs);
                    out.add(key);
                }
            } finally {
                if (cursor != null) try { cursor.close(); } catch (Exception ignored) {}
            }
            return out;
        });
    }

    /**
     * Build a map from messageId -> paymentData by scanning attendance:payments:* hashes.
     *
     * We limit the number of business keys scanned to `businessScanLimit` to avoid high
     * latency on very large datasets. If you expect many business keys, increase the limit
     * or implement a dedicated index mapping (recommended).
     */
    private Map<String, Map<String, Object>> buildMessageIdToPaymentMap(int businessScanLimit) {
        Map<String, Map<String, Object>> out = new HashMap<>();

        // scan attendance:payments:* keys
        List<String> businessKeys = scanKeys("attendance:payments:*", businessScanLimit);
        for (String bkey : businessKeys) {
            try {
                DataType dt = redisTemplate.type(bkey);
                if (dt == DataType.HASH) {
                    Map<Object, Object> raw = redisTemplate.opsForHash().entries(bkey);
                    if (raw == null || raw.isEmpty()) continue;

                    // Convert hash fields to Map<String,Object>
                    Map<String, Object> paymentMap = new LinkedHashMap<>();
                    raw.forEach((k, v) -> {
                        String ks = k == null ? null : k.toString();
                        Object vs = v;
                        paymentMap.put(ks, vs);
                    });

                    // Expect 'messageId' field in business hash (as saved by the Lua script)
                    Object midObj = raw.get("messageId");
                    if (midObj != null) {
                        String mid = midObj.toString();
                        out.put(mid, paymentMap);
                    } else {
                        // optionally index by paymentId if present
                        Object pidObj = raw.get("paymentId");
                        if (pidObj != null) {
                            String pid = pidObj.toString();
                            paymentMap.put("_businessKey", bkey);
                            out.put("paymentId:" + pid, paymentMap); // alternate lookup
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read business key {}", bkey, e);
            }
        }
        return out;
    }

    /**
     * Provide a small overview for non-string/hash types (list/set/zset).
     */
    private Map<String, Object> fetchOverviewForComplexType(String key, DataType type) {
        Map<String, Object> info = new HashMap<>();
        try {
            switch (type) {
                case LIST:
                    Long size = redisTemplate.opsForList().size(key);
                    info.put("type", "list");
                    info.put("size", size);
                    info.put("sample", redisTemplate.opsForList().range(key, 0, Math.min(9, Math.max(0, size == null ? 0 : size))));
                    break;
                case SET:
                    Long sSize = redisTemplate.opsForSet().size(key);
                    info.put("type", "set");
                    info.put("size", sSize);
                    // members() can be big; limit to a few
                    Set<Object> members = redisTemplate.opsForSet().members(key);
                    if (members != null) {
                        info.put("sample", members.stream().limit(10).toList());
                    }
                    break;
                case ZSET:
                    Long zSize = redisTemplate.opsForZSet().size(key);
                    info.put("type", "zset");
                    info.put("size", zSize);
                    info.put("sample", redisTemplate.opsForZSet().range(key, 0, Math.min(9, Math.max(0, zSize == null ? 0 : zSize))));
                    break;
                default:
                    info.put("type", "unknown");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch overview for key {}: {}", key, e.getMessage());
        }
        return info;
    }


}
