package com.yingliu.api.admin.content.category;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {
    private CategoryDtos() { }

    public record Create(@NotBlank @Size(max = 64) String name, @Size(max = 128) String icon,
                         @Min(0) Integer sort, String status) { }
    public record Update(@Size(max = 64) String name, @Size(max = 128) String icon,
                         @Min(0) Integer sort, String status) { }
    public record View(String id, String name, String icon, int sort, String status,
                       int deleted, String createdAt, String updatedAt) { }
}
