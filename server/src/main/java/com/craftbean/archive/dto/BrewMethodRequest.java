package com.craftbean.archive.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrewMethodRequest {
    @NotBlank
    private String name;
    private String status;
}
