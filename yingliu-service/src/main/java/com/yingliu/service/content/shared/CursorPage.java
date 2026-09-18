package com.yingliu.service.content.shared;

import java.util.List;

/** 游标分页结果，用于手机端连续加载，游标格式由查询实现维护。 */
public record CursorPage<T>(List<T> items, String nextCursor) {
}
