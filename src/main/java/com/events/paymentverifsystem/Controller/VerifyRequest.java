package com.events.paymentverifsystem.Controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class VerifyRequest {

    @NotBlank(message = "email is required")
    @Email(message = "invalid email format")
    private String email;

    @NotBlank(message = "amount is required")
    private String amount;

    public VerifyRequest() {}

    public VerifyRequest(String email, String amount) {
        this.email = email;
        this.amount = amount;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
}
