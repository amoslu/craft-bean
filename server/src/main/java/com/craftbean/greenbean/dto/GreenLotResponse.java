package com.craftbean.greenbean.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GreenLotResponse(Long id, String lotNo, Long greenBeanId, String greenBeanName,
                               Long supplierId, String supplierName, LocalDate purchaseDate,
                               BigDecimal totalKg, BigDecimal remainingKg, BigDecimal costPrice,
                               BigDecimal amount, String status, String remark, LocalDateTime createdAt) {
}
