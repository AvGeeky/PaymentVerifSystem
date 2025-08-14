package com.events.paymentverifsystem.Utilities.Email;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.events.paymentverifsystem.Utilities.Payment.PaymentInfo;
public final class EmailParser {
    private static final Logger log = LoggerFactory.getLogger(EmailParser.class);

    private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile("(pay_[A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("₹\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID_ON_PATTERN = Pattern.compile("(\\d{1,2}(?:st|nd|rd|th)?\\s+\\w{3,}\\,?\\s+\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?91[\\-\\s]?)?(\\d{10})");

    private EmailParser() {}

    /**
     * Parse javax.mail.Message -> PaymentInfo
     * Returns null if parser couldn't find paymentId and amount.
     */
    public static PaymentInfo parse(Message message) {
        try {
            String subject = safeGetSubject(message);
            String messageId = safeGetMessageId(message);
            String rawBody = extractText(message);
            Document doc = Jsoup.parse(rawBody);

            String merchantName = extractMerchant(doc);
            String paymentId = extractPaymentId(doc, subject, rawBody);
            String amount = extractAmount(doc, subject, rawBody);
            Instant paidOn = extractPaidOn(doc, message, rawBody);
            String payerEmail = extractPayerEmail(doc, rawBody, message);
            String phone = extractPhone(doc, rawBody);
            String method = extractMethod(doc, rawBody);

            if (paymentId == null && amount == null) {
                log.debug("Parser couldn't find paymentId or amount for message {}", messageId);
                return null;
            }

            if (amount != null) amount = normalizeAmount(amount);
            return new PaymentInfo(paymentId, amount, paidOn, payerEmail, phone, method, merchantName, subject, messageId);
        } catch (Exception e) {
            log.warn("Error parsing email", e);
            return null;
        }
    }

    private static String safeGetSubject(Message m) {
        try { return m.getSubject(); } catch (MessagingException e) { return ""; }
    }

    static String safeGetMessageId(Message message) {
        try {
            String[] hdr = message.getHeader("Message-ID");
            if (hdr != null && hdr.length > 0 && hdr[0] != null && !hdr[0].isBlank()) return hdr[0];
        } catch (Exception ignored) {}
        try {
            if (message instanceof javax.mail.internet.MimeMessage) {
                String mid = ((javax.mail.internet.MimeMessage) message).getMessageID();
                if (mid != null && !mid.isBlank()) return mid;
            }
        } catch (Exception ignored) {}
        return "synth-" + Math.abs(message.hashCode()) + "-" + System.currentTimeMillis();
    }

    private static String extractText(Message message) {
        try {
            Object content = message.getContent();
            if (content instanceof String) return (String) content;
            if (content instanceof Multipart) {
                Multipart mp = (Multipart) content;
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    if (bp.isMimeType("text/html")) return (String) bp.getContent();
                    if (bp.isMimeType("text/plain")) return (String) bp.getContent();
                }
            }
        } catch (IOException | MessagingException e) {
            log.warn("Could not read message content", e);
        }
        return "";
    }

    private static String extractMerchant(Document doc) {
        Element e = doc.selectFirst("h2, .branding-content, .header h2, .title-content, .content-element");
        return e != null ? e.text().trim() : null;
    }

    private static String extractPaymentId(Document doc, String subject, String raw) {
        String text = (doc.text() + " " + subject + " " + raw);
        Matcher m = PAYMENT_ID_PATTERN.matcher(text);
        if (m.find()) return m.group(1);
        // fallback: look in structured rows for label "Payment Id"
        Element info = doc.selectFirst(".information-row, .merchant-highlight, .card");
        if (info != null) {
            Matcher m2 = PAYMENT_ID_PATTERN.matcher(info.text());
            if (m2.find()) return m2.group(1);
        }
        return null;
    }

    private static String extractAmount(Document doc, String subject, String raw) {
        String text = (doc.text() + " " + subject + " " + raw);
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return g1 != null && !g1.isBlank() ? g1 : g2;
        }
        Element rupee = doc.selectFirst(".amount, .rupees, .symbol");
        if (rupee != null) return rupee.text().replaceAll("[^0-9.,]", "");
        return null;
    }

    private static Instant extractPaidOn(Document doc, Message message, String raw) {
        String marker = "Paid On";
        String textSource = doc.text();

        // First try: look for "Paid On" in HTML text
        int startIndex = textSource.indexOf(marker);
        if (startIndex != -1) {
            String afterMarker = textSource.substring(startIndex + marker.length()).trim();
            Instant parsed = tryParsePaidOn(afterMarker);
            if (parsed != null) return parsed;
        }

        // Second try: look for ISO date in whole raw text
        Matcher iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}(?::\\d{2})?(?:Z|[+-]\\d{2}:?\\d{2})?)")
                .matcher(textSource);
        if (iso.find()) {
            try {
                return Instant.parse(iso.group(1).replace(' ', 'T'));
            } catch (Exception ignored) {}
        }

        // Fallback: email sent date
        try {
            java.util.Date sent = message.getSentDate();
            if (sent != null) return sent.toInstant();
        } catch (MessagingException ignored) {}

        // Last fallback: now
        return Instant.now();
    }

    // helper to try multiple patterns
    private static Instant tryParsePaidOn(String dateStr) {
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("d MMM, yyyy hh:mm:ss a 'UTC'XXX", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM, yyyy hh:mm a 'UTC'XXX", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH)
        };
        for (DateTimeFormatter fmt : formatters) {
            try {
                TemporalAccessor ta = fmt.parse(dateStr);
                if (ta instanceof LocalDateTime) {
                    return ((LocalDateTime) ta).atZone(ZoneId.of("UTC")).toInstant();
                }
                if (ta instanceof LocalDate) {
                    return ((LocalDate) ta).atStartOfDay(ZoneId.of("UTC")).toInstant();
                }
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }



    private static String extractPayerEmail(Document doc, String raw, Message message) {
        Element emailEl = doc.selectFirst(".information-row:contains(Email) .value, .card:contains(Email) .value");
        if (emailEl != null) {
            Matcher m = EMAIL_PATTERN.matcher(emailEl.text());
            if (m.find()) return m.group(1).toLowerCase();
        }
        Matcher m = EMAIL_PATTERN.matcher(raw);
        if (m.find()) return m.group(1).toLowerCase();
        try {
            javax.mail.Address[] froms = message.getFrom();
            if (froms != null && froms.length > 0) {
                String addr = ((InternetAddress) froms[0]).getAddress();
                if (addr != null && !addr.isBlank()) return addr.toLowerCase();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractPhone(Document doc, String raw) {
        Matcher m = PHONE_PATTERN.matcher(doc.text());
        if (m.find()) return m.group(1);
        m = PHONE_PATTERN.matcher(raw);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String extractMethod(Document doc, String raw) {
        Element methodEl = doc.selectFirst(".information-row:contains(Method) .value, .information-row:contains(UPI)");
        if (methodEl != null) return methodEl.text();
        String t = (doc.text() + " " + raw).toLowerCase();
        if (t.contains("upi")) return "UPI";
        if (t.contains("card")) return "CARD";
        return null;
    }

    private static String normalizeAmount(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        if (!cleaned.contains(".")) cleaned = cleaned + ".00";
        return cleaned;
    }
}
