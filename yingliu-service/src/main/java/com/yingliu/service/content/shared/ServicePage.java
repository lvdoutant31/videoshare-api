package com.yingliu.service.content.shared;

import java.util.List;

/** 业务分页结果，items 为当前页记录，total 为匹配查询条件的总数。 */
public record ServicePage<T>(List<T> items, long total) {
}
