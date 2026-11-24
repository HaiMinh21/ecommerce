package com.adidas.repository.projection;

import java.math.BigDecimal;

public interface MonthlyRevenueProjection {
    Integer getMonth();
    Long getTotalOrders();
    BigDecimal getRevenue();
}