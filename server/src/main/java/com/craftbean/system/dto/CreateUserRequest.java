package com.craftbean.system.dto;

import com.craftbean.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
    private String password;
    @NotBlank(message = "角色不能为空")
    private String role;
}
