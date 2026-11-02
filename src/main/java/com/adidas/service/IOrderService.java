package com.adidas.service;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.entity.Order;
import com.adidas.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface IOrderService {
    // xử lý đặt hàng
    OrderResponseDTO checkout (Long userId, OrderRequestDTO orderRequestDTO);

    OrderResponseDTO cancelOrder (Long userId, Long orderId);

    // lấy danh sách lịch sử đơn hàng
    Page<OrderResponseDTO> getOrderHistory (Long userId, OrderStatus orderStatus, int page, int size);

    // lấy chi tiết đơn hàng theo orderCode
    OrderResponseDTO getOrderByCode(String orderCode);

    Page<OrderResponseDTO> getAllOrdersForAdmin(OrderStatus status, int page, int size);
}
