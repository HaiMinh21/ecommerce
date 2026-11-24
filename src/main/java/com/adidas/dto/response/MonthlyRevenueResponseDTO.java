package com.adidas.dto.response;

import java.util.List;

public record MonthlyRevenueResponseDTO(int year, String currency, List<MonthlyRevenueDTO> months) {
}