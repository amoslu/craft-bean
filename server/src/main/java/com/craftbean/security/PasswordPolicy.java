package com.craftbean.security;

/** 密码强度策略：至少 8 位，且须同时包含字母、数字与特殊符号（非字母数字字符）。 */
public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    /** 编译期常量，供 @Pattern(regexp = PasswordPolicy.PATTERN) 引用。 */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";
    public static final String MESSAGE = "密码至少8位，且须同时包含字母、数字与特殊符号";
}
