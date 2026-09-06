package com.craftbean.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);
    /** 内置初始密码：满足强密码策略（≥8 位，含字母+数字+特殊符号），admin 无手机号故登录尾号为 0000。 */
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@123456";
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public BootstrapDataInitializer(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        if (count != null && count == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPasswordHash(encoder.encode(DEFAULT_ADMIN_PASSWORD));
            admin.setName("管理员");
            admin.setRole("ADMIN");
            admin.setStatus("ACTIVE");
            userMapper.insert(admin);
            log.warn("已创建默认管理员 admin/{}，请尽快修改密码（登录尾号默认 0000）", DEFAULT_ADMIN_PASSWORD);
        }
    }
}
