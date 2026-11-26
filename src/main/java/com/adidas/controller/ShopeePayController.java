package com.adidas.controller;

import com.adidas.dto.response.ShopeePayResponseDTO;
import com.adidas.entity.ShopeePayIntent.State;
import com.adidas.payment.ShopeePayClient;
import com.adidas.payment.ShopeePayProperties;
import com.adidas.payment.ShopeePayService;
import com.adidas.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShopeePayController {
    private static final String PAYMENT_PATH = "/orders/{orderId}/payments/shopeepay";
    private static final byte[] ACKNOWLEDGMENT = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);

    private final ShopeePayService payments;
    private final ShopeePayClient gateway;
    private final ShopeePayProperties settings;

    @PostMapping(PAYMENT_PATH)
    public ResponseEntity<ShopeePayResponseDTO> create(
            @PathVariable Long orderId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @AuthenticationPrincipal CustomUserDetails principal) {
        ShopeePayResponseDTO payment = payments.create(principal.getUser().getId(), orderId, key);
        HttpStatus status = payment.state() == State.CREATING || payment.state() == State.UNKNOWN
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(payment);
    }

    @GetMapping(PAYMENT_PATH)
    public ResponseEntity<ShopeePayResponseDTO> status(
            @PathVariable Long orderId, @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(payments.status(principal.getUser().getId(), orderId));
    }

    @PostMapping(PAYMENT_PATH + "/check")
    public ResponseEntity<ShopeePayResponseDTO> check(
            @PathVariable Long orderId, @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(payments.check(principal.getUser().getId(), orderId));
    }

    @PostMapping(value = "/payments/shopeepay/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> notify(@RequestBody byte[] body, @RequestHeader HttpHeaders headers) {
        payments.notifyPayment(body, headers.getFirst(settings.getCallbackSignatureHeader()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(settings.getResponseSignatureHeader(), gateway.sign(ACKNOWLEDGMENT))
                .body(ACKNOWLEDGMENT);
    }
}
