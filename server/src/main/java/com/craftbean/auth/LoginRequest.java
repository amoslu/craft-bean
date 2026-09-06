package com.craftbean.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "手机号后四位不能为空")
    @Pattern(regexp = "\\d{4}", message = "手机号后四位须为4位数字")
    private String phoneTail;
}
