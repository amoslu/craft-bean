package com.craftbean.system.dto;

import com.craftbean.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
    private String newPassword;
}
