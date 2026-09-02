package com.craftbean.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.craftbean.common.AppException;
import com.craftbean.common.CurrentUser;
import com.craftbean.common.Result;
import com.craftbean.security.JwtService;
import com.craftbean.system.SysUser;
import com.craftbean.system.SysUserMapper;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper userMapper, JwtService jwtService) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw AppException.unauthorized("用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        CurrentUserResponse cur = new CurrentUserResponse(user.getId(), user.getUsername(), user.getName(), user.getRole());
        return Result.ok(new LoginResponse(token, cur));
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me() {
        SysUser user = CurrentUser.get();
        return Result.ok(new CurrentUserResponse(user.getId(), user.getUsername(), user.getName(), user.getRole()));
    }
}
