package com.craftbean.archive;

import com.craftbean.archive.dto.SupplierRequest;
import com.craftbean.common.CurrentUser;
import com.craftbean.common.PageResult;
import com.craftbean.common.Result;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/archive/suppliers")
public class SupplierController {
    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<Supplier>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.list(keyword, status, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Supplier> create(@Valid @RequestBody SupplierRequest req) {
        return Result.ok(service.create(req, CurrentUser.get().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Supplier> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        return Result.ok(service.update(id, req, CurrentUser.get().getId()));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Supplier> status(@PathVariable Long id, @RequestParam String status) {
        return Result.ok(service.changeStatus(id, status, CurrentUser.get().getId()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Supplier> updateStatus(@PathVariable Long id,
                                         @RequestParam(required = false) String status,
                                         @RequestBody(required = false) Map<String, String> body) {
        String next = status;
        if (next == null && body != null) next = body.get("status");
        return Result.ok(service.changeStatus(id, next, CurrentUser.get().getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.get().getId());
        return Result.ok(null);
    }
}
