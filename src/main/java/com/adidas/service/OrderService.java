package com.adidas.service;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.response.OrderItemResponseDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.dto.response.PaymentResponseDTO;
import com.adidas.entity.*;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ICartRepository cartRepository;
    private final IProductVariantRepository productVariantRepository;
    private final IUserRepository userRepository;
    private final IAddressRepository addressRepository;
    private final ICouponRepository couponRepository;
    private final IProductImageRepository productImageRepository;

    public OrderService(IOrderRepository orderRepository, ICartRepository cartRepository, IProductVariantRepository productVariantRepository, IUserRepository userRepository, IAddressRepository addressRepository, ICouponRepository couponRepository, IProductImageRepository productImageRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.couponRepository = couponRepository;
        this.productImageRepository = productImageRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponseDTO checkout(Long userId, OrderRequestDTO orderRequestDTO) {
        // Lấy và kiểm tra giỏ hàng
        Cart cart = cartRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng"));
        if (cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Giỏ hàng của bạn đang trống. Không thể đặt hàng");
        }
        Users user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // kiểm tra địa chỉ và mã giảm giá
        Address address = addressRepository.findById(orderRequestDTO.getAddressId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng"));

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
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn"));
        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền hủy đơn hàng của người khác");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy đơn hàng ở trạng thái chờ xác nhận (Pending). Trạng thái hiện tại: " + order.getStatus());
        }
        // tra lại số lượng tồn kho
        for (OrderItem item : order.getOrderItems()) {
            ProductVariant variant = item.getProductVariant();
            variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            productVariantRepository.save(variant);
        }
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
    public Page<OrderResponseDTO> getOrderHistory(Long userId, int page, int size) {
        return null;
    }
}
