package com.adidas.controller;

import com.adidas.dto.request.OrderRequestDTO;
import com.adidas.dto.response.OrderResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public interface IOrderController {

    ResponseEntity<OrderResponseDTO> checkout(@PathVariable Long userId, @Valid @RequestBody OrderRequestDTO orderRequestDTO);

    ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable Long userId, @PathVariable Long orderId);
}
