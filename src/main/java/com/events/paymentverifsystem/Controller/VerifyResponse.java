package com.events.paymentverifsystem.Controller;

import com.events.paymentverifsystem.Utilities.Payment.PaymentInfo;

public class VerifyResponse {
    private boolean success;
    private String message;
    private PaymentInfo payment;

    public VerifyResponse() {}

    public VerifyResponse(boolean success, String message, PaymentInfo payment) {
        this.success = success;
        this.message = message;
        this.payment = payment;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public PaymentInfo getPayment() { return payment; }
    public void setPayment(PaymentInfo payment) { this.payment = payment; }
}
