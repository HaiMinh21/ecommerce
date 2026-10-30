package com.adidas.controller;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import com.adidas.service.IOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements IOrderController{

    @Autowired
    private final IOrderService orderService;

    @Override
    @PostMapping("users/{userId}/checkout")
    public ResponseEntity<OrderResponseDTO> checkout(@PathVariable Long userId,@Valid @RequestBody OrderRequestDTO orderRequestDTO) {
        OrderResponseDTO orderResponseDTO = orderService.checkout(userId, orderRequestDTO);
        return ResponseEntity.ok(orderResponseDTO);
    }

    @Override
    @PutMapping("users/{userId}/cancel/{orderId}")
    public ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable Long userId,@PathVariable Long orderId) {
        OrderResponseDTO orderResponseDTO = orderService.cancelOrder(userId, orderId);
        return ResponseEntity.ok(orderResponseDTO);
    }
}
