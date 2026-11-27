package com.adidas.payment;

import com.adidas.dto.response.BankTransferResponseDTO;
import com.adidas.entity.Order;
import com.adidas.entity.OrderStatus;
import com.adidas.entity.Payment;
import com.adidas.entity.PaymentMethod;
import com.adidas.entity.PaymentStatus;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.IOrderRepository;
import com.adidas.repository.IPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SePayService {
    private static final Pattern TRANSFER_CODE = Pattern.compile("DH[0-9]{6,19}");
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,12}");
    private static final Pattern SIGNATURE = Pattern.compile("sha256=[0-9a-fA-F]{64}");
    private static final long MAX_TIMESTAMP_DRIFT_SECONDS = 300;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SePayProperties settings;
    private final IOrderRepository orders;
    private final IPaymentRepository payments;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public BankTransferResponseDTO getTransferDetails(Long userId, Long orderId) {
        settings.requireConfigured();
        Order order = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn"));
        if (!awaitsBankTransfer(order)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không chờ thanh toán chuyển khoản");
        }
        long amount;
        try {
            amount = order.getFinalAmount().longValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền chuyển khoản phải là số nguyên VND");
        }
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền chuyển khoản phải lớn hơn 0");
        }
        String code = transferCode(orderId);
        String qrUrl = UriComponentsBuilder.fromUriString("https://vietqr.app/img")
                .queryParam("acc", settings.getAccountNumber())
                .queryParam("bank", settings.getBank())
                .queryParam("amount", amount)
                .queryParam("des", code)
                .build().encode().toUriString();
        return new BankTransferResponseDTO(orderId, order.getOrderCode(), order.getPaymentStatus(), amount,
                settings.getBank(), settings.getAccountNumber(), code, qrUrl);
    }

    @Transactional
    public void receiveWebhook(byte[] body, String timestamp, String signature) {
        settings.requireConfigured();
        verifySignature(body, timestamp, signature);
        JsonNode event = readEvent(body);
        if (!"in".equals(event.path("transferType").stringValue())
                || !settings.getAccountNumber().equals(event.path("accountNumber").stringValue())
                || !settings.getBank().equalsIgnoreCase(event.path("gateway").stringValue())) {
            return;
        }
        String code = event.path("code").stringValue();
        Long orderId = orderIdFromCode(code);
        if (orderId == null) {
            return;
        }
        String transactionId = Long.toString(positiveNumber(event, "id"));
        BigDecimal amount = BigDecimal.valueOf(positiveNumber(event, "transferAmount"));
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (payments.existsByPaymentMethodAndTransactionId(PaymentMethod.BANK_TRANSFER, transactionId)) {
            return;
        }
        if (order == null || !awaitsBankTransfer(order) || order.getFinalAmount().compareTo(amount) != 0) {
            log.warn("SePay transaction {} did not match a payable bank-transfer order {}", transactionId, orderId);
            return;
        }

        Payment payment = payments.findByOrderIdAndPaymentMethod(orderId, PaymentMethod.BANK_TRANSFER)
                .orElseGet(Payment::new);
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("SePay transaction {} requires reconciliation for order {}", transactionId, orderId);
            return;
        }
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setAmount(amount);
        payment.setTransactionId(transactionId);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        payments.save(payment);
        order.setPaymentStatus(PaymentStatus.PAID);
    }

    private void verifySignature(byte[] body, String timestamp, String signature) {
        if (timestamp == null || !TIMESTAMP.matcher(timestamp).matches()
                || signature == null || !SIGNATURE.matcher(signature).matches()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thiếu hoặc sai chữ ký SePay");
        }
        long signedAt = Long.parseLong(timestamp);
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - signedAt) > MAX_TIMESTAMP_DRIFT_SECONDS) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chữ ký SePay đã hết thời hạn xác thực");
        }

        byte[] expected;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(settings.getWebhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            expected = mac.doFinal(body);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể kiểm tra chữ ký SePay", ex);
        }
        byte[] received = HexFormat.of().parseHex(signature, "sha256=".length(), signature.length());
        if (!MessageDigest.isEqual(expected, received)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chữ ký SePay không hợp lệ");
        }
    }

    private JsonNode readEvent(byte[] body) {
        try {
            JsonNode event = json.readTree(body);
            if (event.isObject()) {
                return event;
            }
        } catch (JacksonException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung webhook SePay không hợp lệ", ex);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung webhook SePay không hợp lệ");
    }

    private static long positiveNumber(JsonNode event, String field) {
        JsonNode value = event.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trường SePay không hợp lệ: " + field);
        }
        return value.longValue();
    }

    private static Long orderIdFromCode(String code) {
        if (code == null || !TRANSFER_CODE.matcher(code).matches()) {
            return null;
        }
        try {
            long orderId = Long.parseLong(code.substring(2));
            return orderId > 0 && transferCode(orderId).equals(code) ? orderId : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String transferCode(long orderId) {
        return String.format(Locale.ROOT, "DH%06d", orderId);
    }

    private static boolean awaitsBankTransfer(Order order) {
        return order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                && order.getPaymentStatus() == PaymentStatus.UNPAID
                && order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RETURNED;
    }
}
