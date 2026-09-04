package com.craftbean.greenbean;

import com.craftbean.common.CurrentUser;
import com.craftbean.common.PageResult;
import com.craftbean.common.Result;
import com.craftbean.greenbean.dto.GreenLotResponse;
import com.craftbean.greenbean.dto.InventoryMovementResponse;
import com.craftbean.greenbean.dto.PurchaseRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/greenbean")
public class PurchaseController {
    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @PostMapping({"/purchases", "/purchase"})
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<GreenLotResponse> purchase(@Valid @RequestBody PurchaseRequest req) {
        return Result.ok(service.purchase(req, CurrentUser.get().getId()));
    }

    @GetMapping({"/inventory/lots", "/inventory", "/lots"})
    public Result<PageResult<GreenLotResponse>> lots(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) Long greenBeanId,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.listLots(keyword, status, supplierId, greenBeanId, page, size));
    }

    @GetMapping({"/inventory/movements", "/movements"})
    public Result<PageResult<InventoryMovementResponse>> movements(
            @RequestParam(required = false) Long greenBeanId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.listMovements(greenBeanId, lotId, bizType, page, size));
    }
}
