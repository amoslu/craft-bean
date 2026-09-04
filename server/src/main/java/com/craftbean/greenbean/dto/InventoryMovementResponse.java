package com.craftbean.greenbean.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryMovementResponse(Long id, String bizType, String batchKind, Long batchId,
                                        String lotNo, Long greenBeanId, String greenBeanName,
                                        Long supplierId, BigDecimal changeKg, BigDecimal unitPrice,
                                        BigDecimal amount, String refDocNo, Long operatorId,
                                        LocalDate movementDate, String remark, LocalDateTime createdAt) {
}
