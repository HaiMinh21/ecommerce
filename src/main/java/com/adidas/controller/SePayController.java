package com.adidas.controller;

import com.adidas.dto.response.BankTransferResponseDTO;
import com.adidas.payment.SePayService;
import com.adidas.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SePayController {
    private final SePayService payments;

    @GetMapping("/orders/{orderId}/payments/bank-transfer")
    public ResponseEntity<BankTransferResponseDTO> getTransferDetails(
            @PathVariable Long orderId, @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(payments.getTransferDetails(principal.getUser().getId(), orderId));
    }

    @PostMapping(value = "/payments/sepay/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Boolean> receiveWebhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature) {
        payments.receiveWebhook(body, timestamp, signature);
        return Map.of("success", true);
    }
}
