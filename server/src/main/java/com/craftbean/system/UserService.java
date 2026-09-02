package com.craftbean.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.audit.AuditService;
import com.craftbean.common.AppException;
import com.craftbean.common.PageResult;
import com.craftbean.system.dto.CreateUserRequest;
import com.craftbean.system.dto.UpdateUserRequest;
import com.craftbean.system.dto.UserResponse;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    private static final Set<String> ROLES = Set.of("ADMIN", "STAFF", "READONLY");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private final SysUserMapper userMapper;
    private final AuditService auditService;
    private final BCryptPasswordEncoder encoder;

    public UserService(SysUserMapper userMapper, AuditService auditService, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.encoder = encoder;
    }

    public PageResult<UserResponse> list(String keyword, String role, String status, long page, long size) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(SysUser::getUsername, keyword).or().like(SysUser::getName, keyword));
        }
        if (StringUtils.hasText(role)) {
            w.eq(SysUser::getRole, role);
        }
        if (StringUtils.hasText(status)) {
            w.eq(SysUser::getStatus, status);
        }
        w.orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), w);
        List<UserResponse> list = p.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(list, p.getTotal());
    }

    @Transactional
    public UserResponse create(CreateUserRequest req, Long operatorId) {
        if (!ROLES.contains(req.getRole())) {
            throw new AppException(400, 400, "角色不合法");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new AppException(400, 400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(req.getUsername());
        u.setName(req.getName());
        u.setPasswordHash(encoder.encode(req.getPassword()));
        u.setRole(req.getRole());
        u.setStatus("ACTIVE");
        u.setFailedAttempts(0);
        userMapper.insert(u);
        auditService.record(operatorId, "USER_CREATE", u.getUsername(), "创建用户 " + u.getUsername());
        return toResponse(u);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, Long operatorId) {
        SysUser target = requireUser(id);
        if (!ROLES.contains(req.getRole())) {
            throw new AppException(400, 400, "角色不合法");
        }
        if (!STATUSES.contains(req.getStatus())) {
            throw new AppException(400, 400, "状态不合法");
        }
        guardSelfAndLastAdmin(target, req.getRole(), req.getStatus(), operatorId);
        target.setName(req.getName());
        target.setRole(req.getRole());
        target.setStatus(req.getStatus());
        userMapper.updateById(target);
        auditService.record(operatorId, "USER_UPDATE", target.getUsername(),
                "更新用户 role=" + req.getRole() + " status=" + req.getStatus());
        return toResponse(target);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, Long operatorId) {
        SysUser target = requireUser(id);
        target.setPasswordHash(encoder.encode(newPassword));
        target.setFailedAttempts(0);
        target.setLockedUntil(null);
        userMapper.updateById(target);
        auditService.record(operatorId, "USER_RESET_PASSWORD", target.getUsername(), "重置密码");
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        SysUser target = requireUser(id);
        if (target.getId().equals(operatorId)) {
            throw new AppException(400, 400, "不能删除自己");
        }
        if ("ADMIN".equals(target.getRole()) && "ACTIVE".equals(target.getStatus()) && countActiveAdmins() <= 1) {
            throw new AppException(400, 400, "不能删除最后一个管理员");
        }
        userMapper.deleteById(id);
        auditService.record(operatorId, "USER_DELETE", target.getUsername(), "删除用户 " + target.getUsername());
    }

    private SysUser requireUser(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new AppException(404, 404, "用户不存在");
        }
        return u;
    }

    private void guardSelfAndLastAdmin(SysUser target, String newRole, String newStatus, Long operatorId) {
        boolean isSelf = target.getId().equals(operatorId);
        if (isSelf) {
            if ("DISABLED".equals(newStatus)) {
                throw new AppException(400, 400, "不能停用自己");
            }
            if (!target.getRole().equals(newRole)) {
                throw new AppException(400, 400, "不能修改自己的角色");
            }
        }
        boolean removingActiveAdmin = "ADMIN".equals(target.getRole()) && "ACTIVE".equals(target.getStatus())
                && ("DISABLED".equals(newStatus) || !"ADMIN".equals(newRole));
        if (removingActiveAdmin && countActiveAdmins() <= 1) {
            throw new AppException(400, 400, "不能停用或降级最后一个管理员");
        }
    }

    private long countActiveAdmins() {
        Long c = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "ADMIN").eq(SysUser::getStatus, "ACTIVE"));
        return c == null ? 0 : c;
    }

    private UserResponse toResponse(SysUser u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getName(), u.getRole(),
                u.getStatus(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
