package com.craftbean.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;
    /** 手机号可选：留空 = 不修改（存量无号账号保持 0000 兜底）；非空则须为合法手机号。 */
    @Pattern(regexp = "^(1\\d{10})?$", message = "手机号格式不正确")
    private String phone;
    @NotBlank(message = "角色不能为空")
    private String role;
    @NotBlank(message = "状态不能为空")
    private String status;
}
