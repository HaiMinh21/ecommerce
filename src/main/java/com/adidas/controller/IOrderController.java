package com.adidas.controller;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.entity.OrderStatus;
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
    ResponseEntity<OrderResponseDTO> getOrderByCode(@PathVariable String orderCode);

    // Admin lấy toàn bộ danh sách đơn hàng
    ResponseEntity<Page<OrderResponseDTO>> getAllOrdersForAdmin(OrderStatus orderStatus, int page, int size);
}
