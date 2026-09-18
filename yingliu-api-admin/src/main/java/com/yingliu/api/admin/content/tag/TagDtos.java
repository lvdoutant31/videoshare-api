package com.yingliu.api.admin.content.tag;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class TagDtos {
    private TagDtos() { }

    public record Create(@NotBlank @Size(max = 64) String name, String status,
                         @DecimalMin("0") BigDecimal heatScore) { }
    public record Update(@Size(max = 64) String name, String status,
                         @DecimalMin("0") BigDecimal heatScore) { }
    public record View(String id, String name, String status, BigDecimal heatScore,
                       int deleted, String createdAt, String updatedAt) { }
}
