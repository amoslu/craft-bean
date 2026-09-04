package com.craftbean.greenbean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("inventory_movement")
public class InventoryMovement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String batchKind;
    private Long batchId;
    private Long greenBeanId;
    private Long supplierId;
    private BigDecimal changeKg;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String refDocNo;
    private Long operatorId;
    private LocalDate movementDate;
    private String remark;
    private LocalDateTime createdAt;
}
