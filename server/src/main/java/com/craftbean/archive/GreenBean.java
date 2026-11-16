package com.craftbean.archive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName("green_bean")
public class GreenBean {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String beanType;
    private String name;
    private String origin;
    private String region;
    private String process;
    private String variety;
    private String season;
    private String flavor;
    private String recipe;
    private Integer agtronColor;
    private BigDecimal moisturePct;
    private Integer altitudeM;
    private String recommendedRoast;
    private String packageSpec;
    private Long defaultSupplierId;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    /** 推荐方向：关联的冲煮方式（仅 list?includeMethods=true 时装配） */
    @TableField(exist = false)
    private List<Long> brewMethodIds;
    @TableField(exist = false)
    private List<String> brewMethodNames;
}
