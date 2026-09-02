package com.craftbean.system;

import com.craftbean.common.CurrentUser;
import com.craftbean.common.PageResult;
import com.craftbean.common.Result;
import com.craftbean.system.dto.CreateUserRequest;
import com.craftbean.system.dto.ResetPasswordRequest;
import com.craftbean.system.dto.UpdateUserRequest;
import com.craftbean.system.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<UserResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(userService.list(keyword, role, status, page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        return Result.ok(userService.create(req, CurrentUser.get().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return Result.ok(userService.update(id, req, CurrentUser.get().getId()));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.getNewPassword(), CurrentUser.get().getId());
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id, CurrentUser.get().getId());
        return Result.ok(null);
    }
}
