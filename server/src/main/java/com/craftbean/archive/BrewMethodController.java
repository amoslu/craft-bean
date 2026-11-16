package com.craftbean.archive;

import com.craftbean.archive.dto.BrewMethodRequest;
import com.craftbean.common.CurrentUser;
import com.craftbean.common.PageResult;
import com.craftbean.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/archive/brew-methods")
public class BrewMethodController {
    private final BrewMethodService service;

    public BrewMethodController(BrewMethodService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<BrewMethod>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.list(keyword, status, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<BrewMethod> create(@Valid @RequestBody BrewMethodRequest req) {
        return Result.ok(service.create(req, CurrentUser.get().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<BrewMethod> update(@PathVariable Long id, @Valid @RequestBody BrewMethodRequest req) {
        return Result.ok(service.update(id, req, CurrentUser.get().getId()));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<BrewMethod> status(@PathVariable Long id, @RequestParam String status) {
        return Result.ok(service.changeStatus(id, status, CurrentUser.get().getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.get().getId());
        return Result.ok(null);
    }
}
