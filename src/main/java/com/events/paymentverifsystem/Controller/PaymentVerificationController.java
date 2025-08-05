package com.events.paymentverifsystem.Controller;

import com.events.paymentverifsystem.Utilities.Payment.PaymentInfo;
import com.events.paymentverifsystem.Utilities.Redis.RedisPaymentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentVerificationController {
    private static final Logger log = LoggerFactory.getLogger(PaymentVerificationController.class);

    private final RedisPaymentStore redisPaymentStore;

    public PaymentVerificationController(RedisPaymentStore redisPaymentStore) {
        this.redisPaymentStore = redisPaymentStore;
    }

    /**
     * Verify and consume a pending payment by email + amount.
     * On success: returns 200 with the PaymentInfo that was consumed.
     * On not found: returns 404.
     */
    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verifyAndConsume(@Valid @RequestBody VerifyRequest req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        String amountNormalized = normalizeAmount(req.getAmount());

        log.info("Verification request received for email={} amount={}", email, amountNormalized);

        PaymentInfo info = redisPaymentStore.consumeByEmailAndAmount(email, amountNormalized);
        if (info == null) {
            log.info("No matching payment found for email={} amount={}", email, amountNormalized);
            VerifyResponse resp = new VerifyResponse(false, "Payment not found", null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        // success
        log.info("Payment consumed for email={} paymentId={}", email, info.getPaymentId());
        VerifyResponse resp = new VerifyResponse(true, "Payment verified", info);
        return ResponseEntity.ok(resp);
    }


    private String normalizeAmount(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[^0-9.]", "").trim();
        if (cleaned.isEmpty()) return "";
        try {
            // Ensure two decimals
            if (!cleaned.contains(".")) {
                return cleaned + ".00";
            } else {
                String[] parts = cleaned.split("\\.", 2);
                String intPart = parts[0].isEmpty() ? "0" : parts[0];
                String frac = parts.length > 1 ? parts[1] : "";
                if (frac.length() == 0) frac = "00";
                if (frac.length() == 1) frac = frac + "0";
                if (frac.length() > 2) frac = frac.substring(0, 2); // cut extra precision
                return intPart + "." + frac;
            }
        } catch (Exception e) {
            return cleaned;
        }
    }


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = (err instanceof FieldError) ? ((FieldError) err).getField() : err.getObjectName();
            String message = err.getDefaultMessage();
            errors.put(field, message);
        });
        return errors;
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Map<String, String> handleGenericException(Exception ex) {
        log.error("Unhandled error in verify endpoint", ex);
        Map<String, String> m = new HashMap<>();
        m.put("error", "internal_server_error");
        m.put("message", ex.getMessage() == null ? "unexpected error" : ex.getMessage());
        return m;
    }
}
