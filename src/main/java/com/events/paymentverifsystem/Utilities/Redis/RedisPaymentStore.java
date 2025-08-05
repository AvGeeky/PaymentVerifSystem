package com.events.paymentverifsystem.Utilities.Redis;
import com.events.paymentverifsystem.Utilities.Payment.PaymentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class RedisPaymentStore {
    private static final Logger log = LoggerFactory.getLogger(RedisPaymentStore.class);
    @Autowired
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisSerializer stringSerializer = new StringRedisSerializer();

    private static final String SAVE_LUA =
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end " +
                    "redis.call('SET', KEYS[1], ARGV[3], 'EX', tonumber(ARGV[1]), 'NX') " +
                    "redis.call('HMSET', KEYS[2], 'paymentId', ARGV[4], 'amount', ARGV[5], 'paymentTs', ARGV[6], 'messageId', ARGV[3], 'payerEmail', ARGV[7], 'status', 'received') " +
                    "redis.call('EXPIRE', KEYS[2], tonumber(ARGV[2])) " +
                    "redis.call('SET', KEYS[3], ARGV[4], 'EX', tonumber(ARGV[2]), 'NX') " +
                    "return 1";

    private static final String CONSUME_LUA =
            "local pid = redis.call('GET', KEYS[1]) " +
                    "if not pid then return nil end " +
                    "local bkey = KEYS[2] " +
                    "local vals = redis.call('HMGET', bkey, 'paymentId','amount','paymentTs','messageId','payerEmail','status') " +
                    "redis.call('DEL', KEYS[1]) " +
                    "redis.call('DEL', bkey) " +
                    "return vals";

    public RedisPaymentStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String processedKey(String messageId) {
        return "processed:message:" + sanitize(messageId);
    }
    private String businessKey(String paymentId) {
        return "attendance:payments:" + sanitize(paymentId);
    }
    private String verificationKey(String email, String amount) {
        return "verification:email:" + sanitize(email.toLowerCase()) + ":amount:" + sanitize(amount);
    }
    private String sanitize(String s) {
        if (s == null) return "null";
        return s.replaceAll("[\\r\\n\\s]+", "_");
    }

    public boolean savePaymentAtomic(PaymentInfo info, Duration businessTtl, int processedTtlSeconds) {
        try {
            String pkey = processedKey(info.getMessageId());
            String bkey = businessKey(info.getPaymentId());
            String vkey = verificationKey(info.getPayerEmail() == null ? "" : info.getPayerEmail(), info.getAmount() == null ? "" : info.getAmount());

            byte[] script = SAVE_LUA.getBytes(StandardCharsets.UTF_8);
            byte[] k1 = stringSerializer.serialize(pkey);
            byte[] k2 = stringSerializer.serialize(bkey);
            byte[] k3 = stringSerializer.serialize(vkey);

            byte[][] argv = new byte[7][];
            argv[0] = stringSerializer.serialize(String.valueOf(processedTtlSeconds));
            argv[1] = stringSerializer.serialize(String.valueOf(businessTtl.getSeconds()));
            argv[2] = stringSerializer.serialize(info.getMessageId());
            argv[3] = stringSerializer.serialize(info.getPaymentId());
            argv[4] = stringSerializer.serialize(info.getAmount() == null ? "" : info.getAmount());
            argv[5] = stringSerializer.serialize(info.getPaidOn() == null ? Instant.now().toString() : info.getPaidOn().toString());
            argv[6] = stringSerializer.serialize(info.getPayerEmail() == null ? "" : info.getPayerEmail());

            Object res = redisTemplate.execute((RedisCallback<Object>) conn ->
                    conn.scriptingCommands().eval(script, ReturnType.INTEGER, 3, k1, k2, k3, argv[0], argv[1], argv[2], argv[3], argv[4], argv[5], argv[6])
            );

            if (res instanceof Number) return ((Number) res).intValue() == 1;
            return false;
        } catch (Exception e) {
            log.error("SAVE_LUA failed, falling back to non-atomic save", e);
            try {
                // fallback naive approach
                String pkey = processedKey(info.getMessageId());
                Boolean set = redisTemplate.opsForValue().setIfAbsent(pkey, info.getMessageId(), Duration.ofSeconds(processedTtlSeconds));
                if (Boolean.TRUE.equals(set)) {
                    String bkey = businessKey(info.getPaymentId());
                    redisTemplate.opsForHash().put(bkey, "paymentId", info.getPaymentId());
                    redisTemplate.opsForHash().put(bkey, "amount", info.getAmount());
                    redisTemplate.opsForHash().put(bkey, "paymentTs", info.getPaidOn() == null ? Instant.now().toString() : info.getPaidOn().toString());
                    redisTemplate.opsForHash().put(bkey, "messageId", info.getMessageId());
                    redisTemplate.opsForHash().put(bkey, "payerEmail", info.getPayerEmail());
                    redisTemplate.opsForHash().put(bkey, "status", "received");
                    redisTemplate.expire(bkey, businessTtl);
                    redisTemplate.opsForValue().set(verificationKey(info.getPayerEmail(), info.getAmount()), info.getPaymentId(), businessTtl);
                    return true;
                } else {
                    return false;
                }
            } catch (Exception ex) {
                log.error("Non-atomic fallback failed", ex);
                return false;
            }
        }
    }

    public PaymentInfo consumeByEmailAndAmount(String email, String amount) {
        try {
            String vkey = verificationKey(email, amount);
            String paymentId = (String) redisTemplate.opsForValue().get(vkey);
            if (paymentId == null) return null;

            String bkey = businessKey(paymentId);
            byte[] script = CONSUME_LUA.getBytes(StandardCharsets.UTF_8);
            byte[] k1 = stringSerializer.serialize(vkey);
            byte[] k2 = stringSerializer.serialize(bkey);

            Object res = redisTemplate.execute((RedisCallback<Object>) conn ->
                    conn.scriptingCommands().eval(script, ReturnType.MULTI, 2, k1, k2)
            );

            if (res instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) res;
                String pid = asString(list, 0);
                String amt = asString(list, 1);
                String paymentTs = asString(list, 2);
                String mid = asString(list, 3);
                String payerEmail = asString(list, 4);
                Instant paidOn;
                try { paidOn = Instant.parse(paymentTs); } catch (Exception ignored) { paidOn = Instant.now(); }
                return new PaymentInfo(pid, amt, paidOn, payerEmail, null, null, null, null, mid);
            }

            // fallback: best-effort
            paymentId = (String) redisTemplate.opsForValue().get(vkey);
            if (paymentId == null) return null;
            Map<Object,Object> hash = redisTemplate.opsForHash().entries(bkey);
            if (hash == null || hash.isEmpty()) { redisTemplate.delete(vkey); return null; }
            String pid = (String) hash.get("paymentId");
            String amt = (String) hash.get("amount");
            String paymentTs = (String) hash.get("paymentTs");
            String mid = (String) hash.get("messageId");
            String payerEmail = (String) hash.get("payerEmail");
            redisTemplate.delete(vkey);
            redisTemplate.delete(bkey);
            Instant paidOn;
            try { paidOn = Instant.parse(paymentTs); } catch (Exception ignored) { paidOn = Instant.now(); }
            return new PaymentInfo(pid, amt, paidOn, payerEmail, null, null, null, null, mid);
        } catch (Exception e) {
            log.error("consumeByEmailAndAmount failed", e);
            return null;
        }
    }

    private String asString(java.util.List<?> list, int idx) {
        if (idx >= list.size()) return null;
        Object o = list.get(idx);
        return o == null ? null : o.toString();
    }
}
