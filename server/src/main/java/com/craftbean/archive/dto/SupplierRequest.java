package com.craftbean.archive.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank private String name;
    private String contactPerson;
    private String phone;
    private String wechat;
    private String address;
    private String mainCategory;
    private String status;
}
