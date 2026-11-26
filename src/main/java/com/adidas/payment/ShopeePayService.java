package com.adidas.payment;

import com.adidas.dto.response.ShopeePayResponseDTO;
import com.adidas.entity.Order;
import com.adidas.entity.OrderStatus;
import com.adidas.entity.Payment;
import com.adidas.entity.PaymentMethod;
import com.adidas.entity.PaymentStatus;
import com.adidas.entity.ShopeePayIntent;
import com.adidas.entity.ShopeePayIntent.State;
import com.adidas.entity.ShopeePayRequest;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.IOrderRepository;
import com.adidas.repository.IPaymentRepository;
import com.adidas.repository.IShopeePayIntentRepository;
import com.adidas.repository.IShopeePayRequestRepository;
import com.adidas.repository.IUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ShopeePayService {
    private static final Logger log = LoggerFactory.getLogger(ShopeePayService.class);
    private static final Pattern KEY = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ShopeePayProperties settings;
    private final ShopeePayClient gateway;
    private final IOrderRepository orders;
    private final IUserRepository users;
    private final IShopeePayIntentRepository intents;
    private final IShopeePayRequestRepository requests;
    private final IPaymentRepository payments;
    private final EntityManager entities;
    private final TransactionTemplate transaction;

    public ShopeePayService(ShopeePayProperties settings, ShopeePayClient gateway, IOrderRepository orders,
                            IUserRepository users, IShopeePayIntentRepository intents,
                            IShopeePayRequestRepository requests, IPaymentRepository payments,
                            EntityManager entities, PlatformTransactionManager manager) {
        this.settings = settings;
        this.gateway = gateway;
        this.orders = orders;
        this.users = users;
        this.intents = intents;
        this.requests = requests;
        this.payments = payments;
        this.entities = entities;
        this.transaction = new TransactionTemplate(manager);
    }

    public ShopeePayResponseDTO create(Long userId, Long orderId, String header) {
        requireEnabled();
        String key = idempotencyKey(header);
        Reservation reservation = transaction.execute(ignored -> reserve(userId, orderId, key));
        if (!reservation.sendToGateway()) {
            return reservation.response();
        }

        ShopeePayResponseDTO pending = reservation.response();
        ShopeePayClient.Qr qr = null;
        try {
            qr = gateway.createQr(pending.reference(), reservation.providerAmount(), pending.expiresAt());
        } catch (ShopeePayClient.GatewayException ex) {
            log.warn("ShopeePay QR creation could not be confirmed for order {}", orderId);
        }
        ShopeePayClient.Qr result = qr;
        return transaction.execute(ignored -> finishCreation(orderId, result));
    }

    public ShopeePayResponseDTO status(Long userId, Long orderId) {
        requireEnabled();
        return transaction.execute(ignored -> {
            Order order = lockOwnedOrder(userId, orderId);
            return response(order, lockedIntent(orderId));
        });
    }

    public ShopeePayResponseDTO check(Long userId, Long orderId) {
        requireEnabled();
        Reservation reservation = transaction.execute(ignored -> reserveCheck(userId, orderId));
        if (!reservation.sendToGateway()) {
            return reservation.response();
        }
        ShopeePayClient.Transaction result;
        try {
            result = gateway.check(reservation.response().reference(), reservation.providerAmount());
        } catch (ShopeePayClient.GatewayException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ShopeePay chưa trả về kết quả đối soát hợp lệ");
        }
        return result == null ? status(userId, orderId)
                : transaction.execute(ignored -> applyResult(orderId, result));
    }

    public void notifyPayment(byte[] rawBody, String signature) {
        requireEnabled();
        ShopeePayClient.Transaction result;
        try {
            result = gateway.parseCallback(rawBody, signature);
        } catch (ShopeePayClient.InvalidSignatureException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chữ ký ShopeePay không hợp lệ");
        } catch (ShopeePayClient.GatewayException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (result.type() != ShopeePayClient.PAYMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại giao dịch ShopeePay không được hỗ trợ");
        }

        Long orderId = intents.findOrderIdByReference(result.reference())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch ShopeePay"));
        transaction.executeWithoutResult(ignored -> applyResult(orderId, result));
    }

    private Reservation reserve(Long userId, Long orderId, String key) {
        // Serialize key bindings for this customer, including requests for different orders.
        users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        Order order = lockOwnedOrder(userId, orderId);
        ShopeePayRequest binding = requests.findByUserIdAndRequestKey(userId, key).orElse(null);
        if (binding != null && !orderId.equals(binding.getOrderId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency-Key đã được dùng cho đơn hàng khác");
        }

        ShopeePayIntent intent = lockedIntent(orderId);
        boolean create = intent == null && order.getPaymentStatus() != PaymentStatus.PAID;
        if (create) {
            if (order.getStatus() != OrderStatus.PENDING || order.getPaymentStatus() == PaymentStatus.REFUNDED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không thể bắt đầu thanh toán");
            }
            intent = new ShopeePayIntent();
            intent.setOrderId(orderId);
            intent.setReference("SPP-" + UUID.randomUUID());
            intent.setProviderAmount(providerAmount(order.getFinalAmount()));
            intent.setExpiresAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1_200));
            intent.setState(State.CREATING);
            intents.save(intent);
            order.setPaymentStatus(PaymentStatus.PENDING);
        }
        if (binding == null) {
            requests.save(new ShopeePayRequest(userId, key, orderId));
        }
        return new Reservation(response(order, intent), intent == null ? 0 : intent.getProviderAmount(), create);
    }

    private Reservation reserveCheck(Long userId, Long orderId) {
        Order order = lockOwnedOrder(userId, orderId);
        ShopeePayIntent intent = lockedIntent(orderId);
        Instant now = Instant.now();
        boolean check = intent != null && intent.getState() != State.PAID && intent.getState() != State.CLOSED
                && (intent.getLastCheckedAt() == null || !now.isBefore(intent.getLastCheckedAt().plusSeconds(5)));
        if (check) {
            intent.setLastCheckedAt(now);
        }
        return new Reservation(response(order, intent), intent == null ? 0 : intent.getProviderAmount(), check);
    }

    private ShopeePayResponseDTO finishCreation(Long orderId, ShopeePayClient.Qr qr) {
        Order order = lockOrder(orderId);
        ShopeePayIntent intent = lockedIntent(orderId);
        if (intent.getState() == State.CREATING) {
            if (qr == null) {
                intent.setState(State.UNKNOWN);
            } else {
                intent.setState(State.READY);
                intent.setQrContent(qr.content());
                intent.setQrUrl(qr.url());
                // Measure the URL lifetime from request creation. This expires the URL conservatively.
                intent.setQrUrlExpiresAt(intent.getExpiresAt().minusSeconds(900));
            }
        }
        return response(order, intent);
    }

    private ShopeePayResponseDTO applyResult(Long orderId, ShopeePayClient.Transaction result) {
        Order order = lockOrder(orderId);
        ShopeePayIntent intent = lockedIntent(orderId);
        if (intent == null || !intent.getReference().equals(result.reference())
                || result.amount() != intent.getProviderAmount() || result.type() != ShopeePayClient.PAYMENT
                || order.getPaymentMethod() != PaymentMethod.SHOPEEPAY
                || providerAmount(order.getFinalAmount()) != result.amount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch không khớp mã tham chiếu hoặc số tiền");
        }
        if (intent.getState() == State.PAID) {
            if (result.status() == ShopeePayClient.SUCCESS && !intent.getProviderTransactionId().equals(result.serial())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch ShopeePay khác giao dịch đã ghi nhận");
            }
            return response(order, intent);
        }
        if (result.status() == ShopeePayClient.SUCCESS) {
            recordPayment(order, intent, result.serial());
        } else if (result.status() == 6 || result.status() == 7) {
            // Only a confirmed expiry or invalidation closes a possibly payable QR.
            intent.setState(State.CLOSED);
            order.setPaymentStatus(PaymentStatus.UNPAID);
        }
        return response(order, intent);
    }

    private void recordPayment(Order order, ShopeePayIntent intent, String serial) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.SHOPEEPAY);
        payment.setAmount(order.getFinalAmount());
        payment.setTransactionId(serial);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        payments.save(payment);
        intent.setProviderTransactionId(serial);
        intent.setState(State.PAID);
        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.error("ShopeePay confirmed a payment for cancelled order {}. Reconciliation is required.", order.getId());
        }
    }

    private ShopeePayIntent lockedIntent(Long orderId) {
        ShopeePayIntent intent = intents.findById(orderId).orElse(null);
        if (intent != null) {
            // A second transaction in this web request can share the request's EntityManager.
            entities.refresh(intent, LockModeType.PESSIMISTIC_WRITE);
        }
        return intent;
    }

    private Order lockOwnedOrder(Long userId, Long orderId) {
        Order order = orders.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        entities.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (order.getPaymentMethod() != PaymentMethod.SHOPEEPAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không dùng ShopeePay");
        }
        return order;
    }

    private Order lockOrder(Long orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        entities.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    private ShopeePayResponseDTO response(Order order, ShopeePayIntent intent) {
        if (intent == null) {
            State state = order.getPaymentStatus() == PaymentStatus.PAID ? State.PAID : null;
            return new ShopeePayResponseDTO(order.getId(), null, order.getFinalAmount(), "VND",
                    order.getPaymentStatus(), state, null, null, null, false);
        }
        Instant now = Instant.now();
        boolean expired = !now.isBefore(intent.getExpiresAt());
        boolean showQr = intent.getState() == State.READY && !expired;
        boolean showUrl = showQr && intent.getQrUrlExpiresAt() != null && now.isBefore(intent.getQrUrlExpiresAt());
        return new ShopeePayResponseDTO(order.getId(), intent.getReference(), order.getFinalAmount(), "VND",
                order.getPaymentStatus(), intent.getState(), intent.getExpiresAt(),
                showQr ? intent.getQrContent() : null, showUrl ? intent.getQrUrl() : null, expired);
    }

    private void requireEnabled() {
        if (!settings.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ShopeePay chưa được cấu hình");
        }
    }

    private static long providerAmount(BigDecimal vnd) {
        try {
            if (vnd.signum() <= 0) {
                throw new ArithmeticException();
            }
            return Math.multiplyExact(vnd.longValueExact(), 100);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền ShopeePay phải là số nguyên VND dương");
        }
    }

    private static String idempotencyKey(String header) {
        String value = header == null ? "" : header.strip();
        if (value.length() == 38 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, 37);
        }
        if (!KEY.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key phải là một UUID");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private record Reservation(ShopeePayResponseDTO response, long providerAmount, boolean sendToGateway) {}
}
