package com.craftbean.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.craftbean.audit.AuditService;
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
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper userMapper, JwtService jwtService, AuditService auditService) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) {
            throw AppException.unauthorized("用户名或密码错误");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw AppException.unauthorized("账号已锁定，请15分钟后再试");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw AppException.unauthorized("账号已停用");
        }
        if (!encoder.matches(req.getPassword(), user.getPasswordHash())) {
            int failed = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
            user.setFailedAttempts(failed);
            if (failed >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plusMinutes(15));
                user.setFailedAttempts(0);
            }
            userMapper.updateById(user);
            throw AppException.unauthorized("用户名或密码错误");
        }

        user.setLastLoginAt(now);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        CurrentUserResponse cur = new CurrentUserResponse(user.getId(), user.getUsername(), user.getName(), user.getRole());
        auditService.record(user.getId(), "LOGIN", user.getUsername(), "登录成功");
        return Result.ok(new LoginResponse(token, cur));
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me() {
        SysUser user = CurrentUser.get();
        return Result.ok(new CurrentUserResponse(user.getId(), user.getUsername(), user.getName(), user.getRole()));
    }
}
