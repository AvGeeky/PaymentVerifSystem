package com.events.paymentverifsystem.Utilities.Payment;

import java.time.Instant;
import java.util.Objects;

public class PaymentInfo {
    private String paymentId;
    private String amount; // normalized like "1.00"
    private Instant paidOn;
    private String payerEmail;
    private String phone;
    private String method;
    private String merchantName;
    private String subject;
    private String messageId;

    public PaymentInfo() {}

    public PaymentInfo(String paymentId, String amount, Instant paidOn, String payerEmail,
                       String phone, String method, String merchantName, String subject, String messageId) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.paidOn = paidOn;
        this.payerEmail = payerEmail;
        this.phone = phone;
        this.method = method;
        this.merchantName = merchantName;
        this.subject = subject;
        this.messageId = messageId;
    }

    // getters and setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public Instant getPaidOn() { return paidOn; }
    public void setPaidOn(Instant paidOn) { this.paidOn = paidOn; }

    public String getPayerEmail() { return payerEmail; }
    public void setPayerEmail(String payerEmail) { this.payerEmail = payerEmail; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    @Override
    public String toString() {
        return "PaymentInfo{" +
                "paymentId='" + paymentId + '\'' +
                ", amount='" + amount + '\'' +
                ", paidOn=" + paidOn +
                ", payerEmail='" + payerEmail + '\'' +
                ", phone='" + phone + '\'' +
                ", method='" + method + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", subject='" + subject + '\'' +
                ", messageId='" + messageId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentInfo)) return false;
        PaymentInfo that = (PaymentInfo) o;
        return Objects.equals(getPaymentId(), that.getPaymentId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPaymentId());
    }
}
