package com.adidas.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Getter
@Setter
@ConfigurationProperties("sepay")
public class SePayProperties {
    private boolean enabled;
    private String bank;
    private String accountNumber;
    private String webhookSecret;

    public void requireConfigured() {
        if (!enabled || !StringUtils.hasText(bank) || !StringUtils.hasText(accountNumber)
                || !StringUtils.hasText(webhookSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SePay chưa được cấu hình");
        }
    }
}
