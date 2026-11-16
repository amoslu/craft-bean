package com.craftbean.greenbean.dto;

/** 批次作废入参。reason 可选，仅记入流水备注与审计日志。 */
public record VoidLotRequest(String reason) {
}
