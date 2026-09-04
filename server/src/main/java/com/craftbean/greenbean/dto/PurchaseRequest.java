package com.craftbean.greenbean.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class PurchaseRequest {
    @NotNull private Long supplierId;
    @NotNull private Long greenBeanId;
    @NotNull private LocalDate purchaseDate;
    private String lotNo;
    @NotNull private BigDecimal quantityKg;
    @NotNull private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
}
