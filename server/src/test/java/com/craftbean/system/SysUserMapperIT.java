package com.craftbean.system;

import static org.assertj.core.api.Assertions.assertThat;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SysUserMapperIT {

    @Autowired
    SysUserMapper mapper;

    @Test
    void adminSeededByInitializer() {
        SysUser admin = mapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
    }
}
