package com.craftbean.archive.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class GreenBeanRequest {
    @NotBlank
    private String name;
    private String beanType;
    private String code;
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
    /** 推荐方向：冲煮方式 id 列表，整存整删 */
    private List<Long> brewMethodIds;
}
