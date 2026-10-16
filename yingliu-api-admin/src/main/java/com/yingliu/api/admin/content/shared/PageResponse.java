package com.yingliu.api.admin.content.shared;

import java.util.List;

public record PageResponse<T>(List<T> items, long page, long pageSize, long total) {
}
