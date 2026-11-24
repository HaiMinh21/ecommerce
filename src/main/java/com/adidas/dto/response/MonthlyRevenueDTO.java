package com.adidas.dto.response;

import java.math.BigDecimal;

public record MonthlyRevenueDTO(int month, long totalOrders, BigDecimal revenue) {
}