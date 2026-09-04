package com.craftbean.greenbean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("green_lot")
public class GreenLot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String lotNo;
    private Long greenBeanId;
    private Long supplierId;
    private LocalDate purchaseDate;
    private BigDecimal totalKg;
    private BigDecimal remainingKg;
    private BigDecimal costPrice;
    private BigDecimal amount;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
