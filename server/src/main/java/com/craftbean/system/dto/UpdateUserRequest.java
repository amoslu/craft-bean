package com.craftbean.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "角色不能为空")
    private String role;
    @NotBlank(message = "状态不能为空")
    private String status;
}
