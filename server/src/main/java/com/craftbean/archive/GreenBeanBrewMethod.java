package com.craftbean.archive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 生豆-冲煮方式关联（生豆「推荐方向」）；明细表无 deleted */
@Data
@TableName("green_bean_brew_method")
public class GreenBeanBrewMethod {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long greenBeanId;
    private Long brewMethodId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
