package com.adidas.service;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.request.ShippingUpdateRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.dto.response.MonthlyRevenueResponseDTO;
import com.adidas.dto.response.PaymentResponseDTO;
import com.adidas.dto.response.ShippingResponseDTO;
import com.adidas.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public interface IOrderService {
    // xử lý đặt hàng
    OrderResponseDTO checkout (Long userId, OrderRequestDTO orderRequestDTO);

    OrderResponseDTO cancelOrder (Long userId, Long orderId);

    // lấy danh sách lịch sử đơn hàng
    Page<OrderResponseDTO> getOrderHistory (Long userId, OrderStatus orderStatus, int page, int size);

    // lấy chi tiết đơn hàng theo orderCode
    OrderResponseDTO getOrderByCode(String orderCode, Long userId, boolean admin);

    Page<OrderResponseDTO> getAllOrdersForAdmin(OrderStatus status, int page, int size);

    OrderResponseDTO updateOrderStatus (Long orderId, OrderStatus orderStatus);

    ShippingResponseDTO updateShipping(Long orderId, ShippingUpdateRequestDTO request);

    PaymentResponseDTO confirmCodPayment(Long orderId);

    MonthlyRevenueResponseDTO getMonthlyRevenue(int year);

    BigDecimal getTotalRevenue();
}
