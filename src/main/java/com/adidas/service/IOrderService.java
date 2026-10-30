package com.adidas.service;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public interface IOrderService {
    // xử lý đặt hàng
    OrderResponseDTO checkout (Long userId, OrderRequestDTO orderRequestDTO);

    OrderResponseDTO cancelOrder (Long userId, Long orderId);

    Page<OrderResponseDTO> getOrderHistory (Long userId, int page, int size)
}
