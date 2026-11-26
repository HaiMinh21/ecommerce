package com.adidas.controller;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.request.ShippingUpdateRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.dto.response.MonthlyRevenueResponseDTO;
import com.adidas.dto.response.PaymentResponseDTO;
import com.adidas.dto.response.ShippingResponseDTO;
import com.adidas.entity.OrderStatus;
import com.adidas.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public interface IOrderController {

    ResponseEntity<OrderResponseDTO> checkout(@PathVariable Long userId, @Valid @RequestBody OrderRequestDTO orderRequestDTO);

    ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable Long userId, @PathVariable Long orderId);

    // xem lịch sử đơn hàng
    ResponseEntity<Page<OrderResponseDTO>> getOrderHistory (Long userId, OrderStatus status, int page, int size);

    // Lấy chi tiết đơn hàng theo mã đơn hàng
    ResponseEntity<OrderResponseDTO> getOrderByCode(@PathVariable String orderCode, CustomUserDetails principal);

    // Admin lấy toàn bộ danh sách đơn hàng
    ResponseEntity<Page<OrderResponseDTO>> getAllOrdersForAdmin(OrderStatus orderStatus, int page, int size);

    ResponseEntity<OrderResponseDTO> updateOrderStatus (Long orderId, OrderStatus newStatus);

    // Quản lý giao hàng: Admin nhập đơn vị vận chuyển, mã vận đơn và cập nhật trạng thái giao hàng. Khách cũng xem được tiến độ trong chi tiết đơn của mình.
    ResponseEntity<ShippingResponseDTO> updateShipping(Long orderId, ShippingUpdateRequestDTO request);

    // Xác nhận đã thu tiền mặt (COD): Sau khi đơn đã giao thành công, admin xác nhận đã thu tiền. Hệ thống ghi nhận thời điểm thu và chuyển trạng thái thanh toán sang PAID.
    ResponseEntity<PaymentResponseDTO> confirmCodPayment(Long orderId);

    // Thống kê doanh thu thực tế
    ResponseEntity<MonthlyRevenueResponseDTO> getMonthlyRevenue(int year);

    // Tổng doanh thu của tất cả các đơn hàng đã giao thành công
    ResponseEntity getTotalRevenue();
}
