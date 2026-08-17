package com.querylens.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalyzeRequest {
    @NotBlank(message = "SQL query is required")
    private String sql;
}
