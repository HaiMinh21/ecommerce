package com.adidas.dto.response;

import com.adidas.entity.PaymentStatus;

public record BankTransferResponseDTO(Long orderId, String orderCode, PaymentStatus paymentStatus, long amount,
                                      String bank, String accountNumber, String transferCode, String qrUrl) {
}
