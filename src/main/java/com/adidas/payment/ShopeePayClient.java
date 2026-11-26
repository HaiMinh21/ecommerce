package com.adidas.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ShopeePayClient {
    public static final int PAYMENT = 13;
    public static final int SUCCESS = 3;
    private static final int MAX_RESPONSE_BYTES = 65_536;
    private static final String HMAC = "HmacSHA256";

    private final ShopeePayProperties settings;
    private final ObjectMapper json;
    private final RestClient http;

    @Autowired
    public ShopeePayClient(ShopeePayProperties settings, ObjectMapper json) {
        this(settings, json, httpBuilder());
    }

    ShopeePayClient(ShopeePayProperties settings, ObjectMapper json, RestClient.Builder builder) {
        this.settings = settings;
        this.json = json;
        if (settings.isEnabled()) {
            validateSettings();
        }
        this.http = builder.baseUrl(settings.getBaseUrl()).build();
    }

    public Qr createQr(String reference, long amount, Instant expiresAt) {
        Map<String, Object> request = request(reference);
        request.put("payment_reference_id", reference);
        request.put("currency", "VND");
        request.put("amount", amount);
        request.put("expiry_time", expiresAt.getEpochSecond());
        request.put("preferred_payment_method_type", "spp_wallet");
        JsonNode response = post("/v3/merchant-host/qr/create", request);
        String content = text(response, "qr_content");
        if (number(response, "errcode") != 0 || content == null || content.length() > 4096) {
            throw new GatewayException("ShopeePay chưa trả về mã QR có thể sử dụng");
        }
        String url = text(response, "qr_url");
        if (url != null && (!url.startsWith("https://") || url.length() > 4096)) {
            url = null;
        }
        return new Qr(content, url);
    }

    public Transaction check(String reference, long amount) {
        Map<String, Object> request = request(UUID.randomUUID().toString());
        request.put("reference_id", reference);
        request.put("transaction_type", PAYMENT);
        request.put("amount", amount);
        JsonNode response = post("/v3/merchant-host/transaction/check", request);
        long error = number(response, "errcode");
        if (error == 4 || error == 5) {
            return null;
        }
        if (error != 0) {
            throw new GatewayException("ShopeePay chưa xác nhận được trạng thái giao dịch");
        }
        JsonNode transaction = response.path("transaction");
        if (transaction.isMissingNode() || transaction.isNull() || (transaction.isObject() && transaction.isEmpty())) {
            return null;
        }
        Transaction result = transaction(transaction, "status");
        if (!reference.equals(result.reference()) || amount != result.amount() || result.type() != PAYMENT) {
            throw new GatewayException("Kết quả đối soát ShopeePay không khớp yêu cầu");
        }
        return result;
    }

    public Transaction parseCallback(byte[] body, String signature) {
        if (body.length > MAX_RESPONSE_BYTES || !signatureMatches(body, signature)) {
            throw new InvalidSignatureException();
        }
        try {
            JsonNode payload = json.readTree(body);
            if (!settings.getMerchantExtId().equals(text(payload, "merchant_ext_id"))
                    || !settings.getStoreExtId().equals(text(payload, "store_ext_id"))
                    || (payload.hasNonNull("currency") && !"VND".equals(text(payload, "currency")))) {
                throw new GatewayException("Thông tin cửa hàng hoặc loại tiền không khớp");
            }
            return transaction(payload, "transaction_status");
        } catch (JacksonException ex) {
            throw new GatewayException("Thông báo ShopeePay không đúng định dạng", ex);
        }
    }

    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(settings.getSecretKey().getBytes(StandardCharsets.UTF_8), HMAC));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể tạo chữ ký ShopeePay", ex);
        }
    }

    private boolean signatureMatches(byte[] body, String signature) {
        return signature != null && MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private Map<String, Object> request(String requestId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("request_id", requestId);
        fields.put("merchant_ext_id", settings.getMerchantExtId());
        fields.put("store_ext_id", settings.getStoreExtId());
        return fields;
    }

    private JsonNode post(String path, Map<String, Object> request) {
        byte[] body = json.writeValueAsBytes(request);
        try {
            return http.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Airpay-ClientId", settings.getClientId())
                    .header("X-Airpay-Req-H", sign(body))
                    .body(body)
                    .exchange((outgoing, response) -> {
                        byte[] raw = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (!response.getStatusCode().is2xxSuccessful() || raw.length > MAX_RESPONSE_BYTES
                                || !signatureMatches(raw, response.getHeaders().getFirst(settings.getResponseSignatureHeader()))) {
                            throw new GatewayException("Phản hồi ShopeePay không thể xác minh");
                        }
                        JsonNode payload = json.readTree(raw);
                        if (!request.get("request_id").equals(text(payload, "request_id"))) {
                            throw new GatewayException("Mã yêu cầu trong phản hồi ShopeePay không khớp");
                        }
                        return payload;
                    });
        } catch (RestClientException | JacksonException ex) {
            throw new GatewayException("Không thể nhận phản hồi hợp lệ từ ShopeePay", ex);
        }
    }

    private Transaction transaction(JsonNode node, String statusField) {
        String reference = text(node, "reference_id");
        String serial = text(node, "transaction_sn");
        long type = number(node, "transaction_type");
        long status = number(node, statusField);
        long amount = number(node, "amount");
        if (reference == null || reference.length() > 64 || amount <= 0
                || (status == SUCCESS && (serial == null || serial.length() > 255))) {
            throw new GatewayException("Thông tin giao dịch ShopeePay thiếu hoặc không hợp lệ");
        }
        return new Transaction(reference, amount, type, status, serial);
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static long number(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new GatewayException("Trường số trong phản hồi ShopeePay không hợp lệ");
        }
        return value.longValue();
    }

    private void validateSettings() {
        String base = settings.getBaseUrl().replaceFirst("/$", "");
        if (!base.equals("https://api.uat.wallet.airpay.vn") && !base.equals("https://api.wallet.airpay.vn")) {
            throw new IllegalStateException("ShopeePay yêu cầu API HTTPS chính thức tại Việt Nam");
        }
        if (blank(settings.getClientId()) || blank(settings.getSecretKey()) || blank(settings.getMerchantExtId())
                || blank(settings.getStoreExtId()) || blank(settings.getResponseSignatureHeader())
                || blank(settings.getCallbackSignatureHeader())) {
            throw new IllegalStateException("Thiếu thông tin cấu hình merchant ShopeePay");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static RestClient.Builder httpBuilder() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory);
    }

    public record Qr(String content, String url) {}
    public record Transaction(String reference, long amount, long type, long status, String serial) {}

    public static class GatewayException extends RuntimeException {
        GatewayException(String message) { super(message); }
        GatewayException(String message, Throwable cause) { super(message, cause); }
    }

    public static class InvalidSignatureException extends RuntimeException {}
}
