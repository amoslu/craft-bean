package com.craftbean.archive.dto;

import lombok.Data;

@Data
public class GreenBeanRequest {
    @jakarta.validation.constraints.NotBlank
    private String name;
    private String origin;
    private String process;
    private String variety;
    private Long defaultSupplierId;
    private String status;
    private String remark;
}
