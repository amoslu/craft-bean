package com.craftbean.archive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("green_bean")
public class GreenBean {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String origin;
    private String process;
    private String variety;
    private Long defaultSupplierId;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
