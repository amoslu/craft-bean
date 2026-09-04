package com.craftbean.archive;

import com.craftbean.archive.dto.GreenBeanRequest;
import com.craftbean.common.CurrentUser;
import com.craftbean.common.PageResult;
import com.craftbean.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/archive/green-beans", "/api/v1/archive/green-bean"})
public class GreenBeanController {
    private final GreenBeanService service;

    public GreenBeanController(GreenBeanService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<GreenBean>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.list(keyword, status, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<GreenBean> create(@Valid @RequestBody GreenBeanRequest req) {
        return Result.ok(service.create(req, CurrentUser.get().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<GreenBean> update(@PathVariable Long id, @Valid @RequestBody GreenBeanRequest req) {
        return Result.ok(service.update(id, req, CurrentUser.get().getId()));
    }
}
