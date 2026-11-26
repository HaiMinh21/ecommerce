package com.adidas.controller;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.request.ShippingUpdateRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.dto.response.MonthlyRevenueResponseDTO;
import com.adidas.dto.response.PaymentResponseDTO;
import com.adidas.dto.response.ShippingResponseDTO;
import com.adidas.entity.OrderStatus;
import com.adidas.security.CustomUserDetails;
import com.adidas.service.IOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements IOrderController{

    @Autowired
    private final IOrderService orderService;

    @Override
    @PostMapping("users/{userId}/checkout")
    @PreAuthorize("#userId == principal.user.id")
    public ResponseEntity<OrderResponseDTO> checkout(@PathVariable Long userId,@Valid @RequestBody OrderRequestDTO orderRequestDTO) {
        OrderResponseDTO orderResponseDTO = orderService.checkout(userId, orderRequestDTO);
        return ResponseEntity.ok(orderResponseDTO);
    }

    @Override
    @PutMapping("users/{userId}/cancel/{orderId}")
    @PreAuthorize("#userId == principal.user.id")
    public ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable Long userId,@PathVariable Long orderId) {
        OrderResponseDTO orderResponseDTO = orderService.cancelOrder(userId, orderId);
        return ResponseEntity.ok(orderResponseDTO);
    }

    @Override
    @GetMapping("users/{userId}/history")
    @PreAuthorize("#userId == principal.user.id")
    public ResponseEntity<Page<OrderResponseDTO>> getOrderHistory(@PathVariable Long userId, @RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Page<OrderResponseDTO> orderResponseDTOS = orderService.getOrderHistory(userId, status, page, size);
        return ResponseEntity.ok(orderResponseDTOS);
    }

    @Override
    @GetMapping("/{orderCode}")
    public ResponseEntity<OrderResponseDTO> getOrderByCode(@PathVariable String orderCode,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        boolean admin = principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        OrderResponseDTO orderResponseDTO = orderService.getOrderByCode(orderCode, principal.getUser().getId(), admin);
        return ResponseEntity.ok(orderResponseDTO);
    }

    @Override
    @GetMapping("/admin")
    public ResponseEntity<Page<OrderResponseDTO>> getAllOrdersForAdmin(@RequestParam(required = false) OrderStatus orderStatus,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "10") int size) {
        Page<OrderResponseDTO> orders = orderService.getAllOrdersForAdmin(orderStatus, page, size);
        return ResponseEntity.ok(orders);
    }

    @Override
    @PutMapping("/admin/status/{orderId}")
    public ResponseEntity<OrderResponseDTO> updateOrderStatus(@PathVariable Long orderId,@RequestParam OrderStatus newStatus) {
        OrderResponseDTO updateOrder = orderService.updateOrderStatus(orderId, newStatus);
        return ResponseEntity.ok(updateOrder);
    }

    @Override
    @PutMapping("/admin/{orderId}/shipping")
    public ResponseEntity<ShippingResponseDTO> updateShipping(@PathVariable Long orderId,
                                                             @Valid @RequestBody ShippingUpdateRequestDTO request) {
        return ResponseEntity.ok(orderService.updateShipping(orderId, request));
    }

    @Override
    @PutMapping("/admin/{orderId}/payments/cod")
    public ResponseEntity<PaymentResponseDTO> confirmCodPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.confirmCodPayment(orderId));
    }

    @Override
    @GetMapping("/admin/revenue/monthly")
    public ResponseEntity<MonthlyRevenueResponseDTO> getMonthlyRevenue(@RequestParam int year) {
        return ResponseEntity.ok(orderService.getMonthlyRevenue(year));
    }

    @Override
    @GetMapping("/admin/revenue")
    public ResponseEntity getTotalRevenue() {
        BigDecimal totalRevenue = orderService.getTotalRevenue();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Tổng doanh thu các đơn hàng đã giao thành công");
        response.put("total", totalRevenue);
        response.put("currency", "VND");
        return ResponseEntity.ok(response);
    }
}

