package com.adidas.service;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.request.ShippingUpdateRequestDTO;
import com.adidas.dto.response.MonthlyRevenueDTO;
import com.adidas.dto.response.OrderItemResponseDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.dto.response.MonthlyRevenueResponseDTO;
import com.adidas.dto.response.PaymentResponseDTO;
import com.adidas.dto.response.ShippingResponseDTO;
import com.adidas.entity.*;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.payment.SePayProperties;
import com.adidas.payment.ShopeePayProperties;
import com.adidas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ICartRepository cartRepository;
    private final IProductVariantRepository productVariantRepository;
    private final IUserRepository userRepository;
    private final IAddressRepository addressRepository;
    private final ICouponRepository couponRepository;
    private final IProductImageRepository productImageRepository;
    private final IShippingRepository shippingRepository;
    private final IPaymentRepository paymentRepository;
    private final IShopeePayIntentRepository shopeePayIntents;
    private final ShopeePayProperties shopeePaySettings;
    private final SePayProperties sePaySettings;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponseDTO checkout(Long userId, OrderRequestDTO orderRequestDTO) {
        boolean shopeePay = orderRequestDTO.getPaymentMethod() == PaymentMethod.SHOPEEPAY;
        boolean bankTransfer = orderRequestDTO.getPaymentMethod() == PaymentMethod.BANK_TRANSFER;
        if (shopeePay && !shopeePaySettings.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ShopeePay chưa được cấu hình");
        }
        if (bankTransfer) {
            sePaySettings.requireConfigured();
        }
        Users user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        // Lấy và kiểm tra giỏ hàng
        Cart cart = cartRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng"));
        if (cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Giỏ hàng của bạn đang trống. Không thể đặt hàng");
        }
        // kiểm tra địa chỉ và mã giảm giá
        Address address = addressRepository.findByIdAndUserId(orderRequestDTO.getAddressId(), userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng"));

        Coupon coupon = null;
        BigDecimal cartTotal = cart.getTotalPrice();
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (orderRequestDTO.getCouponId() != null) {
            coupon = couponRepository.findById(orderRequestDTO.getCouponId()).orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá không tồn tại"));

            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
                throw new BadRequestException("Mã giảm giá chưa đến thời hạn sử dụng hoặc đã hết hạn");
            }
            if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
                throw new BadRequestException("Mã giảm giá đã hết lượt sử dụng");
            }
            if (cartTotal.compareTo(coupon.getMinOrderValue()) < 0) {
                throw new BadRequestException("Đơn hàng chưa đạt giá trị tối thiểu để áp dụng mã giảm giá");
            }

            if (coupon.getDiscountType() == DiscountType.FIXED) {
                discountAmount = coupon.getDiscountValue();
            } else if (coupon.getDiscountType() == DiscountType.PERCENT) {
                BigDecimal calculateDiscount = cartTotal.multiply(coupon.getDiscountValue()).divide(new BigDecimal("100"));

                if (coupon.getMaxDiscount() != null && calculateDiscount.compareTo(coupon.getMaxDiscount()) > 0) {
                    discountAmount = coupon.getMaxDiscount();
                } else {
                    discountAmount = calculateDiscount;
                }
            }
            coupon.setUsedCount(coupon.getUsedCount() + 1);
        }

        // tạo khung đơn hàng
        String orderCode = "ADIDAS-" + System.currentTimeMillis();

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setCoupon(coupon);
        order.setOrderCode(orderCode);
        order.setNote(orderRequestDTO.getNote());

        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(orderRequestDTO.getPaymentMethod());
        // set mặc định người dùng chưa thanh toán
        order.setPaymentStatus(PaymentStatus.UNPAID);

        // chuyển cart item -> order item và trừ tồn kho
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getCartItems()) {
            ProductVariant productVariant = cartItem.getProductVariant();
            if (productVariant.getStockQuantity() < cartItem.getQuantity()) {
                throw new BadRequestException("Sản phẩm" + cartItem.getProduct().getName() + " Size: " + productVariant.getVariantName() + " đã hết hàng hoặc không đủ số lượng trong kho");
            }
            // trừ tồn kho và lưu lại
            productVariant.setStockQuantity(productVariant.getStockQuantity() - cartItem.getQuantity());
            productVariantRepository.save(productVariant);

            // tính tiền và chuyển sang order item
            BigDecimal subtotal = cartItem.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductVariant(productVariant);
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());
            orderItem.setSubtotal(subtotal);

            orderItems.add(orderItem);
        }

        order.setOrderItems(orderItems);

        // chốt ổng tiền và lưu đơn hàng
        order.setTotalAmount(totalAmount);

        // giả lập phí ship cứng cho đơn hàng
        BigDecimal shippingFee = new BigDecimal("30000");
        order.setShippingFee(shippingFee);

        BigDecimal finalAmount = totalAmount.add(shippingFee).subtract(discountAmount);

        if (finalAmount.compareTo(BigDecimal.ZERO) < 0 ) {
            finalAmount = BigDecimal.ZERO;
        }
        if (shopeePay || bankTransfer) {
            finalAmount = finalAmount.setScale(0, RoundingMode.HALF_UP);
            discountAmount = totalAmount.add(shippingFee).subtract(finalAmount);
            if (finalAmount.signum() == 0) {
                order.setPaymentStatus(PaymentStatus.PAID);
            }
        }
        order.setFinalAmount(finalAmount);

        Order savedOrder = orderRepository.save(order);

        //  xóa giỏ hàng
        cart.getCartItems().clear();
        cart.setTotalPrice(BigDecimal.ZERO);
        cartRepository.save(cart);

        List<OrderItemResponseDTO> itemResponseDTOS = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            String mainImageUrl = null;
            List<ProductImage> images = productImageRepository.findByProductId(item.getProduct().getId());
            for (ProductImage img : images) {
                if (Boolean.TRUE.equals(img.getIsMain())) {
                    mainImageUrl = img.getImageUrl();
                    break;
                }
            }

            OrderItemResponseDTO orderItemResponseDTO = OrderItemResponseDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productImageUrl(mainImageUrl)
                    .productVariantId(item.getProductVariant().getId())
                    .variantName(item.getProductVariant().getVariantName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .subtotal(item.getSubtotal())
                    .build();
            itemResponseDTOS.add(orderItemResponseDTO);
        }

        return OrderResponseDTO.builder()
                .id(savedOrder.getId())
                .orderCode(savedOrder.getOrderCode())
                .userId(savedOrder.getUser().getId())
                .receiverName(order.getAddress().getReceiverName())
                .phone(order.getAddress().getPhone())
                .deliveryAddress(order.getAddress().getFullAddress())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(discountAmount)
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .payment(PaymentResponseDTO.builder()
                        .paymentMethod(order.getPaymentMethod())
                        .status(order.getPaymentStatus())
                        .amount(order.getFinalAmount())
                        .transactionId(null)
                        .paidAt(null).build())
                .items(itemResponseDTOS).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponseDTO cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn"));
        validatePaymentCancellation(order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy đơn hàng ở trạng thái chờ xác nhận (Pending). Trạng thái hiện tại: " + order.getStatus());
        }
        restoreStock(order);
        // cập nhật trạng thái đơn hàng
        order.setStatus(OrderStatus.CANCELLED);

        String currentNote = order.getNote() != null ? order.getNote() : "";
        order.setNote(currentNote + "Hệ thống: Khách hàng tự hủy đơn");

        Order savedOrder = orderRepository.save(order);
        List<OrderItemResponseDTO> itemResponseDTOS = new ArrayList<>();
        for (OrderItem item : savedOrder.getOrderItems()) {
            String mainImageUrl = null;
            List<ProductImage> images = productImageRepository.findByProductId(item.getProduct().getId());
            for (ProductImage img : images) {
                if (Boolean.TRUE.equals(img.getIsMain())) {
                    mainImageUrl = img.getImageUrl();
                    break;
                }
            }

            OrderItemResponseDTO orderItemResponseDTO = OrderItemResponseDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productImageUrl(mainImageUrl)
                    .productVariantId(item.getProductVariant().getId())
                    .variantName(item.getProductVariant().getVariantName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .subtotal(item.getSubtotal())
                    .build();
            itemResponseDTOS.add(orderItemResponseDTO);
        }

        BigDecimal discountAmount = savedOrder.getTotalAmount().add(savedOrder.getShippingFee()).subtract(savedOrder.getFinalAmount());

        return OrderResponseDTO.builder()
                .id(savedOrder.getId())
                .orderCode(savedOrder.getOrderCode())
                .userId(savedOrder.getUser().getId())
                .receiverName(order.getAddress().getReceiverName())
                .phone(order.getAddress().getPhone())
                .deliveryAddress(order.getAddress().getFullAddress())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(discountAmount)
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .payment(PaymentResponseDTO.builder()
                        .paymentMethod(order.getPaymentMethod())
                        .status(order.getPaymentStatus())
                        .amount(order.getFinalAmount())
                        .transactionId(null)
                        .paidAt(null).build())
                .items(itemResponseDTOS).build();
    }

    @Override
    public Page<OrderResponseDTO> getOrderHistory(Long userId, OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            orders = orderRepository.findByUserId(userId, pageable);
        }
        List<OrderResponseDTO> orderResponseDTOList = new ArrayList<>();
        for (Order order : orders.getContent()) {
            List<OrderItemResponseDTO> itemResponseDTOS = new ArrayList<>();
            for (OrderItem item : order.getOrderItems()) {
                String mainImageUrl = null;
                List<ProductImage> images = productImageRepository.findByProductId(item.getProduct().getId());
                for (ProductImage img : images) {
                    if (Boolean.TRUE.equals(img.getIsMain())) {
                        mainImageUrl = img.getImageUrl();
                        break;
                    }
                }

                OrderItemResponseDTO orderItemResponseDTO = OrderItemResponseDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productImageUrl(mainImageUrl)
                        .productVariantId(item.getProductVariant().getId())
                        .variantName(item.getProductVariant().getVariantName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getSubtotal())
                        .build();
                itemResponseDTOS.add(orderItemResponseDTO);
            }
            BigDecimal discountAmount = order.getTotalAmount().add(order.getShippingFee()).subtract(order.getFinalAmount());

            OrderResponseDTO dto = OrderResponseDTO.builder()
                    .id(order.getId())
                    .orderCode(order.getOrderCode())
                    .userId(order.getUser().getId())
                    .receiverName(order.getAddress().getReceiverName())
                    .phone(order.getAddress().getPhone())
                    .deliveryAddress(order.getAddress().getFullAddress())
                    .totalAmount(order.getTotalAmount())
                    .shippingFee(order.getShippingFee())
                    .discountAmount(discountAmount)
                    .finalAmount(order.getFinalAmount())
                    .status(order.getStatus())
                    .note(order.getNote())
                    .createdAt(order.getCreatedAt())
                    .payment(PaymentResponseDTO.builder()
                            .paymentMethod(order.getPaymentMethod())
                            .status(order.getPaymentStatus())
                            .amount(order.getFinalAmount())
                            .transactionId(null)
                            .paidAt(null)
                            .build())
                    .items(itemResponseDTOS)
                    .build();
            orderResponseDTOList.add(dto);
        }
        return new PageImpl<>(orderResponseDTOList, pageable, orders.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderByCode(String orderCode, Long userId, boolean admin) {
        var matchingOrder = admin ? orderRepository.findByOrderCode(orderCode)
                : orderRepository.findByOrderCodeAndUserId(orderCode, userId);
        Order order = matchingOrder.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        ShippingResponseDTO shipping = shippingRepository.findByOrderId(order.getId())
                .map(this::shippingResponse)
                .orElse(null);
        Payment payment = order.getPaymentMethod() == PaymentMethod.COD || order.getPaymentMethod() == PaymentMethod.SHOPEEPAY
                || order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                ? paymentRepository.findByOrderIdAndPaymentMethod(order.getId(), order.getPaymentMethod()).orElse(null)
                : null;
        List<OrderItemResponseDTO> itemResponseDTOS = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            String mainImageUrl = null;
            List<ProductImage> images = productImageRepository.findByProductId(item.getProduct().getId());
            for (ProductImage img : images) {
                if (Boolean.TRUE.equals(img.getIsMain())) {
                    mainImageUrl = img.getImageUrl();
                    break;
                }
            }

            OrderItemResponseDTO orderItemResponseDTO = OrderItemResponseDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productImageUrl(mainImageUrl)
                    .productVariantId(item.getProductVariant().getId())
                    .variantName(item.getProductVariant().getVariantName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .subtotal(item.getSubtotal())
                    .build();
            itemResponseDTOS.add(orderItemResponseDTO);
        }

        BigDecimal discountAmount = order.getTotalAmount().add(order.getShippingFee()).subtract(order.getFinalAmount());

        return OrderResponseDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUser().getId())
                .receiverName(order.getAddress().getReceiverName())
                .phone(order.getAddress().getPhone())
                .deliveryAddress(order.getAddress().getFullAddress())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(discountAmount)
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .payment(paymentResponse(order, payment))
                .items(itemResponseDTOS)
                .shipping(shipping)
                .build();
    }

    @Override
    public Page<OrderResponseDTO> getAllOrdersForAdmin(OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByStatus(status, pageable);
        } else {
            orderPage = orderRepository.findAll(pageable);
        }
        List<OrderResponseDTO> orderResponseDTOList = new ArrayList<>();
        for (Order order : orderPage.getContent()) {
            List<OrderItemResponseDTO> itemResponseDTOS = new ArrayList<>();
            for (OrderItem item : order.getOrderItems()) {
                String mainImageUrl = null;
                List<ProductImage> images = productImageRepository.findByProductId(item.getProduct().getId());
                for (ProductImage img : images) {
                    if (Boolean.TRUE.equals(img.getIsMain())) {
                        mainImageUrl = img.getImageUrl();
                        break;
                    }
                }

                OrderItemResponseDTO orderItemResponseDTO = OrderItemResponseDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productImageUrl(mainImageUrl)
                        .productVariantId(item.getProductVariant().getId())
                        .variantName(item.getProductVariant().getVariantName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getSubtotal())
                        .build();
                itemResponseDTOS.add(orderItemResponseDTO);
            }
            BigDecimal discountAmount = order.getTotalAmount().add(order.getShippingFee()).subtract(order.getFinalAmount());
            OrderResponseDTO dto = OrderResponseDTO.builder()
                    .id(order.getId())
                    .orderCode(order.getOrderCode())
                    .userId(order.getUser().getId())
                    .receiverName(order.getAddress().getReceiverName())
                    .phone(order.getAddress().getPhone())
                    .deliveryAddress(order.getAddress().getFullAddress())
                    .totalAmount(order.getTotalAmount())
                    .shippingFee(order.getShippingFee())
                    .discountAmount(discountAmount)
                    .finalAmount(order.getFinalAmount())
                    .status(order.getStatus())
                    .note(order.getNote())
                    .createdAt(order.getCreatedAt())
                    .payment(PaymentResponseDTO.builder()
                            .paymentMethod(order.getPaymentMethod())
                            .status(order.getPaymentStatus())
                            .amount(order.getFinalAmount())
                            .transactionId(null)
                            .paidAt(null)
                            .build())
                    .items(itemResponseDTOS)
                    .build();
            orderResponseDTOList.add(dto);
        }
        return new PageImpl<>(orderResponseDTOList, pageable, orderPage.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponseDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = lockOrder(orderId);
        validateStatusTransition(order.getStatus(), newStatus);
        if (newStatus == OrderStatus.CANCELLED) {
            validatePaymentCancellation(order);
        } else if (newStatus == OrderStatus.PROCESSING || newStatus == OrderStatus.SHIPPING
                || newStatus == OrderStatus.DELIVERED) {
            requirePaymentBeforeFulfillment(order);
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        ShippingStatus shippingStatus = switch (newStatus) {
            case SHIPPING -> ShippingStatus.PICKED_UP;
            case DELIVERED -> ShippingStatus.DELIVERED;
            case CANCELLED -> ShippingStatus.FAILED;
            default -> null;
        };
        if (shippingStatus != null) {
            shippingRepository.findByOrderId(orderId)
                    .ifPresent(shipping -> applyShippingStatus(shipping, shippingStatus, now));
        }
        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        setOrderStatus(order, newStatus, now);
        String currentNote = order.getNote() != null ? order.getNote() : "";
        order.setNote(currentNote + " [Hệ thống: Admin cập nhật trạng thái thành " + newStatus + "]");

        Order savedOrder = orderRepository.save(order);
        List<OrderItemResponseDTO> itemResponseDTOS = new ArrayList<>();
        for (OrderItem item : savedOrder.getOrderItems()) {
            String mainImageUrl = null;
            List<ProductImage> productImages = productImageRepository.findByProductId(item.getProduct().getId());
            for (ProductImage img : productImages) {
                mainImageUrl = img.getImageUrl();
                break;
            }
            OrderItemResponseDTO orderItemResponseDTO = OrderItemResponseDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productImageUrl(mainImageUrl)
                    .productVariantId(item.getProductVariant().getId())
                    .variantName(item.getProductVariant().getVariantName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .subtotal(item.getSubtotal())
                    .build();
            itemResponseDTOS.add(orderItemResponseDTO);
        }
        BigDecimal discountAmount = order.getTotalAmount().add(order.getShippingFee()).subtract(order.getFinalAmount());
        return OrderResponseDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUser().getId())
                .receiverName(order.getAddress().getReceiverName())
                .phone(order.getAddress().getPhone())
                .deliveryAddress(order.getAddress().getFullAddress())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(discountAmount)
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .payment(PaymentResponseDTO.builder()
                        .paymentMethod(order.getPaymentMethod())
                        .status(order.getPaymentStatus())
                        .amount(order.getFinalAmount())
                        .transactionId(null)
                        .paidAt(null)
                        .build())
                .items(itemResponseDTOS)
                .build();
    }

    @Override
    @Transactional
    public ShippingResponseDTO updateShipping(Long orderId, ShippingUpdateRequestDTO request) {
        Order order = lockOrder(orderId);
        requirePaymentBeforeFulfillment(order);
        OrderStatus orderStatus = order.getStatus();
        ShippingStatus requestedStatus = request.status();
        if (orderStatus != OrderStatus.PROCESSING && orderStatus != OrderStatus.SHIPPING
                && !(orderStatus == OrderStatus.DELIVERED && requestedStatus == ShippingStatus.DELIVERED)) {
            throw new BadRequestException("Không thể cập nhật vận đơn ở trạng thái đơn hàng " + orderStatus);
        }

        Shipping shipping = shippingRepository.findByOrderId(orderId).orElseGet(Shipping::new);
        shipping.setOrder(order);
        shipping.setShippingFee(order.getShippingFee());
        shipping.setShippingProvider(request.shippingProvider().strip());
        shipping.setTrackingNumber(request.trackingNumber().strip());

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        applyShippingStatus(shipping, requestedStatus, now);
        if (orderStatus == OrderStatus.PROCESSING
                && (requestedStatus == ShippingStatus.PICKED_UP || requestedStatus == ShippingStatus.IN_TRANSIT)) {
            setOrderStatus(order, OrderStatus.SHIPPING, now);
        }
        if (orderStatus == OrderStatus.SHIPPING && requestedStatus == ShippingStatus.DELIVERED) {
            setOrderStatus(order, OrderStatus.DELIVERED, now);
        }
        return shippingResponse(shippingRepository.save(shipping));
    }

    @Override
    @Transactional
    public PaymentResponseDTO confirmCodPayment(Long orderId) {
        Order order = lockOrder(orderId);
        if (order.getPaymentMethod() != PaymentMethod.COD) {
            throw new BadRequestException("Đơn hàng không sử dụng phương thức thanh toán COD");
        }

        Payment payment = paymentRepository.findByOrderIdAndPaymentMethod(orderId, PaymentMethod.COD)
                .orElseGet(Payment::new);
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("Đơn hàng đã được hoàn tiền");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return paymentResponse(order, payment);
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Chỉ xác nhận thu tiền COD khi đơn hàng đã giao thành công");
        }

        if (payment.getStatus() != PaymentStatus.PAID) {
            payment.setOrder(order);
            payment.setPaymentMethod(PaymentMethod.COD);
            payment.setAmount(order.getFinalAmount());
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            payment = paymentRepository.save(payment);
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        return paymentResponse(order, payment);
    }

    private PaymentResponseDTO paymentResponse(Order order, Payment payment) {
        PaymentResponseDTO.PaymentResponseDTOBuilder response = PaymentResponseDTO.builder()
                .paymentMethod(order.getPaymentMethod())
                .status(order.getPaymentStatus())
                .amount(order.getFinalAmount());
        if (payment != null) {
            response.id(payment.getId())
                    .transactionId(payment.getTransactionId())
                    .paidAt(payment.getPaidAt());
        }
        return response.build();
    }

    private Order lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng có ID: " + orderId));
    }

    private void validatePaymentCancellation(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                && order.getPaymentStatus() == PaymentStatus.PAID && order.getFinalAmount().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn đã thanh toán chuyển khoản cần được hoàn tiền trước khi hủy");
        }
        if (order.getPaymentMethod() != PaymentMethod.SHOPEEPAY) {
            return;
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID && order.getFinalAmount().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn đã thanh toán ShopeePay cần được hoàn tiền trước khi hủy");
        }
        if (shopeePayIntents.findById(order.getId())
                .filter(intent -> intent.getState() != ShopeePayIntent.State.CLOSED).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cần đối soát ShopeePay trước khi hủy đơn");
        }
    }

    private void requirePaymentBeforeFulfillment(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }
        if (order.getPaymentMethod() == PaymentMethod.SHOPEEPAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng chưa thanh toán ShopeePay");
        }
        if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng chưa thanh toán chuyển khoản");
        }
    }

    private void setOrderStatus(Order order, OrderStatus status, LocalDateTime now) {
        order.setStatus(status);
        if (status == OrderStatus.DELIVERED) {
            order.setDeliveredAt(now);
        }
    }

    private void applyShippingStatus(Shipping shipping, ShippingStatus status, LocalDateTime now) {
        if (shipping.getStatus() == null) {
            shipping.setStatus(ShippingStatus.WAITING);
        }
        ShippingStatus current = shipping.getStatus();
        if (current == status) {
            return;
        }
        boolean allowed = switch (current) {
            case WAITING -> status == ShippingStatus.PICKED_UP || status == ShippingStatus.IN_TRANSIT
                    || status == ShippingStatus.FAILED;
            case PICKED_UP -> status == ShippingStatus.IN_TRANSIT || status == ShippingStatus.DELIVERED
                    || status == ShippingStatus.FAILED;
            case IN_TRANSIT -> status == ShippingStatus.DELIVERED || status == ShippingStatus.FAILED;
            case FAILED -> status == ShippingStatus.WAITING;
            case DELIVERED -> false;
        };
        if (!allowed) {
            throw new BadRequestException("Không thể chuyển trạng thái vận đơn từ " + current + " sang " + status);
        }

        shipping.setStatus(status);
        switch (status) {
            case WAITING -> shipping.setShippedAt(null);
            case PICKED_UP, IN_TRANSIT -> {
                if (shipping.getShippedAt() == null) {
                    shipping.setShippedAt(now);
                }
            }
            case DELIVERED -> shipping.setDeliveredAt(now);
            default -> { }
        }
    }

    private ShippingResponseDTO shippingResponse(Shipping shipping) {
        return ShippingResponseDTO.builder()
                .id(shipping.getId())
                .orderId(shipping.getOrder().getId())
                .shippingProvider(shipping.getShippingProvider())
                .trackingNumber(shipping.getTrackingNumber())
                .shippingFee(shipping.getShippingFee())
                .status(shipping.getStatus())
                .shippedAt(shipping.getShippedAt())
                .deliveredAt(shipping.getDeliveredAt())
                .build();
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            throw new BadRequestException("Trạng thái đơn hàng không được để trống");
        }
        boolean allowed = switch (currentStatus) {
            case PENDING -> newStatus == OrderStatus.PROCESSING || newStatus == OrderStatus.CANCELLED;
            case PROCESSING -> newStatus == OrderStatus.SHIPPING || newStatus == OrderStatus.CANCELLED;
            case SHIPPING -> newStatus == OrderStatus.DELIVERED;
            case DELIVERED -> newStatus == OrderStatus.RETURNED;
            case CANCELLED, RETURNED -> false;
        };
        if (!allowed) {
            throw new BadRequestException("Không thể chuyển trạng thái đơn hàng từ "
                    + currentStatus + " sang " + newStatus);
        }
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            ProductVariant variant = item.getProductVariant();
            variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            productVariantRepository.save(variant);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyRevenueResponseDTO getMonthlyRevenue(int year) {
        if (year < 1000 || year > 9998) {
            throw new BadRequestException("Năm thống kê phải nằm trong khoảng 1000 đến 9998");
        }
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime end = start.plusYears(1);
        List<MonthlyRevenueDTO> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            months.add(new MonthlyRevenueDTO(month, 0L, BigDecimal.ZERO));
        }
        for (var row : orderRepository.calculateMonthlyRevenue(OrderStatus.DELIVERED, start, end)) {
            months.set(row.getMonth() - 1,
                    new MonthlyRevenueDTO(row.getMonth(), row.getTotalOrders(), row.getRevenue()));
        }
        return new MonthlyRevenueResponseDTO(year, "VND", List.copyOf(months));
    }

    @Override
    public BigDecimal getTotalRevenue() {
        // tìm các đơn hàng đã giao thành công
        BigDecimal total = orderRepository.calculateTotalRevenueByStatus(OrderStatus.DELIVERED);
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total;
    }
}
